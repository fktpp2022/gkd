package li.gkd.app.authoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import li.gkd.app.snapshot.SnapshotNodeAdapter
import li.gkd.app.subscription.RawSubscription
import li.gkd.db.SubsAppConfig
import li.gkd.db.SubsAppGroupConfig
import li.gkd.db.SubsItem
import li.gkd.db.SubscriptionConfigSnapshot
import li.gkd.db.SubscriptionIds

class GuidedAuthoringStateTest {
    private val nodes = listOf(
        testNode(0, -1, name = "android.widget.FrameLayout", childCount = 2),
        testNode(
            1, 0,
            name = "androidx.recyclerview.widget.RecyclerView",
            viewId = "com.example:id/settings_list",
            childCount = 3,
            depth = 1,
        ),
        testNode(2, 1, name = "android.widget.TextView", text = "Wi-Fi", depth = 2),
        testNode(3, 1, name = "android.widget.TextView", text = "Bluetooth", index = 1, depth = 2),
        testNode(4, 1, childCount = 1, index = 2, depth = 2),
        testNode(
            5, 4,
            name = "android.widget.ImageButton",
            clickable = true,
            depth = 3,
        ),
        testNode(
            6, 0,
            name = "android.widget.Button",
            viewId = "com.example:id/btn_ok",
            text = "OK",
            clickable = true,
            childCount = 1,
            index = 1,
            depth = 1,
        ),
        testNode(7, 6, depth = 2),
    )
    private val adapter = SnapshotNodeAdapter(nodes)

    private val local = RawSubscription(
        id = SubscriptionIds.LOCAL,
        name = "Local rules",
        version = 1,
    )
    private val enabledItem = SubsItem(id = SubscriptionIds.LOCAL, enable = true, order = 0)
    private val appId = "com.example"

    @Test
    fun draftDefaultsToTapWithoutLimitsOrNameOverride() {
        val draft = GuidedRuleDraft()
        assertEquals(GuidedRuleAction.Click, draft.action)
        assertFalse(draft.limitToAppOpening)
        assertNull(draft.nameOverride)
        assertNull(draft.context)
        assertTrue(draft.target.isEmpty())
        assertTrue(draft.usedProperties.isEmpty())
    }

    @Test
    fun conditionsKeepOneRowPerProperty() {
        val draft = GuidedRuleDraft()
            .addCondition(GuidedCondition.Text(GuidedTextOperator.Is, "OK"))
            .addCondition(GuidedCondition.Clickable)
            .addCondition(GuidedCondition.Text(GuidedTextOperator.Contains, "Later"))
        assertEquals(2, draft.target.size)
        assertEquals(
            GuidedCondition.Text(GuidedTextOperator.Contains, "Later"),
            draft.target.first(),
        )
        assertEquals(
            setOf(GuidedConditionProperty.Text, GuidedConditionProperty.Clickable),
            draft.usedProperties,
        )
        assertEquals(
            listOf(GuidedCondition.Clickable),
            draft.removeCondition(GuidedConditionProperty.Text).target,
        )
    }

    @Test
    fun updatingAConditionKeepsItsPosition() {
        val draft = GuidedRuleDraft()
            .addCondition(GuidedCondition.Text(GuidedTextOperator.Is, "OK"))
            .addCondition(GuidedCondition.ViewId("btn_ok"))
            .updateCondition(GuidedConditionProperty.Text) {
                GuidedCondition.Text(GuidedTextOperator.Contains, "OK")
            }
        assertEquals(
            listOf(
                GuidedCondition.Text(GuidedTextOperator.Contains, "OK"),
                GuidedCondition.ViewId("btn_ok"),
            ),
            draft.target,
        )
        assertEquals(
            GuidedRuleDraft()
                .addCondition(GuidedCondition.Text(GuidedTextOperator.Is, "OK"))
                .updateCondition(GuidedConditionProperty.Description) { error("unused") },
            GuidedRuleDraft().addCondition(GuidedCondition.Text(GuidedTextOperator.Is, "OK")),
        )
    }

