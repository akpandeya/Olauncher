package app.olauncher.ui

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.view.isVisible
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.SheetAppActionsBinding
import app.olauncher.helper.hideStatusBar
import com.google.android.material.bottomsheet.BottomSheetDialog

class AppActionsSheet(
    context: Context,
    private val appModel: AppModel,
    private val isHidden: Boolean,
    private val isDistracting: Boolean,
    private val flag: Int = Constants.FLAG_LAUNCH_APP,
    private val appLabelGravity: Int = Gravity.START,
    private val onAddToHome: () -> Unit,
    private val onRename: () -> Unit,
    private val onHide: () -> Unit,
    private val onToggleDistracting: () -> Unit,
    private val onAppInfo: () -> Unit,
    private val onUninstall: () -> Unit,
) : BottomSheetDialog(context) {

    private lateinit var binding: SheetAppActionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SheetAppActionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundResource(android.R.color.transparent)

        setupRows()
    }

    private fun setupRows() {
        val horizontalGravity = appLabelGravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK
        val rowGravity = (if (horizontalGravity != 0) horizontalGravity else Gravity.START) or Gravity.CENTER_VERTICAL

        binding.actionAddToHome.gravity = rowGravity
        binding.actionRename.gravity = rowGravity
        binding.actionHide.gravity = rowGravity
        binding.actionDistracting.gravity = rowGravity
        binding.actionAppInfo.gravity = rowGravity
        binding.actionUninstall.gravity = rowGravity

        // Text setup
        binding.actionHide.text = context.getString(if (isHidden) R.string.unhide else R.string.hide)
        binding.actionDistracting.text = context.getString(
            if (isDistracting) R.string.not_distracting else R.string.mark_distracting
        )

        // Visibility setup
        if (appModel.isArchived) {
            binding.actionAddToHome.isVisible = false
            binding.actionRename.isVisible = false
            binding.actionHide.isVisible = false
            binding.actionDistracting.isVisible = false
            binding.actionAppInfo.isVisible = true
            binding.actionUninstall.isVisible = true
        } else {
            binding.actionAddToHome.isVisible = true
            binding.actionRename.isVisible = flag != Constants.FLAG_HIDDEN_APPS
            binding.actionHide.isVisible = true
            binding.actionDistracting.isVisible = true
            binding.actionAppInfo.isVisible = true
            binding.actionUninstall.isVisible = true
        }

        // Click listeners
        binding.actionAddToHome.setOnClickListener {
            dismiss()
            onAddToHome()
        }
        binding.actionRename.setOnClickListener {
            dismiss()
            onRename()
        }
        binding.actionHide.setOnClickListener {
            dismiss()
            onHide()
        }
        binding.actionDistracting.setOnClickListener {
            dismiss()
            onToggleDistracting()
        }
        binding.actionAppInfo.setOnClickListener {
            dismiss()
            onAppInfo()
        }
        binding.actionUninstall.setOnClickListener {
            dismiss()
            onUninstall()
        }
    }

    fun showRespectingStatusBar() {
        val window = window
        if (window == null || Prefs(context).showStatusBar) {
            show()
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            show()
            window.hideStatusBar()
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
    }
}
