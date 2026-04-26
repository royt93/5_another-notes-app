package com.mckimquyen.notes.di

import android.content.Context
import com.mckimquyen.notes.RApp
import com.mckimquyen.notes.receiver.AlarmReceiver
import com.mckimquyen.notes.ui.edit.EditFrm
import com.mckimquyen.notes.ui.home.HomeFrm
import com.mckimquyen.notes.ui.labels.LabelEditDlg
import com.mckimquyen.notes.ui.labels.LabelFrm
import com.mckimquyen.notes.ui.main.MainAct
import com.mckimquyen.notes.ui.noti.NotificationAct
import com.mckimquyen.notes.ui.reminder.ReminderDlg
import com.mckimquyen.notes.ui.search.SearchFrm
import com.mckimquyen.notes.ui.setting.ExportPasswordDlg
import com.mckimquyen.notes.ui.setting.ImportPasswordDlg
import com.mckimquyen.notes.ui.setting.SettingsFrm
import com.mckimquyen.notes.ui.sort.SortDialog
import com.mckimquyen.notes.ui.splash.SplashActivity
import com.mckimquyen.notes.widget.NoteCountWidget
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [AppModule::class])
interface AppComponent {
    fun inject(rApp: RApp)
    fun inject(activity: MainAct)
    fun inject(widget: NoteCountWidget)
    fun inject(factory: com.mckimquyen.notes.widget.RecentNotesRemoteViewsFactory)
    fun inject(activity: NotificationAct)
    fun inject(activity: SplashActivity)
    fun inject(fragment: HomeFrm)
    fun inject(fragment: SearchFrm)
    fun inject(fragment: EditFrm)
    fun inject(fragment: LabelFrm)
    fun inject(fragment: SettingsFrm)
    fun inject(dialog: ReminderDlg)
    fun inject(dialog: LabelEditDlg)
    fun inject(dialog: SortDialog)
    fun inject(dialog: ExportPasswordDlg)
    fun inject(dialog: ImportPasswordDlg)
    fun inject(receiver: AlarmReceiver)

    @Component.Factory
    interface Factory {
        fun create(@BindsInstance appContext: Context): AppComponent
    }
}
