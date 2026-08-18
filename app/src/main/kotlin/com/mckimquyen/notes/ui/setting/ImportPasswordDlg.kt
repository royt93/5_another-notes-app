package com.mckimquyen.notes.ui.setting

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.WindowManager
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mckimquyen.notes.RApp
import com.mckimquyen.notes.R
import com.mckimquyen.notes.databinding.DlgImportPasswordBinding
import com.mckimquyen.notes.ext.hideCursorInAllViews
import com.mckimquyen.notes.ext.setTitleIfEnoughSpace
import com.mckimquyen.notes.ui.observeEvent
import com.mckimquyen.notes.ui.viewModel
import javax.inject.Inject

class ImportPasswordDlg : DialogFragment() {

    companion object {
        fun newInstance(): ImportPasswordDlg {
            return ImportPasswordDlg()
        }
    }

    @Inject
    lateinit var viewModelFactory: ImportPasswordVM.Factory
    val viewModel by viewModel { viewModelFactory.create(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        (requireContext().applicationContext as RApp?)?.appComponent?.inject(this)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val binding = DlgImportPasswordBinding.inflate(layoutInflater, null, false)

        val passwordInput = binding.passwordInput

        val builder = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                val selectedPassword = passwordInput.text.toString()
                callback.onImportPasswordDialogPositiveButtonClicked(selectedPassword)
            }
            .setNegativeButton(R.string.action_cancel) { _, _ ->
                callback.onImportPasswordDialogNegativeButtonClicked()
            }
            .setTitleIfEnoughSpace(R.string.encrypted_import_dialog_title)

        val dialog = builder.create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.setCanceledOnTouchOutside(true)

        passwordInput.doAfterTextChanged {
            val okBtn = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            okBtn.isEnabled = it?.isNotEmpty() ?: false
            viewModel.onPasswordChanged(it?.toString() ?: "")
        }

        passwordInput.requestFocus()
        viewModel.setDialogDataEvent.observeEvent(this) { password ->
            passwordInput.setText(password)
        }

        viewModel.start()

        return dialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        hideCursorInAllViews()
        viewModel.clearPassword()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        callback.onImportPasswordDialogCancelled()
    }

    private val callback: Callback
        get() = (parentFragment as? Callback)
            ?: (activity as? Callback)
            ?: error("No callback for ImportPasswordDlg")

    interface Callback {
        fun onImportPasswordDialogPositiveButtonClicked(password: String) = Unit
        fun onImportPasswordDialogNegativeButtonClicked() = Unit
        fun onImportPasswordDialogCancelled() = Unit
    }
}
