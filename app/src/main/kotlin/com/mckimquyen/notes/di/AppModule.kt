package com.mckimquyen.notes.di

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.mckimquyen.notes.model.DefaultJsonManager
import com.mckimquyen.notes.model.DefaultLabelsRepository
import com.mckimquyen.notes.model.DefaultNotesRepository
import com.mckimquyen.notes.model.JsonManager
import com.mckimquyen.notes.model.LabelsRepository
import com.mckimquyen.notes.model.NotesRepository
import com.mckimquyen.notes.model.ReminderAlarmCallback
import com.mckimquyen.notes.receiver.ReceiverAlarmCallback
import dagger.Binds
import dagger.Module
import dagger.Provides
import kotlinx.serialization.json.Json

@Module(
    includes = [
        DbModule::class,
        BuildTypeModule::class,
    ]
)
abstract class AppModule {

    @Binds
    abstract fun bindNotesRepository(impl: DefaultNotesRepository): NotesRepository

    @Binds
    abstract fun bindLabelsRepository(impl: DefaultLabelsRepository): LabelsRepository

    @Binds
    abstract fun bindJsonManager(impl: DefaultJsonManager): JsonManager

    @Binds
    abstract fun bindAlarmCallback(impl: ReceiverAlarmCallback): ReminderAlarmCallback

    companion object {
        @Provides
        fun providesSharedPreferences(context: Context): SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(context)

        @get:Provides
        val json
            get() = Json {
                encodeDefaults = false
                ignoreUnknownKeys = true
            }
    }
}
