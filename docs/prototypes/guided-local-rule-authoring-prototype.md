# Guided local-rule authoring — paper prototype

**Status:** prototype v1, October 2026. Section 9 is the recording amendment (map [#11](https://github.com/fktpp2022/gkd/issues/11)), added October 2026. Review artifact for the UX spec; no production code.

**Scope:** the Android golden path from a visible screen to a saved local rule, plus the states that decide the flow's shape: overlap disambiguation, multiple matches, zero matches, an already-handled target, duplicate saves, re-entry, and dead ends.

**Inputs:** [capture choreography](https://github.com/fktpp2022/gkd/issues/3), [selector strategy](https://github.com/fktpp2022/gkd/issues/2), [condition model](https://github.com/fktpp2022/gkd/issues/5), [existing-rule detection](https://github.com/fktpp2022/gkd/issues/4), and the research reports under `docs/research/`.

**What it is not:** runnable code, visual design, or the final spec. It is a screen-order and wording prototype to react to. Fidelity is deliberately low: boxes, labels, and state transitions.

---

## 1. Principles the prototype is testing

1. **The screenshot is the interface.** The user points at what they mean; syntax is never the primary object.
2. **One page, sheets for decisions.** Pick, conditions, and preview live on one authoring page. Ambiguity, broad/narrow, and save conflicts are bottom sheets, not wizard steps.
3. **Show the consequence before saving.** Every condition change updates the highlighted matches on the frozen screen; the count is always qualified with "on this screen".
4. **No silent risk.** Broad matches, zero matches, duplicates, and risky labels ask once, in plain language, and never pretend the rule is verified.
5. **Vocabulary budget.** Basic path: element, screen, app, rule, tap, long-press. Hidden: selector, group key, match time, fastQuery, JSON5, activity, subscription. "Group" appears only in the existing-rule banner, as a name, not as a concept to manage.

---

## 2. Flow at a glance

```
[Floating GKD button] ──tap──▶ Capture ──▶ Authoring page
[GKD app page: Create rule] ─────────────▶ (latest snapshot or condition editor)
[Snapshot page: Create rule] ────────────▶ Authoring page

Authoring page
  Pick ──▶ Conditions (inline preview) ──▶ Save ──▶ toast ──▶ return
    │            │
    │            ├─ N > 1 ──▶ Broad/Narrow sheet ──▶ Context pick ──▶ Conditions
    │            ├─ N = 0 ──▶ zero-match state ──▶ Save anyway
    │            └─ existing rule ──▶ banner ──▶ Edit rule | Edit in JSON | Create anyway
    │
    ├─ overlap at tap point ──▶ Which element? sheet ──▶ Conditions
    └─ no usable nodes ──▶ dead end ──▶ manual conditions | retry | raw

Save ──▶ duplicate check ──▶ Replace | Keep both
Back with changes ──▶ Discard this rule?
```

---

## 3. Screens

### S0 — Floating entry

Not a screen. A small focus button, off by default, enabled from GKD with a short explanation. Tap hides all GKD overlays, captures the foreground app, and opens the authoring page.

First-run enable sheet (GKD settings):

```
Create rules from what you see
Adds a small GKD button over other apps. Tap it on a screen
you want to fix; GKD captures that screen and lets you pick
the element to act on.

[ Turn on ]   [ Not now ]
```

The existing snapshot button is unchanged.

---

### S1 — Capture and analysis

The page opens on the frozen screenshot immediately. Nodes may still be loading.

```
┌──────────────────────────────────────┐
│ ←   New rule                         │
│                                      │
│                                      │
│        [ frozen screenshot ]         │
│                                      │
│                                      │
│           Finding elements…          │
│                                      │
└──────────────────────────────────────┘
```

- No blocking dialog. If analysis takes long, the screenshot stays visible and the hint remains.
- If the screenshot is unavailable (`FLAG_SECURE`), show a neutral grid with node outlines and: "The screenshot is protected. You can still choose elements by their outlines."
- If node analysis fails or returns no usable nodes → **S9**.

---

### S2 — Pick

```
┌──────────────────────────────────────┐
│ ←   New rule                         │
│                                      │
│   [ frozen screenshot ]              │
│        ┌──────────┐                  │
│        │ 跳过 5s  │  ◀ selected      │
│        └──────────┘                  │
│                                      │
│  Tap the element GKD should act on.  │
│                                      │
│  ┌────────────────────────────────┐  │
│  │ Selected: “跳过 5s”            │  │
│  │ [ Use this ]  [ Choose again ] │  │
│  └────────────────────────────────┘  │
└──────────────────────────────────────┘
```

- Taps hit-test the stored node bounds; zoom and pan do not change the hit targets.
- The default candidate is the smallest node at the point. A selection ring and a one-line summary appear; **Use this** advances.
- When the picked node is not clickable and has no usable text/description, the suggestion silently targets the nearest clickable ancestor and the summary says "Acting on the button around this text". No technical explanation.
- If more than one candidate occupies the point → **S2b**.

---

### S2b — Which element?

```
┌──────────────────────────────────────┐
│  Which element do you mean?          │
│                                      │
│  [○] 跳过 5s       Text   118 × 48   │
│  [○] Button        Button 640 × 120  │
│  [○] FrameLayout   View   720 × 1280 │
│                                      │
│  [ Use this ]        [ Cancel ]      │
└──────────────────────────────────────┘
```

- Candidates are filtered to the smallest meaningful nodes at the point and sorted smallest-first; the smallest clickable node with text is preselected.
- Selecting a row draws its bounds on the screenshot behind the sheet.
- Labels use text → description → type, in that order.

---

### S3 — Conditions and inline preview

```
┌──────────────────────────────────────┐
│ ←   New rule for <App>          ⋮    │
│                                      │
│  [ screenshot thumbnail, matches     │
│    highlighted, picked one ringed ]  │
│                                      │
│  When this appears:                  │
│  ┌────────────────────────────────┐  │
│  │ Text    [ is ▾ ] [ 跳过 5s   ] │  │
│  │ ⚠ This text may change          │  │
│  └────────────────────────────────┘  │
│  + Add condition                     │
│  + Add context                       │
│                                      │
│  ✓ 1 element matches on this screen  │
│                                      │
│  ▸ Options                           │
│    [ ] Only act right after app open │
│                                      │
│  ┌────────────────────────────────┐  │
│  │            Save                │  │
│  └────────────────────────────────┘  │
└──────────────────────────────────────┘
```

- Rows are prefilled from the pick and remain fully editable. Each row has a remove action; the operator is a two-state toggle for text and description.
- **Add condition** opens a sheet: Text, Description, View ID, Type, Clickable, Visible, More → Full ID.
- **Add context** starts the second pick (S5) and becomes a context chip once set:
  `Inside “Registration dialog” ×` or `Next to “Close” ×`.
- The thumbnail expands to the full frozen screen on tap. All matches are highlighted; the picked node has a distinct ring.
- Count copy is a pluralized Compose resource and always says "on this screen".
- **Options** is collapsed. It contains "Only act right after the app opens" (the limits bundle), off by default, with the helper "Stops 10 seconds after the app opens, or after one tap," and the rule name (auto-suggested, editable).
- The top-right overflow contains **Edit as JSON** (S11) only for users who open it; it is not part of the basic path.
- **Save** behavior:
  - 1 match → save pipeline (duplicate check first).
  - 0 matches → S8.
  - N > 1 → S4.
- No separate preview step: the preview is the screenshot at the top of this page.
- Back with edits → S12 discard confirm; back without edits → pick.

---

### S4 — More than one match

```
┌──────────────────────────────────────┐
│  3 elements match on this screen     │
│                                      │
│  GKD will act on whichever one       │
│  appears first. If this includes     │
│  other buttons, narrow it.           │
│                                      │
│  [ previews of the 3 matches ]       │
│                                      │
│  [ Narrow it ]   [ Keep it broad ]   │
└──────────────────────────────────────┘
```

- **Narrow it** → S5. The user adds context until the count is 1, or gives up and returns.
- **Keep it broad** → confirmation:
  `This may also act on 2 other elements. Save anyway?` `[ Save ] [ Narrow it ]`
- The picked element is emphasized in the previews; every match is visible so a decoy close button is hard to miss.
- Copy says "act on whichever one appears first", never "tap all". The engine acts on one node.

---

### S5 — Narrow with context

```
┌──────────────────────────────────────┐
│ ←   Narrow it                        │
│                                      │
│  [ frozen screenshot, pick mode ]     │
│                                      │
│  Tap the container or nearby element │
│  that identifies this one.           │
│                                      │
│  [ Inside ]   [ Next to ]            │
└──────────────────────────────────────┘
```

- **Inside**: the next tap picks an ancestor; the selector becomes "target inside container".
- **Next to**: the next tap picks a sibling; the direction (+/−) is derived from the node order, never exposed.
- The context anchor is reduced to a stable label/id automatically; the user can remove or change it on S3.
- One context unit in v1. If the count is still >1, S4 returns with the same choices.
- If the context pick yields an unusable anchor (no stable identifier), show "That element has no stable label. Try its container or another nearby element."

---

### S6 — Already handled

Shown inline above the conditions, after the pick:

```
┌──────────────────────────────────────┐
│ This is already handled by           │
│ “Splash ads — My rules”.             │
│                                      │
│ [ Edit rule ]  [ Create anyway ]  ×  │
└──────────────────────────────────────┘
```

- **Edit rule** (owned authoring group + fully representable) opens S10 with the existing rule; the draft stays behind and back returns to it.
- Otherwise the action is **Edit in JSON** and the banner adds: "This rule uses advanced settings the guided editor can't show."
- **Create anyway** dismisses the banner; the duplicate check still runs at save (S7).
- The banner never blocks. Dismissal applies to this pick only.
- Remote subscription matches are not shown in v1.

---

### S7 — Duplicate at save

Same group:

```
┌──────────────────────────────────────┐
│ An identical rule already exists in  │
│ “My rules”.                          │
│                                      │
│ [ Replace it ]     [ Keep both ]     │
└──────────────────────────────────────┘
```

Different local group (including a local global group):

```
┌──────────────────────────────────────┐
│ An identical rule already exists in  │
│ another local group. The guided      │
│ editor won't change that group.      │
│                                      │
│ [ Keep both ]          [ Cancel ]    │
└──────────────────────────────────────┘
```

- Comparison is canonical selector equality, not "matches the same node".
- **Replace it** rewrites the existing rule in the owned group and keeps its key; **Keep both** appends a new rule.

---

### S8 — Nothing matches

Inline state on S3, replacing the count:

```
⚠ Nothing on this screen matches yet.

The element may not be showing now. You can
still save, or edit the conditions.

[ Save anyway ]     [ Edit conditions ]
```

- **Save anyway** saves with the zero-match warning already on screen; no second dialog.
- Never show "0 matches" as an error if the snapshot has no preview available; show "Preview unavailable" instead.

---

### S9 — Dead end: no usable elements

```
┌──────────────────────────────────────┐
│ ←   New rule                         │
│                                      │
│  GKD couldn't read this screen's     │
│  elements.                           │
│                                      │
│  Some apps hide their interface from │
│  accessibility tools.                │
│                                      │
│  [ Try again ]                       │
│  [ Describe the element manually ]   │
│  [ Open advanced editor ]            │
└──────────────────────────────────────┘
```

- **Describe the element manually** opens the blank condition editor with a text field and typeahead over captured labels; class/description/id are available under Add condition.
- **Open advanced editor** goes to the existing group editor.
- No coordinate fallback in v1; no fake success.

---

### S10 — Edit an existing rule

Same layout as S3, with:

- Title: `Edit rule` and the rule's current name.
- Rows parsed from the saved selector; the selector string is shown read-only under Options → Advanced.
- **Save** updates the rule in place (same key).
- If the rule is outside the owned group or not fully representable, the flow never reaches this screen: S6 sends it to JSON (S11).
- If no recent snapshot exists, the preview area says "Preview unavailable — no recent screen capture" and the match count is hidden, not zero.
- There is no delete action in v1.

---

### S11 — Raw handoff

- **Edit as JSON** opens the existing group editor for the containing group.
- A one-line note at the top: "Advanced: this is GKD's rule format."
- If the user leaves without saving, the guided draft remains on the previous screen.
- If saved, the guided view is re-derived on return only when the rule is representable; otherwise the group stays on the raw editor.

---

### S12 — Save result / abandon

Saved:

```
Rule saved.
```

Toast, then return to the previous screen (target app when started from the floating button; the GKD page otherwise). The rule is active; no forced "Try it now".

Abandon with edits:

```
Discard this rule?

[ Discard ]     [ Keep editing ]
```

Abandon without edits returns directly and deletes the transient capture. An abandoned capture is never kept as a snapshot.

---

## 4. State machine

| State | Entry | Primary action | Exits |
| --- | --- | --- | --- |
| Idle | App / floating button | Start capture or use latest snapshot | Capturing, DeadEnd |
| Capturing | Entry tap | Wait for screenshot + nodes | Analyzing, DeadEnd |
| Analyzing | Capture complete | Wait for node indexing | Picking, DeadEnd |
| Picking | Analysis complete | Tap a node | Disambiguating, Conditions |
| Disambiguating | >1 node at point | Choose candidate | Conditions |
| Conditions | Pick accepted / edit existing | Edit rows, context, options | Conditions, ContextPick, BroadNarrow, ZeroMatch, Save, Raw, Discard |
| ContextPick | Add context / Narrow it | Tap container or sibling | Conditions |
| BroadNarrow | Save with N > 1 | Narrow / keep broad / confirm | ContextPick, Saving, Conditions |
| ZeroMatch | Save with 0 matches | Save anyway / edit | Saving, Conditions |
| ExistingBanner | Detection found a local rule | Edit / create anyway / dismiss | EditRule, Conditions |
| Saving | Confirmed save | Write rule + duplicate check | Duplicate, Saved, Error |
| Duplicate | Canonical duplicate found | Replace / keep both / cancel | Saving, Saved |
| Saved | Save success | Return | Idle |
| EditRule | Banner or re-entry | Edit rows | Conditions (existing), Raw |
| Raw | Edit as JSON | External group editor | Idle |
| DeadEnd | No nodes / no screenshot + no nodes | Retry / manual / raw | Capturing, Conditions, Raw |
| Discard | Back with edits | Discard / keep editing | Idle, Conditions |

---

## 5. Copy deck

| Context | Copy | Note |
| --- | --- | --- |
| Entry | Create rules from what you see | Avoid "automation" and "selector" |
| Pick hint | Tap the element GKD should act on. | "Element" is the only new noun |
| Overlap title | Which element do you mean? | Recognition, not description |
| Conditions title | New rule for <App> | App name, not package id |
| Conditions heading | When this appears: | Plain-language trigger |
| Add condition | Add condition | Not "attribute" |
| Context | Inside / Next to | Never show `>` / `+` / `-` |
| Count | 1 element matches on this screen. | Pluralized; always scoped |
| Broad title | 3 elements match on this screen. | Pluralized |
| Broad body | GKD will act on whichever one appears first. If this includes other buttons, narrow it. | No "tap all" |
| Broad confirm | This may also act on 2 other elements. Save anyway? | Names the decoy risk |
| Narrow hint | Tap the container or nearby element that identifies this one. | No "ancestor" or "sibling" |
| Existing rule | This is already handled by “<name>”. | Name, not "group key" |
| Existing advanced | This rule uses advanced settings the guided editor can't show. | Explains the JSON fallback |
| Duplicate | An identical rule already exists in “<name>”. | Canonical equality |
| Zero match | Nothing on this screen matches yet. The element may not be showing now. | Allows future screens |
| Limits option | Only act right after the app opens | Off by default; helper: Stops 10 seconds after the app opens, or after one tap. |
| Dead end | GKD couldn't read this screen's elements. | Honest; no blame |
| Save | Save | One word |
| Saved | Rule saved. | No claim of verification |
| Abandon | Discard this rule? | Only when edits exist |

**Hidden vocabulary:** selector, node, activity, activity id, group key, match time, action maximum, reset match, fast query, JSON5, subscription, package name. These may appear in the advanced editor only.

---

## 6. Interaction notes

- **Zoom and pan** are gesture-based; a double tap fits the screen. Selection rings and match highlights scale with the screenshot.
- **IME and insets:** the condition value editor sits in a bottom sheet that moves above the keyboard; the page uses the shared `GkScaffold` insets. No content is hidden behind the IME.
- **Loading:** the page is usable for picking as soon as nodes exist; match counts may show a small inline progress state while recomputing, without disabling editing.
- **No blocking animation:** saving, switching themes, or expanding options never locks the page. Duplicate/discard dialogs are the only intentional interrupts.
- **Accessibility:** the authoring UI itself exposes meaningful labels ("Element: 跳过 5s, selected"), 48dp targets, and does not rely on color alone for match highlights.
- **Localization:** counts use plurals; app/group names are passed through as data; the hidden vocabulary is never concatenated into a sentence.
- **Desktop replay:** the same page opens against a fixture snapshot; the floating button and live capture report "Not supported on this platform". The replay honors the same states, minus capture timing.
- **Transient capture:** closing/discarding deletes the authoring capture; the existing snapshot gallery is untouched.

---

## 7. What to react to

1. **Screen order:** one authoring page with an inline preview, sheets for ambiguity and broad matches — no wizard.
2. **Wording:** "element", "Keep it broad", "Narrow it"; no "selector" or "group" outside the banner.
3. **Ambiguity:** default to the smallest node; ask only when several nodes share the point.
4. **Re-entry:** in v1, edit an existing guided rule by re-capturing its screen and using the banner; there is no rule list inside the authoring flow.
5. **Amount of visible GKD vocabulary:** zero in the golden path; JSON remains an explicitly advanced exit.

---

## 8. Non-goals of this prototype

Templates/recipes, OR variants, activity-only scope, index/position narrowing, rule-management lists, global rules, multi-step rules, remote-subscription detection, and Desktop as a product authoring surface.

---

## 9. Recording a demonstration — amendment

**Status:** added October 2026 for the [recording effort](https://github.com/fktpp2022/gkd/issues/11). This section adds an alternative to the pick step; it changes nothing downstream of the picked node.

**Inputs:** [recording vocabulary](https://github.com/fktpp2022/gkd/issues/15), [what a demonstration must yield](https://github.com/fktpp2022/gkd/issues/16), [observing non-native taps](https://github.com/fktpp2022/gkd/issues/20), and the [device evidence](https://github.com/fktpp2022/gkd/issues/14).

**In one line:** in a live-capture session the user may perform one tap or long-press in the app; GKD locates that node on the frozen capture and continues exactly as if it had been picked.

### 9.1 Where Record sits, and when it is offered

Record is a single icon in the top bar of S2 — a filled record dot with the accessible name "Record a tap" — not a labelled button.

```
┌──────────────────────────────────────┐
│ ←   New rule                      ●  │
│                                      │
│   [ frozen screenshot ]              │
│        ┌──────────┐                  │
│        │ 跳过 5s  │  ◀ selected      │
│        └──────────┘                  │
│                                      │
│  Tap the element GKD should act on.  │
│                                      │
│  ┌────────────────────────────────┐  │
│  │ Selected: “跳过 5s”            │  │
│  │ [ Use this ]  [ Choose again ] │  │
│  └────────────────────────────────┘  │
└──────────────────────────────────────┘
```

- **Only a live-capture session offers it** — the floating-rule-button entry. The app-page and stored-snapshot entries work from a stored snapshot with no guaranteed live foreground app; the Desktop replay has no live app at all. Those sessions never show the icon.
- It appears on S2 only after node analysis has produced a usable tree. In the S9 dead end there is nothing a demonstration could resolve against, so no icon.
- It is absent from S3 and later screens: recording substitutes the pick, and the conditions screen has nothing to record.
- It is hidden once a demonstration has succeeded in this authoring session, even if the user returns to S2 — one demonstration per session. After a failed attempt it returns; a failed attempt is not a demonstration.
- Tapping it starts recording immediately. There is no confirmation sheet; the recording chip is the explanation, and Stop reverses the mode.

### 9.2 S13 — Recording

The authoring page steps back; the captured app comes forward, live. A small chip floats near the top and is the only GKD surface.

```
┌──────────────────────────────────────┐
│   ┌──────────────────────────────┐   │
│   │ ● Recording a tap — <App>    │   │
│   │ GKD rules are paused  [Stop] │   │
│   └──────────────────────────────┘   │
│                                      │
│          (live app screen)           │
│                                      │
│             ┌──────────┐             │
│             │ 跳过 5s  │             │
│             └──────────┘             │
│                                      │
└──────────────────────────────────────┘
```

- The chip is a small floating window; only its surface intercepts touches. The rest of the screen passes every touch to the app, so the demonstration is a real tap or long-press.
- The title names the captured app. The pause line is the promise: while the chip is live, GKD's rule engine does not act on any rule. It resumes the moment recording ends.
- Stop is the only GKD control. There is no timer; silence waits indefinitely. Back and Home remain ordinary app/launcher actions.
- On start, TalkBack announces "Recording. Tap the element in <App>. GKD rules are paused." The chip is one focusable group with a Stop action.
- The live screen is never marked, outlined, or highlighted. GKD shows no target; the user acts on the real app.
- Starting recording takes no new screenshot. The authoring capture from entry stays the only reference; if the app had already moved on before the tap, the attempt fails honestly.
- The first click or long-click event **from the captured app** ends the recording session, usable or not. The event carries which of the two actions was performed.
- The engine pause is load-bearing, not decoration: on Android 8–11 no service-side signal separates GKD's own click from a user tap, so an engine that kept acting could record itself.

### 9.3 Return, ambiguity, and the conditions screen

When the session ends, GKD brings the authoring page forward over the frozen capture. There is no notification and no manual return step.

- The event's node is located **on the authoring capture**, never on the live screen: source view id → source text/description → event text items within the source bounds, then the existing actable climb and prefill ladder.
- No capture node is found → S14, "Nothing was recorded".
- Several capture nodes match → S2b "Which element?" over the authoring page; choosing one continues to S3. Cancelling returns to S2's normal pick state; the attempt was not a demonstration, so the Record icon returns.
- One capture node resolves → S3, with the target ringed as after a pick.

The conditions screen is the existing S3, unchanged except that the action row arrives prefilled from the event:

```
┌──────────────────────────────────────┐
│ ←   New rule for <App>          ⋮    │
│                                      │
│  [ screenshot thumbnail, matches     │
│    highlighted, picked one ringed ]  │
│                                      │
│  Action                              │
│  [ Tap ]   [ Long-press ✓ ]          │
│                                      │
│  When this appears:                  │
│  ┌────────────────────────────────┐  │
│  │ Text    [ is ▾ ] [ 跳过 5s   ] │  │
│  └────────────────────────────────┘  │
│  + Add condition                     │
│  + Add context                       │
│                                      │
│  ✓ 1 element matches on this screen  │
│                                      │
│  ┌────────────────────────────────┐  │
│  │            Save                │  │
│  └────────────────────────────────┘  │
└──────────────────────────────────────┘
```

- A tap prefills **Tap**; a long-press prefills **Long-press**. The row stays editable, and changing it updates the auto-suggested name until the name is edited — exactly as after a pick.
- Nothing on the page — no badge, no toast, no extra row — says the target came from a demonstration. The saved rule is the same kind of rule.
- Everything downstream is unchanged: existing-rule banner, match count, broad/narrow, zero-match save, duplicate check, save/error/discard, limits option, raw fallback.
- Back from S3 without edits returns to S2 with the demonstration spent; the Record icon stays hidden. With edits, the usual discard prompt applies. The user may still pick a node on the capture instead — that is a normal pick, not a second demonstration.

### 9.4 S14 — Nothing was recorded

Inline on S2, below the still-visible frozen screenshot; the pick hint is replaced.

```
┌──────────────────────────────────────┐
│ ←   New rule                         │
│                                      │
│          [ frozen screenshot ]       │
│                                      │
│  Nothing was recorded.               │
│  You stopped before GKD saw a tap.   │
│  Some apps don't report taps;        │
│  picking on this screen still works. │
│                                      │
│  ┌────────────────────────────────┐  │
│  │        Pick it instead         │  │
│  └────────────────────────────────┘  │
│  [ Record again ]      [ Cancel ]    │
│                                      │
└──────────────────────────────────────┘
```

- The cause line adapts:
  - Stopped with no event: `You stopped before GKD saw a tap.` followed by the helper `Some apps don't report taps; picking on this screen still works.`
  - An event that identifies no capture node, with no screen change: `That tap didn't match anything on this screen.`
- The helper is the one place the feature's known blind spot is stated in the flow. It is honest without naming Compose: some apps never report taps, and picking remains available.
- **Pick it instead** returns to S2's normal pick state; the Record icon returns because no demonstration occurred.
- **Record again** starts a fresh recording session against the same capture.
- **Cancel** ends the authoring session; there are no edits, so no discard prompt.
- The Record icon is hidden while this state is shown; its actions are the two ways forward.
- This state never appears on its own. Silence does not time out; it requires Stop or an unusable event.

### 9.5 S15 — Screen changed

Triggered by a pre-tap window-state change in the captured app, or by a failed tap whose own navigation changed the screen. A window change caused by a usable tap is the demonstration's result, not an invalidation.

```
┌──────────────────────────────────────┐
│ ←   New rule                         │
│                                      │
│          [ frozen screenshot ]       │
│                                      │
│  The screen changed.                 │
│  Nothing was recorded.               │
│                                      │
│  ┌────────────────────────────────┐  │
│  │ Capture this screen and record │  │
│  └────────────────────────────────┘  │
│  [ Pick on the captured screen ]     │
│  [ Cancel ]                          │
└──────────────────────────────────────┘
```

- **Capture this screen and record again** replaces the authoring capture with the app's current screen and starts a new recording session. If that capture fails, the existing S9 dead end applies.
- **Pick on the captured screen** keeps the old capture and enters picking — the same stale screenshot the page is already showing. The attempt was not a demonstration; the Record icon returns.
- **Cancel** ends the authoring session; no discard prompt.
- The Record icon is hidden while this state is shown.
- The copy is deliberately generic: pre-tap drift and a tap that navigated before it could be used are the same dead end, and the user's choice is the same.

### 9.6 App scope and other apps

- A recording session is scoped to the captured app's package. Only click and long-click events from that app end it.
- A tap in another app is ignored: it does not end the attempt, does not record, and does not consume the demonstration. The chip stays visible and still names the captured app.
- Leaving the captured app (Home, recents, another app) leaves the session live. The user returns to the captured app to demonstrate, or taps Stop.
- Because the pause is global while the chip is live, Stop is always one tap away and the chip is never hidden.
- The floating rule button is hidden while recording; the chip is the only GKD surface.
- A screen change in the captured app before the tap ends the attempt with S15, wherever the user is; the authoring page returns.

### 9.7 State machine additions

| State | Entry | Primary action | Exits |
| --- | --- | --- | --- |
| Recording | S2 record icon, live capture only | Tap/long-press the target in the captured app | S3 Conditions, S14 NothingRecorded, S15 ScreenChanged |
| NothingRecorded | Stop with no event; an event identifying no capture node | Pick it instead / Record again / Cancel | S2 Picking, Recording, Idle |
| ScreenChanged | Pre-tap window change; failed tap that changed the screen | Capture this screen and record again / Pick on the captured screen / Cancel | Recording (new capture), S2 Picking, Idle |

Recording is a phase inside the authoring session: the authoring page stays in the back stack, the engine is idle for the whole phase, and the one-demonstration budget belongs to the authoring session.

### 9.8 Copy deck additions

| Context | Copy | Note |
| --- | --- | --- |
| Record action | ● | Icon only; content description "Record a tap" |
| Recording title | Recording a tap — %1$s | Captured app label |
| Recording pause | GKD rules are paused | While the chip is live |
| Recording stop | Stop | Only GKD control |
| Recording announced | Recording. Tap the element in %1$s. GKD rules are paused. | TalkBack, on start |
| Nothing title | Nothing was recorded. | |
| Nothing cause (stopped) | You stopped before GKD saw a tap. | |
| Nothing helper | Some apps don't report taps; picking on this screen still works. | Under the stopped cause only |
| Nothing cause (no node) | That tap didn't match anything on this screen. | No helper |
| Nothing actions | Pick it instead / Record again / Cancel | Pick is primary |
| Changed title | The screen changed. | |
| Changed body | Nothing was recorded. | |
| Changed actions | Capture this screen and record again / Pick on the captured screen / Cancel | Recapture is primary |
| Conditions after a demonstration | No new copy | Prefilled action row is the only trace |

### 9.9 What to react to

1. **Icon-only entry.** Is a top-bar dot discoverable enough, or does the pick hint need one line pointing at it?
2. **The honest helper.** "Some apps don't report taps; picking on this screen still works." Is that the right amount of truth, or too technical for a failure state?
3. **No success cue.** The ringed target and prefilled action row are the only trace of the demonstration. Does the return from the live app read without a transient "Tap recorded."?
4. **The global pause across other apps.** Taps elsewhere are ignored and the chip is always visible; is that enough protection for a session that can outlive the captured app's foreground time?
5. **Recapture replaces the capture.** S15 keeps the old capture pickable while the primary action discards it; is that the right balance?

### 9.10 Non-goals of this amendment

- Compose and custom-drawn observation; those apps reach S14 honestly.
- Multi-action or multi-rule recording, sequences, and replay.
- Recording from stored snapshots, the app page, or Desktop.
- A provenance field, badge, toast, or any rule-level difference after saving.
- Recording as an accessibility path; the list pick remains the non-visual route.
