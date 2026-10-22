# Guided local-rule authoring on Android — UX spec

**Status:** decision-locked, October 2026. This is the destination artifact of the wayfinder map [#1](https://github.com/fktpp2022/gkd/issues/1). It specifies behaviour, not implementation.

**Sources:** selector strategy [#2](https://github.com/fktpp2022/gkd/issues/2), capture choreography [#3](https://github.com/fktpp2022/gkd/issues/3), existing-rule detection [#4](https://github.com/fktpp2022/gkd/issues/4), condition model [#5](https://github.com/fktpp2022/gkd/issues/5), interaction prototype [#6](https://github.com/fktpp2022/gkd/issues/6), MVP cut [#7](https://github.com/fktpp2022/gkd/issues/7), and the research reports under `docs/research/` (on their research branches).

**Companion artifact:** [paper prototype](../prototypes/guided-local-rule-authoring-prototype.md). The prototype shows screen order and wording; this spec is authoritative where the two differ.

---

## 1. Problem and outcome

### 1.1 Today

The only way to add a local rule is the raw JSON5 group editor (`UpsertRuleGroupPage`). A normal user must understand the selector language, the rule/group nesting, the `matches` funnel, and the subscription model before producing a working rule. The existing visual inspector is desktop-only and ends at "copy this JSON5".

### 1.2 Outcome

A user who can see an annoying element can create an active, understandable local rule without writing syntax:

1. Tap the GKD rule button on the screen.
2. Tap the element GKD should act on.
3. Confirm or adjust the conditions shown in plain language.
4. Choose tap or long-press.
5. Save.

The saved rule uses the existing local subscription, the existing selector engine, and the existing rule semantics. Nothing runs until the user saves. With the limits option off, the rule may fire immediately if the target is still visible.

### 1.3 Success signals

- A first rule can be created in under a minute on a typical ad-skip button, with no selector or JSON vocabulary on the golden path.
- Every saved rule can be traced to a visible preview and an explicit decision; nothing broad, duplicate, or zero-match is saved silently.
- The user can re-open a guided rule, or is honestly routed to JSON when the rule falls outside the guided subset.
- Rules created this way behave exactly like rules written by hand: same engine, same enablement, same logs.

---

## 2. Scope

### 2.1 In scope (v1)

- Android only; Desktop is a replay harness.
- The local subscription (`SubscriptionIds.LOCAL = -2`) and app-scoped rules only.
- Actions: tap and long-press.
- Sessions from a live capture, a stored snapshot, or the app page (latest snapshot or manual editor).
- Structured conditions, one context, live match preview, broad/narrow, zero-match save, duplicate check, existing-rule detection, guided re-edit for representable rules, raw JSON fallback.
- The limits option ("Only act right after the app opens"), off by default.

### 2.2 Out of scope (v1)

- Templates or named recipes.
- A UI for alternatives (OR), multi-context, negation, regex, `position`/index narrowing, `excludeMatches`, `preKeys`, multi-step rules.
- Activity scope editing (`activityIds` stays empty).
- Rule management: a guided rule list, detail page, delete, rename, or a guided badge outside re-capture.
- Creating global-group rules (detection of existing local global groups is in scope); remote-subscription detection; subscription authoring or sharing.
- Coordinate fallback; simulated action/automator self-test; "why not matched" inspection beyond the count.
- Schema, selector-language, or database changes.
- Desktop as a product authoring surface.

### 2.3 Non-goals

- Replacing the raw editor. It stays the escape hatch and the surface for everything outside the guided subset.
- Making the preview a guarantee. It reports what matches on the captured screen, not what will match in the future.
- Auto-running or auto-enabling rules during authoring.

---

## 3. Vocabulary

### 3.1 Terms

| Term | Meaning |
| --- | --- |
| **Authoring capture** | The transient screenshot + node tree taken for one authoring session. Never added to the snapshot gallery; deleted on save, abandon, or session teardown. |
| **Authoring group** | The per-app local group with the reserved localized name ("My rules" in English). Found by exact name or created on first save. |
| **Picked node** | The node under the user's tap. |
| **Target element / actable node** | The node the rule acts on: the picked node, or the nearest clickable ancestor when the picked node is not clickable and has no usable text/description. |
| **Condition row** | One AND-ed predicate over `text`, `desc`, `vid`, `id`, class, `clickable`, or `visibleToUser`. |
| **Context** | One relation to a second picked node: Inside or Next to. |
| **Alternative** | One complete target + optional context selector. v1 always writes exactly one. |
| **Canonical selector** | The selector string after `Selector.compile(...)` and `toString()`. This is the persisted contract. |
| **Guided-representable rule** | A rule wholly inside the guided subset (section 8.7). Only these open in the guided editor. |
| **Broad** | A selector that matches more than one node on the captured screen. Not a persisted mode. |
| **Limits option** | The single "Only act right after the app opens" toggle that writes three timing fields (section 9.5). |

### 3.2 UI vocabulary budget

Allowed on the golden path: element, screen, app, rule, tap, long-press, Inside, Next to.

Hidden until the advanced editor: selector, node, activity, activity id, group key, match time, action maximum, reset match, fast query, JSON5, subscription, package name, ancestor, sibling, index, `@`, `>`, `+`, `-`.

"Group" appears only as a name in the existing-rule and duplicate copy, never as a concept to manage.

---

## 4. Surfaces and entries

### 4.1 Entry matrix

| Entry | Platform | Screen data | Session opens | On save/abandon |
| --- | --- | --- | --- | --- |
| Floating rule button | Android | Live capture of the foreground window | Authoring page over the frozen screenshot | Return to the target app |
| GKD app page → Create rule | Android + Desktop replay | Latest stored snapshot for that app; if none, the manual condition editor | Authoring page or manual editor | Return to the app page |
| Stored snapshot → Create rule | Android + Desktop replay | That snapshot | Authoring page | Return to the snapshot detail |
| Desktop fixture replay | Desktop | Fixture snapshot | Authoring page | Return to the replay page |

No Quick Settings tile, notification action, share target, launcher shortcut, or "arm for the next screen" state. The existing snapshot button keeps its current behaviour.

### 4.2 Floating rule button

- A dedicated overlay button, **off by default**, enabled in GKD settings with a short explanation (A0).
- Requires the accessibility service and the overlay permission. Enabling the switch walks the user through whatever is missing; the switch does not turn on until both are available.
- Tap hides all GKD overlays, captures the foreground app's active window, and opens the authoring page. One tap, no confirmation.
- If capture fails, the page stays in GKD with a plain-language retry/dead-end state (A9); it never pretends to have a screen.
- If the overlay permission is revoked while the button is on, the button disappears and the setting shows the permission action; the app-page and snapshot entries remain usable.

### 4.3 GKD app page entry

- One action on the app's local-rule area: **Create rule**.
- Uses the newest stored snapshot for that app when one exists, labelled with its age ("Preview from a snapshot taken 2 hours ago").
- With no stored snapshot, opens the manual condition editor (A14): same rows, no preview, no capture.
- Never creates an empty snapshot as a side effect.

### 4.4 Stored snapshot entry

- **Create rule from this snapshot** on the snapshot detail, available when the snapshot has an app id and at least one usable node.
- Uses the stored tree for picking, preview, and detection. The session does not modify or delete the snapshot.
- When the snapshot has no app id or no usable nodes, the action is disabled with the dead-end explanation rather than opening a broken picker.

### 4.5 Desktop

- The authoring page opens against a fixture snapshot for layout and state work.
- The floating entry reports "Not supported on this platform" as a non-modal message.
- Desktop is not evidence for pick accuracy, capture timing, or a11y behaviour.

---

## 5. Session lifecycle

### 5.1 Capture

- One capture per session: screenshot + node tree of the active window, plus the app id/label.
- The capture is transient and never listed in the snapshot gallery.
- It is deleted when the session ends by save, abandon, or teardown. A process death drops the session and the capture; v1 does not restore authoring state.
- Rotation and other configuration changes preserve the session (screenshot, tree, draft, preview) without re-capturing.

### 5.2 Back stack and return

- Floating entry: the authoring task closes on save/abandon and the target app resumes.
- GKD entry: returns to the originating page with its scroll state.
- Editing an existing rule pushes the edit state inside the session; Back returns to the new-rule draft unchanged, with no discard prompt.
- Opening the raw editor hands off to the existing group editor; Back returns to the guided draft.

### 5.3 Save, failure, discard

- **Save** writes through the existing local-subscription update path, then shows "Rule saved." and returns.
- The rule is active immediately; no "try it now" step. With the limits option off, it may fire before the user returns if the target is still visible; with the option on, it waits for the next app opening.
- **Save failure** keeps the draft and the current screen; shows an inline retryable message. No navigation, no data loss.
- **Discard** shows a confirmation only when the draft has changes. A draft has changes when the user modified the selection-derived prefill (conditions, action, context, name, or limits); returning immediately after a pick counts as no changes and closes directly. The confirmation is the only modal on the abandon path.

---

## 6. Screens and states

### 6.1 State machine

| State | Entered from | Primary actions | Exits |
| --- | --- | --- | --- |
| A0 Enable button | GKD settings | Turn on / Not now | Settings |
| A1 Capture | Entry | Wait for screenshot + nodes | A2 Pick, A9 Dead end |
| A2 Pick | A1 | Tap a node | A2b Overlap, A3 Conditions |
| A2b Overlap | >1 node at the point | Choose candidate / Cancel | A3 Conditions |
| A3 Conditions | Pick, edit, context | Edit rows, action, options; Save | A4 Broad, A5 Context, A6 Banner (inline), A8 Zero (inline), A12 Save |
| A4 Broad | Save with N > 1 | Narrow it / Keep it broad / Cancel | A5, A7 Duplicate, A3 |
| A5 Context pick | Add context / Narrow it | Pick container or sibling | A3 |
| A6 Existing rule | Detection after a pick | Edit rule / Edit in JSON / Create anyway / Dismiss | A10 Edit, A11 Raw, A3 |
| A7 Duplicate | Save | Replace / Keep both / Cancel | A12 Saved, A3 |
| A8 Zero match | Live count 0 | Save anyway / Edit conditions | A12 Saved, A3 |
| A9 Dead end | No nodes, no screenshot, capture failure | Try again / Describe manually / Open advanced editor | A1, A14, A11 |
| A10 Edit existing | Banner, re-capture | Edit rows; Save | A3, A11 Raw |
| A11 Raw | Edit as JSON, fallbacks | Existing group editor | Session |
| A12 Save result | Confirmed save | Return | Origin |
| A13 Save error | Write failure | Retry | A12, A3 |
| A14 Manual editor | No snapshot, dead end | Add rows; Save | A12, A11 |

A6 and A8 are inline states on A3, not separate pages.

### 6.2 A0 — First-run enable sheet

- Opened from the setting "Create rules from what you see".
- Copy states what the button does, that GKD captures only when tapped, and that the capture is deleted when the session ends.
- Actions: Turn on (continues the permission/setup flow), Not now.
- No authoring page is reachable from here.

### 6.3 A1 — Capture and analysis

- Opens on the frozen screenshot as soon as it exists; nodes may still be loading.
- A short "Finding elements…" hint while indexing. No blocking dialog.
- `FLAG_SECURE` screenshot with usable nodes: neutral grid with node outlines; copy says the screenshot is protected and elements can still be chosen by outline.
- No screenshot and no nodes, or an unusable tree: A9.

### 6.4 A2 — Pick

- Tap hit-tests the stored node bounds. Zoom/pan transforms both the screenshot and the hit targets together.
- The smallest meaningful node at the point is selected by default; a ring and a one-line summary appear.
- Summary: "Selected: “<label>”" and the resolved action target when it differs ("Acting on the button around this text").
- Actions: **Use this**, **Choose again**.
- If the picked node is not clickable and has no usable text/description, the actable node is the nearest clickable ancestor; this is stated, not hidden.
- Accessibility alternative: **Choose from a list** lists labeled nodes in screen order with their indent; selecting one behaves like a pick. If the list is empty, route to A14.
- More than one candidate at the point: A2b.

### 6.5 A2b — Which element?

- Candidates are the smallest meaningful nodes at the point, sorted smallest-first; the smallest clickable node with text is preselected.
- Each row: label (text → desc → type), type, size. Selecting a row draws its bounds behind the sheet.
- Actions: Use this, Cancel.

### 6.6 A3 — Conditions and preview

- Header: "New rule for <App>" (or "Edit rule: <name>").
- Preview: screenshot thumbnail with every match highlighted and the actable node ringed. Expands to full screen; highlights scale.
- Count: always qualified, pluralized, e.g. "1 element matches on this screen." During recomputation show an inline progress state; editing stays enabled.
- Action row: **Tap** / **Long-press**, default Tap. Changing it updates the auto-suggested rule name until the name is edited.
- Condition rows: prefilled, editable, removable. Text and Description have an is/contains toggle.
- **Add condition** sheet: Text, Description, View ID, Type, Clickable, Visible, More → Full ID. Properties already used are hidden; v1 allows one row per property.
- **Add context**: starts A5. Once set, a chip reads `Inside “<anchor>” ×` or `Next to “<anchor>” ×`.
- Options (collapsed): the limits option, the rule name (auto-suggested, editable), and Advanced → the read-only canonical selector.
- Overflow: **Edit as JSON** (A11). Not part of the basic path.
- Save:
  - 0 matches → A8 inline, primary button becomes "Save anyway".
  - 1 match → duplicate check (A7 if needed) → save.
  - N > 1 → A4 broad sheet before saving.
- Back without edits: return to A2 pick. Back with edits: ask to discard (A12); Keep editing stays on A3. Changing the picked element after this point means discarding and starting over in v1.

### 6.7 A4 — More than one match

- Title: "N elements match on this screen." Body: "GKD will act on whichever one appears first. If this includes other buttons, narrow it."
- Shows every match; the picked one is emphasized.
- **Narrow it** → A5. **Keep it broad** → confirmation naming the risk: "This may also act on N other elements. Save anyway?" → duplicate check → save.
- Never says or implies that all matches will be tapped. The engine acts on one node.

### 6.8 A5 — Narrow with context

- Frozen screenshot in pick mode with the target still highlighted.
- Hint: "Tap the container or nearby element that identifies this one."
- Mode control: **Inside** / **Next to**. Next to derives the relation direction from node order; the symbol is never shown.
- The anchor must have a stable identifier (text, desc, vid, id, or class). If it has none: "That element has no stable label. Try its container or another nearby element."
- One context unit in v1. If the count is still > 1, A4 returns with the same choices.
- The context chip can be removed or replaced from A3.

### 6.9 A6 — Existing rule (inline on A3)

- Scanned after a pick resolves; appears when the scan finds an enabled local rule that would fire on the captured screen. It never blocks picking or editing.
- Names the top match's group: "This is already handled by “<name>”." With more matches, "+N more" expands the ordered list (authoring group → other local app groups → local global groups) and lets the user choose.
- **Edit rule** — only when the rule is in the authoring group and fully representable. Opens A10; Back returns here with the new draft intact.
- **Edit in JSON** — every other match, including a non-representable rule in the authoring group. Opens A11 with "This rule uses advanced settings the guided editor can't show."
- **Create anyway** and dismiss. Dismissal applies to this pick only.
- Remote rules are not scanned.

### 6.10 A7 — Duplicate at save

- Comparison is canonical selector equality against enabled local rules applicable to the target app; not "matches the same node".
- Same authoring group, representable duplicate:
  - "An identical rule already exists in “My rules”."
  - **Replace it** rewrites the rule in place, keeping its key and its position; the draft's name, action, selector, and limits replace the old values.
  - **Keep both** appends a new rule.
  - **Cancel** returns to A3.
- Same authoring group, non-representable duplicate: **Keep both**, **Edit in JSON**, **Cancel**. Replace is not offered because it would silently drop settings the guided editor cannot show.
- Any other local group: warning that the guided editor will not change that group; **Keep both** only, plus Cancel. No Replace, no editing of the foreign group.

### 6.11 A8 — Zero match (inline on A3)

- "Nothing on this screen matches yet. The element may not be showing now. You can still save, or edit the conditions."
- Primary action becomes **Save anyway**; no second confirmation dialog.
- If the session has no preview (manual editor or no nodes), show "Preview unavailable" instead of a zero count and allow saving without this warning.

### 6.12 A9 — Dead end

- Used when the tree has no usable nodes, the tree is empty or merged, or capture fails.
- "GKD couldn't read this screen's elements. Some apps hide their interface from accessibility tools."
- Actions:
  - **Try again** → A1.
  - **Describe the element manually** → A14, with typeahead over whatever labels exist (possibly none).
  - **Open advanced editor** → A11.
- No coordinate fallback; no fake success.

### 6.13 A10 — Edit an existing rule

- Same layout as A3, title "Edit rule: <name>".
- Rows are re-derived from the stored selector. The canonical selector is read-only under Advanced.
- Save updates in place, keeping the rule key and position.
- The current capture (or the stored snapshot that started the session) is the preview; if it has no screenshot, the node-outline grid is used.
- No delete action in v1; delete stays in the existing management surfaces.

### 6.14 A11 — Raw handoff

- Opens the existing group editor for the containing group.
- When the draft has no group yet, it opens the new-group editor preloaded with the draft rule text in the authoring group; nothing is written until the raw editor saves.
- One-line note: "Advanced: this is GKD's rule format."
- Leaving without saving returns to the guided draft.
- Saving returns to the guided view only if the rule is still representable; otherwise the session ends at the raw editor.

### 6.15 A12 — Save result and abandon

- Success: "Rule saved." toast, then the return path for the entry.
- Abandon with changes: "Discard this rule?" → Discard / Keep editing. Discard deletes the capture.
- Abandon without changes: no dialog.

### 6.16 A13 — Save error

- Inline banner on the current screen: "Couldn't save the rule. Try again."
- **Retry** repeats the save; the draft, preview, and conditions stay intact.
- The raw editor remains reachable.

### 6.17 A14 — Manual condition editor

- Same rows and options as A3, without a screenshot or a pick.
- Add condition works as usual; Next to/Inside require a captured tree, so the context action is unavailable here.
- If a captured or stored tree is available, the typeahead uses its labels; otherwise it is empty. There is no preview when the session has no screenshot.
- Save performs the duplicate check; the existing-rule banner is unavailable without a tree.
- Saved rules are identical in shape to picked rules.

---

## 7. Interaction rules

### 7.1 Picking

- Hit-testing uses stored bounds, not rendered pixels; zoom/pan do not change which node a tap resolves to.
- The tap point resolves to the smallest meaningful node; zero-area, invisible, and snapshot-only nodes are not candidates.
- When several candidates share the point, A2b decides; never guess silently between siblings with different labels.
- Selecting a node always shows which node will be acted on, including the actable-ancestor climb.

### 7.2 Preview

- The count is computed against the captured tree with the selector engine, the same semantics the a11y engine uses.
- Highlights show every match; the actable node is visually distinct and not distinguished by colour alone.
- Recompute is asynchronous and never disables editing. Stale counts are not shown as current: show a progress state until the new count is ready.
- The count is scoped: "on this screen", never a universal claim.

### 7.3 Sheets and dialogs

- Bottom sheets for overlap, Add condition, broad/narrow, and duplicates; the existing-rule banner is inline.
- Only duplicate, discard, and broad confirmation interrupt; everything else is inline or a sheet.
- No transition, save, or animation disables taps or delays input.
- A sheet dismisses on system Back before the page does.

### 7.4 Accessibility

- Every control has a meaningful label; the preview canvas exposes the selected element and the match count.
- **Choose from a list** on A2 is the non-visual pick path (screen order, labeled nodes, 48 dp rows).
- Match highlights and the selection ring use shape/outline as well as colour.
- Target size: 48 dp minimum; text scales to the system font size; no content is clipped at large font sizes.
- The page respects IME insets; the value editor sheet moves above the keyboard.
- TalkBack: the sheet title is announced; the count updates are announced politely, not as an interrupt.

### 7.5 Localization and data

- All UI copy uses Compose string/plural resources; counts use plurals; parameters are positional (`%1$s`).
- App labels, element labels, group names, and rule names are user/app data; never concatenate them into a translatable string.
- Labels are whitespace-collapsed and truncated for display only; condition values preserve the exact captured string.
- The hidden vocabulary is not localized; it only exists in the raw editor.
- The authoring group's name is locale-dependent by design; the consequences are documented in section 9.1.

---

## 8. Condition model and selector construction

### 8.1 Condition rows

| Row | Operators | Persisted form | Notes |
| --- | --- | --- | --- |
| Text | is / contains | `text="…"` / `text*="…"` | Empty/whitespace-only rejected; dynamic text hint |
| Description | is / contains | `desc="…"` / `desc*="…"` | Same |
| View ID | is | `vid="…"` | Resource id name |
| Full ID | is | `id="…"` | Under More |
| Type | is | class token (suffix match) | Under More |
| Clickable | true | `clickable=true` | False/negation stays raw |
| Visible | true | `visibleToUser=true` | False/negation stays raw |

- One row per property in v1; used properties are hidden from Add condition.
- At most three identifier rows are added automatically before the flow stops trying to narrow; the user may still add any supported rows manually, up to one per property.
- No regex, negation, arithmetic, index, `position`, size, bounds, child count, or `excludeMatches` in the guided rows.

### 8.2 Context

- One context unit, added by a second pick:
  - Inside → `[anchor] > @[target]`.
  - Next to → `[anchor] + @[target]` when the anchor precedes the target; `[anchor] - @[target]` when it follows.
- `@` marks the target whenever a context is present.
- The anchor uses stable identifiers only (text/desc/vid/id/type); flags are not anchors.
- The chip shows the relation and the anchor label; changing or removing it recomputes the preview.

### 8.3 Pre-fill from the pick

1. Resolve the actable node (climb only when the picked node is neither clickable nor labeled).
2. Try identifiers in order: text → vid → id → desc → class. Icon-only nodes use desc first. Skip empty values and snapshot-only fields.
3. Dynamic text (pure digits, `%`, counters, timestamps): if there is a stable prefix of at least two characters, prefill `contains` with the hint "This text may change"; otherwise use the next identifier.
4. Start with the best identifier, compute the count, and add the next identifier while the count is 0 or > 1 and fewer than three rows exist. Class/flags are filters, not identifiers.
5. If still ambiguous, try one context. Stop at one match.
6. If still > 1, offer Broad/Narrow. If still 0, show A8. Never over-fit beyond the limits above; prefer broad or raw.

### 8.4 Compilation and canonical form

- Build each selector from structured parts with the shared selector-string escaper; never interpolate user text into syntax by hand.
- Compile with `Selector.compile`; canonicalize with `toString()`; persist only the canonical string.
- Identifier comparisons precede flag filters so the selector remains fast-query eligible; `fastQuery` is never auto-enabled.
- One alternative → `matches: "<selector>"` (a single string, as the parser accepts both a string and an array).
- Two or more alternatives → `anyMatches: ["…", "…"]`; never both `matches` and `anyMatches`. The v1 UI cannot create more than one alternative, but the mapping is fixed now.
- A generated selector that fails to compile is a defect: keep the draft, show A13, and offer raw; do not save a partial selector.

### 8.5 Broad, narrow, zero

- Broad vs narrow is selector specificity, not a persisted mode.
- N > 1: highlight all, emphasize the target, offer Narrow (context) or Keep broad with a confirmation that names the decoy risk.
- N = 0: explicit save (A8); the element may appear later or the conditions may be wrong.
- N = 1: no extra ceremony beyond the duplicate check.

### 8.6 Alternatives (future)

The model supports alternatives as complete target + context selector sets. When the UI ships (phase 2), they are shown as "Match any variant", ordered, reorderable, and compiled to `anyMatches`. Alternatives never resolve on-screen ambiguity; that remains Broad vs Narrow.

### 8.7 Parse-back and guided-representable

A rule opens in the guided editor only when all of the following hold:

- It is in the authoring group (owned by exact reserved name).
- Action is `click` or `longClick`.
- Rule-level fields are limited to the v1 subset: no `preKeys`, `position`, `swipeArg`, `excludeMatches`, `excludeAllMatches`, `actionCd`, `actionDelay`, `matchDelay`, `fastQuery`, `matchRoot`, `forcedTime`, `order`, `priorityTime`, `priorityActionMaximum`, `actionCdKey`, `actionMaximumKey`, `snapshotUrls`, `excludeSnapshotUrls`, or `exampleUrls`.
- Timing fields are either absent or exactly the limits bundle (`matchTime: 10000`, `actionMaximum: 1`, `resetMatch: "app"`).
- The selector is wholly inside the row/context subset: literal comparisons on the whitelisted properties, at most one context, `@` only on the target, and exactly one `matches` selector. A rule using `anyMatches` opens raw until alternatives ship in phase 2.
- Anything else opens raw with a plain-language reason. Never partially map and drop a condition.

Parse-back requires a read-only selector AST (or a restricted parser sharing the engine grammar) exposed by `gkd-selector`. No grammar fork in the app. This is a v1 prerequisite for guided editing, not for authoring from scratch; if it is not delivered, representable rules route to raw instead of silently degrading.

---

## 9. Persistence mapping (JSON5)

### 9.1 Authoring group

- One group per app, found or created by exact name: the localized "My rules" string resource.
- The group is created on the first save for that app, with `enable: true` and a fresh group key allocated by the existing subscription editor path.
- If the user renames the group or changes the app language, the old group becomes foreign: its rules are still detected, but Edit opens JSON, and the next guided save creates a new "My rules" group. This is accepted, not a bug.
- Group enablement is never changed by later guided saves.
- The local subscription itself already exists (created by the repository); the guided flow never creates or re-enables it.
- If the local subscription is disabled when A3 opens, or the existing authoring group is disabled when A3 resolves it, A3 shows a non-blocking notice ("This rule won't run until “%1$s” is turned on.", naming the disabled subscription or group). Saving still succeeds; the guided flow never flips the switch. A newly created group is enabled, so this notice only concerns pre-existing state.

### 9.2 Fields written

| Field | Value |
| --- | --- |
| `apps[].id` | Captured package name |
| `apps[].name` | App label when available |
| `groups[].name` | Reserved localized authoring-group name |
| `groups[].key` | Existing or freshly allocated group key |
| `groups[].enable` | `true` only on creation |
| `groups[].rules[].name` | Auto-suggested as `Tap “<label>”` / `Long-press “<label>”` (fallback `Tap element`), localized at creation, user-editable; omitted when empty |
| `groups[].rules[].key` | Existing rule key on replace; otherwise `max(existing keys)+1`, starting at 1 |
| `groups[].rules[].action` | `"click"` or `"longClick"` |
| `groups[].rules[].matches` | Canonical selector string (one alternative) |
| `groups[].rules[].matchTime` | `10000` only when the limits option is on |
| `groups[].rules[].actionMaximum` | `1` only when the limits option is on |
| `groups[].rules[].resetMatch` | `"app"` only when the limits option is on |

- `activityIds` is omitted (empty means all screens in the app).
- The write goes through the existing local-subscription update path under its lock: find-or-create the group and append/replace the rule in one update. Never write a whole subscription from a stale copy.
- No new persisted fields, tables, or schema versions; the transient authoring capture may use the existing cache area and is deleted at session end.

### 9.3 Example — limits off

```json5
{
  id: -2,
  name: "Local rules",
  version: 0,
  apps: [
    {
      id: "com.example.app",
      name: "Example",
      groups: [
        {
          name: "My rules",
          key: 1,
          enable: true,
          rules: [
            {
              name: "Tap “跳过 5s”",
              key: 1,
              action: "click",
              matches: "[text=\"跳过 5s\"]",
            },
          ],
        },
      ],
    },
  ],
}
```

Quoting is delegated to the existing JSON5 encoder; the example shows equivalent JSON5 and English placeholder names (the real subscription/group names are localized). The canonical selector is `[text="跳过 5s"]`.

### 9.4 Example — limits on

```json5
{
  name: "Tap “跳过 5s”",
  key: 2,
  action: "click",
  matches: "[text=\"跳过 5s\"]",
  matchTime: 10000,
  actionMaximum: 1,
  resetMatch: "app",
}
```

### 9.5 Limits semantics

- `matchTime` is a time-since-reset cutoff. With `resetMatch: "app"`, the reset happens on the first activity after an app change, so the window covers the opening of the app session, not 10 seconds after each match.
- On means: at most one action, only within 10 seconds of the app session opening.
- Off means: no timing fields. The engine's default `actionCd` (1000 ms) is the only throttle; a persistent target can be acted on roughly once per second until it disappears.
- A rule saved mid-session with the option on may not fire until the app is opened again; this is expected and is consistent with the copy. There is no "try it now" in v1.
- Copy: "Only act right after the app opens", helper "Stops 10 seconds after the app opens, or after one tap." The option is offered, never applied silently.
- Recommendation in the UI is explicit: splash screens may benefit; later screens will time out.

### 9.6 Fields the guided flow never writes

`anyMatches` (until alternatives ship), `excludeMatches`, `excludeAllMatches`, `preKeys`, `position`, `swipeArg`, `activityIds`, `excludeActivityIds`, `versionCode`, `versionName`, `fastQuery`, `matchRoot`, `matchDelay`, `actionDelay`, `actionCd`, `forcedTime`, `order`, `priorityTime`, `priorityActionMaximum`, `actionCdKey`, `actionMaximumKey`, `scopeKeys`, category keys, snapshots/examples, and any group-level defaults.

---

## 10. Existing-rule detection

- Runs after a pick resolves, against the captured tree, off the main thread; the banner appears when the scan finishes and never blocks the preview.
- Candidates: enabled local rules eligible on the captured screen — the captured app's groups plus applicable local global groups. Eligibility reuses the existing policy: subscription/category/app/group enablement, group validity, activity prefix matching (`activityIds` empty = all, `excludeActivityIds`), version matchers.
- Each candidate's selectors are evaluated on the captured tree. A hit is when the final matched node is the picked node, an ancestor, or a descendant; `anyMatches` fallback and exclusions are honoured.
- `preKeys` rules are tested on primary selectors only; the banner is advisory, not proof that the trigger happened.
- Ordering: authoring group → other local app groups → local global groups. The banner names the first; "+N more" lists the rest.
- Re-runs on a new pick; a dismissal applies to that pick only.
- Remote rules are not scanned in v1.
- The scan uses the same snapshot-backed adapter as the preview; if the two disagree, the preview is authoritative for the user and the discrepancy is a defect.

---

## 11. Duplicate check on save

- Before writing, canonical-compare the draft selector with every enabled local rule applicable to the target app.
- Compare canonical strings only; two selectors that match the same node are not duplicates.
- Same authoring group and representable → Replace / Keep both / Cancel.
- Same authoring group but not representable → Keep both / Edit in JSON / Cancel.
- Another local group, including a local global group → Keep both only, plus Cancel; the guided flow never edits a foreign group.
- Replace keeps the existing key and position and replaces name, action, selector, and limits. Keep both allocates a fresh key.
- No remote comparison.

---

## 12. Diagnostics and safety

| Situation | Surface | Required behaviour |
| --- | --- | --- |
| 1 match | Inline count | No ceremony; save proceeds to duplicate check |
| N > 1 | A4 sheet + confirmation | Names the count and the decoy risk; acts on one node only |
| 0 matches | A8 inline | Explicit "Save anyway"; never a silent save |
| Dynamic text | Row hint | "This text may change"; prefill uses contains with a stable prefix or switches identifier |
| Existing rule | A6 banner | Informational, non-blocking; guided edit or raw |
| Duplicate | A7 dialog | Replace only for representable rules in the authoring group |
| Subscription or existing group disabled | A3 notice (before save) | Save still succeeds; states that the rule won't run until it is turned on; never flips the switch |
| Protected screenshot | A1/A2 | Node outlines; no fake screenshot |
| No usable tree | A9 | Honest dead end; manual/raw routes |
| Capture/permission failure | A9 / settings | Retry or guide to the existing enable paths |
| Save failure | A13 | Draft preserved; retry |
| Abandon with edits | A12 | Discard confirmation |
| Limits option | A3 options | Off by default; helper states the 10 s/one-tap consequence |

Rules are never described as "verified", "safe", or "working". The preview speaks only about the captured screen.

---

## 13. Copy model

English source strings; ship as Compose resources with plurals and positional parameters.

| Context | Copy |
| --- | --- |
| Enable sheet title | Create rules from what you see |
| Enable sheet body | Adds a small GKD button over other apps. Tap it on a screen you want to fix; GKD captures that screen and lets you pick the element to act on. |
| Enable actions | Turn on / Not now |
| Pick hint | Tap the element GKD should act on. |
| Selected | Selected: “%1$s” |
| Actable climb | Acting on the button around this text |
| Choose from list | Choose from a list |
| Overlap title | Which element do you mean? |
| Conditions title | New rule for %1$s |
| Edit title | Edit rule: %1$s |
| Conditions heading | When this appears: |
| Action label | Action |
| Action options | Tap / Long-press |
| Add condition | Add condition |
| Add context | Add context |
| Context modes | Inside / Next to |
| Context chip | Inside “%1$s” / Next to “%1$s” |
| Context invalid anchor | That element has no stable label. Try its container or another nearby element. |
| Count (one) | %1$d element matches on this screen. |
| Count (many) | %1$d elements match on this screen. |
| Broad title | %1$d elements match on this screen. |
| Broad body | GKD will act on whichever one appears first. If this includes other buttons, narrow it. |
| Broad actions | Narrow it / Keep it broad |
| Broad confirm | This may also act on %1$d other elements. Save anyway? |
| Narrow hint | Tap the container or nearby element that identifies this one. |
| Dynamic text hint | This text may change |
| Existing rule | This is already handled by “%1$s”. |
| Existing advanced | This rule uses advanced settings the guided editor can't show. |
| Existing actions | Edit rule / Edit in JSON / Create anyway |
| Duplicate same group | An identical rule already exists in “%1$s”. |
| Duplicate other group | An identical rule already exists in another local group. The guided editor won't change that group. |
| Duplicate actions (representable) | Replace it / Keep both / Cancel |
| Duplicate actions (non-representable) | Keep both / Edit in JSON / Cancel |
| Duplicate actions (foreign group) | Keep both / Cancel |
| Zero match | Nothing on this screen matches yet. The element may not be showing now. You can still save, or edit the conditions. |
| Save anyway | Save anyway |
| Preview unavailable | Preview unavailable |
| Preview age | Preview from a snapshot taken %1$s ago |
| Dead end | GKD couldn't read this screen's elements. Some apps hide their interface from accessibility tools. |
| Dead-end actions | Try again / Describe the element manually / Open advanced editor |
| Protected screenshot | The screenshot is protected. You can still choose elements by their outlines. |
| Finding elements | Finding elements… |
| Limits option | Only act right after the app opens |
| Limits helper | Stops 10 seconds after the app opens, or after one tap. |
| Disabled notice | This rule won't run until “%1$s” is turned on. |
| Rule name | Rule name |
| Save | Save |
| Saved | Rule saved. |
| Save error | Couldn't save the rule. Try again. |
| Discard title | Discard this rule? |
| Discard actions | Discard / Keep editing |
| Raw note | Advanced: this is GKD's rule format. |

Rules: counts are pluralized; names/labels are data; no sentence concatenation; no vocabulary from section 3.2 on the golden path.

---

## 14. Phasing

**Phase 1 — this spec**

- All entries, the one-page flow, condition rows + one context, live preview, broad/narrow, zero-match save, duplicate check, existing-rule banner, guided re-edit for representable rules, raw fallback, limits option, copy, accessibility list pick.

**Phase 2 — after device evidence**

- Alternatives/OR UI mapped to `anyMatches`.
- Activity scope editing.
- Rule list/detail entry for guided rules and delete.
- Templates/recipes (needs corpus evidence for wording and selection).
- Risky-target classification and stronger gating based on measured decoy rates.
- Multi-capture examples.

**Phase 3 — later**

- Remote subscription detection (after measuring scan cost).
- Multi-step/`preKeys` authoring.
- Simulated action/self-test.

---

## 15. Risks and prerequisites

| Risk / prerequisite | Impact | Mitigation |
| --- | --- | --- |
| No snapshot-backed `NodeAdapter` in commonMain | Preview, counts, detection, and the preview after re-editing cannot work | Build it first; it is a shared prerequisite, not an editor detail |
| No read-only selector AST API | Guided edit cannot re-derive rows without forking grammar | Add a read-only AST or restricted parser in `gkd-selector`; otherwise route representable rules to raw |
| Dialog/other-window capture coverage | Targets in non-active windows may be missing | Device-test; dead end and re-capture remain honest |
| Floating overlay timing/back stack | Capture may race the overlay hiding; return may land on the wrong screen | Prototype on device before UI polish; test permission loss |
| Process death mid-session | Draft and capture are lost | Explicitly accepted; transient capture is deleted, no stale data |
| Preview/engine disagreement | The saved rule may not fire as previewed | Same adapter and selector engine; parity tests on fixture snapshots |
| Repeated action on a persistent target | A rule without limits can act ~once per second | Broad confirmation plus the limits option; document the default honestly |
| Reserved-name group and localization | Rename/language change creates a second group | Accepted in #4; documented in 9.1; old group remains editable as raw |
| Stale stored snapshot | Preview may not represent the current screen | Show snapshot age; keep the count scoped to the preview |

---

## 16. Device-test checklist

**Setup and permissions**

- [ ] Enable the floating rule button with both prerequisites present; confirm the explanation and switch state.
- [ ] Deny the overlay permission; the switch stays off and offers the system settings action.
- [ ] Revoke the overlay permission while the switch is on; the button disappears and the GKD entries still work.
- [ ] Turn the accessibility service off; the entry routes to the existing enable path, never a fake picker.

**Capture**

- [ ] Floating button on a normal app: screenshot and nodes match the foreground window.
- [ ] Target inside a dialog or a second window: record whether it is captured; dead end if not.
- [ ] Split-screen and IME visible: capture and hit-testing remain coherent.
- [ ] `FLAG_SECURE`: neutral grid, node outlines, picking and counting work.
- [ ] Empty/merged tree: A9, manual and raw routes work.
- [ ] Process death mid-session: no orphan capture, no resumed half-session.

**Pick and preview**

- [ ] Overlapping nodes at one point: A2b candidates and preselection are correct.
- [ ] Zoom/pan: selection ring and hit targets stay aligned.
- [ ] Unlabeled clickable wrapper: actable climb is chosen and explained.
- [ ] Counts and highlighted nodes agree with the a11y engine on the same tree, including a decoy close button.
- [ ] Edit conditions rapidly: no stale count shown as current; editing never blocks.

**Conditions and context**

- [ ] Prefill ladder: text → vid → id → desc → class; dynamic text uses contains/prefix or the next identifier.
- [ ] One row per property; used properties hidden from Add condition.
- [ ] Inside and both Next-to directions compile and match the intended node.
- [ ] Invalid anchor (no stable identifier) shows the guidance copy.
- [ ] Canonical round-trip: model → canonical selector → model for every supported row/operator/context.

**Save and limits**

- [ ] Limits off: the rule fires whenever the selector matches, subject to the 1 s cooldown.
- [ ] Limits on: at most one action within 10 s of the app session opening; re-entering the app resets the window; a rule saved mid-session waits for the next opening.
- [ ] Save from the floating entry returns to the target app; with limits off, the rule can fire immediately.
- [ ] Save from the app/snapshot entry returns to the originating page with its scroll state.
- [ ] Save failure keeps the draft and retry works.
- [ ] Duplicate paths: replace in the authoring group, keep both in the authoring group, keep both in a foreign group, non-representable duplicate offers no Replace.
- [ ] Zero-match save is explicit; no second dialog.
- [ ] Abandon with edits asks; abandon without edits does not.
- [ ] Saving into a disabled authoring group or a disabled local subscription succeeds and shows the notice; enabling the group makes the rule fire.

**Re-entry and raw**

- [ ] Re-capture the same screen: the banner detects the new rule and guided edit round-trips.
- [ ] Rename the authoring group or change the app language: detection still works, Edit opens JSON, a new guided save creates a new group.
- [ ] Edit in JSON round-trip: save a representable rule and reopen it guided; save a non-representable rule and reopen it raw.
- [ ] The rule's canonical selector is shown read-only under Advanced and matches the file.

**Accessibility, localization, data**

- [ ] TalkBack: list pick, sheet titles, selection, count announcements, 48 dp targets.
- [ ] Large font, dark theme, IME insets: no clipping or hidden controls.
- [ ] Counts use plural resources in a language with multiple plural forms; parameter order is correct.
- [ ] Local backup/export includes the new rule; import restores it; no schema change.
- [ ] The local subscription's unrelated groups and rules are untouched after guided saves.

---

## 17. Acceptance criteria

A build is spec-complete when:

1. A user can go from the floating button to an enabled tap rule on a test app without seeing selector/JSON vocabulary.
2. Every save path is one of: 1 match + no duplicate; broad confirmed; zero-match explicit; duplicate resolved.
3. The saved JSON5 loads through the existing raw subscription parser and the selector compiles and matches the same node in the a11y engine.
4. A representable saved rule reopens guided with identical conditions; a non-representable one opens raw with a reason.
5. The floating entry, app entry, snapshot entry, and Desktop replay all reach the same states with the documented return paths.
6. No authoring capture survives a completed, abandoned, or killed session.
7. The limits option writes exactly the three documented fields when on and none when off.
8. Copy exists as Compose resources with plurals and positional parameters; the golden path uses only the approved vocabulary.

---

## 18. Traceability

| Section | Decisions |
| --- | --- |
| 4, 5 | #3 capture choreography, #7 MVP cut |
| 6 | #6 prototype, #7 MVP cut |
| 8 | #2 selector strategy, #5 condition model |
| 9 | #2 defaults, #5 compilation, #7 limits decision |
| 10 | #4 detection |
| 11 | #4 duplicates, #5 representability |
| 12, 13 | #2 safety, #6 copy deck, #7 diagnostics |
| 14–16 | #7 phasing, risks, device tests |
