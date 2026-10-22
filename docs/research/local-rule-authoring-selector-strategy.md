# Deriving a selector from one picked node — strategy and chip model

**Status:** research complete, October 2026. Input to the rule-editor spec, not the spec.

**Scope:** how the on-device editor turns one user-picked accessibility node into a GKD selector, and which plain-language conditions v1 exposes.

**Method:** read `gkd-selector` (Selector/parser/printer/FastQuery/RelationOperator/CompareOperator), `AttrInfo`/`NodeInfo`, `RawSubscription`, `A11yContext.queryRule`; prior-art and capture-feasibility reports on their research branches. No production code changed.

**Evidence limits:** no device run and no corpus scan of `gkd-kit/subscription`; ranking below is derived from language semantics plus the prior-art examples. Items marked **[device]** or **[corpus]** need empirical confirmation.

---

## TL;DR

1. Rank candidates by *stability*, then keep only as much as *uniqueness* on the captured snapshot requires. Text first, `vid` second, `id` third; class/flags only as filters; bounds only as a last resort.
2. Uniqueness is measured (compile the selector, walk the captured nodes); stability is heuristic. Show both, and label the count "on this screen".
3. "Act on any identical match" = one short selector, no uniqueness constraints (the normal skip-ad case). "This exact node" = add structural context, never a node identity — GKD has none across frames.
4. v1 chips: text is/contains, desc is/contains, vid is, id is, class is, clickable, visible, plus `inside`/`next to` context chips via a second pick. AND by default; OR, negation, regex, index arithmetic and raw text stay advanced.
5. Chips build the selector; the selector string is what persists. Parse-back into chips only for the supported subset, otherwise open read-only-text mode and say so.
6. Confirm only for: >1 match with "act on all", non-tap actions, global scope, risky labels. One match + tap + one app must save without ceremony.

---

## 1. Candidate order and why

Order is a heuristic; the real gate is the snapshot match count from §3.4 of the prior-art report.

| # | Condition | Stability | Uniqueness | Notes |
|---|---|---|---|---|
| 1 | `text="…"` | medium (localized, counters, A/B copy) | high | Fast-query eligible with `= ^= *= $=`. Skip values that look dynamic (digits, `%`, counts, timestamps). |
| 2 | `vid="…"` | high (developer-assigned resource id) | high | Fast-query eligible only with `=`. Empty on many WebView/Canvas/Compose nodes. |
| 3 | `id="…"` | high, but shared by list rows | high on one screen, low across repeats | Fully-qualified view-id resource name; fast-query `=` only. |
| 4 | `desc="…"` | medium (localization) | medium-high, good for icon buttons | Not a fast-query attribute. First choice for icon-only close/back buttons. |
| 5 | class (`TextView`, `ImageView`) | high | low alone | Printer/parser use suffix match on `name` after `.`, so short names are fine. Never emit alone. |
| 6 | `clickable` / `visibleToUser` | high | low | Filters, not identifiers. `clickable` is often false on the text node while the parent handles the tap. |
| 7 | `width`/`height`/`left`/`right` comparisons | low (density, layout) | medium | "small icon" heuristics only; advanced. |
| 8 | rule-level `position` (x/y or left/top) | very low | n/a | Only for non-clickable/canvas targets; advanced, needs its own warning. |

Derivation rules:

