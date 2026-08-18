@file:UseSerializers(
    DateTimeConverter::class,
    NoteTypeConverter::class,
    NoteStatusConverter::class,
    NoteMetadataConverter::class,
    PinnedStatusConverter::class
)

package com.mckimquyen.notes.model

import android.content.Context
import android.util.Base64
import androidx.annotation.Keep
import androidx.room.ColumnInfo
import com.mckimquyen.notes.model.JsonManager.ImportResult
import com.mckimquyen.notes.model.converter.DateTimeConverter
import com.mckimquyen.notes.model.converter.NoteMetadataConverter
import com.mckimquyen.notes.model.converter.NoteStatusConverter
import com.mckimquyen.notes.model.converter.NoteTypeConverter
import com.mckimquyen.notes.model.converter.PinnedStatusConverter
import com.mckimquyen.notes.model.entity.Label
import com.mckimquyen.notes.model.entity.LabelRef
import com.mckimquyen.notes.model.entity.Note
import com.mckimquyen.notes.model.entity.NoteMetadata
import com.mckimquyen.notes.model.entity.NoteStatus
import com.mckimquyen.notes.model.entity.NoteType
import com.mckimquyen.notes.model.entity.PinnedStatus
import com.mckimquyen.notes.model.entity.Reminder
import com.mckimquyen.notes.widget.NoteCountWidget
import com.mckimquyen.notes.widget.RecentNotesWidget
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.util.Date
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

