# On-device GKD rule authoring: prior art and UX patterns

**Status:** research complete, October 2026. This is research input for a decision-locked UX spec, not the spec itself.

**Scope:** GKD's official web inspector and docs; the current on-device authoring baseline; visual and record-based rule builders in adjacent tools; GKD user-reported friction.

**Method:** primary sources only where possible: GKD source code and docs, the GKD issue/discussion tracker, official tool documentation, and the SUGILITE paper. Claims about GKD behavior were checked against the current `main` branches of `gkd-kit/inspect`, `gkd-kit/docs`, and the local app source. No production code was changed.

**Evidence limits:** the issue tracker is a self-selected sample, not a usage study. Tool descriptions are from vendor docs and user reports, not hands-on usability testing. Percentages from SUGILITE are from its controlled study, not from GKD users.

---

## TL;DR

1. **The closest existing experience to the target is GKD's web inspector, but it is desktop-only and ends at "copy JSON5".** It already has snapshot browsing, screenshot click-to-focus, overlap disambiguation, live selector syntax validation, match counts, selector-path visualization, a rule composer, and a scoped selector library. It does not persist a generated rule into the user's local subscription or export ([inspector README](https://github.com/gkd-kit/inspect/blob/main/README.md), [rule composer source](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/RuleComposerDialog.vue)).
2. **The most reusable interaction is screenshot-first picking, not the node tree.** Tap the thing you mean, show the smallest node at that point, and when several nodes overlap, ask which one; the inspector already does this ([snapshot.ts](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/snapshot.ts), [snapshot docs](https://gkd.li/guide/snapshot)).
3. **Every visual builder eventually exposes an expression.** MacroDroid, AutoInput, Automate, AutoX, Appium, browser DevTools and SUGILITE all reduce to a selector/path/coordinate when the UI is ambiguous. The design should make that expression an advanced view, not the primary object.
4. **GKD's friction is broader than selector syntax.** The recurring failures are rule level and scope, snapshot-versus-runtime mismatch, overconfidence in `fastQuery`, missing accessibility nodes in WebView/Compose/Flutter, relative-position semantics, and decoy targets. A friendly editor that only fixes syntax will still fail users.
5. **Recommended on-device shape:** recipe or blank start, screenshot pick with disambiguation, plain-language condition chips, a live match preview with highlighted targets, guided scope/limits, an explicit save into the local subscription, and raw JSON5 only behind "Advanced".

---

## 1. GKD's official web inspector

### 1.1 What it is

The inspector at [i.gkd.li](https://i.gkd.li) is GKD's official snapshot review and rule-generation tool. Its README lists capture, review, and share snapshot; connect to GKD's internal HTTP server; enable subscription rules and execute click selectors live; selector-path view; selector library scoped to global/app/activity; and configurable rule generation with static diagnostics and screenshot-based `position` generation. It targets Chrome 119+ ([README](https://github.com/gkd-kit/inspect/blob/main/README.md)).

A snapshot is a zip containing the screenshot, device info, UI info, and accessibility node information ([snapshot docs](https://gkd.li/guide/snapshot)). The app can capture one from a floating button, a quick-settings tile, a screenshot event, the HTTP inspector, or an external service invocation. Snapshot sharing is not private by default: the docs warn that uploading to GitHub makes the snapshot publicly viewable and describe redaction steps before sharing ([snapshot docs](https://gkd.li/guide/snapshot)).

### 1.2 The end-to-end workflow

The de facto workflow is:

1. **Capture** a snapshot on the device while the target screen is visible.
2. **Load** the snapshot in i.gkd.li. The screen shows a screenshot, a node tree, and attribute/search panels.
3. **Click the screenshot** to focus a node. The inspector finds nodes at that point, filters out ancestors and containers, and sorts by smallest area; when more than one remains, it shows an overlap/disambiguation panel ([snapshot.ts](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/snapshot.ts)).
4. **Inspect attributes** in the attribute card. Clicking a row copies `name=value`; the card exposes a generated `_selector`; rows for `id`, `vid`, and `text` are marked "可快速查找" when the snapshot says they are quick-find capable ([AttrCard.vue](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/AttrCard.vue)).
5. **Search** in either "字符搜索" or "选择器查询" mode. The selector input validates as you type and reports `语法正确` or `语法错误 · 位置 N：...`; a successful search reports how many nodes matched ([SearchCard.vue](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/SearchCard.vue), [SelectorSyntaxInput.vue](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/entities/selector/ui/SelectorSyntaxInput.vue)).
6. **Inspect the path** in the selector-path view, which renders each selector unit as a colored token in a graph and notes that unrelated nodes are hidden for simplicity ([SelectorTrackCard.vue](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/entities/selector/ui/SelectorTrackCard.vue)).
7. **Compose a rule** in the rule composer. The composer asks for output depth, group key/name/description, action, `preKeys`, whether to restrict the current activity, whether to include `matchRoot`, whether to include execution limits, whether to include snapshot links, and optionally a tap position. It then shows generated JSON5/TypeScript plus a diagnostic panel.
8. **Copy the output** and paste it into GKD's local subscription editor. This is the persistence step.
9. **Optionally test live**: connect the inspector to the GKD HTTP server, capture a fresh snapshot, push a memory subscription, and execute a selector directly on the device ([device control source](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/device-control/DeviceControlView.vue)).

Deep links are part of the documentation workflow: many examples throughout the docs link to a snapshot with `?gkd=<encoded selector>` so the selector is prefilled in the inspector (for example the selector examples page: [gkd.li/guide/example](https://gkd.li/guide/example)). Full share links can additionally restore the focused node and up to 20 text/selector queries ([snapshot_url_codec.ts](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/snapshot_url_codec.ts)).

### 1.3 UX anatomy

| Surface | What it shows | Key interaction | Why it works |
|---|---|---|---|
| Screenshot canvas | The captured screen | Click to focus the smallest node at the point; multiple candidates open an overlap panel | Recognition over recall: the user points at what they mean instead of describing it |
| Node tree | Android class hierarchy, labels, children | Expand/collapse; select a node | Gives precise context when the screenshot is ambiguous |
| Attribute card | All node attributes, quick-find badges, generated `_selector` | Click a row to copy `name=value` | Turns inspection into composable pieces instead of free-form typing |
| Search card | Character search or selector query | Type and press Enter; live syntax status; result count | Fast feedback loop between hypothesis and match |
| Result card | Selector tokens, "N 个节点", fastQuery badge with reason, compose/save/share actions | Choose a result to compose a rule | Connects "found it" to "make a rule" without retyping |
| Selector-path view | Graph of selector units over the node path | Inspect which part of the selector matched which node | Makes a complex expression explainable |
| Rule composer | Output depth, metadata, action, scope, limits, position | Generate and inspect output; copy | Separates "what rule" from "where it goes" |
| Rule diagnostics | Matched/rejected selector counts, target node button, caveats | Re-run diagnostic | Surfaces likely breakage before export |
| Selector library | Saved selectors with global/app/activity scope, tags, pin/use counts | Save, filter, reuse | Avoids retyping and preserves known-good building blocks |
| Device control | Capture snapshot, download snapshots, update memory subscription, execute selector | Live device loop | Shortens the edit-test cycle |
| Share link | Snapshot + focus + queries | Open in browser | Makes help requests reproducible |

### 1.4 Rule composition: powerful, but copy-only

The rule composer is the most relevant prior art for the on-device flow, but its boundaries matter.

**Output depth is explicit.** The user chooses 应用配置 (`app`), 规则组 (`group`), 单条规则 (`rule`), or TypeScript 文件 (`ts`). Defaults are an app-level object with a placeholder name `[ChangeMe]规则名称-<timestamp>` and description `[ChangeMe]本规则由GKD网页端审查工具生成` ([rule_composer.ts](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/rule_composer.ts)).

**Execution limits are a single toggle.** "包含执行限制" inserts `matchTime: 10000`, `actionMaximum: 1`, and `resetMatch: 'app'` at the group level ([rule_composer.ts](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/rule_composer.ts)).

**Position generation has three reference frames.** The composer can generate node-relative proportions (`width * ...`, `height * ...`), node-relative pixels, or absolute screen coordinates. This responds to a documented point of confusion: `position.left/top/right/bottom` are relative to the matched node, not the screen, while `position.x/y` are screen coordinates ([rule_composer.ts](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/rule_composer.ts), [Position API](https://gkd.li/api/type-aliases/Position); maintainer explanation in [discussion #951](https://github.com/orgs/gkd-kit/discussions/951)).

**It ends at copy.** The only actions in the composer are `关闭` and `复制结果`; there is no "save to subscription" action. The generated output is JSON5 or a TypeScript module. The only subscription write path in the inspector is the separate device-control action "修改内存订阅", which posts to `/updateSubscription`; the app saves it under the special ID `LOCAL_HTTP` with the display name "内存订阅" and `enableUpdate = false` ([rule composer](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/RuleComposerDialog.vue), [device control](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/device-control/DeviceControlView.vue), local [InspectionServer.kt](D:/Projects/gkd/gkd-app/src/commonMain/kotlin/li/gkd/app/network/inspection/InspectionServer.kt)). That subscription is a debug target, not the user's authored local subscription.

**The diagnostics are honest about their limits.** The rule card says: "仅验证当前快照中的选择器、界面范围和排除条件，不模拟规则执行时序" ("only validates selectors, UI scope, and exclusion conditions in the current snapshot; does not simulate rule execution timing"). Its fastQuery evidence text says a quick-find match "不代表客户端运行时实际采用了快查" ("does not mean the client actually used fast query at runtime") ([RuleCard.vue](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/RuleCard.vue), [selector_diagnostics.ts](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/selector_diagnostics.ts)).

**Selector library is scoped, but stores selectors, not rules.** Presets carry a `global`, `app`, or `activity` scope plus `name`, `selector`, `description`, `tags`, `pinned`, and `useCount`; app/activity scopes require IDs. This is a good model for a future on-device "my selectors" shelf ([library.ts](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/entities/selector-library/library.ts)).

### 1.5 What the inspector does not solve

- **Desktop dependency.** It is a Chrome 119+ web app. Users who only have a phone cannot use it, which is exactly the gap that prompted [issue #708](https://github.com/gkd-kit/gkd/issues/708).
- **Persistence.** It generates and copies, but does not write into the user's local subscription or a subscription export. The user still has to find the right editor and paste at the right level.
- **Runtime truth.** A snapshot is static. The inspector explicitly does not simulate execution timing, and quick-find badges do not prove runtime behavior.
- **Guided authoring.** The composer still exposes group keys, output depth, `preKeys`, and an action vocabulary (`click`, `clickNode`, `clickCenter`, `back`, `longClick`, `longClickNode`, `longClickCenter`, `none`). It is a power tool, not a wizard.
- **Safety.** Nothing prevents generating a rule that clicks a decoy close button, a payment control, or a node that only exists in the snapshot.
- **Plain language.** The node tree and selector path are precise but technical. There is no sentence such as "when a clickable text containing 'Skip' appears in this app, tap it once".

---

## 2. GKD docs and the current on-device path

### 2.1 The documented workflow

GKD's docs do not contain one "authoring" page. The path is distributed across three pages:

- [Snapshot](https://gkd.li/guide/snapshot): what a snapshot contains, how to capture it, how to share and redact it.
- [Selector](https://gkd.li/guide/selector) and [Selector examples](https://gkd.li/guide/example): the selector language and worked examples.
- [Subscription](https://gkd.li/guide/subscription): where local rules live and what the JSON5 looks like.

The resulting workflow is: capture snapshot, open it in i.gkd.li, test a selector in the search box, compose/copy a rule, then paste it into `首页-订阅-本地订阅-应用规则` or `全局规则` ([subscription docs](https://gkd.li/guide/subscription)). The docs' examples link straight into the inspector with the selector prefilled, which is a strong "learn by trying" pattern.

### 2.2 What a rule author must learn

| Concept | What the user must hold in their head | Evidence | Design opportunity |
|---|---|---|---|
| Attribute selector | `[text="跳过"]`, `[vid="..."]`, class shorthand `TextView`, `@` for the target node | [selector docs](https://gkd.li/guide/selector) | Offer attribute chips and a visible "this node" marker; never require `@` in the simple path |
| Mandatory whitespace | `div>img` is illegal; it must be `div > img` | [selector docs](https://gkd.li/guide/selector) | Generate spacing automatically; validate at the token level |
| 14 attribute operators | `= != > >= < <= ^= !^= *= !*= $= !$= ~= !~=` | [selector docs](https://gkd.li/guide/selector) | Present 4 everyday relations (equals, contains, starts/ends, regex) and hide the rest |
| 6 relation operators | `+`, `-`, `>`, `<`, `<<`, plus tuple/arithmetic relations like `(1,2)` or `(2n-1)` | [selector docs](https://gkd.li/guide/selector) | Translate to "near", "inside", "after", "before" with a visual anchor |
| Logical composition | `&&`, `||`, `!(...)`, implicit AND between adjacent `[]` | [selector docs](https://gkd.li/guide/selector) | Avoid boolean syntax in the basic path; use condition rows with AND by default |
| Value expressions and functions | `parent.childCount.minus(1)`, `getChild(0)`, `getPrev(1)`, arithmetic | [selector examples](https://gkd.li/guide/example) | Advanced only; surface as "structural position" templates |
| Snapshot-only attributes | `_id`, `_pid`, `depth`; `depth` has a warning; `id`/`vid`/`text` are quick-find candidates | [AttrCard.vue](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/AttrCard.vue) | Never write snapshot-only fields into a runtime rule; filter them out |
| `fastQuery` | An optimization with format constraints; can change result order and first match | [optimize docs](https://gkd.li/guide/optimize) | Explain as "faster lookup, may change order"; default off unless the user asks for speed |
| Rule schema and scope | `matches` vs `anyMatches`, `activityIds`, `matchTime`, `actionMaximum`, `resetMatch`, `preKeys`, `matchRoot`, `priorityTime`, `order` | [subscription docs](https://gkd.li/guide/subscription), [issue #824](https://github.com/gkd-kit/gkd/issues/824) | Ask "where and when" in product language; generate the fields |
| JSON5 and escaping | Comments/trailing commas are allowed, but regex backslashes and nested quotes are error-prone | [issue #603](https://github.com/gkd-kit/gkd/issues/603) | Do not expose JSON5 in the basic path; auto-escape and preserve backslashes on save |

The selector docs are explicit that they describe the language from an abstract syntax tree perspective and redirect beginners to the examples ([selector docs](https://gkd.li/guide/selector)). That is appropriate reference documentation, but it confirms the learning burden: the docs teach the grammar before the task.

### 2.3 Recipes already in the docs

The docs contain de facto recipes, even though they are presented as snippets:

**Global splash-ad rule** (a global group; `fastQuery` enabled; 10-second window, one action, reset per app):

```json5
{
  key: 0,
  name: '开屏广告',
  fastQuery: true,
  matchTime: 10000,
  actionMaximum: 1,
  resetMatch: 'app',
  rules: [
    {
      key: 0,
      matches: '[text*="跳过"][text.length<10][visibleToUser=true]',
    },
  ],
}
```

This is exactly the kind of rule a "skip splash ad" recipe should generate, but the user currently has to know which fields to add and why ([subscription docs](https://gkd.li/guide/subscription)).

**App-scoped close-button rule** (activity restriction plus a quick-find `vid`):

```json5
{
  key: 1,
  name: '全屏广告-会员活动弹窗',
  rules: [
    {
      key: 0,
      fastQuery: true,
      activityIds: 'com.blued.android.core.ui.TransparentActivity',
      matches: '[vid="img_close"]',
      snapshotUrls: 'https://i.gkd.li/i/14310618',
    },
  ],
}
```

**Simple app rule** (Zhihu's skip button by view ID):

```json5
{
  key: 0,
  name: '开屏广告',
  rules: { matches: 'TextView[id="com.zhihu.android:id/btn_skip"]' },
  snapshotUrls: ['https://i.gkd.li/i/13070251'],
}
```

Sources: [subscription docs](https://gkd.li/guide/subscription).

These recipes encode three different scopes (global, app group, app rule), three different selectors (text contains, `vid`, full `id`), and optional execution limits. A recipe picker could carry all of that without showing JSON5.

### 2.4 Current on-device baseline

The current app's "add/edit rule group" screen is a full-screen JSON5 text field with syntax highlighting, autofocus, a character count, and a save action ([UpsertRuleGroupPage.kt](D:/Projects/gkd/gkd-app/src/commonMain/kotlin/li/gkd/app/ui/subscription/UpsertRuleGroupPage.kt)). The view model parses the text as an app group or global group and saves it into the selected subscription ([UpsertRuleGroupViewModel.kt](D:/Projects/gkd/gkd-app/src/commonMain/kotlin/li/gkd/app/ui/subscription/UpsertRuleGroupViewModel.kt)). There is no structured target picker, condition builder, match preview, or rule-level test action on that screen.

The app already contains infrastructure that an on-device flow could reuse:

- Snapshot capture and storage (`SnapshotStore`, snapshot pages). The on-device snapshot preview is an image gallery, not a node tree ([SnapshotPreviewPage.kt](D:/Projects/gkd/gkd-app/src/commonMain/kotlin/li/gkd/app/ui/snapshot/SnapshotPreviewPage.kt)).
- A local HTTP inspection server exposing `/getSnapshot`, `/captureSnapshot`, `/getSnapshots`, `/deleteSnapshot`, `/updateSubscription`, and `/execSelector` ([InspectionServer.kt](D:/Projects/gkd/gkd-app/src/commonMain/kotlin/li/gkd/app/network/inspection/InspectionServer.kt)).
- A special `LOCAL_HTTP` subscription displayed as "内存订阅" for inspector pushes ([InspectionServer.kt](D:/Projects/gkd/gkd-app/src/commonMain/kotlin/li/gkd/app/network/inspection/InspectionServer.kt), [SubscriptionRepository.kt](D:/Projects/gkd/gkd-app/src/commonMain/kotlin/li/gkd/app/subscription/SubscriptionRepository.kt)).

One naming trap: `SelectToSpeakService` in the app is not a select-to-speak picker. It is a second `A11yService` entry using a system-looking package/class name, apparently to work around OEM accessibility restrictions ([SelectToSpeakService.kt](D:/Projects/gkd/gkd-app/src/androidMain/kotlin/com/google/android/accessibility/selecttospeak/SelectToSpeakService.kt), [A11yService.kt](D:/Projects/gkd/gkd-app/src/androidMain/kotlin/li/gkd/app/service/A11yService.kt)). Android's actual Select to Speak is a read-aloud accessibility feature, not an authoring tool ([Google support](https://support.google.com/accessibility/android/answer/7349565?hl=en)).

### 2.5 Learning burden, in users' words

The tracker provides direct evidence that the burden is real and varied:

- **A plausible operator typo kills the selector.** A user wrote `text!*="退出"` instead of a valid operator; the selector looked reasonable but matched nothing. The working version switched to exact text conditions ([issue #1182](https://github.com/gkd-kit/gkd/issues/1182)).
- **Backslashes disappear.** A regex `.*\(\d*\)` was saved as `.*(d*)`, changing the meaning ([issue #603](https://github.com/gkd-kit/gkd/issues/603)).
- **Chinese input produces a parse error.** A user pasted a rule containing Chinese and got `Unexpected Char：酷` ([issue #931](https://github.com/gkd-kit/gkd/issues/931)).
- **Wrong level is a common mistake.** A user pasted a rule group into the single-rule editor and got "非法规则: matches 和 anyMatches 至少存在一个"; the maintainer replied that the editor only validated a single rule at the time ([discussion #751](https://github.com/orgs/gkd-kit/discussions/751)).
- **The rule doesn't fire and the reason is invisible.** Threads show users with valid-looking selectors and no trigger, then a long exchange about activity scope, timing, `matchRoot`, `fastQuery`, or engine behavior ([issue #643](https://github.com/gkd-kit/gkd/issues/643), [issue #824](https://github.com/gkd-kit/gkd/issues/824), [issue #1000](https://github.com/gkd-kit/gkd/issues/1000)).

The docs are strong reference material. They are not a guided authoring experience, and the current app gives the user a text box.

---

## 3. Prior art from other tools

### 3.1 Comparison

| Tool | How the user picks a target | Abstraction produced | Test/feedback | Notable failure mode |
|---|---|---|---|---|
| MacroDroid UI Interaction | "Identify in app": tap the control while the app is open | Text, ID, or screen location | Runs the macro; user observes the result | Controls often have no ID; falls back to text/location; reported `id: null` and failed identification |
| Tasker AutoInput | UI Query/Action; pick fields (text, ID, coordinates); Actions v2 helper walks through actions | Tasker action fields with text/ID/point | Helper performs the action to confirm; task log | Hangs when the target is not found; multi-state text requires `=:=` syntax |
| Automate Interact | Record interaction, search picker, or hand-written XPath | XPath expression | Runs the flow; NO path on failure | Recording can fail when the XML changes; generated XPath often needs manual edits |
| AutoX.js / AutoJs6 | Layout analysis: select a node, generate code | JavaScript selector code (`id(...)`, `text(...)`, etc.) | Run script; record/replay | IDs missing or change between app versions; generated code is code |
| Appium Inspector | Element tree, screenshot tap, locator search | Appium locator (accessibility id, XPath, etc.) | Tap/swipe/send keys; recorder emits client code | Expert tool; many locator strategies; no product-level rule/scope model |
| Apple Accessibility Inspector | Target an element (Option-Space), tap the element | Accessibility element/properties, not a reusable selector | Green highlight; Basic/Actions/Element/Hierarchy; audits | Developer-facing; no rule generation or persistence |
| Browser DevTools / Playwright | Element picker; hover highlight; click to select; Playwright "Pick locator" | CSS/XPath/role locator; codegen | Live page; locator resolves or fails | DOM changes break selectors; generated locators need editing |
| SUGILITE | Demonstrate the task; system records taps and text | Script of operations with feature heuristics | Confirmation popup; disambiguation; error popup with repair | WebView/browser actions unsupported; unlabeled icons fall back to coordinates |
| iOS Shortcuts / App Intents | Choose an app-declared action | Intent + parameters | Run shortcut | No general screen-element picker; only what the app declares |

### 3.2 Android automation apps

**MacroDroid** markets itself as "No programming knowledge required. Simply select triggers, actions, and constraints" ([macrodroid.com](https://macrodroid.com/)). Its UI Interaction action offers "Identify in app": the user navigates to the target screen and taps the control. A forum answer explains the common failure: "It's quite normal for controls to not have id's ... It does pick up the text and location so will try and 'find' these" ([MacroDroid forum](https://www.tapatalk.com/groups/macrodroid/cannot-get-ui-interaction-to-work-with-identify-in-t6788-s10.html)). A Reddit report shows the identification result with `id: null`, leaving text and coordinates as the only options ([r/macrodroid](https://www.reddit.com/r/macrodroid/comments/pykrul/click_ui_interaction_doesnt_work/)). The lesson: a picker that silently degrades from ID to coordinates is dangerous; the user needs to see which anchor was chosen and how stable it is.

**Tasker AutoInput** exposes UI Query and UI Action, with text, ID, and coordinate targeting. Its Actions v2 helper is a guided, step-by-step builder: it opens the app, asks where to click, performs the click so the user can confirm it, and then returns to Tasker ([AutoInput Actions v2](https://joaoapps.com/autoinput-actions-v2-single-action-total-ui-automation/)). It also has a non-obvious multi-state text syntax: multiple values separated by `=:=` ([AutoInput FAQ](https://joaoapps.com/autoinput/faq/)). A Tasker group thread notes that when a task hangs, "it means AutoInput cannot find what you wanted" ([Tasker group](https://groups.google.com/g/tasker/c/Bf6eONAHxr0)). The lesson: guided action setup plus an explicit "target not found" state is better than a silent hang, and advanced matching syntax needs a UI.

**Automate** uses an Interact block with XPath, plus a "Record interactions" button that locates an element and generates the XPath. Its docs are unusually candid: if recording fails, "it's likely due to the XML document changing again before the XPath expression can be generated"; the workaround is to use the search picker instead; and "the generated XPath expressions may have to be manually modified to work since it's difficult automatically predetermine how the UI will change" ([Automate Interact docs](https://llamalab.com/automate/doc/block/interact.html)). Users report the same: recording may not generate a click because the UI hides the button before Automate can record it ([r/AutomateUser](https://www.reddit.com/r/AutomateUser/comments/p1721o/problem_interacting_with_app/)). The lesson: recording is a draft generator, not a guarantee; the repair path is part of the product.

**AutoX.js / AutoJs6** provide a layout-analysis tool that shows the view hierarchy and can generate code for a selected node. The AutoX docs warn that IDs are convenient but not always stable: for WeChat and NetEase Cloud Music, "每次更新他的控件id都会变化", so the same code is not compatible across versions ([AutoX docs](https://v7.autoxjs.dayudada.com/docs/rhino/base/widgetsBasedAutomation/)). AutoJs6 adds layout analysis, a selector API, recording, and replay ([AutoJs6](https://github.com/SuperMonster003/AutoJs6)). The lesson: generated selectors should prefer stable, user-facing attributes, and the UI should warn when the chosen anchor is version-fragile.

### 3.3 Developer inspectors

**Appium Inspector** is a GUI assistant for Appium sessions. It shows the app screenshot and page source, lets the user select elements directly through the screenshot, search by supported locator strategies, view suggested locators, interact (tap, swipe, send keys), and record actions into client code ([overview](https://appium.github.io/appium-inspector/latest/overview/), [README](https://github.com/appium/appium-inspector)). It is powerful but explicitly a developer tool: it produces locators and code, not a scoped automation rule. The lesson: screenshot selection and suggested locators are good; exposing five locator strategies and code output is not a consumer flow.

**Apple Accessibility Inspector** is the cleanest point-to-inspect reference. The user chooses "Target an element" or presses Option-Space to toggle inspection mode, then taps the element; the app highlights it with a green rectangle. The inspector shows Basic, Actions, Element, and Hierarchy sections ([Apple docs](https://developer.apple.com/documentation/accessibility/inspecting-the-accessibility-of-screens)). It is for developers and does not generate reusable rules, but the interaction grammar is exactly what a phone-based picker should feel like: enter pick mode, tap the thing, see it highlighted, see its details.

**Browser DevTools and Playwright** provide the familiar element picker: hover highlights, click selects, and the element is revealed in the DOM tree ([Firefox docs](https://firefox-source-docs.mozilla.org/devtools-user/page_inspector/how_to/select_an_element/index.html), [Chrome docs](https://developer.chrome.com/docs/devtools/dom)). Playwright adds "Pick locator" to copy a locator into a test, and its guidance is to prioritize user-facing attributes, use the code generator, and then edit the generated locator ([Playwright pick locator](https://playwright.dev/docs/debug#pick-locator), [Playwright locators](https://playwright.dev/docs/locators)). The lesson: generated locators are drafts; the tool expects the user to refine them. A consumer flow should make refinement visual rather than textual.

### 3.4 Programming by demonstration

SUGILITE is the strongest academic prior art for teaching an agent by demonstration. It records taps and text entry, then uses heuristics to identify the target: first text or accessibility label if unique; then a second-level feature such as view ID or child label; otherwise screen coordinates. It shows a confirmation popup for each recorded action, and if the feature set cannot uniquely identify the target, it shows a disambiguation panel for the user to choose the identifying features. When execution fails, it offers "keep waiting", "end executing", or "fix the script", with "replace" and "create a fork" sub-options ([SUGILITE paper](https://toby.li/files/TobyLi-CHI2017-Sugilite.pdf), DOI [10.1145/3025453.3025483](https://doi.org/10.1145/3025453.3025483)).

The study is worth citing for expectations: 65 of 76 scripts (85.5%) created by participants completed the tasks; the break-even point for demonstration versus direct use was 3 to 6 repetitions; the study included 19 participants across a range of programming experience, including three with none. The paper also documents hard limits: because of the Android accessibility mechanism, SUGILITE "cannot record actions performed inside of browsers or in web-based applications", and unlabeled icons can only be identified by screen coordinates, which are "unreliable and not consistent across devices" ([SUGILITE paper](https://toby.li/files/TobyLi-CHI2017-Sugilite.pdf)).

For GKD, SUGILITE validates four things: demonstration is viable for non-programmers; confirmation and disambiguation are not optional; a repair dialog is part of the interaction; and some surfaces simply cannot be automated reliably.

### 3.5 Declared-intent systems

iOS Shortcuts/App Intents take the opposite approach. Apps declare actions and entities; the Shortcuts app lets people configure workflows that include those actions ([App Intents](https://developer.apple.com/documentation/appintents)). There is no general "pick any control in any app" authoring flow. This is safer and more stable, but far less powerful. GKD cannot rely on app-declared intents because its purpose is to automate apps that do not expose them. The contrast is still useful: a constrained action vocabulary (tap, long-press, back, wait) is easier to explain than a general programming model, and most ad-skipping rules need only a few actions.

Android's Select to Speak is a useful point-to-inspect reference, not an authoring tool: the user taps an item on screen and hears it read or described aloud ([Google support](https://support.google.com/accessibility/android/answer/7349565?hl=en)). It normalizes "tap the thing itself" and gives immediate feedback, which is the interaction primitive a phone-based picker should use.

### 3.6 Cross-tool failure modes

Across tools, the same failure modes recur:

- **The anchor is missing.** Controls have no ID or text; the tool silently falls back to coordinates (MacroDroid, SUGILITE, GKD issues #662 and #1400).
- **The anchor is unstable.** IDs change between app versions (AutoX), the DOM/XML changes before recording (Automate), or the screen is a WebView/Compose/Flutter surface with incomplete accessibility (GKD discussion #526).
- **The target is ambiguous.** Several nodes overlap or share text; the tool either picks one silently or asks the user (SUGILITE, GKD inspector overlap panel).
- **The expression is unreadable.** The tool generates XPath/code/JSON5, and the user cannot tell what it will do (Automate, AutoX, GKD).
- **Failure is silent.** AutoInput hangs; GKD rules don't fire; the user has no "why" (Tasker group, GKD issues #643/#824).
- **The test is not the runtime.** Snapshot/recording succeeds but live execution fails (GKD #571, #1000; Automate recording).
- **The action is unsafe.** A decoy close button or a destructive control gets tapped because the selector was too broad (GKD #1205).

---

## 4. Patterns worth borrowing

### 4.1 Screenshot-first pick with overlap disambiguation

**What:** Make the screenshot the primary canvas. Tap a point; highlight the smallest node whose bounds contain it; if several candidates remain, show a small chooser with class, text, ID, and bounds.

**Evidence:** GKD inspector's `findNodesByXy` filters ancestors/containers, sorts by smallest area, and opens an overlap panel when there is more than one candidate ([snapshot.ts](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/snapshot.ts)). SUGILITE uses a disambiguation panel when its heuristics cannot uniquely identify the target ([SUGILITE](https://toby.li/files/TobyLi-CHI2017-Sugilite.pdf)). Apple Accessibility Inspector highlights the selected element in green ([Apple](https://developer.apple.com/documentation/accessibility/inspecting-the-accessibility-of-screens)).

**Application:** On a phone, the screenshot can be the snapshot the user just captured or a live screen capture. Pinch-zoom and pan are essential. The chooser should use plain labels ("the close icon in the top-right", "the whole dialog") rather than class names alone.

**Risk:** Screenshot picking selects a point, not an intent. A user may tap the ad image instead of its close button. Always show the selected node's bounds and a short label, and let the user correct it.

### 4.2 Attribute chips, not syntax

**What:** Present target properties as chips or rows: text contains "跳过", clickable is true, class is TextView, view ID is ... . Combine them with AND by default. Show the generated selector as a secondary, read-only line.

**Evidence:** The inspector's attribute card turns each attribute into a copyable `name=value` row and marks quick-find-capable attributes ([AttrCard.vue](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/AttrCard.vue)). GKD's own operator set has 14 attribute operators and 6 relation operators, plus arithmetic ([selector docs](https://gkd.li/guide/selector)); that is far too much for a first-run flow. The `!*=` typo in issue #1182 is exactly the kind of error chips prevent.

**Application:** Start with a small set: exact text, text contains, view ID, class, clickable, visible. Add "near a node" and "inside a node" as relation chips with a second pick. Keep OR, negation, regex, tuple relations, and structural functions behind "Advanced".

**Risk:** Chips can hide expressive power. Always provide a read-only generated selector and an "Edit as text" escape hatch, with a warning that text edits may be overwritten.

### 4.3 Plain-language summary of the generated rule

**What:** Show a sentence such as: "In this app, when a clickable text containing '跳过' (shorter than 10 characters) is visible, tap it once within 10 seconds."

**Evidence:** No tool in this review generates a plain-language summary of a selector's semantics. GKD's inspector shows selector tokens and a path graph, but not a sentence ([SelectorTrackCard.vue](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/entities/selector/ui/SelectorTrackCard.vue)). The docs explain semantics in prose that could be generated ([selector docs](https://gkd.li/guide/selector)). SUGILITE's confirmation popup is a step in this direction, but it confirms actions, not rule semantics ([SUGILITE](https://toby.li/files/TobyLi-CHI2017-Sugilite.pdf)).

**Application:** Generate the summary from the structured model, not from the raw string. Put it above the selector preview. Use it as the primary confirmation surface before saving. Localize it; GKD's docs and UI strings are Chinese, and the summary must read naturally.

**Risk:** Natural-language summaries can be vague or wrong. Pair the sentence with a highlighted match preview so the user can verify both.

### 4.4 Live match count and highlight

**What:** After every condition change, show "1 node matches" or "3 nodes match" and highlight all matches on the screenshot. If the count is zero, say so immediately.

**Evidence:** The inspector reports "选择到 N 个节点" on search and highlights the focused node ([SearchCard.vue](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/SearchCard.vue), [snapshot.ts](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/snapshot.ts)). Appium Inspector and browser DevTools provide equivalent selection feedback ([Appium](https://appium.github.io/appium-inspector/latest/overview/), [Firefox](https://firefox-source-docs.mozilla.org/devtools-user/page_inspector/how_to/select_an_element/index.html)). Multiple matches are a warning sign, not a success: a rule that matches three "close" nodes may click a decoy (GKD #1205).

**Application:** Show all matches, with the "primary" match emphasized. If there are multiple, ask the user to add context ("only the one inside this dialog") before saving. Do not silently take the first match.

**Risk:** Highlighting on a static snapshot still does not prove runtime behavior. Label it "on this screen" and offer a live test when possible.

### 4.5 "Why not matched" diagnostics

**What:** When zero nodes match, explain which condition failed and why: wrong app/activity, node absent, text differs, node not clickable, `fastQuery` unsupported, or the screen changed.

**Evidence:** GKD's inspector has static diagnostics with matched/rejected counts and caveats ([RuleCard.vue](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/RuleCard.vue)). The issue tracker shows that "valid selector, no trigger" is a recurring, high-frustration failure ([#643](https://github.com/gkd-kit/gkd/issues/643), [#824](https://github.com/gkd-kit/gkd/issues/824), [#1000](https://github.com/gkd-kit/gkd/issues/1000)). Tasker AutoInput simply hangs when the target is missing ([Tasker group](https://groups.google.com/g/tasker/c/Bf6eONAHxr0)); GKD can do better.

**Application:** Make the diagnostic a first-class screen, not a toast. Show the condition rows with pass/fail icons, the current app/activity, and the node that was expected. Offer concrete fixes: "remove the class condition", "search the whole app instead of this activity", "re-capture the screen", "this screen exposes no accessibility nodes".

**Risk:** Diagnostics can overwhelm. Show one primary cause and one suggested fix, with details expandable.

### 4.6 Recipes with preconfigured scope and limits

**What:** Offer a small gallery of task recipes: skip splash ad, close popup, dismiss update prompt, deny/allow permission, close video ad, dismiss rating prompt. Each recipe preconfigures scope, action, and limits, then asks the user to pick the target.

**Evidence:** GKD's docs already contain the splash-ad rule with `matchTime: 10000`, `actionMaximum: 1`, and `resetMatch: 'app'` ([subscription docs](https://gkd.li/guide/subscription)). The inspector's "包含执行限制" toggle inserts exactly those values ([rule_composer.ts](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/rule_composer.ts)). MacroDroid's trigger/action/constraint model shows the value of a constrained vocabulary ([macrodroid.com](https://macrodroid.com/)). These are proven defaults, not guesses.

**Application:** Start the flow with "What do you want to do?" and a recipe grid. The recipe defines the shape; the picker defines the target. A "blank rule" option remains for experts. Recipes should be editable after creation, not one-shot wizards.

**Risk:** Recipes can become stale as apps change. Version them, show a last-validated date, and let users repair rather than recreate.

### 4.7 Scope and limits as guided choices, not fields

**What:** Ask "Where should this run?" with choices: this app (recommended), this screen only, or all apps (advanced). Ask "How often?" with plain choices: once per app launch (recommended), every time it appears, or at most once every N seconds. Generate `activityIds`, `matchTime`, `actionMaximum`, and `resetMatch`.

**Evidence:** GKD's rule model has many interacting scope/timing fields: `activityIds`, `matchTime`, `actionMaximum`, `resetMatch`, `matchRoot`, `matchDelay`, `priorityTime`, `order`, `preKeys` ([subscription docs](https://gkd.li/guide/subscription); [issue #824](https://github.com/gkd-kit/gkd/issues/824)). The inspector exposes some as toggles but still asks for group keys and `preKeys` ([RuleComposerDialog.vue](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/RuleComposerDialog.vue)). Users report rules that never fire because the activity or timing is wrong ([#643](https://github.com/gkd-kit/gkd/issues/643), [#824](https://github.com/gkd-kit/gkd/issues/824)).

**Application:** Default to the app and activity detected in the snapshot. Make "all apps" an explicit, warned choice. Default limits should match the splash recipe (10s window, one action, reset per app) but be explained as "try once when the app opens". Put raw fields behind "Advanced".

**Risk:** Auto-detected activity may be a transient or wrong activity. Let the user see and change it; do not hide it entirely.

### 4.8 Test run with action preview and confirmation

**What:** Before saving, show exactly what will be tapped (marker on the screenshot), let the user run it once, and ask for confirmation if the target is risky (payment, delete, send) or if multiple candidates match.

**Evidence:** The inspector can generate a `position` with a red marker and three reference modes ([RuleComposerDialog.vue](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/RuleComposerDialog.vue)). Issue #1321 asks for an on-screen coordinate indicator because a one-pixel tap point is hard to see ([issue #1321](https://github.com/gkd-kit/gkd/issues/1321)). SUGILITE confirms each recorded action and asks before executing ([SUGILITE](https://toby.li/files/TobyLi-CHI2017-Sugilite.pdf)). Decoy close buttons are a documented real-world hazard ([discussion #1205](https://github.com/orgs/gkd-kit/discussions/1205)).

**Application:** A "Test" button that highlights and optionally performs the action once, with a confirmation sheet: "This will tap '关闭' in 得间免费小说. Continue?" For first-time rules, default to preview-only, not auto-run. For dangerous actions, require an explicit confirmation. If the rule uses screen coordinates (`x/y`), warn that split-screen or small-window layouts can put the point outside the app window ([Position API](https://gkd.li/api/type-aliases/Position)).

**Risk:** A live test can change app state or trigger an ad. Warn, make it opt-in, and provide a "just highlight" mode.

### 4.9 Repair loop with history and fork

**What:** When a saved rule stops matching, offer "Repair": capture the current screen, show the old target versus the new candidates, and let the user update the selector, add context, or fork a new version. Keep the old version.

**Evidence:** SUGILITE's error dialog offers keep waiting, end, or fix, with replace/fork ([SUGILITE](https://toby.li/files/TobyLi-CHI2017-Sugilite.pdf)). Automate explicitly expects generated XPath to need manual modification ([Automate docs](https://llamalab.com/automate/doc/block/interact.html)). GKD rules depend on the app's UI structure, and no-trigger reports are recurring ([#643](https://github.com/gkd-kit/gkd/issues/643), [#824](https://github.com/gkd-kit/gkd/issues/824)); app updates are a known reason selectors stop matching.

**Application:** Store a snapshot link or a small reference image with each rule. When the rule fails, the repair screen can show "this is what it used to look like" and "this is now". Do not silently overwrite; fork by default and let the user compare.

**Risk:** History and snapshots consume storage and raise privacy concerns. Keep local, cap retention, and let users delete.

### 4.10 Selector library and reuse

**What:** Save named, scoped selectors ("close button in the top-right", "skip text") that can be reused across rules. Show scope, last used, and a short description.

**Evidence:** The inspector's selector library stores `name`, `selector`, `description`, `tags`, `pinned`, `useCount`, and global/app/activity scope, with validation and ranking ([library.ts](https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/entities/selector-library/library.ts)). GKD users often write the same patterns repeatedly (skip text, close icon); a library reduces recall burden.

**Application:** Start with a built-in library of generic selectors (skip text, close content description, common dialog buttons) plus user-saved ones. Scope them correctly: a selector saved for one app should not silently appear in another.

**Risk:** A library of opaque selectors can become a junk drawer. Require a human-readable name and show the plain-language summary.

### 4.11 Output destination and depth clarity

**What:** Ask "Where should this rule go?" before generating output: this app's rules, global rules, or export/share. Show the destination path and a preview of the resulting structure. Do not ask the user to choose between "rule", "group", "app", and "TypeScript" unless they are exporting.

**Evidence:** The inspector's output depth selector is necessary for its copy-paste workflow, but it creates a known error: a user pasted a group into a single-rule editor and got "非法规则: matches 和 anyMatches 至少存在一个" ([discussion #751](https://github.com/orgs/gkd-kit/discussions/751)). The app's editor accepts a group or app but not arbitrary fragments ([UpsertRuleGroupViewModel.kt](D:/Projects/gkd/gkd-app/src/commonMain/kotlin/li/gkd/app/ui/subscription/UpsertRuleGroupViewModel.kt)).

**Application:** On-device, the destination is almost always "a group in my local subscription". Hide the structural choice. For export, offer "copy JSON5" and "share subscription" as advanced actions with a preview.

**Risk:** Power users may want a single rule or a TypeScript module. Keep an advanced export sheet, but do not make it the default mental model.

### 4.12 Safety: decoys, dangerous actions, and first-run confirmation

**What:** Detect and warn about ambiguous targets, decoy-like labels, and destructive actions. Require confirmation for the first execution, for global scope, and for actions other than a simple tap.

**Evidence:** GKD discussion #1205 documents a decoy close button (`close_2`) versus the genuine `_2` control; a naive text/id match clicks the decoy ([discussion #1205](https://github.com/orgs/gkd-kit/discussions/1205)). Issue #1384 shows a user searching for "关闭" and selecting the mini-program's close control instead of the ad's close button ([discussion #1384](https://github.com/orgs/gkd-kit/discussions/1384)). SUGILITE confirms each recorded action before executing it ([SUGILITE](https://toby.li/files/TobyLi-CHI2017-Sugilite.pdf)).

**Application:** When multiple matches share a close-like label, show all of them and ask. When the selected node's text or ID suggests payment/delete/send, show a stronger warning. For global rules, show the app list they will run in and require an explicit opt-in. Never auto-run a newly created rule without a preview.

**Risk:** Too many confirmations cause fatigue. Reserve them for genuine ambiguity and risk; make the common case (one match, app-scoped, simple tap) fast.

---

## 5. GKD user-reported friction, ranked

The ranking is based on recurrence and severity in the tracker, plus the design cost of leaving each unaddressed. It is qualitative, not a frequency count.

| Rank | Friction | Representative evidence | Design implication |
|---|---|---|---|
| 1 | Selector syntax, operators, and escaping | `!*=` typo (#1182), dropped backslashes (#603), `Unexpected Char：酷` (#931), docs' 14 operators and mandatory spaces | Do not make syntax the interface. Use chips, auto-escaping, and token-level validation. |
| 2 | Rule level, scope, and output structure | Group pasted into rule editor (#751), many scope/timing fields (#824), rules that never fire (#643) | Hide rule/group/app depth; ask "where and when" in product language; generate fields. |
| 3 | Snapshot-versus-runtime mismatch and missing feedback | Inspector works but execution fails (#571), query fails with `vid` + bounds (#1000), rule not triggered (#643, #824) | Provide a live test on the current screen, a clear snapshot-only caveat, and a "why not matched" view. |
| 4 | Missing or incomplete accessibility information | Can't capture controls (#662), "该应用无无障碍信息" (#1400), WebView/Compose/Flutter limitations (#526) | Detect unsupported surfaces early; explain the limitation; offer a position-based fallback only if an anchor node exists, otherwise stop with an honest explanation. |
| 5 | Position semantics and coordinate fragility | `position.left/top` are node-relative, not screen-relative (#951); click outside popup (#929); expert-only selector + relative position (#1384); request for coordinate indicator (#1321) | Visual point picker with an explicit reference frame, marker preview, and node-relative coordinates by default; warn that `x/y` in split screen can land outside the app window. |
| 6 | False matches, decoys, and multiple candidates | Honeypot `close_2` vs `_2` (#1205); "关闭" selects the wrong close control (#1384); inspector overlap panel | Show all matches; require disambiguation; prefer context; warn on close-like duplicates. |
| 7 | Raw JSON5 editor and format confusion | Full-screen JSON5 editor ([UpsertRuleGroupPage.kt](D:/Projects/gkd/gkd-app/src/commonMain/kotlin/li/gkd/app/ui/subscription/UpsertRuleGroupPage.kt)); Chinese parse error (#931); group/rule error (#751) | Structured form first; JSON5 as read-only preview/advanced; import with validation and repair. |
| 8 | Desktop dependency and expertise barrier | No on-device picker (#708); community mobile helper (#1114); paid custom-automation request 200-500 RMB (#1419); planned AI assistant (#1484) | Build the on-device loop; consider optional AI assistance, but require verification. |

### 5.1 What the ranking means

**Syntax is the visible problem; structure and runtime are the costly ones.** A user can eventually fix a typo. A user cannot easily discover that the rule was saved at the wrong level, scoped to the wrong activity, or evaluated at the wrong time. The on-device flow should optimize for getting the structure right, not just for writing a prettier selector.

**Snapshot success is not runtime success.** The inspector's own caveat is correct: a single snapshot cannot validate timing. The on-device flow has an advantage the web inspector does not: it can test against the live screen, in the same session, with the same accessibility service. That should be the core differentiator, not an afterthought.

**Missing accessibility nodes are a hard boundary.** No amount of UI polish can create a node that the app does not expose. The flow needs an honest dead end: "This screen does not expose enough accessibility information. You can try a position-based rule if there is an anchor node, but it may break." The alternative, silently falling back to coordinates, is what makes tools feel unreliable.

**The demand for on-device authoring is documented.** The maintainer closed the 2024 picker request with "没有" and suggested building a mobile web page for snapshot parsing ([issue #708](https://github.com/gkd-kit/gkd/issues/708)). A community member later shared a mobile rule-authoring helper ([discussion #1114](https://github.com/orgs/gkd-kit/discussions/1114)), users offer 200-500 RMB for custom automation ([issue #1419](https://github.com/gkd-kit/gkd/issues/1419)), and the maintainer opened a plan for an AI assistant that uses snapshots, screenshot box selection, and natural language to generate or modify rules ([issue #1484](https://github.com/gkd-kit/gkd/issues/1484), opened 2026-10-14). The direction is clear even if the implementation is not.

---

## 6. Implications for the on-device flow

### 6.1 A candidate flow skeleton

This is a research-informed skeleton, not a locked design.

1. **Start from intent.** "Create a rule for this screen" (from the current app/snapshot) or "Start from a recipe" (skip splash, close popup, dismiss dialog). Show the destination: local subscription, current app.
2. **Pick the target.** Show the screenshot. Tap the target. Highlight the smallest node. If several candidates overlap, ask which one with plain labels and bounds. Offer a node-tree view as "Advanced".
3. **Review the condition.** Show 2-5 plain-language chips ("text contains 跳过", "clickable", "visible"). Add context if there are multiple matches ("inside this dialog", "near this text"). Show the generated selector read-only.
4. **Test.** Run against the current screen. Show match count and highlight every match. If zero, show the "why" panel. If multiple, require disambiguation. Preview the action with a marker.
5. **Set scope.** Default to the current app and activity. Offer "this screen only" and "all apps" with clear warnings. Default limits: try once when the app opens, within 10 seconds.
6. **Set behavior.** Default action "tap". Offer long-press, back, and none. For risky targets, require confirmation.
7. **Name and save.** Suggest a name from the recipe and target ("Skip splash ad in 知乎"). Save to the local subscription as a group; show the exact path ("首页-订阅-本地订阅-知乎-开屏广告"). Show a success state with "Test again" and "View rule".
8. **Advanced.** Raw selector, JSON5 preview, export/copy, selector library, position mode. Keep these out of the happy path.

### 6.2 Decisions the spec must lock

| Decision | Options | Recommended default | Rationale |
|---|---|---|---|
| Primary entry point | Current screen / snapshot gallery / rule list / recipe gallery | "Create rule for this screen" plus recipe gallery | Matches the user's context; avoids asking them to find a snapshot first |
| Primary canvas | Screenshot / node tree / split view | Screenshot-first, tree secondary | Recognition over recall; node tree is precise but technical |
| Target abstraction | Raw selector / attribute chips / natural language | Chips with a read-only generated selector | Keeps the common case simple without losing power |
| Test surface | Snapshot only / live current screen / both | Live current screen with snapshot fallback | The app can query the live accessibility tree; this is the key advantage over the web inspector |
| Scope default | App / activity / global | App + detected activity | Safer; matches the docs' app-group pattern |
| Execution limits | Expose fields / fixed recipe defaults / guided choices | Guided choices with recipe defaults | Prevents both "never fires" and "fires forever" |
| Action safety | Auto-run / preview only / confirm | Preview by default; confirm for first run, global scope, and risky targets | Avoids decoys and destructive actions without constant friction |
| Persistence | Auto-save / explicit save / export only | Explicit save to local subscription, with destination preview | The web inspector's copy-only gap is a major friction; on-device should own persistence |
| Rule level | Ask rule/group/app/TS / hide it | Hide it; save a group in the local subscription | #751 shows the cost of exposing structural levels |
| Raw editor | Always available / advanced only / hidden | Advanced only, with a warning | JSON5 is the implementation format, not the user's mental model |
| Templates | None / small curated set / large gallery | Small curated set at launch, expand from usage | Recipes carry scope and limits that users otherwise miss |
| AI assist | Core / optional / none | Optional, always verified by a match preview | The maintainer plans AI assistance (#1484), but it cannot replace target verification |

### 6.3 Feasibility checks before locking the spec

- **Live node query and action execution.** The app already exposes `execSelector` over HTTP and has the accessibility service. Confirm that the same query/action path can be invoked from an in-app screen, against the foreground app, without routing through the HTTP server.
- **Self-targeting.** When GKD's authoring UI is in the foreground, the accessibility service may see GKD's own window instead of the target app. The flow needs a way to capture/query the target screen while GKD is not covering it, or an overlay picker that can inspect the app underneath.
- **Overlay permission vs in-app snapshot.** An overlay picker is the most direct interaction, but it adds a permission and may conflict with the app's distribution constraints. An in-app screenshot picker reuses the existing snapshot capture and has no extra permission; it is the safer default. Validate both against the product's distribution channels.
- **Transient screens.** Splash ads and popups disappear quickly. A picker that requires navigating into GKD, capturing, and returning may miss the screen. The existing floating-button/quick-tile capture is the likely bridge; confirm the flow.
- **WebView/Compose/Flutter.** The accessibility tree may be missing or shallow. Detect this and show a clear limitation state; do not silently switch to coordinates ([discussion #526](https://github.com/orgs/gkd-kit/discussions/526)).
- **Large trees.** A snapshot with thousands of nodes can make a phone UI sluggish. Virtualize the tree, cap rendering, and rely on the screenshot picker for the common path.
- **Privacy.** Snapshot sharing uploads to GitHub and is public by default ([snapshot docs](https://gkd.li/guide/snapshot)). On-device rules should stay local unless the user explicitly exports; redaction must be available before any share.
- **Authoring UI accessibility.** The picker itself must be usable with large text, screen readers, and one hand. Test the chips, the highlight overlays, and the confirmation sheets.

### 6.4 Success measures

- Time from opening the flow to a saved, enabled rule.
- Percentage of first rules that match exactly one node on the live screen.
- Percentage of saved rules that still match after 7 days / after an app update.
- Drop-off at each step: pick, condition, test, scope, save.
- Repair success rate after a "no match" state.
- Raw JSON5 editor usage (should be low; if high, the structured path is failing).
- Rate of rules saved at the wrong scope/level (measured by user corrections).
- Number of unsafe-action confirmations and user cancellations.

---

## 7. Open questions

1. Can the live selector query and action run entirely in-app, or does it need the HTTP server as an intermediary?
2. Does the accessibility service see the target app reliably while GKD's authoring UI is visible, or is an overlay/snapshot detour required?
3. Which rule fields can be safely auto-derived from the snapshot (app, activity, class, text, ID), and which need user input?
4. What is the smallest recipe set that covers most first rules? The docs suggest splash skip, popup close, and permission dialogs; validate against action logs.
5. How should the flow handle a screen with no usable accessibility nodes? Hard stop, position fallback, or "not now"?
6. Should the on-device editor expose `fastQuery` at all, given that it can change result order and does not work in WebView/Compose/Flutter?
7. Is a selector library worth shipping in v1, or does it add a junk-drawer problem before there is enough usage?
8. How does the planned AI assistant (#1484) fit? Is it a generator of draft selectors, a repair assistant, or a natural-language front end? The UX must not depend on it.
9. What does "saved successfully" mean to users? A toast, a rule list entry, a test result, or all three?
10. How should GKD explain the difference between "works in this snapshot" and "works when the app runs"? The inspector's caveat is accurate but buried.

---

## Sources

### GKD official

- GKD web inspector: https://i.gkd.li
- Inspector repository and feature list: https://github.com/gkd-kit/inspect
- Inspector README: https://github.com/gkd-kit/inspect/blob/main/README.md
- Rule composer dialog: https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/RuleComposerDialog.vue
- Rule composer model: https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/rule_composer.ts
- Search card: https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/SearchCard.vue
- Live selector syntax input: https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/entities/selector/ui/SelectorSyntaxInput.vue
- Snapshot interaction: https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/snapshot.ts
- Node labels and generated selectors: https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/entities/snapshot/node.ts
- Attribute card: https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/AttrCard.vue
- Rule diagnostics: https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/RuleCard.vue
- fastQuery diagnostics: https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/selector_diagnostics.ts
- Selector path view: https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/entities/selector/ui/SelectorTrackCard.vue
- Selector library: https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/entities/selector-library/library.ts
- Device control: https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/device-control/DeviceControlView.vue
- Share-link codec: https://github.com/gkd-kit/inspect/blob/main/gkd-inspect/src/features/snapshot-inspector/snapshot_url_codec.ts

### GKD docs

- Selector: https://gkd.li/guide/selector
- Selector examples: https://gkd.li/guide/example
- Snapshot: https://gkd.li/guide/snapshot
- Subscription and local rules: https://gkd.li/guide/subscription
- Optimization and fastQuery: https://gkd.li/guide/optimize
- Node API: https://gkd.li/guide/node
- Position API: https://gkd.li/api/type-aliases/Position
- FAQ: https://gkd.li/guide/faq
- Docs repository: https://github.com/gkd-kit/docs

### GKD local source

- Rule group editor: `D:/Projects/gkd/gkd-app/src/commonMain/kotlin/li/gkd/app/ui/subscription/UpsertRuleGroupPage.kt`
- Rule group save/view model: `D:/Projects/gkd/gkd-app/src/commonMain/kotlin/li/gkd/app/ui/subscription/UpsertRuleGroupViewModel.kt`
- Local inspection server: `D:/Projects/gkd/gkd-app/src/commonMain/kotlin/li/gkd/app/network/inspection/InspectionServer.kt`
- Subscription repository: `D:/Projects/gkd/gkd-app/src/commonMain/kotlin/li/gkd/app/subscription/SubscriptionRepository.kt`
- On-device snapshot preview: `D:/Projects/gkd/gkd-app/src/commonMain/kotlin/li/gkd/app/ui/snapshot/SnapshotPreviewPage.kt`
- Extra accessibility service: `D:/Projects/gkd/gkd-app/src/androidMain/kotlin/com/google/android/accessibility/selecttospeak/SelectToSpeakService.kt`
- Accessibility service wiring: `D:/Projects/gkd/gkd-app/src/androidMain/kotlin/li/gkd/app/service/A11yService.kt`

### GKD issues and discussions

- #708, request for a phone-side rule picker: https://github.com/gkd-kit/gkd/issues/708
- #1484, planned AI rule assistant: https://github.com/gkd-kit/gkd/issues/1484
- #1321, coordinate indicator request: https://github.com/gkd-kit/gkd/issues/1321
- #1182, valid-looking selector fails due to operator typo: https://github.com/gkd-kit/gkd/issues/1182
- #571, snapshot query succeeds but execution fails: https://github.com/gkd-kit/gkd/issues/571
- #662, unable to capture controls: https://github.com/gkd-kit/gkd/issues/662
- #1400, app reports no accessibility information: https://github.com/gkd-kit/gkd/issues/1400
- #603, regex escaping lost on save: https://github.com/gkd-kit/gkd/issues/603
- #931, Chinese rule JSON parse error: https://github.com/gkd-kit/gkd/issues/931
- #643, valid selector does not trigger: https://github.com/gkd-kit/gkd/issues/643
- #824, rule does not trigger: https://github.com/gkd-kit/gkd/issues/824
- #1000, selector with vid and bounds fails: https://github.com/gkd-kit/gkd/issues/1000
- #1419, paid custom automation request: https://github.com/gkd-kit/gkd/issues/1419
- Discussion #1114, community mobile rule-authoring helper: https://github.com/orgs/gkd-kit/discussions/1114
- Discussion #751, rule group pasted into single-rule editor: https://github.com/orgs/gkd-kit/discussions/751
- Discussion #1384, position-based rule and wrong "关闭" target: https://github.com/orgs/gkd-kit/discussions/1384
- Discussion #929, clicking outside a popup: https://github.com/orgs/gkd-kit/discussions/929
- Discussion #526, quickFind limitations in WebView/Compose/Flutter: https://github.com/orgs/gkd-kit/discussions/526
- Discussion #951, position is relative to the node: https://github.com/orgs/gkd-kit/discussions/951
- Discussion #1205, decoy close button: https://github.com/orgs/gkd-kit/discussions/1205

### Other tools and research

- MacroDroid: https://macrodroid.com/
- MacroDroid "Identify in app" forum thread: https://www.tapatalk.com/groups/macrodroid/cannot-get-ui-interaction-to-work-with-identify-in-t6788-s10.html
- MacroDroid Reddit report with `id: null`: https://www.reddit.com/r/macrodroid/comments/pykrul/click_ui_interaction_doesnt_work/
- Tasker AutoInput: https://joaoapps.com/autoinput/
- AutoInput Actions v2 helper: https://joaoapps.com/autoinput-actions-v2-single-action-total-ui-automation/
- AutoInput FAQ: https://joaoapps.com/autoinput/faq/
- Tasker group thread on AutoInput hangs: https://groups.google.com/g/tasker/c/Bf6eONAHxr0
- Automate Interact block: https://llamalab.com/automate/doc/block/interact.html
- Automate user report on recording: https://www.reddit.com/r/AutomateUser/comments/p1721o/problem_interacting_with_app/
- AutoX layout analysis: https://v7.autoxjs.dayudada.com/docs/rhino/base/widgetsBasedAutomation/
- AutoJs6: https://github.com/SuperMonster003/AutoJs6
- Appium Inspector overview: https://appium.github.io/appium-inspector/latest/overview/
- Appium Inspector repository: https://github.com/appium/appium-inspector
- Apple Accessibility Inspector: https://developer.apple.com/documentation/accessibility/inspecting-the-accessibility-of-screens
- Firefox element picker: https://firefox-source-docs.mozilla.org/devtools-user/page_inspector/how_to/select_an_element/index.html
- Chrome DevTools DOM: https://developer.chrome.com/docs/devtools/dom
- Playwright pick locator: https://playwright.dev/docs/debug#pick-locator
- Playwright locators: https://playwright.dev/docs/locators
- SUGILITE paper: https://toby.li/files/TobyLi-CHI2017-Sugilite.pdf
- SUGILITE DOI: https://doi.org/10.1145/3025453.3025483
- Apple App Intents: https://developer.apple.com/documentation/appintents
- Android Select to Speak: https://support.google.com/accessibility/android/answer/7349565?hl=en