    @Test
    fun compilerOrdersIdentifiersBeforeFlags() {
        val draft = GuidedRuleDraft()
            .addCondition(GuidedCondition.Clickable)
            .addCondition(GuidedCondition.Text(GuidedTextOperator.Is, "OK"))
        val compiled = assertIs<GuidedSelectorCompileResult.Success>(
            GuidedSelectorCompiler.compile(draft.toSelectorDraft()),
        )
        assertEquals("[text=\"OK\"][clickable=true]", compiled.selector)
    }

    @Test
    fun resolvesTheActableNodeOnlyForUnlabeledNonClickablePicks() {
        val clickable = GuidedPickResolver.resolve(nodes[6], adapter)
        assertEquals(6, clickable.actable.id)
        assertFalse(clickable.climbed)

        val labeled = GuidedPickResolver.resolve(nodes[2], adapter)
        assertEquals(2, labeled.actable.id)
        assertFalse(labeled.climbed)

        val climbed = GuidedPickResolver.resolve(nodes[7], adapter)
        assertEquals(6, climbed.actable.id)
        assertTrue(climbed.climbed)

        val classOnly = nodes[7].copy(attr = nodes[7].attr.copy(name = "android.widget.TextView"))
        val classOnlyClimbed = GuidedPickResolver.resolve(classOnly, adapter)
        assertEquals(6, classOnlyClimbed.actable.id)
        assertTrue(classOnlyClimbed.climbed)

        val noClickableAncestor = GuidedPickResolver.resolve(nodes[4], adapter)
        assertEquals(4, noClickableAncestor.actable.id)
        assertFalse(noClickableAncestor.climbed)
    }

    @Test
    fun labelsPreferTextThenDescriptionThenType() {
        assertEquals("OK", GuidedNodeLabel.of(nodes[6]))
        assertEquals("android.widget.TextView", GuidedNodeLabel.of(nodes[7].copy(attr = nodes[7].attr.copy(name = "android.widget.TextView"))))
        assertNull(GuidedNodeLabel.of(nodes[4]))
    }

    @Test
    fun buildsAnInsideContextFromAStableAnchor() {
        val context = assertIs<GuidedContextBuildResult.Success>(
            GuidedContextBuilder.build(
                mode = GuidedContextMode.Inside,
                picked = nodes[2],
                anchor = nodes[1],
                nodes = nodes,
                adapter = adapter,
            ),
        ).context
        assertEquals(GuidedContextRelation.Inside, context.relation)
        assertEquals(listOf<GuidedCondition>(GuidedCondition.ViewId("settings_list")), context.anchor)
        val compiled = assertIs<GuidedSelectorCompileResult.Success>(
            GuidedSelectorCompiler.compile(
                GuidedSelectorDraft(
                    target = listOf(GuidedCondition.Text(GuidedTextOperator.Is, "Wi-Fi")),
                    context = context,
                ),
            ),
        )
        assertEquals("[vid=\"settings_list\"] > @[text=\"Wi-Fi\"]", compiled.selector)
    }

    @Test
    fun derivesTheNextToDirectionFromNodeOrder() {
        val before = assertIs<GuidedContextBuildResult.Success>(
            GuidedContextBuilder.build(
                mode = GuidedContextMode.NextTo,
                picked = nodes[3],
                anchor = nodes[2],
                nodes = nodes,
                adapter = adapter,
            ),
        ).context
        assertEquals(GuidedContextRelation.AnchorBeforeTarget, before.relation)

        val after = assertIs<GuidedContextBuildResult.Success>(
            GuidedContextBuilder.build(
                mode = GuidedContextMode.NextTo,
                picked = nodes[2],
                anchor = nodes[3],
                nodes = nodes,
                adapter = adapter,
            ),
        ).context
        assertEquals(GuidedContextRelation.AnchorAfterTarget, after.relation)

        val compiled = assertIs<GuidedSelectorCompileResult.Success>(
            GuidedSelectorCompiler.compile(
                GuidedSelectorDraft(
                    target = listOf(GuidedCondition.Text(GuidedTextOperator.Is, "Bluetooth")),
                    context = before,
                ),
            ),
        )
        assertEquals("[text=\"Wi-Fi\"] + @[text=\"Bluetooth\"]", compiled.selector)
    }

