package li.gkd.app.authoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import li.gkd.app.subscription.RawSubscription
import li.gkd.db.SubscriptionIds

class GuidedRuleWriterTest {
    private val local = RawSubscription(
        id = SubscriptionIds.LOCAL,
        name = "Local rules",
        version = 3,
    )

    @Test
    fun createsAppGroupAndRule() {
        val result = GuidedRuleWriter.write(
            local,
            request(
                ruleName = "Tap “Skip”",
                draft = GuidedSelectorDraft(
                    listOf(GuidedCondition.Text(GuidedTextOperator.Is, "Skip")),
                ),
            ),
        )
        val success = assertIs<GuidedRuleWriteResult.Success>(result)
        val subscription = success.subscription
        assertEquals(3, subscription.version)
        assertEquals("Local rules", subscription.name)
        val app = subscription.apps.single()
        assertEquals("com.example", app.id)
        assertEquals("Example", app.name)
        val group = app.groups.single()
        assertEquals("My rules", group.name)
        assertEquals(1, group.key)
        assertEquals(true, group.enable)
        assertNull(group.activityIds)
        val rule = group.rules.single()
        assertEquals(1, rule.key)
        assertEquals("Tap “Skip”", rule.name)
        assertEquals("click", rule.action)
        assertEquals(listOf("[text=\"Skip\"]"), rule.matches)
        assertNull(rule.anyMatches)
        assertNull(rule.matchTime)
        assertNull(rule.actionMaximum)
        assertNull(rule.resetMatch)
        assertNull(rule.activityIds)
        assertEquals(1, success.groupKey)
        assertEquals(1, success.ruleKey)
        assertTrue(success.createdGroup)
        assertFalse(success.replaced)
    }

    @Test
    fun appendsToExistingGroupWithoutTouchingItsOwnership() {
        val existing = testAppGroup(
            key = 7,
            name = "My rules",
            enable = false,
            rules = listOf(testAppRule(key = 4, name = "Old")),
        )
        val current = local.copy(
            apps = listOf(
                RawSubscription.RawApp("com.example", "Example", listOf(existing)),
            ),
        )
        val result = GuidedRuleWriter.write(
            current,
            request(
                draft = GuidedSelectorDraft(
                    listOf(GuidedCondition.Text(GuidedTextOperator.Contains, "Later")),
                ),
            ),
        )
        val success = assertIs<GuidedRuleWriteResult.Success>(result)
        val group = success.subscription.apps.single().groups.single()
        assertEquals(7, group.key)
        assertEquals(false, group.enable)
        assertEquals(listOf(4, 5), group.rules.map { it.key })
        assertEquals("[text*=\"Later\"]", group.rules.last().matches?.single())
        assertEquals(5, success.ruleKey)
        assertFalse(success.createdGroup)
    }

    @Test
    fun allocatesFreshGroupKeyAndPreservesOtherAppsAndGroups() {
        val otherGroup = testAppGroup(key = 2, name = "Other")
        val highestGroup = testAppGroup(key = 7, name = "Highest")
        val current = local.copy(
            apps = listOf(
                RawSubscription.RawApp("com.other", "Other app", listOf(testAppGroup(key = 9))),
                RawSubscription.RawApp(
                    "com.example",
                    "Example",
                    listOf(otherGroup, highestGroup),
                ),
            ),
        )
        val result = GuidedRuleWriter.write(current, request())
        val success = assertIs<GuidedRuleWriteResult.Success>(result)
        assertEquals(8, success.groupKey)
        assertTrue(success.createdGroup)
        val apps = success.subscription.apps
        assertEquals(listOf("com.other", "com.example"), apps.map { it.id })
        assertEquals(listOf(2, 7, 8), apps[1].groups.map { it.key })
        assertEquals("My rules", apps[1].groups.last().name)
    }

    @Test
    fun writesLimitsOnlyWhenEnabled() {
        val result = GuidedRuleWriter.write(local, request(limitToAppOpening = true))
        val success = assertIs<GuidedRuleWriteResult.Success>(result)
        val rule = success.subscription.apps.single().groups.single().rules.single()
        assertEquals(10_000L, rule.matchTime)
        assertEquals(1, rule.actionMaximum)
        assertEquals("app", rule.resetMatch)
    }