- **Resolve the actable node first.** From the picked node, if `clickable=false`, climb to the nearest `clickable=true` ancestor; if none, keep the picked node. Tapping a text child usually still works, so do not climb when the picked node already has text. **[device]** Compose/WebView trees often expose `clickable` on an ancestor only.
- **Never emit snapshot-only fields**: `_id`, `_pid`, `depth`. `index`/`childCount` are legal but content-dependent; advanced only.
- **Order filters deliberately.** Fast query is derived from `filters.firstOrNull()` (only `id`/`vid` with `=`, or `text` with `= ^= *= $=` on a non-empty string literal; an OR of those also works). Emit the identifying comparison first and flags after it, so `[text*="跳过"][clickable=true]` stays fast-query capable while `[clickable=true][text*="跳过"]` silently loses it. Do not auto-enable `fastQuery`; keep the selector eligible and explain the trade-off.
- **Add context in this order** when the base selector yields 0 or >1: (a) nearest ancestor with stable `vid` → `inside`; (b) sibling with stable text/desc → `next to`; (c) class + `clickable` filter; (d) index/childCount; (e) bounds/position. Stop at the first that reaches one match, cap at ~3 conditions.
- **Stability vs uniqueness are different failures.** Too few conditions → decoys (§4). Too many → the rule stops firing after an app update, and the user gets no error. Prefer a stable identifier plus one structural condition over three textual ones.
- **Emit `@` on the picked target whenever anything is prepended**, e.g. `[vid="ad_root"] > @[text="跳过"]`, so the acted-on node is explicit. **[device]** Confirm the engine's return-node rule for multi-part chains and that `@` is respected there.

## 2. "Any identical match" vs "this exact node"

- **Any match** is already the default behavior of a rule: one short selector, evaluated on every event, acts on whichever node matches. Do not add uniqueness constraints; do not use `matches` chaining. This is the right v1 default for skip/close.
- **This exact node** is expressed structurally, because node ids are per-frame. Allowed levers: more attributes from the picked node, ancestor context, sibling context, `index`, tuple/offset relations (`(1,2)`, `-2`), `parent.childCount` arithmetic. Each is a recall bet.
- **`matches` is a funnel, not a set.** `A11yContext.queryRule` applies each selector to the previous result; an empty result aborts the rule. Use it for "find the dialog, then the button inside", i.e. scoping.
- **`anyMatches` is OR fallback.** The first selector that yields a node wins and becomes the anchor for `matches`. Use it for "text 跳过, or desc 关闭, or vid close_btn" variants, never to resolve ambiguity — it encodes alternatives, not a decision.
- **`excludeMatches` is evaluated from the rule's start node**, not from the matched result. It is the right tool for "don't fire when the real close button is also present", but sibling-relative excludes will surprise users; keep it advanced.
- **Brittleness ladder** (most to least stable): stable `vid`/`id` + one ancestor → text + class → sibling offset `-1`/`+1` → `index=N` → `childCount` arithmetic → pixel bounds/`position`. Anything below the second row should show "may break on layout changes".

## 3. Minimal chip model (v1)

Condition rows, each `property + operator + value`, combined with **AND**; the toolbar offers one `OR` group as advanced.

| Chip | Operators | Selector shape | Default |
|---|---|---|---|
| text | is, contains | `[text="X"]`, `[text*="X"]` | prefilled from pick |
| description | is, contains | `[desc="X"]`, `[desc*="X"]` | offered when non-empty |
| view id | is | `[vid="X"]` | offered when non-empty |
| full id | is | `[id="X"]` | advanced-ish, hidden behind "more" |
| type | is | `TextView`, `android.widget.TextView` | advanced-ish |
| clickable | is true/false | `[clickable=true]` | off |
| visible | is true | `[visibleToUser=true]` | off |
| inside | pick a second node | `[ctx…] > @[target…]` | when >1 match |
| next to | pick a second node | `[ctx…] - @[target…]` (or `+`) | when >1 match |

Advanced only: OR, `!`, `!=`/`!*=`, regex (`~=`), `index`, `childCount`, tuple/arithmetic relations, `position`, `excludeMatches`, `preKeys`, `matchRoot`. Prior art §2.2 and §4.2 agree: 14 attribute operators and 6 relation operators do not belong in a first-run flow; the `!*=` typo in issue #1182 is the failure mode chips prevent.

**Round-trip**