    @Test
    fun rejectsAnAnchorWithoutAStableLabel() {
        assertEquals(
            GuidedContextBuildResult.Failure(GuidedContextBuildFailure.NoStableLabel),
            GuidedContextBuilder.build(
                mode = GuidedContextMode.Inside,
                picked = nodes[2],
                anchor = nodes[4],
                nodes = nodes,
                adapter = adapter,
            ),
        )
    }

    @Test
    fun previewsAllMatchesWithTheActableNode() {
        val preview = assertIs<GuidedPreviewResult.Ready>(
            preview(draft(GuidedCondition.Text(GuidedTextOperator.Is, "OK")), actable = nodes[6]),
        )
        assertEquals("[text=\"OK\"]", preview.selector)
        assertEquals(listOf(6), preview.matches.map { it.id })
        assertEquals(6, preview.actable.id)

        val many = assertIs<GuidedPreviewResult.Ready>(
            preview(draft(GuidedCondition.TypeName("android.widget.TextView")), nodes[2]),
        )
        assertEquals(listOf(2, 3), many.matches.map { it.id })

        val zero = assertIs<GuidedPreviewResult.Ready>(
            preview(draft(GuidedCondition.Text(GuidedTextOperator.Is, "Missing")), nodes[6]),
        )
        assertTrue(zero.matches.isEmpty())
    }

    @Test
    fun previewCountsARootOnlyMatch() {
        val preview = assertIs<GuidedPreviewResult.Ready>(
            preview(draft(GuidedCondition.TypeName("android.widget.FrameLayout")), nodes[0]),
        )
        assertEquals(listOf(0), preview.matches.map { it.id })
    }

    @Test
    fun previewsContextualSelectors() {
        val preview = assertIs<GuidedPreviewResult.Ready>(
            preview(
                GuidedSelectorDraft(
                    target = listOf(GuidedCondition.Text(GuidedTextOperator.Is, "Wi-Fi")),
                    context = GuidedContext(
                        GuidedContextRelation.Inside,
                        listOf(GuidedCondition.ViewId("settings_list")),
                    ),
                ),
                nodes[2],
            ),
        )
        assertEquals("[vid=\"settings_list\"] > @[text=\"Wi-Fi\"]", preview.selector)
        assertEquals(listOf(2), preview.matches.map { it.id })
    }

    @Test
    fun marksAnUnfinishedDraftIncompleteAndAGeneratedSyntaxFailureAsDefect() {
        assertEquals(GuidedPreviewResult.Incomplete, preview(GuidedSelectorDraft(emptyList()), nodes[6]))
        assertEquals(
            GuidedPreviewResult.Defect,
            preview(draft(GuidedCondition.TypeName("Bad[")), nodes[6]),
        )
    }

    @Test
    fun savePlanCarriesScopeAndDuplicateResult() {
        val rule = testAppRule(key = 4, matches = listOf("[text=\"OK\"]"))
        val candidates = GuidedRuleCandidates.fromGroup(
            testAppGroup(name = "My rules", rules = listOf(rule)),
            "My rules",
        )
        val plan = assertIs<GuidedSavePlanResult.Ready>(
            GuidedSavePlanner.plan(
                draft = draft(GuidedCondition.Text(GuidedTextOperator.Is, "OK")),
                actable = nodes[6],
                adapter = adapter,
                candidates = candidates,
            ),
        ).plan
        assertEquals(GuidedMatchScope.One, plan.scope)
        assertEquals(1, plan.matchCount)
        assertEquals("[text=\"OK\"]", plan.selector)
        val duplicate = assertIs<GuidedDuplicateResult.Found>(plan.duplicate)
        assertEquals(4, duplicate.candidate.ruleKey)
        assertTrue(duplicate.canReplace)

        assertEquals(
            GuidedMatchScope.Many,
            assertIs<GuidedSavePlanResult.Ready>(
                GuidedSavePlanner.plan(
                    draft = draft(GuidedCondition.TypeName("android.widget.TextView")),
                    actable = nodes[2],
                    adapter = adapter,
                    candidates = emptyList(),
                ),
            ).plan.scope,
        )
        assertEquals(
            GuidedMatchScope.Zero,
            assertIs<GuidedSavePlanResult.Ready>(
                GuidedSavePlanner.plan(
                    draft = draft(GuidedCondition.Text(GuidedTextOperator.Is, "Missing")),
                    actable = nodes[6],
                    adapter = adapter,
                    candidates = emptyList(),
                ),
            ).plan.scope,
        )
        assertEquals(
            GuidedSavePlanResult.Incomplete,
            GuidedSavePlanner.plan(
                draft = GuidedSelectorDraft(emptyList()),
                actable = nodes[6],
                adapter = adapter,
                candidates = emptyList(),
            ),
        )
        assertEquals(
            GuidedSavePlanResult.Defect,
            GuidedSavePlanner.plan(
                draft = draft(GuidedCondition.TypeName("Bad[")),
                actable = nodes[6],
                adapter = adapter,
                candidates = emptyList(),
            ),
        )
    }

