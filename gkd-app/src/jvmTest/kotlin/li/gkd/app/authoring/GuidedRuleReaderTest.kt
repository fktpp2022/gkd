package li.gkd.app.authoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import li.gkd.app.subscription.RawSubscription
import li.gkd.db.SubscriptionIds

class GuidedRuleReaderTest {
    @Test
    fun readsRepresentableRuleWithoutLimits() {
        val result = GuidedRuleReader.read(testAppRule())
        val data = assertIs<GuidedRuleReadResult.Success>(result).data
        assertEquals(
            GuidedSelectorDraft(
                listOf(GuidedCondition.Text(GuidedTextOperator.Is, "Skip")),
            ),
            data.draft,
        )
        assertEquals(GuidedRuleAction.Click, data.action)
        assertEquals(false, data.limitToAppOpening)
        assertEquals("Rule", data.name)
    }

    @Test
    fun readsLimitsBundle() {
        val rule = testAppRule(action = "longClick").copy(
            matchTime = 10_000L,
            actionMaximum = 1,
            resetMatch = "app",
        )
        val data = assertIs<GuidedRuleReadResult.Success>(GuidedRuleReader.read(rule)).data
        assertEquals(GuidedRuleAction.LongPress, data.action)
        assertTrue(data.limitToAppOpening)
    }

    @Test
    fun rejectsEveryAdvancedRuleField() {
        val position = RawSubscription.Position(
            left = "0",
            top = "0",
            right = null,
            bottom = null,
            x = null,
            y = null,
        )
        val advanced = listOf<Pair<String, RawSubscription.RawAppRule.() -> RawSubscription.RawAppRule>>(
            "preKeys" to { copy(preKeys = listOf(1)) },
            "position" to { copy(position = position) },
            "swipeArg" to {
                copy(
                    swipeArg = RawSubscription.SwipeArg(
                        start = position,
                        end = null,
                        duration = 100,
                    ),
                )
            },
            "excludeMatches" to { copy(excludeMatches = listOf("[text=\"x\"]")) },
            "excludeAllMatches" to { copy(excludeAllMatches = listOf("[text=\"x\"]")) },
            "actionCd" to { copy(actionCd = 100) },
            "actionDelay" to { copy(actionDelay = 100) },
            "matchDelay" to { copy(matchDelay = 100) },
            "fastQuery" to { copy(fastQuery = false) },
            "matchRoot" to { copy(matchRoot = false) },
            "forcedTime" to { copy(forcedTime = 1) },
            "order" to { copy(order = 1) },
            "priorityTime" to { copy(priorityTime = 1) },
            "priorityActionMaximum" to { copy(priorityActionMaximum = 1) },
            "actionCdKey" to { copy(actionCdKey = 1) },
            "actionMaximumKey" to { copy(actionMaximumKey = 1) },
            "snapshotUrls" to { copy(snapshotUrls = listOf("https://example.com/1")) },
            "excludeSnapshotUrls" to {
                copy(excludeSnapshotUrls = listOf("https://example.com/1"))
            },
            "exampleUrls" to { copy(exampleUrls = listOf("https://example.com/1")) },
            "activityIds" to { copy(activityIds = listOf("com.example.MainActivity")) },
            "excludeActivityIds" to {
                copy(excludeActivityIds = listOf("com.example.MainActivity"))
            },
            "versionCode" to {
                copy(
                    versionCode = RawSubscription.IntegerMatcher(
                        minimum = null,
                        maximum = null,
                        include = listOf(1),
                        exclude = null,
                    ),
                )
            },
            "versionName" to {
                copy(
                    versionName = RawSubscription.StringMatcher(
                        pattern = null,
                        include = listOf("1.0"),
                        exclude = null,
                    ),
                )
            },
        )
        advanced.forEach { (field, mutate) ->
            assertEquals(
                GuidedRuleReadFailure.AdvancedFields,
                (
                        GuidedRuleReader.read(testAppRule().mutate()) as GuidedRuleReadResult.Unsupported
                        ).reason,
                field,
            )
        }
    }

    @Test
    fun rejectsUnsupportedActions() {
        assertEquals(
            GuidedRuleReadFailure.UnsupportedAction,
            (
                    GuidedRuleReader.read(testAppRule(action = null)) as GuidedRuleReadResult.Unsupported
                    ).reason,
        )
        assertEquals(
            GuidedRuleReadFailure.UnsupportedAction,
            (
                    GuidedRuleReader.read(testAppRule(action = "swipe")) as GuidedRuleReadResult.Unsupported
                    ).reason,
        )
    }

    @Test
    fun rejectsSelectorsOutsideTheGuidedSubset() {
        val sources = listOf(
            listOf("[text=\"a\"]", "[text=\"b\"]"),
            emptyList(),
            listOf("[text~=\"a\"]"),
            listOf("([text=\"a\"]) && ([text=\"b\"])"),
            listOf("[text=\"a\"] >2 @[text=\"b\"]"),
            listOf("[clickable=true] + @[text=\"b\"]"),
        )
        sources.forEach { matches ->
            assertEquals(
                GuidedRuleReadFailure.UnsupportedSelector,
                (
                        GuidedRuleReader.read(testAppRule(matches = matches)) as GuidedRuleReadResult.Unsupported
                        ).reason,
                matches.toString(),
            )
        }
        assertEquals(
            GuidedRuleReadFailure.UnsupportedSelector,
            (
                    GuidedRuleReader.read(testAppRule(matches = null)) as GuidedRuleReadResult.Unsupported
                    ).reason,
        )
        assertEquals(
            GuidedRuleReadFailure.UnsupportedSelector,
            (
                    GuidedRuleReader.read(
                        testAppRule(anyMatches = listOf("[text=\"Skip\"]")),
                    ) as GuidedRuleReadResult.Unsupported
                    ).reason,
        )
    }

    @Test
    fun rejectsPartialLimits() {
        val partial = listOf(
            testAppRule().copy(matchTime = 10_000L),
            testAppRule().copy(
                matchTime = 9_999L,
                actionMaximum = 1,
                resetMatch = "app",
            ),
            testAppRule().copy(
                matchTime = 10_000L,
                actionMaximum = 2,
                resetMatch = "app",
            ),
            testAppRule().copy(
                matchTime = 10_000L,
                actionMaximum = 1,
                resetMatch = "activity",
            ),
        )
        partial.forEach { rule ->
            assertEquals(
                GuidedRuleReadFailure.UnsupportedLimits,
                (GuidedRuleReader.read(rule) as GuidedRuleReadResult.Unsupported).reason,
            )
        }
    }

    @Test
    fun readsWriterOutputBackToTheSameRule() {
        val original = testAppRule(
            key = 1,
            name = "Tap “Skip”",
            matches = listOf("[vid=\"settings_list\"] > @[text=\"Skip\"]"),
        )
        val data = assertIs<GuidedRuleReadResult.Success>(GuidedRuleReader.read(original)).data
        val current = RawSubscription(SubscriptionIds.LOCAL, "Local rules", 0)
        val written = assertIs<GuidedRuleWriteResult.Success>(
            GuidedRuleWriter.write(
                current,
                GuidedRuleWriteRequest(
                    appId = "com.example",
                    appName = "Example",
                    authoringGroupName = "My rules",
                    ruleName = data.name,
                    action = data.action,
                    draft = data.draft,
                    limitToAppOpening = data.limitToAppOpening,
                ),
            ),
        )
        val rule = written.subscription.apps.single().groups.single().rules.single()
        assertEquals(original, rule)
        assertNull(rule.matchTime)
    }
}
