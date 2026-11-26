package ch.orellbuehler.jackdaw

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import ch.orellbuehler.jackdaw.ui.nav.AddPackRoute
import ch.orellbuehler.jackdaw.ui.nav.PackRoute
import ch.orellbuehler.jackdaw.ui.nav.SettingsRoute
import ch.orellbuehler.jackdaw.ui.nav.StatsRoute
import ch.orellbuehler.jackdaw.ui.nav.StudyRoute
import kotlinx.coroutines.runBlocking

object StoreScreenshots {
    const val EXTRA_SCREEN = "store_screenshot"
    const val EXTRA_PACK = "store_screenshot_pack"
    const val EXTRA_SECTION = "store_screenshot_section"
    private const val PACK_PREFIX = "com.github.orellbuehler.jackdaw.examples."

    fun prepare(
        activity: Activity,
        intent: Intent,
    ): Any? {
        val debuggable = activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val screen = intent.getStringExtra(EXTRA_SCREEN)
        if (!debuggable || screen == null) return null
        runBlocking { (activity.application as JackdawApplication).sdk.seedDemoLibrary() }
        val packId = PACK_PREFIX + intent.getStringExtra(EXTRA_PACK).orEmpty()
        return when (screen) {
            "pack" -> PackRoute(packId)
            "study" -> StudyRoute(packId, sectionId = intent.getStringExtra(EXTRA_SECTION))
            "stats" -> StatsRoute
            "settings" -> SettingsRoute
            "add" -> AddPackRoute
            else -> null
        }
    }
}
