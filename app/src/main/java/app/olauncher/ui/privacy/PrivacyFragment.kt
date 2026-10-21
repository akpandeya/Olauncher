package app.olauncher.ui.privacy

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import app.olauncher.databinding.FragmentPrivacyBinding
import app.olauncher.databinding.ItemPrivacyBinding
import app.olauncher.helper.PrivacyInventory
import app.olauncher.ui.BaseFragment

/** Lists everything Thumbline can touch, its current state, and where to take it away. */
class PrivacyFragment : BaseFragment() {

    private var _binding: FragmentPrivacyBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPrivacyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val context = requireContext()
        val granted = PrivacyInventory.entries
            .filter { it.kind == PrivacyInventory.Kind.RUNTIME || it.kind == PrivacyInventory.Kind.SPECIAL_ACCESS }
            .filter { isGranted(context, it) }
            .map { it.id }
            .toSet()
        binding.privacyContainer.removeAllViews()
        for (item in PrivacyInventory.states(granted)) {
            val purpose = PrivacyText.purposeRes(item.entry.purposeKey) ?: continue
            val row = ItemPrivacyBinding.inflate(layoutInflater, binding.privacyContainer, false)
            row.tvPurpose.setText(purpose)
            row.tvState.setText(PrivacyText.stateRes(item.state))
            val target = PrivacyText.revokeTarget(item.entry.revokeKey)
            val label = PrivacyText.revokeLabelRes(target)
            row.tvRevoke.isVisible = label != null
            if (label != null) {
                row.tvRevoke.setText(label)
                row.tvRevoke.setOnClickListener { revoke(target) }
            }
            binding.privacyContainer.addView(row.root)
        }
    }

    private fun isGranted(context: Context, entry: PrivacyInventory.Entry): Boolean =
        if (entry.kind == PrivacyInventory.Kind.SPECIAL_ACCESS) {
            // Usage access is not a runtime permission; the app-ops check is the system's answer.
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ops?.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            ) == AppOpsManager.MODE_ALLOWED
        } else {
            ContextCompat.checkSelfPermission(context, entry.id) == PackageManager.PERMISSION_GRANTED
        }

    private fun revoke(target: PrivacyText.RevokeTarget) {
        val context = requireContext()
        val appInfo = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
        try {
            when (target) {
                PrivacyText.RevokeTarget.APP_INFO -> startActivity(appInfo)
                PrivacyText.RevokeTarget.USAGE_ACCESS -> startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                PrivacyText.RevokeTarget.HEALTH_CONNECT -> try {
                    startActivity(
                        Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS")
                            .putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName),
                    )
                } catch (e: Exception) {
                    startActivity(appInfo)
                }
                PrivacyText.RevokeTarget.THIS_SETTINGS -> findNavController().popBackStack()
                PrivacyText.RevokeTarget.NONE -> Unit
            }
        } catch (e: Exception) {
            // A missing settings screen must never crash the page.
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
