package li.gkd.app.ui.authoring

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import li.gkd.app.SimulatorStore
import li.gkd.app.authoring.GuidedCondition
import li.gkd.app.authoring.GuidedRuleAction
import li.gkd.app.authoring.GuidedRuleCandidate
import li.gkd.app.authoring.GuidedTextOperator
import li.gkd.app.authoring.testAppGroup
import li.gkd.app.authoring.testAppRule
import li.gkd.app.permission.SimulatorPermissionRequester
import li.gkd.app.resources.*
import li.gkd.app.state.Loadable
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionJson
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.navigation.AppNavigator
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.GuidedAuthoringRoute
import li.gkd.app.ui.navigation.UpsertRuleGroupRoute
import li.gkd.app.ui.subscription.UpsertRuleGroupViewModel
import li.gkd.db.SubscriptionIds
import org.jetbrains.compose.resources.getString
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ticket 28: a raw-editor save from the guided handoff is the only write. A representable
 * result returns to a refreshed guided draft; an unrepresentable one ends the session at the
 * raw editor.
 */
class GuidedRawHandoffTest(private val simulator: SimulatorStore) {
    fun runAll() = runBlocking {
        withContext(Dispatchers.Main.immediate) {
            aRepresentableRawSaveReturnsToTheRefreshedGuidedRule()
            anUnrepresentableRawSaveEndsTheGuidedSessionAtTheRawEditor()
        }
    }

