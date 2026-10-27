package li.gkd.app.authoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import li.gkd.app.model.AppInfo
import li.gkd.app.model.NodeInfo
import li.gkd.app.snapshot.SnapshotNodeAdapter
import li.gkd.app.subscription.RawSubscription
import li.gkd.db.SubsAppConfig
import li.gkd.db.SubsAppGroupConfig
import li.gkd.db.SubsItem
import li.gkd.db.SubscriptionConfigSnapshot
import li.gkd.db.SubscriptionIds

class GuidedExistingRuleTest {
    private val appId = "com.example"
    private val appInfo = AppInfo(
        id = appId,
        name = "Example",
        versionCode = 5,
        versionName = "1.0",
        isSystem = false,
        mtime = 0L,
        hidden = false,
        userId = 0,
    )
    private val enabledItem = SubsItem(id = SubscriptionIds.LOCAL, enable = true, order = 0)
    private val local = RawSubscription(
        id = SubscriptionIds.LOCAL,
        name = "Local rules",
        version = 1,
    )

    private val nodes = listOf(
        testNode(0, -1, name = "android.widget.FrameLayout", childCount = 2),
        testNode(
            1, 0,
            name = "androidx.recyclerview.widget.RecyclerView",
            viewId = "com.example:id/settings_list",
            childCount = 2,
            depth = 1,
        ),
        testNode(2, 1, name = "android.widget.TextView", text = "Wi-Fi", depth = 2),
        testNode(3, 1, name = "android.widget.TextView", text = "Bluetooth", index = 1, depth = 2),
        testNode(
            4, 0,
            name = "android.widget.Button",
            viewId = "com.example:id/btn_ok",
            text = "OK",
            clickable = true,
            longClickable = true,
            index = 1,
            depth = 1,
        ),
    )
    private val adapter = SnapshotNodeAdapter(nodes)
    private val picked = nodes[4]

    @Test
    fun detectsARuleThatMatchesThePickedNode() {
        val hits = detect(testAppRule(key = 3, name = "Confirm", matches = listOf("[text=\"OK\"]")))
        val hit = hits.single()
        assertEquals(4, hit.target.id)
        assertEquals(3, hit.candidate.ruleKey)
        assertEquals("My rules", hit.candidate.groupName)
        assertTrue(hit.candidate.inAuthoringGroup)
    }

    @Test
    fun detectsARuleThatMatchesAnAncestorOfThePickedNode() {
        val hits = detect(
            testAppRule(matches = listOf("[vid=\"settings_list\"]")),
            picked = nodes[2],
        )
        assertEquals(1, hits.single().target.id)
    }

    @Test
    fun detectsARuleWhoseTargetIsADescendantThroughAContext() {
        val hits = detect(
            testAppRule(matches = listOf("[vid=\"settings_list\"] > [text=\"Wi-Fi\"]")),
            picked = nodes[1],
        )
        assertEquals(2, hits.single().target.id)
    }

    @Test
    fun ignoresRulesWhoseTargetIsUnrelatedToThePickedNode() {
        assertEquals(emptyList(), detect(testAppRule(matches = listOf("[text=\"Wi-Fi\"]"))))
    }

    @Test
    fun honorsExcludeMatches() {
        val rule = testAppRule(matches = listOf("[vid=\"settings_list\"]"))
        assertEquals(
            1,
            detect(rule, picked = nodes[2]).size,
        )
        assertEquals(
            emptyList(),
            detect(
                rule.copy(excludeMatches = listOf("[text=\"Bluetooth\"]")),
                picked = nodes[2],
            ),
        )
    }

    @Test
    fun honorsExcludeAllMatches() {
        val rule = testAppRule(matches = listOf("[vid=\"settings_list\"]"))
        assertEquals(
            emptyList(),
            detect(
                rule.copy(excludeAllMatches = listOf("[text=\"Missing\"]", "[text=\"Bluetooth\"]")),
                picked = nodes[2],
            ),
        )
        assertEquals(
            1,
            detect(
                rule.copy(excludeAllMatches = listOf("[text=\"Missing\"]")),
                picked = nodes[2],
            ).size,
        )
    }

    @Test
    fun supportsAnyMatchesAlternatives() {
        val rule = testAppRule(
            matches = null,
            anyMatches = listOf("[text=\"Missing\"]", "[text=\"Bluetooth\"]"),
        )
        assertEquals(3, detect(rule, picked = nodes[3]).single().target.id)
        assertEquals(emptyList(), detect(rule, picked = picked))
    }