- Chips → text: build the structured model, print via `Selector.toString()` (`SelectorPrinter` emits canonical spacing, quoting and escaping for quotes, backslash, control chars and lone surrogates). Never concatenate user text into a selector by hand.
- Text → chips: `Selector.parse` gives the AST plus tokens/positions. Map only if the AST is entirely inside the v1 subset: AND-joined comparisons on whitelisted properties, literal right-hand sides, optional class name, at most one context unit with a literal-only context selector. Otherwise switch the rule to text mode permanently and disable the chip toggle with an explicit message ("this selector uses features the chip editor cannot show").
- **Never partially map.** A dropped condition is a silently different rule. If any node of the AST is unsupported, keep the text authoritative.
- Persist the selector string only (it is the contract in the local subscription JSON5); re-derive chips on open. Do not add a chip sidecar that can drift. Text-authored rules keep the user's exact string; chip-authored rules store printer output.
- Canonical printing also gives cheap duplicate detection inside a group (`compile(a).toString() == compile(b).toString()`).

## 4. Safety signals

- **Risky labels**: 关闭/close, 跳过/skip, 确认/确定/confirm/ok, 支付/购买/order/pay, 删除/delete/remove, 发送/send, 卸载/uninstall, 同意/agree, 立即更新/update. Match against text, desc and `vid`/`id` substrings (`close_2` decoys, discussion #1205; #1384 picked the mini-program's close instead of the ad's).
- **Multiple matches** is the primary signal, not a success. Show every match on the screenshot, emphasize the picked one, and force a choice: "narrow it" or "act on all N".
- **Near-duplicate siblings** (same text/desc, differing id) and matches outside the captured activity are secondary decoy signals.
- **Justified confirmations**: act-on-all with >1 match; any non-tap action; global/all-app scope with the app list shown; risky label on a tap; position-based tap with no node identity.
- **Unjustified**: every save. The common case (one match, one app, tap) must stay one screen, or the confirmations get dismissed reflexively (prior art §4.12 risk).
- Never auto-enable or auto-run a new rule; the match preview is the confirmation surface.

## 5. Defaults for a first rule

- **Action**: tap — reuse the existing action value/registry (`RawSubscription.action` is an unvalidated string), do not hardcode a literal in the editor. **[device]** Confirm the persisted spelling of tap via `GkdAction` before writing.
- **Scope**: the captured app, one app entry, one authoring group per app (`RawApp.groups`), rules appended with fresh `key`s; never reuse or renumber existing keys.
- **Activity**: leave `activityIds` empty by default (= every activity of the app). Offer "only on this screen" as a secondary toggle that fills the captured `activityId`. Rationale: activity names are obfuscated and vary per version; the captured ad activity is often transient. **[corpus]** Check how often splash-ad selectors recur on a different activity than the captured one before locking this.
- **Match semantics**: target node = picked node, `matchRoot` off, `fastQuery` left unset (eligible but not forced), rule name suggested from the recipe + target. Limits (the inspector's known-good bundle `matchTime: 10000`, `actionMaximum: 1`, `resetMatch: "app"`) are offered as "tap once when the app opens", not applied silently; the engine's own defaults are unknown here.

## Unknowns / validation needed

- **[device]** Chain return-node semantics and `@` handling; whether a tap on a non-clickable text child dispatches to the clickable ancestor across Compose/Flutter/WebView.
- **[device]** How often `visibleToUser` is false on nodes that are visually on screen (would make the chip a false-negative source).
- **[corpus]** Attribute hit rates per app family (share of pickable nodes with `vid`, `text`, `desc`), and whether generated text-first selectors survive app updates; sample real subscription rules to calibrate the ranking.
- **[corpus]** Frequency of duplicate matches for a single text selector on ad screens (decoy rate), which sets the default "narrow it" threshold.
- **[device]** Whether a snapshot-backed `NodeAdapter` over `NodeInfo` exists in commonMain; the editor needs one to compute match counts without a live a11y tree.
- Engine defaults for `matchTime`/`actionMaximum`/`resetMatch` were not read; the §5 limits recommendation depends on them.