    @Test
    fun reportsWhenTheRuleWillNotRun() {
        assertEquals(
            GuidedSaveNotice.None,
            notice(configs = SubscriptionConfigSnapshot(subsItems = listOf(enabledItem))),
        )
        assertEquals(
            GuidedSaveNotice.Disabled(GuidedDisabledScope.Subscription, "Local rules"),
            notice(
                configs = SubscriptionConfigSnapshot(
                    subsItems = listOf(enabledItem.copy(enable = false)),
                ),
            ),
        )
        assertEquals(
            GuidedSaveNotice.Disabled(GuidedDisabledScope.Subscription, "Local rules"),
            notice(configs = SubscriptionConfigSnapshot()),
        )
        val subscription = local.copy(
            apps = listOf(
                RawSubscription.RawApp(
                    appId,
                    "Example",
                    listOf(testAppGroup(name = "My rules", rules = listOf(testAppRule()))),
                ),
            ),
        )
        assertEquals(
            GuidedSaveNotice.Disabled(GuidedDisabledScope.App, "Example"),
            notice(
                subscription = subscription,
                configs = SubscriptionConfigSnapshot(
                    subsItems = listOf(enabledItem),
                    appConfigs = listOf(SubsAppConfig(false, SubscriptionIds.LOCAL, appId)),
                ),
            ),
        )
        assertEquals(
            GuidedSaveNotice.Disabled(GuidedDisabledScope.Group, "My rules"),
            notice(
                subscription = subscription.copy(
                    apps = listOf(
                        RawSubscription.RawApp(
                            appId,
                            "Example",
                            listOf(testAppGroup(enable = false, rules = listOf(testAppRule()))),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(
            GuidedSaveNotice.Disabled(GuidedDisabledScope.Group, "My rules"),
            notice(
                subscription = subscription,
                configs = SubscriptionConfigSnapshot(
                    subsItems = listOf(enabledItem),
                    appGroupConfigs = listOf(
                        SubsAppGroupConfig(SubscriptionIds.LOCAL, appId, 1, enable = false),
                    ),
                ),
            ),
        )
    }

    private fun draft(condition: GuidedCondition) =
        GuidedRuleDraft().addCondition(condition).toSelectorDraft()

    private fun preview(
        draft: GuidedSelectorDraft,
        actable: li.gkd.app.model.NodeInfo,
    ): GuidedPreviewResult = GuidedPreviewBuilder.build(draft, actable, adapter)

    private fun notice(
        subscription: RawSubscription = local,
        configs: SubscriptionConfigSnapshot = SubscriptionConfigSnapshot(
            subsItems = listOf(enabledItem),
        ),
    ): GuidedSaveNotice = GuidedSaveNotices.resolve(
        scope = GuidedRuleScope(
            local = subscription,
            configs = configs,
            appId = appId,
            activityId = "com.example.MainActivity",
            appInfo = null,
            launcherAppId = "com.other.launcher",
            systemAppIds = emptySet(),
        ),
        authoringGroupName = "My rules",
    )
}
