package app.hydra.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class UpdateWorkReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                UpdateWorkScheduler.ensureScheduled(
                    context = context,
                    runImmediateCatchUp = true
                )
            }
        }
    }
}
