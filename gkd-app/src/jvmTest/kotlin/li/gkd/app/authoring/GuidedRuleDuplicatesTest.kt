package li.gkd.app.authoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GuidedRuleDuplicatesTest {
    private val skip = "[text=\"Skip\"]"

    @Test
    fun reportsNoDuplicateForADifferentSelector() {
        assertEquals(
            GuidedDuplicateResult.None,
            GuidedDuplicateCheck.find(
                testCandidates(listOf(testAppGroup(name = "My rules", rules = listOf(testAppRule())))),
                "[text=\"Other\"]",
            ),
        )
    }

    @Test
    fun comparesCanonicalStringsAcrossGroups() {
        val candidates = testCandidates(
            groups = listOf(
                testAppGroup(
                    key = 3,
                    name = "Other",
                    rules = listOf(testAppRule(key = 6, matches = listOf("[text = \"Skip\"]"))),
                ),
            ),
        )
        val found = assertIs<GuidedDuplicateResult.Found>(
            GuidedDuplicateCheck.find(candidates, skip),
        )
        assertEquals("Other", found.candidate.groupName)
        assertEquals(6, found.candidate.ruleKey)
        assertFalse(found.canReplace)
    }

    @Test
    fun prefersTheAuthoringGroupOverForeignGroups() {
        val candidates = testCandidates(
            listOf(
                testAppGroup(key = 1, name = "Other", rules = listOf(testAppRule(key = 1))),
                testAppGroup(key = 2, name = "My rules", rules = listOf(testAppRule(key = 9))),
            ),
        )
        val found = assertIs<GuidedDuplicateResult.Found>(
            GuidedDuplicateCheck.find(candidates, skip),
        )
        assertEquals("My rules", found.candidate.groupName)
        assertEquals(9, found.candidate.ruleKey)
        assertTrue(found.canReplace)
    }

    @Test
    fun offersReplaceOnlyForAKeyedRepresentableRuleInTheAuthoringGroup() {
        fun found(
            rule: li.gkd.app.subscription.RawSubscription.RawAppRule,
            groupName: String = "My rules",
        ): GuidedDuplicateResult.Found =
            assertIs(
                GuidedDuplicateCheck.find(
                    testCandidates(listOf(testAppGroup(name = groupName, rules = listOf(rule)))),
                    skip,
                ),
            )

        assertTrue(found(testAppRule(key = 1)).canReplace)
        assertFalse(found(testAppRule(key = null)).canReplace)
        assertFalse(found(testAppRule(key = 1).copy(fastQuery = false)).canReplace)
        assertFalse(found(testAppRule(key = 1, matches = null, anyMatches = listOf(skip))).canReplace)
        assertFalse(found(testAppRule(key = 1), groupName = "Other").canReplace)
    }

    @Test
    fun matchesAnyMatchesAlternatives() {
        val candidates = testCandidates(
            listOf(
                testAppGroup(
                    name = "My rules",
                    rules = listOf(
                        testAppRule(key = 1, matches = null, anyMatches = listOf("[vid=\"x\"]", skip)),
                    ),
                ),
            ),
        )
        val found = assertIs<GuidedDuplicateResult.Found>(
            GuidedDuplicateCheck.find(candidates, skip),
        )
        assertEquals(1, found.candidate.ruleKey)
    }

    @Test
    fun ordersCandidatesAuthoringGroupThenOtherAppGroupsThenGlobalGroups() {
        val candidates = testCandidates(
            groups = listOf(
                testAppGroup(key = 1, name = "Other", rules = listOf(testAppRule(key = 1))),
                testAppGroup(key = 2, name = "My rules", rules = listOf(testAppRule(key = 2))),
            ),
            globalGroups = listOf(
                testGlobalGroup(
                    key = 1,
                    name = "Global",
                    rules = listOf(testGlobalRule(key = 3)),
                ),
            ),
        )
        assertEquals(listOf("My rules", "Other", "Global"), candidates.map { it.groupName })
        assertEquals(listOf(true, false, false), candidates.map { it is GuidedRuleCandidate.App && it.inAuthoringGroup })
    }

    @Test
    fun ignoresStoredSelectorsThatDoNotCompile() {
        val candidates = testCandidates(
            listOf(
                testAppGroup(name = "My rules", rules = listOf(testAppRule(matches = listOf("[text=")))),
            ),
        )
        assertEquals(GuidedDuplicateResult.None, GuidedDuplicateCheck.find(candidates, skip))
    }
}
