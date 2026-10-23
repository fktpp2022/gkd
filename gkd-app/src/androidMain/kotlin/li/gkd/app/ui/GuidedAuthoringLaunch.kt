package li.gkd.app.ui

import android.content.Intent
import li.gkd.app.META
import li.gkd.app.MainActivity
import li.gkd.app.app

object GuidedAuthoringLaunch {
    val action: String = META.appId + ".action.GUIDED_AUTHORING"

    fun open() {
        val intent = Intent(app, MainActivity::class.java).apply {
            action = action
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        app.startActivity(intent)
    }
}