class DefaultJsonManager @Inject constructor(
    private val context: Context,
    private val notesDb: NotesDb,
    private val notesDao: NotesDao,
    private val labelsDao: LabelsDao,
    private val json: Json,
    private val reminderAlarmManager: ReminderAlarmManager,
    private val prefs: PrefsManager,
) : JsonManager {

    override suspend fun exportJsonData(): String {
        // Map notes by ID, with labels
        val notesMap = mutableMapOf<Long, NoteSurrogate>()
        val notesList = notesDao.getAll()
        for (noteWithLabels in notesList) {
            val note = noteWithLabels.note
            notesMap[note.id] = NoteSurrogate(note.type, note.title, note.content,
                note.metadata, note.addedDate, note.lastModifiedDate, note.status, note.pinned,
                note.reminder, noteWithLabels.labels.map { it.id }, note.color, note.mood, note.isLocked)
        }

        // Map labels by ID
        val labelsMap = mutableMapOf<Long, Label>()
        val labelsList = labelsDao.getAll()
        for (label in labelsList) {
            labelsMap[label.id] = label
        }

        // Encode to JSON and insert labels afterwards
        val notesData = NotesData(VERSION, notesMap, labelsMap)

        // Handle optional encryption
        return if (prefs.shouldEncryptExportedData) {
            json.encodeToString(encryptNotesData(notesData))
        } else {
            json.encodeToString(notesData)
        }
    }

    private suspend fun encryptNotesData(notesData: NotesData): EncryptedNotesData {
        // Load the Android key store
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        withContext(Dispatchers.IO) {
            keyStore.load(null)
        }
        // Retrieve the encryption key
        val keyStoreKey = keyStore.getKey(EXPORT_ENCRYPTION_KEY_ALIAS, null)

        // Initialize cipher object
        val cipher = Cipher.getInstance(EXPORT_ENCRYPTION_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, keyStoreKey)

        // Encrypt notesData
        val ciphertext = cipher.doFinal(json.encodeToString(notesData).toByteArray(Charsets.UTF_8))

        return EncryptedNotesData(
            salt = prefs.encryptedExportKeyDerivationSalt,
            nonce = Base64.encodeToString(cipher.iv, BASE64_FLAGS),
            ciphertext = Base64.encodeToString(ciphertext, BASE64_FLAGS)
        )
    }

    override suspend fun importJsonData(
        data: String,
        importKey: SecretKey?,
    ): ImportResult {
        // JSON can either describe an EncryptedNotesData object or a NotesData object.
        val jsonData: String = try {
            val encryptedNotesData: EncryptedNotesData = json.decodeFromString(data)

            // Key needs to be derived in order to decrypt the backup file
            if (importKey == null) {
                prefs.encryptedImportKeyDerivationSalt = encryptedNotesData.salt
                return ImportResult.KEY_MISSING_OR_INCORRECT
            }

            // Get GCM nonce
            val nonce = Base64.decode(encryptedNotesData.nonce, BASE64_FLAGS)
            val gcmParameterSpec = GCMParameterSpec(128, nonce)

            // Initialize the cipher object
            val cipher = Cipher.getInstance(EXPORT_ENCRYPTION_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, importKey, gcmParameterSpec)

            val ciphertext = Base64.decode(encryptedNotesData.ciphertext, BASE64_FLAGS)
            val plaintext = try {
                cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
            } catch (e: AEADBadTagException) {
                // This mainly occurs when the user has entered the wrong password
                return ImportResult.KEY_MISSING_OR_INCORRECT
            } catch (e: Exception) {
                return ImportResult.BAD_DATA
            }
            plaintext
        } catch (e: Exception) {
            // Data is probably not encrypted.
            data
        }

        val notesData: NotesData = try {
            json.decodeFromString(jsonData)
        } catch (e: BadDataException) {
            // could happen if user imported data from future version, which has incompatibilities.
            return ImportResult.BAD_DATA
        } catch (e: Exception) {
            // bad json structure, missing required fields, field has bad value, etc.
            return ImportResult.BAD_FORMAT
        }

        if (notesData.version < FIRST_VERSION) {
            // first version is 3, this data is clearly wrong.
            return ImportResult.BAD_DATA
        }

        // Import all data. Wrapped in one transaction — importNotes() collects labelRefs into
        // a local list and only flushes them in a single insertRefs() call at the very end, so
        // without a transaction, a process kill partway through left already-inserted notes
        // permanently orphaned from their labels. A bad note also throws inside the Note
        // constructor's invariant checks (see debugRequire) with nothing catching it before
        // this point, crashing the app mid-import instead of failing cleanly. FIX-H05.
        try {
            notesDb.withTransaction {
                val newLabelsMap = importLabels(notesData)
                importNotes(notesData, newLabelsMap)
            }
        } catch (e: CancellationException) {
            // CancellationException is a subtype of Exception on the JVM — rethrow it so
            // structured concurrency isn't violated (e.g. ViewModel scope cancelled mid-import
            // because the user backed out of Settings). A blanket catch(Exception) here would
            // swallow the cancellation and misreport it as BAD_DATA. Found by review.
            throw e
        } catch (e: Exception) {
            return ImportResult.BAD_DATA
        }

        // Wrapped in NonCancellable for the same reason every DB-mutating method in
        // DefaultNotesRepository/DefaultLabelsRepository is (see FIX-M23 in this same sprint):
        // the transaction above already committed successfully, so a cancellation landing here
        // shouldn't leave reminders unscheduled or widgets stale for imported notes. Found by
        // review — this import path wasn't given the same treatment as the repositories.
        withContext(NonCancellable) {
            // Update all reminders
            reminderAlarmManager.updateAllAlarms()

            // Import writes directly through notesDao/labelsDao, bypassing DefaultNotesRepository
            // (the only other place these widgets get refreshed) — without this, NoteCountWidget
            // stays wrong for up to its 30-minute update period and RecentNotesWidget (which has
            // no periodic update at all) never refreshes until some other note action happens. FIX-M05.
            NoteCountWidget.updateAllWidgets(context)
            RecentNotesWidget.updateAllWidgets(context)
        }

        return if (notesData.version > VERSION) {
            // data comes from future version of app
            ImportResult.FUTURE_VERSION
        } else {
            ImportResult.SUCCESS
        }
    }

    private suspend fun importLabels(notesData: NotesData): Map<Long, Long> {
        val existingLabels = labelsDao.getAll()
        val existingLabelsIdMap = existingLabels.associateBy { it.id }
        // Lowercased keys so import dedup matches getLabelByName()'s case-insensitive lookup —
        // otherwise "Work" and "work" would both survive as separate labels through import.
        // FIX-M26.
        val existingLabelsNameMap = existingLabels.associateBy { it.name.lowercase() }
        val newLabelsMap = mutableMapOf<Long, Long>()
        for ((id, label) in notesData.labels) {
            val name = label.name.trim().replace("""\s+""".toRegex(), " ")
            val existingLabelById = existingLabelsIdMap[id]
            if (existingLabelById != null) {
                // Label ID already exists, if name doesn't match (case-insensitively — same
                // rule as getLabelByName()/existingLabelsNameMap above, see FIX-M26) assume
                // this is a different label. A case-only difference (e.g. local label was
                // renamed "Work" -> "work" after this export was made) is still the same
                // label, not a new one — reviewer-caught gap in FIX-M26.
                if (!name.equals(existingLabelById.name, ignoreCase = true)) {
                    newLabelsMap[id] = labelsDao.insert(Label(Label.NO_ID, name))
                } else {
                    newLabelsMap[id] = id
                }
            } else {
                val existingLabelByName = existingLabelsNameMap[name.lowercase()]
                if (existingLabelByName != null) {
                    // Label name already exists, create a new one.
                    var newName: String
                    var num = 2
                    do {
                        newName = "$name ($num)"
                        num++
                    } while (newName.lowercase() in existingLabelsNameMap)
                    newLabelsMap[id] = labelsDao.insert(Label(id = id, name = newName, hidden = false))
                } else {
                    newLabelsMap[id] = labelsDao.insert(Label(id = id, name = name, hidden = label.hidden))
                }
            }
        }
        return newLabelsMap
    }

    private suspend fun importNotes(
        notesData: NotesData,
        newLabelsMap: Map<Long, Long>,
    ) {
        val existingNotes = notesDao.getAll().associateBy { it.note.id }
        val labelRefs = mutableListOf<LabelRef>()
        for ((id, ns) in notesData.notes) {
            var noteId = id
            val newNote = Note(
                id = id,
                type = ns.type,
                title = ns.title,
                content = ns.content,
                metadata = ns.metadata,
                addedDate = ns.addedDate,
                lastModifiedDate = ns.lastModifiedDate,
                status = ns.status,
                pinned = ns.pinned,
                reminder = ns.reminder,
                color = ns.color,
                mood = ns.mood,
                isLocked = ns.isLocked
            )
            val oldNote = existingNotes[noteId]

            // Remap labels appropriately and discard unresolved label IDs.
            var labelIds = ns.labels.mapNotNull { newLabelsMap[it] }

            when {
                oldNote == null -> {
                    notesDao.insert(newNote)
                }

                oldNote.note.addedDate == newNote.addedDate &&
                        oldNote.note.lastModifiedDate == newNote.lastModifiedDate -> {
                    // existing note has same added and modified date as the data, assume this is the same
                    // same that was exported in the first place, unmodified since.
                    // changing the reminder or labels doesn't affect last modified date so merge them explicitly.
                    val mergedNote = mergeNotes(oldNote.note, newNote)
                    if (mergedNote != null) {
                        labelIds = (labelIds union oldNote.labels.map { it.id }).toList()
                        notesDao.update(mergedNote)
                    } else {
                        noteId = notesDao.insert(newNote.copy(id = Note.NO_ID))
                    }
                }

                else -> {
                    // ID clash, assign new ID.
                    noteId = notesDao.insert(newNote.copy(id = Note.NO_ID))
                }
            }

            labelRefs += labelIds.map { LabelRef(noteId, it) }
        }
        labelsDao.insertRefs(labelRefs)
    }

    private fun mergeNotes(
        old: Note,
        new: Note,
    ): Note? {
        val reminder = when {
            old.reminder == null && new.reminder != null -> new.reminder
            old.reminder != null && new.reminder == null -> old.reminder
            old.reminder != null && new.reminder != null &&
                    !compareReminders(old.reminder, new.reminder) -> {
                // Old and new notes have different reminders, do not merge to avoid losing one or the other.
                return null
            }

            // Either both null, or both non-null and equal — keep as is.
            else -> new.reminder
        }

        return new.copy(reminder = reminder)
    }

    private fun compareReminders(
        old: Reminder,
        new: Reminder,
    ) =
        old.start == new.start && old.recurrence == new.recurrence

    companion object {
        private const val VERSION = 4
        private const val FIRST_VERSION = 3
        private const val EXPORT_ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding"
        private const val EXPORT_ENCRYPTION_KEY_ALIAS = "export_key"
        private const val BASE64_FLAGS = Base64.NO_WRAP or Base64.NO_PADDING
    }
}

