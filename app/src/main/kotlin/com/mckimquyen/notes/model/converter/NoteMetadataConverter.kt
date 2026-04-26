package com.mckimquyen.notes.model.converter

import androidx.room.TypeConverter
import com.mckimquyen.notes.model.BadDataException
import com.mckimquyen.notes.model.entity.NoteMetadata
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

/**
 * Converter used to store instances of [NoteMetadata] in the database and to serialize them.
 * When serialized, metadata JSON in itself encoded into a JSON string. This wouldn't be
 * necessary but it simplifies the server's job. Also metadata *could* eventually not be JSON.
 */
object NoteMetadataConverter : KSerializer<NoteMetadata> {

    private val json = Json

    @TypeConverter
    @JvmStatic
    fun toMetadata(str: String): NoteMetadata = try {
        json.decodeFromString(NoteMetadata.serializer(), str)
    } catch (e: SerializationException) {
        throw BadDataException(cause = e)
    }

    @TypeConverter
    @JvmStatic
    fun toString(metadata: NoteMetadata): String = json.encodeToString(NoteMetadata.serializer(), metadata)

    override val descriptor = PrimitiveSerialDescriptor("NoteMetadata", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: NoteMetadata) =
        encoder.encodeString(toString(value))

    override fun deserialize(decoder: Decoder) = toMetadata(decoder.decodeString())
}
