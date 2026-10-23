package li.gkd.app.ui.platform

import androidx.compose.runtime.Composable
import li.gkd.app.authoring.GuidedRecordingContext
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.DesktopRuntime
import li.gkd.app.SimulatedService
import li.gkd.app.settings.AutomatorMode

actual fun UiHost.setStatusServiceEnabled(enabled: Boolean) {
    DesktopRuntime.requireCurrent().services.setEnabled(SimulatedService.Status, enabled)
}

actual fun UiHost.changeAutomatorMode(mode: AutomatorMode) {
    if (SettingsRepository.settings.value.automatorMode == mode.value) return
    val simulator = state.simulator.settings.value
    if (mode == AutomatorMode.Automation && simulator.privilege.available && simulator.prompts.automationOccupied) {
        state.overlay = "occupied"
        return
    }
    SettingsRepository.updateAutomatorMode(mode.value)
    DesktopRuntime.requireCurrent().services.setEnabled(SimulatedService.Accessibility, false)
    DesktopRuntime.requireCurrent().services.setEnabled(SimulatedService.Automation, false)
}

actual fun UiHost.finishGuidedAuthoring() {}

@Composable
actual fun guidedRecordingSupported(): Boolean = false

actual fun UiHost.startGuidedRecording(context: GuidedRecordingContext): Boolean = false

actual suspend fun UiHost.captureAndStartGuidedRecording(
    appId: String,
    appName: String?,
): Boolean = false