    @Test
    fun detectsRulesWithPreKeysThroughTheirPrimarySelectors() {
        val hits = detect(
            testAppRule(matches = listOf("[text=\"OK\"]")).copy(preKeys = listOf(1)),
        )
        assertEquals(4, hits.single().target.id)
    }

    @Test
    fun honorsMatchRoot() {
        val hits = detect(
            testAppRule(matches = listOf("[name=\"android.widget.FrameLayout\"]"))
                .copy(matchRoot = true),
        )
        assertEquals(0, hits.single().target.id)
    }

    @Test
    fun skipsCandidatesWithStoredSelectorsThatDoNotCompile() {
        assertEquals(
            emptyList(),
            detect(testAppRule(matches = listOf("[text="))),
        )
    }

    @Test
    fun detectsOnlyTheValidRuleWhenAnotherStoredRuleIsInvalid() {
        val invalidGroup = testAppGroup(
            key = 9,
            rules = listOf(testAppRule(key = 9, matches = listOf("[text="))),
        )
        val validGroup = testAppGroup(
            key = 3,
            rules = listOf(testAppRule(key = 3, name = "Confirm", matches = listOf("[text=\"OK\"]"))),
        )
        assertFalse(invalidGroup.valid)

        val hits = GuidedExistingRuleDetector.detect(
            candidates = listOf(candidate(invalidGroup), candidate(validGroup)),
            picked = picked,
            nodes = nodes,
            adapter = adapter,
        )

        assertEquals(listOf(3), hits.map { it.candidate.ruleKey })
        assertEquals(listOf(4), hits.map { it.target.id })
    }

    @Test
    fun detectsTheValidRuleWhenNoStoredRuleIsInvalid() {
        val group = testAppGroup(
            key = 3,
            rules = listOf(testAppRule(key = 3, name = "Confirm", matches = listOf("[text=\"OK\"]"))),
        )
        assertTrue(group.valid)

        val hits = GuidedExistingRuleDetector.detect(
            candidates = listOf(candidate(group)),
            picked = picked,
            nodes = nodes,
            adapter = adapter,
        )

        assertEquals(listOf(3), hits.map { it.candidate.ruleKey })
        assertEquals(listOf(4), hits.map { it.target.id })
    }

    private fun candidate(group: RawSubscription.RawAppGroup): GuidedRuleCandidate =
        GuidedRuleCandidate.App(
            group = group,
            rule = group.rules.single(),
            inAuthoringGroup = true,
        )

    @Test
    fun preservesCandidateOrder() {
        val group = testAppGroup(
            rules = listOf(
                testAppRule(key = 1, name = "First", matches = listOf("[text=\"OK\"]")),
                testAppRule(key = 2, name = "Second", matches = listOf("[vid=\"btn_ok\"]")),
            ),
        )
        val hits = GuidedExistingRuleDetector.detect(
            testCandidates(listOf(group)),
            picked,
            nodes,
            adapter,
        )
        assertEquals(listOf(1, 2), hits.map { it.candidate.ruleKey })
        assertEquals(listOf(4, 4), hits.map { it.target.id })
    }

    @Test
    fun returnsEnabledLocalRulesAuthoringGroupFirst() {
        val candidates = eligibility(
            groups = listOf(
                testAppGroup(key = 1, name = "Other", rules = listOf(testAppRule(key = 1))),
                testAppGroup(key = 2, name = "My rules", rules = listOf(testAppRule(key = 2))),
            ),
        )
        assertEquals(listOf("My rules", "Other"), candidates.map { it.groupName })
        assertEquals(
            listOf(true, false),
            candidates.map { it is GuidedRuleCandidate.App && it.inAuthoringGroup },
        )
    }

    @Test
    fun returnsEmptyWhenTheLocalSubscriptionIsDisabledOrMissing() {
        assertEquals(
            emptyList(),
            eligibility(
                configs = SubscriptionConfigSnapshot(
                    subsItems = listOf(enabledItem.copy(enable = false)),
                ),
            ),
        )
        assertEquals(emptyList(), eligibility(configs = SubscriptionConfigSnapshot()))
    }

