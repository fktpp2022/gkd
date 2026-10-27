package li.gkd.app

import kotlin.test.Test
import kotlin.test.assertEquals
import li.gkd.app.ui.navigation.GuidedAuthoringRoute

class DesktopRouteNameTest {
    // Guided authoring is the one route with no scenario page, and /state must still name it.
    @Test
    fun guidedAuthoringRouteReportsAPage() {
        assertEquals("guided-authoring", routeName(GuidedAuthoringRoute()))
    }
}