    private suspend fun aRepresentableRawSaveReturnsToTheRefreshedGuidedRule() {
        val localId = SubscriptionIds.LOCAL
        val appId = "test.beta"
        val authoringGroupName = getString(Res.string.guided_authoring_group_name)
        val previous = SubscriptionRepository.awaitSnapshot().subscriptions.getValue(localId)
        SubscriptionRepository.update(localId) { current ->
            current.copy(
                apps = current.apps.filterNot { it.id == appId } + RawSubscription.RawApp(
                    id = appId,
                    name = "Beta",
                    groups = listOf(
                        testAppGroup(
                            key = 1,
                            name = authoringGroupName,
                            rules = listOf(
                                testAppRule(
                                    key = 11,
                                    name = "Before",
                                    matches = listOf("[text=\"Before\"]"),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }
        val owner = ViewModelStore()
        try {
            val mainVm = MainViewModel(SimulatorPermissionRequester(simulator))
                .also { owner.put("main", it) }
            mainVm.registerCurrent()
            val guided = GuidedAuthoringViewModel(appId).also { owner.put("guided", it) }
            try {
                withTimeout(5_000) {
                    guided.state.first { it.appName != null && it.scan is Loadable.Ready }
                }
                guided.startManual()
                guided.addCondition(GuidedCondition.Text(GuidedTextOperator.Is, "Stale"))
                guided.setName("Stale name")
                val staleDraft = guided.state.value.draft

                val group = SubscriptionRepository.awaitSnapshot().subscriptions.getValue(localId)
                    .apps.first { it.id == appId }.groups.single()
                val candidate = GuidedRuleCandidate.App(
                    group = group,
                    rule = group.rules.single(),
                    inAuthoringGroup = true,
                )
                guided.requestRawHandoff(candidate)
                val target = assertNotNull(guided.rawHandoff.value)
                assertEquals(1, target.groupKey)
                assertFalse(target.global)
                guided.consumeRawHandoff()

                mainVm.navigator.navigate(GuidedAuthoringRoute())
                val rawRoute = UpsertRuleGroupRoute(
                    subsId = localId,
                    groupKey = target.groupKey,
                    appId = appId,
                    guided = true,
                )
                mainVm.navigator.navigate(rawRoute)
                awaitTop(mainVm.navigator) { it is UpsertRuleGroupRoute }

                val rawVm = UpsertRuleGroupViewModel(rawRoute).also { owner.put("raw", it) }
                withTimeout(5_000) { rawVm.uiState.first { it is Loadable.Ready } }
                rawVm.saveRule(
                    rawGroupText(
                        authoringGroupName,
                        """
                        {
                          key: 11,
                          name: 'Raw name',
                          action: 'longClick',
                          matches: ['[text="Raw target"]'],
                          matchTime: 10000,
                          actionMaximum: 1,
                          resetMatch: 'app'
                        }
                        """.trimIndent(),
                    ),
                )
                rawVm.onSaved()

                val refreshed = withTimeout(5_000) {
                    guided.state.first { it.draft.nameOverride == "Raw name" }
                }
                assertEquals(GuidedAuthoringPhase.Editing, refreshed.phase)
                assertEquals(GuidedRuleAction.LongPress, refreshed.draft.action)
                assertEquals(
                    listOf(GuidedCondition.Text(GuidedTextOperator.Is, "Raw target")),
                    refreshed.draft.target,
                )
                assertTrue(refreshed.draft.limitToAppOpening)
                assertTrue(refreshed.nameEdited)
                assertNotEquals(staleDraft, refreshed.draft)
                assertEquals(11, refreshed.editing?.ruleKey)
                assertEquals("Raw name", refreshed.editing?.ruleName)
                assertFalse(guided.draftChanged())

                awaitTop(mainVm.navigator) { it is GuidedAuthoringRoute }
                assertEquals(2, mainVm.navigator.backStack.size)

                val savedRule = SubscriptionRepository.awaitSnapshot().subscriptions.getValue(localId)
                    .apps.first { it.id == appId }
                    .groups.first { it.name == authoringGroupName }
                    .rules.single()
                assertEquals(11, savedRule.key)
                assertEquals("Raw name", savedRule.name)
                assertEquals("longClick", savedRule.action)
                assertEquals(listOf("[text=\"Raw target\"]"), savedRule.matches)
                assertEquals(null, savedRule.anyMatches)
                assertEquals(10_000L, savedRule.matchTime)
                assertEquals(1, savedRule.actionMaximum)
                assertEquals("app", savedRule.resetMatch)
            } finally {
                owner.clear()
            }
        } finally {
            SubscriptionRepository.update(localId) { previous }
        }
    }

    private suspend fun anUnrepresentableRawSaveEndsTheGuidedSessionAtTheRawEditor() {
        val localId = SubscriptionIds.LOCAL
        val appId = "test.beta"
        val authoringGroupName = getString(Res.string.guided_authoring_group_name)
        val previous = SubscriptionRepository.awaitSnapshot().subscriptions.getValue(localId)
        SubscriptionRepository.update(localId) { current ->
            current.copy(
                apps = current.apps.filterNot { it.id == appId } + RawSubscription.RawApp(
                    id = appId,
                    name = "Beta",
                    groups = listOf(testAppGroup(key = 7, name = "Other")),
                ),
            )
        }
        val owner = ViewModelStore()
        try {
            val mainVm = MainViewModel(SimulatorPermissionRequester(simulator))
                .also { owner.put("main", it) }
            mainVm.registerCurrent()
            val guided = GuidedAuthoringViewModel(appId).also { owner.put("guided", it) }
            try {
                withTimeout(5_000) {
                    guided.state.first { it.appName != null && it.scan is Loadable.Ready }
                }
                guided.startManual()
                guided.addCondition(GuidedCondition.Text(GuidedTextOperator.Is, "Stale"))
                val staleDraft = guided.state.value.draft

                guided.requestRawHandoff()
                val target = assertNotNull(guided.rawHandoff.value)
                assertNull(target.groupKey)
                assertFalse(target.global)
                guided.consumeRawHandoff()

                mainVm.navigator.navigate(GuidedAuthoringRoute())
                val rawRoute = UpsertRuleGroupRoute(
                    subsId = localId,
                    groupKey = target.groupKey,
                    appId = appId,
                    initialText = guided.rawDraftText(),
                    guided = true,
                )
                mainVm.navigator.navigate(rawRoute)
                awaitTop(mainVm.navigator) { it is UpsertRuleGroupRoute }

                val rawVm = UpsertRuleGroupViewModel(rawRoute).also { owner.put("raw", it) }
                withTimeout(5_000) { rawVm.uiState.first { it is Loadable.Ready } }
                rawVm.saveRule(
                    rawGroupText(
                        authoringGroupName,
                        """
                        {
                          name: 'Raw both',
                          action: 'click',
                          matches: ['[text="Raw"]'],
                          anyMatches: ['[text="Other"]']
                        }
                        """.trimIndent(),
                    ),
                )
                rawVm.onSaved()

                withTimeout(5_000) { guided.finished.first { it } }
                assertTrue(guided.finished.value)
                assertFalse(guided.saved.value)
                assertEquals(staleDraft, guided.state.value.draft)
                assertTrue(mainVm.navigator.topRoute is UpsertRuleGroupRoute)
                assertEquals(3, mainVm.navigator.backStack.size)

                val savedGroup = SubscriptionRepository.awaitSnapshot().subscriptions.getValue(localId)
                    .apps.first { it.id == appId }
                    .groups.first { it.name == authoringGroupName }
                val savedRule = savedGroup.rules.single()
                assertEquals("Raw both", savedRule.name)
                assertEquals(listOf("[text=\"Raw\"]"), savedRule.matches)
                assertEquals(listOf("[text=\"Other\"]"), savedRule.anyMatches)
            } finally {
                owner.clear()
            }
        } finally {
            SubscriptionRepository.update(localId) { previous }
        }
    }

    private fun rawGroupText(name: String, rules: String): String = """
        {
          name: ${SubscriptionJson.toJson5String(name)},
          rules: [
$rules
          ]
        }
    """.trimIndent()

    private suspend fun awaitTop(
        navigator: AppNavigator,
        predicate: (AppRoute) -> Boolean,
    ) {
        withTimeout(5_000) {
            while (!predicate(navigator.topRoute)) delay(10)
        }
    }
}
