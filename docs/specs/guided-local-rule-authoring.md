# Guided local-rule authoring on Android — UX spec

**Status:** decision-locked, October 2026. This is the destination artifact of the wayfinder map [#1](https://github.com/fktpp2022/gkd/issues/1). It specifies behaviour, not implementation. **Amended October 2026** with the recording-v1 addendum from the [recording effort](https://github.com/fktpp2022/gkd/issues/11) and ticket [#19](https://github.com/fktpp2022/gkd/issues/19). The amendment is additive; section 19 records exactly which sections changed and why.

**Sources:** selector strategy [#2](https://github.com/fktpp2022/gkd/issues/2), capture choreography [#3](https://github.com/fktpp2022/gkd/issues/3), existing-rule detection [#4](https://github.com/fktpp2022/gkd/issues/4), condition model [#5](https://github.com/fktpp2022/gkd/issues/5), interaction prototype [#6](https://github.com/fktpp2022/gkd/issues/6), MVP cut [#7](https://github.com/fktpp2022/gkd/issues/7), and the research reports under `docs/research/` (on their research branches). Recording sources: platform semantics [#12](https://github.com/fktpp2022/gkd/issues/12), prior art [#13](https://github.com/fktpp2022/gkd/issues/13), device evidence [#14](https://github.com/fktpp2022/gkd/issues/14), vocabulary [#15](https://github.com/fktpp2022/gkd/issues/15), what a demonstration must yield [#16](https://github.com/fktpp2022/gkd/issues/16), choreography [#17](https://github.com/fktpp2022/gkd/issues/17), go/no-go and the MVP cut [#18](https://github.com/fktpp2022/gkd/issues/18), and non-native observation [#20](https://github.com/fktpp2022/gkd/issues/20).

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
- Recording phases in a live-capture session: one demonstrated tap or long-press becomes the picked node and the existing authoring flow continues (sections 5.4 and 8.8). A failed attempt may be retried as a new recording session against the same capture, except when the screen changed (section 6.20).
- Recording observes taps only where the app reports accessibility click events — native Views and WebView. Compose and custom-drawn taps remain an honest dead end (section 8.9).
- Structured conditions, one context, live match preview, broad/narrow, zero-match save, duplicate check, existing-rule detection, guided re-edit for representable rules, raw JSON fallback.
- The limits option ("Only act right after the app opens"), off by default.

### 2.2 Out of scope (v1)

- Templates or named recipes.
- A UI for alternatives (OR), multi-context, negation, regex, `position`/index narrowing, `excludeMatches`, `preKeys`, multi-step rules.
- Activity scope editing (`activityIds` stays empty).
- Rule management: a guided rule list, detail page, delete, rename, or a guided badge outside re-capture.
- Creating global-group rules (detection of existing local global groups is in scope); remote-subscription detection; subscription authoring or sharing.
- Coordinate fallback; simulated action/automator self-test; "why not matched" inspection beyond the count.
- Multi-action or multi-rule recording, sequences, replay, and script/macro semantics.
- Actions other than tap and long-press while recording.
- Recording from the app page, a stored snapshot, or Desktop.
- Observing Compose or custom-drawn taps through privileged input, touch exploration, or tree-diff; v1 uses the accessibility click event only.
- Feeding demonstrations to automatic or LLM rule generation.
- A provenance field, badge, toast, or any rule-level difference marking a rule as recorded; a separate Record setting.
- Recording as an accessibility path; the list pick remains the non-visual route.
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
| **Picked node** | The node the user designates as the rule's target — by tapping it on the authoring capture, or by performing the action on it in the live app during recording. How it was designated is a session-level fact, never a rule-level one. |
| **Target element / actable node** | The node the rule acts on: the picked node, or the nearest clickable ancestor when the picked node is not clickable and has no usable text/description. |
| **Condition row** | One AND-ed predicate over `text`, `desc`, `vid`, `id`, class, `clickable`, or `visibleToUser`. |
| **Context** | One relation to a second picked node: Inside or Next to. |
| **Alternative** | One complete target + optional context selector. v1 always writes exactly one. |
| **Canonical selector** | The selector string after `Selector.compile(...)` and `toString()`. This is the persisted contract. |
| **Guided-representable rule** | A rule wholly inside the guided subset (section 8.7). Only these open in the guided editor. |
| **Broad** | A selector that matches more than one node on the captured screen. Not a persisted mode. |
| **Limits option** | The single "Only act right after the app opens" toggle that writes three timing fields (section 9.5). |
| **Recording session** | A phase of a live-capture authoring session in which GKD watches the captured app for one tap or long-press, rule engine idle. It ends at the first click/long-click event from the captured app, a screen change that invalidates the capture, Stop, or session end. An authoring session holds at most one demonstration but may run several recording sessions after failed attempts. |
| **Demonstration** | One user-performed tap or long-press during a recording session, from which the picked node is derived. A tap that yields no node of the authoring capture is not a demonstration. At most one per authoring session; never persisted. |
| **Recorded rule** | Not a kind of rule. Informal shorthand only for "a rule whose authoring session included a demonstration"; nothing distinguishes it after saving. |

### 3.2 UI vocabulary budget

Allowed on the golden path: element, screen, app, rule, tap, long-press, Inside, Next to.

The recording path adds **Record / Recording** for the icon and the live chip, **Stop** for its only control, and the status line "GKD rules are paused". It introduces no new rule vocabulary; success adds no copy at all.

Hidden until the advanced editor: selector, node, activity, activity id, group key, match time, action maximum, reset match, fast query, JSON5, subscription, package name, ancestor, sibling, index, `@`, `>`, `+`, `-`.

"Group" appears only as a name in the existing-rule and duplicate copy, never as a concept to manage.

---

## 4. Surfaces and entries

### 4.1 Entry matrix

| Entry | Platform | Screen data | Record offered | Session opens | On save/abandon |
| --- | --- | --- | --- | --- | --- |
| Floating rule button | Android | Live capture of the foreground window | Yes, after the picker has usable nodes | Authoring page over the frozen screenshot; a recording phase brings the live app forward | Return to the target app |
| GKD app page → Create rule | Android + Desktop replay | Latest stored snapshot for that app; if none, the manual condition editor | No — no guaranteed live foreground app | Authoring page or manual editor | Return to the app page |
| Stored snapshot → Create rule | Android + Desktop replay | That snapshot | No — no guaranteed live foreground app | Authoring page | Return to the snapshot detail |
| Desktop fixture replay | Desktop | Fixture snapshot | No — no live app | Authoring page | Return to the replay page |

Record is offered only in a session with a guaranteed live foreground app: the floating rule button. It never appears in the app-page, stored-snapshot, or Desktop sessions, in the A9 dead end, or on A3 and later. It is hidden for the rest of the authoring session after a demonstration succeeds and returns after a failed attempt (sections 5.4 and 6.18).

No Quick Settings tile, notification action, share target, launcher shortcut, or "arm for the next screen" state. Recording is a live phase, not an armed state: it watches only while the chip is visible, and Stop ends it. The existing snapshot button keeps its current behaviour.

### 4.2 Floating rule button

- A dedicated overlay button, **off by default**, enabled in GKD settings next to the existing floating snapshot setting, with a short explanation (A0).
- This is the only entry that offers recording (section 5.4). There is no separate Record setting; the Record icon lives in the live-capture picker and is offered only after the picker has usable nodes.
- Requires the accessibility service and the overlay permission. Enabling the switch walks the user through whatever is missing; the switch does not turn on until both are available.
- Tap hides all GKD overlays, captures the foreground app's active window, and opens the authoring page. One tap, no confirmation.
- If capture fails, the page stays in GKD with a plain-language retry/dead-end state (A9); it never pretends to have a screen.
- If the overlay permission is revoked while the button is on, the button disappears and the setting shows the permission action; the app-page and snapshot entries remain usable.
- Hidden while GKD's own UI is foreground — the authoring page included — and returns when the target app is foreground. It draws nothing there, so it cannot cover or intercept GKD's own screens; the recording chip is a separate surface (section 5.4).

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
- During a recording phase the authoring page stays in the back stack and the captured app comes forward; the first event from the captured app, Stop, or an invalidating screen change brings the authoring page back automatically. Back and Home in the live app are ordinary app actions and do not end the phase.
- GKD entry: returns to the originating page with its scroll state.
- Editing an existing rule pushes the edit state inside the session; Back returns to the new-rule draft unchanged, with no discard prompt.
- Opening the raw editor hands off to the existing group editor; Back returns to the guided draft.

### 5.3 Save, failure, discard

- **Save** writes through the existing local-subscription update path, then shows "Rule saved." and returns.
- The rule is active immediately; no "try it now" step. With the limits option off, it may fire before the user returns if the target is still visible; with the option on, it waits for the next app opening.
- **Save failure** keeps the draft and the current screen; shows an inline retryable message. No navigation, no data loss.
- **Discard** shows a confirmation only when the draft has changes. A draft has changes when the user modified the selection-derived prefill (conditions, action, context, name, or limits); returning immediately after a pick counts as no changes and closes directly. The confirmation is the only modal on the abandon path.
- A demonstration adds no save path. The same save, duplicate, failure, and discard rules apply; the only difference is that the action row arrives prefilled (section 6.6).

### 5.4 Recording phase (live capture only)

- Available only in a live-capture session, from A2, after the picker has a usable tree, and only while no demonstration has succeeded in this authoring session. Entering it starts immediately; there is no confirmation sheet and no separate setting.
- The authoring page stays in the back stack; the captured app comes forward. Recording starts **no new capture** — the authoring capture from entry remains the only reference for target resolution, uniqueness, and preview. The floating rule button is hidden while recording; the chip is the only GKD surface.
- The rule engine is idle for the entire phase, globally: no rule acts on any app while recording. This is load-bearing, not cosmetic. On Android 8–11 the service receives no action discriminator, so engine idle is the only separator between GKD's own clicks and the user's tap. An action already in flight when the phase starts is guarded so it cannot be recorded as the demonstration. The engine resumes when the phase ends.
- The phase ends at the first click or long-click event from the captured app, usable or not; at **Stop**; or at a screen change that invalidates the capture (A17). There is no timer; silence waits indefinitely.
- Events from other apps are ignored: they neither end nor consume the attempt. The chip remains visible, so the global pause stays stated and Stop stays one tap away. Leaving the captured app does not end the phase; the user returns to the captured app to demonstrate, or taps Stop.
- A failed attempt may be retried against the same capture as a new recording session; A17 is the exception, offering recapture or picking on the old capture. A failed attempt is not a demonstration. A successful demonstration spends the one-demonstration budget: the Record icon stays hidden for the rest of the authoring session, including after returning to A2. Picking on the capture remains available and is not a demonstration.
- A post-tap screen change is the demonstration's result, not an invalidation. A pre-tap window change in the captured app invalidates the attempt (A17). A failed tap whose own navigation changed the screen is also A17.
- Recording observes accessibility click events only. Native Views and WebView pass this path; Compose and custom-drawn user taps emit no event and reach A16 honestly (section 8.9). No privileged input, touch exploration, or tree-diff observation is part of v1.

---

## 6. Screens and states

### 6.1 State machine

| State | Entered from | Primary actions | Exits |
| --- | --- | --- | --- |
| A0 Enable button | GKD settings | Turn on / Not now | Settings |
| A1 Capture | Entry | Wait for screenshot + nodes | A2 Pick, A9 Dead end |
| A2 Pick | A1 | Tap a node; Record (live capture only) | A2b Overlap, A3 Conditions, A15 Recording |
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
| A15 Recording | A2 Record, live capture only | Tap/long-press the target in the captured app; Stop | A3 Conditions, A16 Nothing recorded, A17 Screen changed |
| A16 Nothing recorded | Stop with no event; an event that identifies no capture node | Pick it instead / Record again / Cancel | A2 Pick, A15 Recording, session end |
| A17 Screen changed | Pre-tap screen change in the captured app; failed tap whose navigation changed the screen | Capture this screen and record again / Pick on the captured screen / Cancel | A15 Recording with a new capture, A2 Pick, session end |

A6 and A8 are inline states on A3, not separate pages. A15–A17 are the recording amendment and correspond to S13–S15 in the paper prototype; A16 and A17 are inline states on A2, not separate pages. A15 is a phase in which the live app is in front, not a page over the screenshot.

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
- Accessibility alternative: **Choose from a list** lists labeled nodes in screen order with their indent; selecting one behaves like a pick. When the capture has no labeled node, the sheet lists nothing and offers **Describe manually** as its primary action instead; that opens A14 for the session's app, with the captured tree available for label typeahead only and no preview or context.
- In a live-capture session, A2 also offers **Record**: a single top-bar icon (a record dot), accessible name "Record a tap". It appears only after the picker has usable nodes, is absent from A9 and from A3 onward, and starts A15 immediately with no confirmation sheet. It is hidden for the rest of the authoring session after a demonstration succeeds and returns after a failed attempt. Picking remains the primary path.
- More than one candidate at the point: A2b.

### 6.5 A2b — Which element?

- Candidates are the smallest meaningful nodes at the point, sorted smallest-first; the smallest clickable node with text is preselected. When no candidate has text, the smallest candidate is preselected.
- Each row: label (text → desc → type), type, size. Selecting a row draws its bounds behind the sheet.
- Actions: Use this, Cancel.

### 6.6 A3 — Conditions and preview

- Header: "New rule for <App>" (or "Edit rule: <name>").
- Preview: screenshot thumbnail with every match highlighted and the actable node ringed. Expands to full screen; highlights scale.
- Count: always qualified, pluralized, e.g. "1 element matches on this screen." During recomputation show an inline progress state; editing stays enabled.
- Action row: **Tap** / **Long-press**, default Tap. Changing it updates the auto-suggested rule name until the name is edited.
- When the target came from a demonstration, the action row arrives prefilled Tap or Long-press from the event and remains fully editable. Nothing else on A3 records the demonstration: no badge, toast, extra row, or success copy; the prefilled action row is the only trace.
- Condition rows: prefilled, editable, removable. Text and Description have an is/contains toggle.
- **Add condition** sheet: Text, Description, View ID, Type, Clickable, Visible, More → Full ID. Properties already used are hidden; v1 allows one row per property.
- **Add context**: starts A5. Once set, a chip reads `Inside “<anchor>” ×` or `Next to “<anchor>” ×`.
- Options (collapsed): the limits option, the rule name (auto-suggested, editable), and Advanced → the read-only canonical selector.
- Overflow: **Edit as JSON** (A11). Not part of the basic path.
- Save:
  - Save stays disabled until the existing-rule scan completes, and stays disabled if the scan fails. The conditions screen and A14 show the same gate.
  - Scan failure → an inline banner (not a modal, not a toast) with the failure copy and **Retry**, in place of the existing-rule banner. A failure is never rendered as an empty rule list. Retry re-runs the scan without re-capturing; once it completes, Save behaves exactly like the completed-scan path, including the zero-candidate path.
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
- Entered from A9 **Describe manually**, or from A2's **Choose from a list** sheet when the capture has no labeled node; both keep the session's app id.
- Add condition works as usual; Next to/Inside require a captured tree, so the context action is unavailable here.
- There is never a preview here, even when the session holds a screenshot: the preview card shows its unavailable state.
- If a captured or stored tree is available, the typeahead uses its labels; otherwise it is empty. That tree feeds typeahead only and is not otherwise surfaced.
- Save performs the duplicate check; the existing-rule banner is unavailable without a tree.
- Saved rules are identical in shape to picked rules.

### 6.18 A15 — Recording (live capture only)

- Entered from the Record icon on A2. There is no confirmation sheet; the chip is the explanation, and Stop ends the phase.
- The authoring page steps back and the captured app comes forward, live. A small floating chip is the only GKD surface. Only the chip intercepts touches; the rest of the screen passes every touch to the app, so the demonstration is a real tap or long-press.
- Chip copy: `Recording a tap — <App>` / `GKD rules are paused` / **Stop**. The title names the captured app; the pause line states the global rule pause.
- The engine is idle for the whole phase, everywhere; it resumes the moment recording ends. This is the separator from GKD's own clicks, and the only one on Android 8–11.
- There is no timer and no target highlight. The live screen is never marked, outlined, or highlighted. Starting recording takes no new screenshot; the authoring capture remains the only reference.
- The first click or long-click event **from the captured app** ends the phase, usable or not. Events from other apps are ignored and do not consume the attempt; leaving the captured app leaves the phase live. A pre-tap window change in the captured app ends it with A17.
- Back and Home remain ordinary app/launcher actions; they do not end the phase. Stop is offered while the phase is still accepting the demonstrated tap; once a tap is being resolved the chip drops Stop for the resolving window, and the phase ends when resolution finishes.
- On start, TalkBack announces: "Recording. Tap the element in %1$s. GKD rules are paused." The chip is one focusable group with a Stop action.
- When the phase ends, the authoring page comes forward automatically over the frozen capture; there is no notification and no manual return step.

### 6.19 A16 — Nothing was recorded (inline on A2)

- Triggered by Stop with no event, or by an event that identifies no capture node. No-event causes include a Compose or custom-drawn tap and a self-deleting target; both reach A16 when the user stops.
- Inline on A2, below the still-visible frozen screenshot; the pick hint is replaced.
- Copy: **Nothing was recorded.** The cause line adapts:
  - Stopped with no event: "You stopped before GKD saw a tap." plus the helper "Some apps don't report taps; picking on this screen still works."
  - An event that identifies no capture node, with no screen change: "That tap didn't match anything on this screen." No helper.
- Actions: **Pick it instead** (primary), **Record again**, **Cancel**.
- **Pick it instead** returns to A2's normal pick state; because no demonstration occurred, the Record icon returns.
- **Record again** starts a fresh recording session against the same capture.
- **Cancel** ends the authoring session; there are no edits, so no discard prompt.
- The Record icon is hidden while this state is shown. The state never appears on its own; silence does not time out, it requires Stop or an unusable event.

### 6.20 A17 — Screen changed (inline on A2)

- Triggered by a pre-tap window-state change in the captured app, or by a failed tap whose own navigation changed the screen. A window change caused by a usable tap is the demonstration's result, not an invalidation.
- Inline on A2, below the still-visible frozen screenshot. Copy: **The screen changed.** / "Nothing was recorded."
- Actions: **Capture this screen and record again** (primary), **Pick on the captured screen**, **Cancel**.
- **Capture this screen and record again** replaces the authoring capture with the app's current screen and starts a new recording session. If that capture fails, A9 applies.
- **Pick on the captured screen** keeps the old capture and enters picking — the same screenshot the page is already showing. The attempt was not a demonstration, so the Record icon returns.
- **Cancel** ends the authoring session; no discard prompt.
- The Record icon is hidden while this state is shown. The copy is deliberately generic: pre-tap drift and a failed tap that navigated before it could be used are the same dead end.

---

## 7. Interaction rules

### 7.1 Picking

- Hit-testing uses stored bounds, not rendered pixels; zoom/pan do not change which node a tap resolves to.
- The tap point resolves to the smallest meaningful node; zero-area, invisible, and snapshot-only nodes are not candidates.
- When several candidates share the point, A2b decides; never guess silently between siblings with different labels.
- Selecting a node always shows which node will be acted on, including the actable-ancestor climb.
- A recorded target is a picked node too. Once it is resolved to a node of the authoring capture (section 8.8), every rule in this section applies unchanged; recording never introduces a second picking model.

### 7.2 Preview

- The count is computed against the captured tree with the selector engine, the same semantics the a11y engine uses.
- Highlights show every match; the actable node is visually distinct and not distinguished by colour alone.
- Recompute is asynchronous and never disables editing. Stale counts are not shown as current: show a progress state until the new count is ready.
- The count is scoped: "on this screen", never a universal claim.
- For a demonstration, the baseline is the pre-tap authoring capture, never a post-tap tree or the live screen. Uniqueness is measured on the screen the user demonstrated on.

### 7.3 Sheets and dialogs

- Bottom sheets for overlap, Add condition, broad/narrow, and duplicates; the existing-rule banner is inline.
- Only duplicate, discard, and broad confirmation interrupt; everything else is inline or a sheet.
- No transition, save, or animation disables taps or delays input.
- A sheet dismisses on system Back before the page does.

### 7.4 Accessibility

- Every control has a meaningful label; the preview canvas exposes the selected element and the match count.
- The Record icon has the accessible name "Record a tap"; the recording chip is one focusable group with a Stop action and announces the recording start (section 6.18).
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
- One alternative → `matches: ["<selector>"]` (a one-element array, the canonical persisted form; the parser also accepts a bare string).
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

### 8.8 Recorded target resolution

The demonstration must yield a node of the **pre-tap authoring capture** plus the demonstrated action. Anything less fails: A16 when the capture is intact, A17 when the screen changed first. The capture is taken once at entry and is the only reference. There is no rolling capture, no tap-time tree, and no matching against the live screen after navigation.

Resolution order:

1. Source has a `viewId` → the capture node with the same vid whose bounds overlap the source bounds, or the unique vid node.
2. Otherwise, source has text/description → the capture node with the same text/description whose bounds overlap the source bounds.
3. Otherwise the source is unlabeled → event text items in order; the first item with exactly one capture node whose text/description equals it and whose bounds lie within the source bounds wins. This recovers a labeled child from a bare clickable container.
4. Multiple candidates → A2b "Which element?".
5. No candidate → A16.

The upstream actable climb and prefill ladder (section 8.3) then run unchanged. A class-plus-bounds fallback for labels that changed after the capture is rejected for v1: it can bind to a same-class node at the same bounds, and picking on the frozen capture already handles that case.

The demonstration contributes exactly the picked node and the action, nothing else:

- A tap prefills `click`, a long-press `longClick`; the action row stays editable.
- No demonstration-specific conditions, activity scope, `preKeys`, timing, navigation memory, or new fields.
- A labeled child of a clickable row targets that child; the engine's center-tap fallback makes it act correctly, so no climb to the row is needed.
- The save bar is unchanged. Because the picked node is a capture node, its prefilled selector matches at least that node on the capture; zero-match can only follow user edits, and broad/duplicate keep their existing gates.
- There is no live-screen check at save: every picked node is a node of the frozen capture, so "the target no longer exists at save time" cannot occur.
- A window-state change **before** the tap invalidates the attempt (A17). A window-state change **caused by** the tap is the demonstration's result. A failed tap whose own navigation changed the screen is A17, never a retry against a stale capture.

### 8.9 Observation boundary

Recording v1 uses the accessibility click-event path only:

- Native Views emit `TYPE_VIEW_CLICKED` / `TYPE_VIEW_LONG_CLICKED` for user taps. WebView taps also emit a click event and expose the DOM id; WebView long-press and provider variation remain device-test items, not assumptions.
- Compose user taps emit no click event at all, and a custom-drawn tap emits one only if the app calls `performClick()`. A tap that removes its own target emits nothing. These reach A16 honestly; picking on the same capture still works.
- No privileged raw-input observation, touch-exploration hover, tree-diff inference, or typed content-change path ships in v1. Each either rewrites the demonstrated gesture, requires privileged reach, or fails to name the node.
- The event source can be a bare container. That is why step 3 of 8.8 uses event text within the source bounds; the source node alone is not trusted as the target.
- GKD's own clicks would be indistinguishable from a user's on Android 8–11. The global engine pause during the recording phase is therefore mandatory. `event.getAction()` exists only from Android 12 and is not a documented contract; v1 does not rely on it.
- Residual device variation — OEM/vendor ROMs, literal physical-finger behaviour, Android 8–11 on an older AVD, and WebView provider differences — is covered by the device-test checklist (section 16), not assumed away.

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
| `groups[].rules[].matches` | Canonical selector string in a one-element array (one alternative); the parser also accepts a bare string |
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
              matches: ["[text=\"跳过 5s\"]"],
            },
          ],
        },
      ],
    },
  ],
}
```

Quoting is delegated to the existing JSON5 encoder; the example shows equivalent JSON5 and English placeholder names (the real subscription/group names are localized). The canonical selector is `[text="跳过 5s"]`, persisted as the one-element `matches` array shown; the parser treats a bare string the same.

### 9.4 Example — limits on

```json5
{
  name: "Tap “跳过 5s”",
  key: 2,
  action: "click",
  matches: ["[text=\"跳过 5s\"]"],
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
- Recommendation in the UI is explicit and shown with the option: "Best for splash screens; later screens will time out." The helper stays as additional information.

### 9.6 Fields the guided flow never writes

`anyMatches` (until alternatives ship), `excludeMatches`, `excludeAllMatches`, `preKeys`, `position`, `swipeArg`, `activityIds`, `excludeActivityIds`, `versionCode`, `versionName`, `fastQuery`, `matchRoot`, `matchDelay`, `actionDelay`, `actionCd`, `forcedTime`, `order`, `priorityTime`, `priorityActionMaximum`, `actionCdKey`, `actionMaximumKey`, `scopeKeys`, category keys, snapshots/examples, and any group-level defaults.

### 9.7 Recording changes no persisted field

- A demonstration's action maps directly: tap → `action: "click"`, long-press → `action: "longClick"`. The target goes through the same canonical selector path as a pick.
- The fields in 9.2 are the complete set. The limits fields follow the limits option only. No provenance, demonstration, source, or recording field is written.
- No new group type, schema version, table, or subscription shape. The JSON5 mapping is unchanged, and a recorded rule is indistinguishable from a picked rule after saving.

---

## 10. Existing-rule detection

- Runs after a pick resolves, against the captured tree, off the main thread; the banner appears when the scan finishes and never blocks the preview.
- Candidates: enabled local rules eligible on the captured screen — the captured app's groups plus applicable local global groups. Eligibility reuses the existing policy: subscription/category/app/group enablement, group validity, activity prefix matching (`activityIds` empty = all, `excludeActivityIds`), version matchers.
- Each candidate's selectors are evaluated on the captured tree. A hit is when the final matched node is the picked node, an ancestor, or a descendant; `anyMatches` fallback and exclusions are honoured.
- `preKeys` rules are tested on primary selectors only; the banner is advisory, not proof that the trigger happened.
- Ordering: authoring group → other local app groups → local global groups. The banner names the first; "+N more" lists the rest.
- Re-runs on a new pick; a dismissal applies to that pick only.
- If the scan fails, the existing-rule banner is replaced by an inline failure banner with **Retry**. Save stays disabled until a retry completes; a failure is never shown as "no existing rules". Retry reuses the same scan path, without re-capturing the screen.
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
| Stop with no tap event, including Compose/custom-drawn/self-deleting taps | A16 | Honest "nothing was recorded"; no timeout before Stop; picking remains available |
| Event identifies no capture node | A16 | Never invents a node; same fallback |
| Screen changed before the tap, or a failed tap navigated | A17 | Recapture or pick on the old capture; no silent matching after navigation |
| Tap in another app during recording | No surface change | Ignored; the chip stays; the attempt is not consumed |
| GKD's own rule action during recording | A15 | Impossible by design: the engine is idle for the whole phase; mandatory on Android 8–11 |
| Record requested in a stored, Desktop, dead-end, or post-pick session | — | Not offered; there is no fake recording entry |
| Recapture from A17 fails | A9 | The existing dead end applies |
| Record icon missed | Device test | Discoverability is a device-test observation; a one-line hint is a follow-up only if the icon is missed |

Rules are never described as "verified", "safe", or "working". The preview speaks only about the captured screen. Recording success adds no copy; the prefilled action row is the only trace.

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
| Limits recommendation | Best for splash screens; later screens will time out. |
| Disabled notice | This rule won't run until “%1$s” is turned on. |
| Rule name | Rule name |
| Save | Save |
| Saved | Rule saved. |
| Save error | Couldn't save the rule. Try again. |
| Scan error | Couldn't check existing rules, so saving is unavailable for now. |
| Discard title | Discard this rule? |
| Discard actions | Discard / Keep editing |
| Raw note | Advanced: this is GKD's rule format. |
| Record action | (record-dot icon; accessible name) Record a tap |
| Recording title | Recording a tap — %1$s |
| Recording pause | GKD rules are paused |
| Recording stop | Stop |
| Recording announced | Recording. Tap the element in %1$s. GKD rules are paused. |
| Nothing title | Nothing was recorded. |
| Nothing cause (stopped) | You stopped before GKD saw a tap. |
| Nothing helper | Some apps don't report taps; picking on this screen still works. |
| Nothing cause (no node) | That tap didn't match anything on this screen. |
| Nothing actions | Pick it instead / Record again / Cancel |
| Changed title | The screen changed. |
| Changed body | Nothing was recorded. |
| Changed actions | Capture this screen and record again / Pick on the captured screen / Cancel |

Rules: counts are pluralized; names/labels are data; no sentence concatenation; no vocabulary from section 3.2 on the golden path. Recording has no success copy; a successful demonstration is shown only by the ringed target and the prefilled action row.

---

## 14. Phasing

**Phase 1 — this spec**

- All entries, the one-page flow, condition rows + one context, live preview, broad/narrow, zero-match save, duplicate check, existing-rule banner, guided re-edit for representable rules, raw fallback, limits option, copy, accessibility list pick.

**Phase 1 addendum — recording v1**

Recording ships as a secondary entry alongside the picking flow. Picking must work unchanged; recording substitutes only the pick step. The evidence basis is device observation of native-View and WebView taps; Compose and custom-drawn apps remain an honest dead end.

Ships:

- **Entry:** live-capture sessions only (the floating rule button), never the app page, a stored snapshot, or Desktop. Record is an icon-only top-bar dot on A2 with the accessible name "Record a tap"; it appears only after usable nodes, is hidden after a successful demonstration and returns after a failed attempt. No confirmation sheet and no separate setting.
- **Vocabulary:** tap and long-press only.
- **Budget:** one demonstration per authoring session → one candidate rule. A failed attempt is a new recording session against the same capture, except when A17 recaptures; retries do not spend the demonstration.
- **Live phase:** the captured app comes forward; a floating chip names the app, states the global rule pause and offers Stop. The authoring page stays in the back stack; the chip is the only GKD surface. No timer. The engine is idle everywhere for the whole phase.
- **Scope:** only events from the captured app count; taps elsewhere are ignored and do not consume the attempt; leaving the app keeps the phase live; a pre-tap screen change ends it with A17; a post-tap change is the demonstration's result.
- **Return and resolution:** the first click/long-click event from the captured app ends the phase, usable or not. The recording starts no new capture; resolution uses the pre-tap capture per 8.8, then the existing actable climb and prefill. Ambiguity → A2b; no node → A16; one node → A3 with the action row prefilled Tap/Long-press and fully editable.
- **Failure states:** both ship in full — A16 "Nothing was recorded" (Pick it instead / Record again / Cancel) and A17 "The screen changed" (Capture this screen and record again / Pick on the captured screen / Cancel).
- **Downstream:** unchanged — preview, existing-rule banner, broad/narrow, zero-match, duplicate check, save/error/discard, limits option, raw handoff. No provenance field, badge, toast, or rule-level difference.
- **Persistence:** JSON5 mapping unchanged (section 9.7); `click` / `longClick`, no new fields, tables, or schema versions.
- **Discoverability:** an icon-only entry is a device-test observation. A one-line hint is a follow-up only if the icon is missed; it is not part of the initial cut.
- **Evidence bar:** record → save → reopen the app → the engine fires; engine idle while recording; the device-test checklist in section 16 covers native/WebView taps, the Compose/custom-drawn dead end, other-app taps, screen changes, bare-container sources, long-press, ambiguity, Android 8–11, and return paths.

Deferred past recording v1: multi-action/multi-rule sessions; actions other than tap and long-press (scroll, text input, back); recording from the app page, a stored snapshot, or Desktop; Compose/custom-drawn observation (privileged input, touch exploration, tree-diff); feeding demonstrations to automatic or LLM generation; provenance fields, badges, a separate Record setting, recording as an accessibility path, and sequencing/replay semantics.

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
| Compose/custom-drawn user taps emit no click event | Recording sees nothing; the target cannot be identified | Honest A16; picking remains available; scope stated in 8.9; no fake success |
| GKD acts during a recording phase | GKD's own clicks could be recorded; on Android 8–11 nothing else separates them | Engine idle for the whole phase; device-test on Android 8–11 and current versions |
| Event source is a bare container | Source node has no label, so the target cannot be named from the source alone | Event-text resolution within source bounds (8.8); device-test with a Settings row |
| OEM/vendor ROM and WebView provider variation | Taps or long-presses may be missed or shaped differently; WebView long-press is not yet verified | Device-test matrix; treat as residual, not a silent assumption |
| Icon-only Record entry is missed | Users never discover recording | Device-test observation; one-line hint is a follow-up only if missed |
| Screen changes before the tap | The pre-tap capture no longer describes the demonstrated screen | A17 with recapture or picking on the old capture; no silent matching |
| Recording phase outlives the captured app's foreground time | The global pause and the chip must remain reachable | Chip never hidden; Stop one tap away; engine resumes on any exit from the phase |

---

## 16. Device-test checklist

**PR QA note:** sections 4.2, 6.18, and 8.4/9.2–9.4 record the behaviour verified for this PR — floating-button hiding over GKD's own UI, Stop during resolution, and the one-element `matches` form; re-check only the screens those sections cover.

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

**Recording (live capture only)**

- [ ] End to end: Record → demonstrate a tap → save → reopen the target app → the engine fires the saved rule.
- [ ] Engine idle: while recording, no rule acts on any app; the engine resumes when recording ends.
- [ ] Native-View app: a tap lands on A3; a long-press lands on A3 with Long-press prefilled and editable.
- [ ] WebView: a user tap lands on A3; a long-press lands on A3 with Long-press prefilled, or reaches A16 if the provider emits no long-click event. Record the provider/version because variation is residual.
- [ ] Compose plain-button tap: no click event; Stop lands on A16, and picking on the capture still works.
- [ ] Self-deleting target: the tap emits nothing; Stop lands on A16.
- [ ] Bare-container source: event text within the source bounds resolves the intended capture node (for example a labeled Settings row child).
- [ ] Ambiguous event: multiple capture candidates open A2b; cancelling returns to picking and the Record icon returns.
- [ ] Stop with no event: A16 with "You stopped before GKD saw a tap."; Pick it instead returns to picking.
- [ ] An event that identifies no capture node without a screen change: A16 with "That tap didn't match anything on this screen."
- [ ] Pre-tap screen change in the captured app: A17 wherever the user is; the authoring page returns.
- [ ] Post-tap navigation or dialog with a locatable node: A3, not A17.
- [ ] Failed tap whose own navigation changed the screen: A17.
- [ ] A17 recapture: "Capture this screen and record again" replaces the authoring capture; if capture fails, A9 applies.
- [ ] A17 "Pick on the captured screen": keeps the old capture and returns to picking; the Record icon returns.
- [ ] Tap in another app: ignored, chip stays, attempt not consumed; Stop remains one tap away.
- [ ] Leaving the captured app (Home/recents): session stays live; returning to the captured app still works.
- [ ] Return from recording brings the authoring page forward automatically; no notification or manual step.
- [ ] A successful demonstration hides the Record icon for the rest of the authoring session, including after returning to A2; a failed attempt restores it.
- [ ] Record is absent from app-page, stored-snapshot, and Desktop sessions, from A9, and from A3 onward.
- [ ] Record starts no new capture; the pre-tap screenshot is the preview and uniqueness baseline.
- [ ] Icon discoverability: observe whether the top-bar dot is found without a hint; record the result.
- [ ] Android 8–11 AVD: a click event is observed, the engine pause holds, and GKD's own clicks are never recorded.
- [ ] OEM/vendor ROM and literal physical-finger tap: record any variation as residual risk.

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
9. Recording is offered only in live-capture sessions after a usable tree; it never appears in app-page, stored-snapshot, Desktop, dead-end, or post-pick states.
10. A demonstration resolves to a node of the pre-tap capture and prefills the action. It adds no rule-level or persisted difference. No event, no locatable node, and pre-tap screen changes are covered by A16/A17, and the engine is idle for the whole recording phase.
11. The JSON5 mapping is unchanged: a recorded tap or long-press writes exactly the fields in 9.2, plus the limits bundle only when the option is on.

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
| 3, 4, 5.4, 6.18–6.20, 13 | Recording amendment: #15 vocabulary, #17 choreography, #18 MVP cut |
| 8.8–8.9 | #16 target resolution, #20 observation boundary, #12 platform semantics, #14 device evidence |
| 9.7, 14 addendum | #18 persistence and MVP, #16 no demonstration-specific conditions |
| 16 (recording) | #18 device-test additions, #14 residual risks |

---

## 19. Amendment log — recording v1

The recording addendum was folded in by ticket [#19](https://github.com/fktpp2022/gkd/issues/19) from the decisions on map [#11](https://github.com/fktpp2022/gkd/issues/11). It is an addition, not a rewrite: the upstream picking flow remains the primary path and is unchanged.

| Section | Change | Why |
| --- | --- | --- |
| Header, status, sources | Amendment note and recording sources added | Point readers at the addendum and its decision tickets |
| 2.1–2.2 | Recording phases in scope; deferred recording work out of scope | #18 go/no-go and MVP cut |
| 3.1–3.2 | Picked node widened; recording session, demonstration, recorded-rule non-term; vocabulary note | #15 domain model |
| 4.1–4.2 | "Record offered" column and live-capture-only entry | #17 choreography, #18 entry cut |
| 5.2–5.4 | Recording back stack, one-demonstration budget, engine pause, retries, capture as sole reference, screen-change semantics | #16 baseline and session boundary, #17 choreography, #18 |
| 6.1, 6.4, 6.6 | A2 Record icon entry, A15–A17 state rows, action prefill on A3 | #16, #17, #18 |
| 6.18–6.20 | A15 Recording, A16 Nothing was recorded, A17 Screen changed, with copy and actions | #17 choreography, #18 failure-state cut |
| 7.1, 7.2, 7.4 | Recorded target is a picked node; pre-tap baseline; recording accessibility | #15, #16, #17 |
| 8.8 | Recorded-target resolution order, actable climb, action prefill, same save bar, no live-screen check | #16 what a demonstration must yield |
| 8.9 | Click-event observation boundary; non-native dead end; engine pause; residuals | #12 platform semantics, #14 device evidence, #20 observation mechanisms |
| 9.7 | Explicit statement that JSON5 mapping is unchanged; action mapping; no provenance | #18 amendment contract item 8 |
| 12–13 | Diagnostics and copy additions | #17 copy deck, #18 failure states |
| 14 | Phase 1 recording-v1 addendum: ships and deferred | #18 MVP cut |
| 15 | Recording risks added | #12, #14, #20 |
| 16 | Recording device-test checklist added | #18 amendment contract item 6, #14 residual risks |
| 17 | Recording acceptance criteria added | #18 amendment contract |
| 18 | Recording traceability rows added | #18 amendment contract item 7 |

All other sections are unchanged. The upstream decisions in #2–#7 remain in force; recording only adds a second way to designate the picked node and does not alter the conditions model, preview, save gates, or persistence. `GLOSSARY.md` was already aligned by #15 and needed no change here.