    @Test
    fun honorsAppAndGroupOverrides() {
        assertEquals(
            emptyList(),
            eligibility(
                configs = SubscriptionConfigSnapshot(
                    subsItems = listOf(enabledItem),
                    appConfigs = listOf(SubsAppConfig(false, SubscriptionIds.LOCAL, appId)),
                ),
            ),
        )
        assertEquals(
            emptyList(),
            eligibility(
                configs = SubscriptionConfigSnapshot(
                    subsItems = listOf(enabledItem),
                    appGroupConfigs = listOf(
                        SubsAppGroupConfig(SubscriptionIds.LOCAL, appId, 1, enable = false),
                    ),
                ),
            ),
        )
        val enabledByOverride = eligibility(
            groups = listOf(testAppGroup(enable = false, rules = listOf(testAppRule()))),
            configs = SubscriptionConfigSnapshot(
                subsItems = listOf(enabledItem),
                appGroupConfigs = listOf(
                    SubsAppGroupConfig(SubscriptionIds.LOCAL, appId, 1, enable = true),
                ),
            ),
        )
        assertEquals(1, enabledByOverride.size)
    }

    @Test
    fun filtersRulesByTheCapturedActivity() {
        val rule = testAppRule().copy(activityIds = listOf(".MainActivity"))
        assertEquals(emptyList(), eligibility(activityId = "com.example.Other", rule = rule))
        assertEquals(1, eligibility(activityId = "com.example.MainActivity", rule = rule).size)
        assertEquals(1, eligibility(activityId = null, rule = rule).size)
    }

    @Test
    fun filtersRulesByTheInstalledAppVersion() {
        val mismatched = testAppGroup(
            rules = listOf(testAppRule()),
        ).copy(
            versionCode = RawSubscription.IntegerMatcher(6, null, null, null),
        )
        assertEquals(emptyList(), eligibility(groups = listOf(mismatched)))
        assertEquals(1, eligibility().size)
    }

    @Test
    fun includesApplicableGlobalRules() {
        val global = testGlobalGroup(rules = listOf(testGlobalRule()))
        val candidates = eligibility(groups = emptyList(), globalGroups = listOf(global))
        assertIs<GuidedRuleCandidate.Global>(candidates.single())

        assertEquals(
            emptyList(),
            eligibility(groups = emptyList(), globalGroups = listOf(global), launcherAppId = appId),
        )
        assertEquals(
            1,
            eligibility(
                groups = emptyList(),
                globalGroups = listOf(global.copy(matchLauncher = true)),
                launcherAppId = appId,
            ).size,
        )
    }

    @Test
    fun ignoresRulesOfOtherApplications() {
        val other = RawSubscription.RawApp(
            id = "com.other",
            name = "Other",
            groups = listOf(testAppGroup(key = 1, name = "My rules", rules = listOf(testAppRule()))),
        )
        val subscription = local.copy(
            apps = listOf(
                other,
                RawSubscription.RawApp(
                    id = appId,
                    name = "Example",
                    groups = listOf(testAppGroup(key = 2, rules = listOf(testAppRule(key = 2)))),
                ),
            ),
        )
        val candidates = eligibility(subscription = subscription)
        assertEquals("My rules", candidates.single().groupName)
        assertEquals(2, candidates.single().ruleKey)
    }

    private fun detect(
        rule: RawSubscription.RawAppRule,
        picked: NodeInfo = this.picked,
    ): List<GuidedExistingRuleHit> = GuidedExistingRuleDetector.detect(
        candidates = testCandidates(listOf(testAppGroup(rules = listOf(rule)))),
        picked = picked,
        nodes = nodes,
        adapter = adapter,
    )

    private fun eligibility(
        groups: List<RawSubscription.RawAppGroup> = listOf(
            testAppGroup(rules = listOf(testAppRule())),
        ),
        rule: RawSubscription.RawAppRule? = null,
        globalGroups: List<RawSubscription.RawGlobalGroup> = emptyList(),
        subscription: RawSubscription? = null,
        configs: SubscriptionConfigSnapshot = SubscriptionConfigSnapshot(
            subsItems = listOf(enabledItem),
        ),
        activityId: String? = "com.example.MainActivity",
        launcherAppId: String = "com.other.launcher",
    ): List<GuidedRuleCandidate> {
        val actualGroups = if (rule == null) groups else listOf(
            testAppGroup(rules = listOf(rule)),
        )
        val actualSubscription = subscription ?: local.copy(
            apps = listOf(RawSubscription.RawApp(appId, "Example", actualGroups)),
            globalGroups = globalGroups,
        )
        return GuidedRuleEligibility.candidates(
            scope = GuidedRuleScope(
                local = actualSubscription,
                configs = configs,
                appId = appId,
                activityId = activityId,
                appInfo = appInfo,
                launcherAppId = launcherAppId,
                systemAppIds = emptySet(),
            ),
            authoringGroupName = "My rules",
        )
    }
}