/**
 * Same fields as [Note], minus redundant [Note.id] and
 * adding [labels] to store label references.
 */
@Serializable
@Keep
private data class NoteSurrogate(
    @SerialName("type")
    val type: NoteType,
    @SerialName("title")
    val title: String,
    @SerialName("content")
    val content: String,
    @SerialName("metadata")
    val metadata: NoteMetadata,
    @SerialName("added")
    val addedDate: Date,
    @SerialName("modified")
    val lastModifiedDate: Date,
    @SerialName("status")
    val status: NoteStatus,
    @ColumnInfo(name = "pinned")
    @SerialName("pinned")
    val pinned: PinnedStatus,
    @SerialName("reminder")
    val reminder: Reminder? = null,
    @SerialName("labels")
    val labels: List<Long> = emptyList(),
    @SerialName("color")
    val color: Int = 0,
    @SerialName("mood")
    val mood: Int = 0,
    @SerialName("locked")
    val isLocked: Boolean = false,
)

@Serializable
@Keep
private data class NotesData(
    @SerialName("version")
    val version: Int,
    @SerialName("notes")
    val notes: Map<Long, NoteSurrogate> = emptyMap(),
    @SerialName("labels")
    val labels: Map<Long, Label> = emptyMap(),
)

@Serializable
@Keep
private data class EncryptedNotesData(
    @SerialName("salt")
    val salt: String,
    @SerialName("nonce")
    val nonce: String,
    @SerialName("ciphertext")
    val ciphertext: String,
)