    @Test
    fun replacesInPlaceKeepingKeyAndPosition() {
        val old = testAppRule(key = 2, name = "Old").copy(
            matchTime = 10_000L,
            actionMaximum = 1,
            resetMatch = "app",
        )
        val group = testAppGroup(
            key = 1,
            rules = listOf(testAppRule(key = 1), old, testAppRule(key = 3)),
        )
        val current = local.copy(
            apps = listOf(RawSubscription.RawApp("com.example", "Example", listOf(group))),
        )
        val result = GuidedRuleWriter.write(
            current,
            request(
                ruleName = "Long-press “Later”",
                action = GuidedRuleAction.LongPress,
                draft = GuidedSelectorDraft(
                    listOf(GuidedCondition.Text(GuidedTextOperator.Is, "Later")),
                ),
                replaceRuleKey = 2,
            ),
        )
        val success = assertIs<GuidedRuleWriteResult.Success>(result)
        assertTrue(success.replaced)
        assertEquals(2, success.ruleKey)
        val rules = success.subscription.apps.single().groups.single().rules
        assertEquals(listOf(1, 2, 3), rules.map { it.key })
        val replaced = rules[1]
        assertEquals("Long-press “Later”", replaced.name)
        assertEquals("longClick", replaced.action)
        assertEquals(listOf("[text=\"Later\"]"), replaced.matches)
        assertNull(replaced.matchTime)
        assertNull(replaced.actionMaximum)
        assertNull(replaced.resetMatch)
    }

    @Test
    fun rejectsReplaceWhenTheAuthoringRuleIsMissing() {
        val group = testAppGroup(key = 1, rules = listOf(testAppRule(key = 1)))
        val current = local.copy(
            apps = listOf(RawSubscription.RawApp("com.example", "Example", listOf(group))),
        )
        assertEquals(
            GuidedRuleWriteFailure.ReplaceTargetMissing,
            (GuidedRuleWriter.write(current, request(replaceRuleKey = 99)) as GuidedRuleWriteResult.Failure).reason,
        )
        val foreignOnly = current.copy(
            apps = listOf(
                RawSubscription.RawApp(
                    "com.example",
                    "Example",
                    listOf(testAppGroup(key = 1, name = "Other", rules = listOf(testAppRule(key = 99)))),
                ),
            ),
        )
        assertEquals(
            GuidedRuleWriteFailure.ReplaceTargetMissing,
            (GuidedRuleWriter.write(foreignOnly, request(replaceRuleKey = 99)) as GuidedRuleWriteResult.Failure).reason,
        )
    }

    @Test
    fun rejectsRemoteSubscriptionAndInvalidSelector() {
        assertEquals(
            GuidedRuleWriteFailure.NotLocalSubscription,
            (GuidedRuleWriter.write(local.copy(id = 1), request()) as GuidedRuleWriteResult.Failure).reason,
        )
        assertEquals(
            GuidedRuleWriteFailure.InvalidSelector,
            (
                    GuidedRuleWriter.write(
                        local,
                        request(
                            draft = GuidedSelectorDraft(
                                listOf(GuidedCondition.Text(GuidedTextOperator.Is, " ")),
                            ),
                        ),
                    ) as GuidedRuleWriteResult.Failure
                    ).reason,
        )
        assertEquals(
            GuidedRuleWriteFailure.InvalidSelector,
            (
                    GuidedRuleWriter.write(
                        local,
                        request(draft = GuidedSelectorDraft(emptyList())),
                    ) as GuidedRuleWriteResult.Failure
                    ).reason,
        )
    }

    @Test
    fun omitsBlankRuleNameAndStartsRuleKeysAtOne() {
        val group = testAppGroup(key = 1, rules = listOf(testAppRule(key = null)))
        val current = local.copy(
            apps = listOf(RawSubscription.RawApp("com.example", "Example", listOf(group))),
        )
        val result = GuidedRuleWriter.write(current, request(ruleName = "  "))
        val success = assertIs<GuidedRuleWriteResult.Success>(result)
        val rules = success.subscription.apps.single().groups.single().rules
        assertEquals(listOf(null, 1), rules.map { it.key })
        assertNull(rules.last().name)
    }

    private fun request(
        ruleName: String? = "Tap “Skip”",
        action: GuidedRuleAction = GuidedRuleAction.Click,
        draft: GuidedSelectorDraft = GuidedSelectorDraft(
            listOf(GuidedCondition.Text(GuidedTextOperator.Is, "Skip")),
        ),
        limitToAppOpening: Boolean = false,
        replaceRuleKey: Int? = null,
    ) = GuidedRuleWriteRequest(
        appId = "com.example",
        appName = "Example",
        authoringGroupName = "My rules",
        ruleName = ruleName,
        action = action,
        draft = draft,
        limitToAppOpening = limitToAppOpening,
        replaceRuleKey = replaceRuleKey,
    )
}
