# Prior art: macro recorders and teach-by-demonstration on Android

**Status:** research complete, October 2026. This is research input for the wayfinder map [#11](https://github.com/fktpp2022/gkd/issues/11) ("recording user actions for rule authoring"), not a design decision.

**Scope:** what "record my actions" systems actually capture and promise; where the macro model breaks when the target product is declarative; whether any system turns a demonstration into a declarative trigger-plus-action rule; and what GKD can borrow for a recording entry that substitutes the *pick* step only.

**Method:** primary sources only where possible — vendor documentation, official API references, official source repositories, and first-party product documentation. GKD-side claims were checked against the local `main` source (`gkd-app`, `gkd-selector`). The AutoHotkey community forum and a few Windows support pages could not be fetched programmatically and are marked as thin evidence. No production code was changed.

**Evidence limits:** there is **no quantitative study** of recorder abandonment, breakage-after-update rates, or user acceptance of the demonstration→rule gap. Every claim in section 5.5 is vendor-authored or anecdotal. "Prior art" here means *documented product behaviour*, not measured outcomes. A notable gap: no shipped product was found that offers "record a tap in an arbitrary Android app → emit a declarative accessibility rule"; see section 3.10.

**Relation to existing notes:** this note is about *demonstration* as an authoring entry. The other notes in `docs/research/` cover the surrounding authoring flow — the visual inspector ([`local-rule-authoring-prior-art`](../../blob/research/local-rule-authoring-prior-art/docs/research/local-rule-authoring-prior-art.md)), selector construction ([`local-rule-authoring-selector-strategy`](../../blob/research/selector-construction/docs/research/local-rule-authoring-selector-strategy.md)), and on-device capture feasibility ([`local-rule-authoring-capture-feasibility`](../../blob/research/local-rule-authoring-capture-feasibility/docs/research/local-rule-authoring-capture-feasibility.md)).

---

## TL;DR

1. **Every mature recorder produces a *draft*, and every vendor says so out loud.** Excel: "the resolution is to re-record the entire sequence, or modify the VBA code itself" ([Microsoft](https://int.support.office.com/f1/article/1e55959b-e077-4c88-a696-c3017600db44)). Power Automate Desktop: "most recorded tasks should be modified to achieve optimal results" ([Microsoft](https://learn.microsoft.com/en-us/power-automate/desktop-flows/recording-flow)). Playwright: "manually improve it if needed" ([playwright.dev](https://playwright.dev/docs/codegen)). A recorder that claims to be finished is a lie that users discover on first broken run.
2. **The recording unit is the whole story.** Where the substrate has a stable node identity (UIA, ARIA/CSS, `AccessibilityNodeInfo`), recorders capture *that* and the artifact is selector-based. Where it does not (canvas, games, images), recorders fall back to coordinates or image matching, and the artifact rots immediately. The single most transferable fact: **coordinate recording and selector recording are different products with different failure curves**, and GKD is unambiguously on the selector side.
3. **The macro model breaks in four specific places**, and each has a known repair: literal reproduction of mistakes (repair: edit or re-record), timing (repair: wait-for-state instead of sleep), UI change (repair: semantic selectors over positional ones), and the missing "then" (repair: the trigger/condition split). GKD is immune to the first three by construction and *structurally* exposed to the fourth.
4. **The missing "then" is exactly what MacroDroid documents as a rule, not a bug.** MacroDroid's wiki states that triggers "combine with OR, never AND" and that state conditions must be attached as constraints instead ([Triggers](https://wiki.macrodroid.com/wiki/index.php/Triggers)). Automate merges event triggers and condition checks into one block with a `Proceed` option ([flow docs](https://llamalab.com/automate/doc/flow.html)). GKD's "a rule fires whenever its selector matches" is a *legitimate* position in this design space, not a deficiency to apologise for.
5. **The closest shipped analogue to GKD on Android is MacroDroid's Screen Content trigger + UI Interaction action** — an accessibility-service trigger on text/view-id and an action that clicks by text or view id, not by coordinate ([Screen content](https://macrodroidforum.com/wiki/index.php?title=Trigger:_Screen_content), [UI Interaction](https://macrodroidforum.com/wiki/index.php?title=Action:_UI_Interaction)). Its limits (polling rate, 500 ms debounce, graphics-rendered text unreadable) are GKD's limits too. Crucially, **MacroDroid has no tap recorder at all** — the "closest to a macro recorder" it has is a root-only coordinate replayer.
6. **The strongest prior art for GKD's actual feature is Chrome DevTools Recorder and Power Automate Desktop**, not the desktop macro recorders. Both record a demonstration, produce a **declarative step list keyed by element identity**, allow editing steps *during* recording, and offer a replay that is explicitly a debugging tool. DevTools Recorder's selector-priority algorithm (ARIA → test id → id → CSS → XPath → text) is a directly stealable policy for "which node did you mean".
7. **Borrowable patterns, in order of confidence:** the *Verify* story (test the action half independently of the trigger half — MacroDroid's "Test actions", Tasker's per-step green/red circles), the *nothing captured* state (Appium Inspector distinguishes "recording off", "recording on, nothing captured", and "clear the buffer"), the *"Wait for action"* live cue (Power Automate Desktop) to stop the user clicking twice, and the *edit-during-recording* affordance (Chrome Recorder) to avoid re-recording a single mistake.
8. **The known abandonment patterns are consistent and specific**: the artifact doesn't reproduce → it doesn't reproduce *later* (app updated, layout changed, timing shifted); the artifact is code and therefore hostile; the permission surface is too wide; the target app can opt out of being observable. Each maps to a concrete GKD mitigation.
9. **The "replay" affordance people cite most often is weaker than it looks.** Windows Steps Recorder's famous green **Play** button replays the *screenshots as a slide show* — it does not re-execute your actions ([third-party tutorial](https://helpdeskgeek.com/how-to/how-to-use-steps-recorder-when-troubleshooting-windows/); the official Microsoft article documents no Play button at all). GKD's verify button must be explicit about whether it re-runs the rule or merely shows what would match.

---

## 1. What macro recorders actually capture and promise

### 1.1 The shape of every recorder

A recorder has five moving parts. Reading them across products exposes the design space:

| Part | Options seen in the wild |
|---|---|
| **Unit** | keystroke; mouse click at a coordinate; click at a point on a screenshot; click on a *named element* (text/view-id/class/ARIA/CSS); a whole window state |
| **Artifact** | source code (VBA, `.ahk`, Java/Python/JUnit, Puppeteer); a JSON step list (DevTools Recorder); a flow graph (Power Automate Desktop, Automate); a trigger+action list (MacroDroid, Tasker); a screenshot+text report (Steps Recorder); a raw coordinate script (MacroDroid "Launch and Press") |
| **Start/stop** | a toolbar button (Excel, Chrome Recorder); a header toggle (Appium Inspector); a global hotkey (AHK recorders); a floating window's Stop button (Steps Recorder); a device button or power-button press (MacroDroid) |
| **Post-hoc editing** | edit the source (Excel, AHK); edit individual steps in a list (Chrome Recorder, PAD); pick from dropdowns (MacroDroid, Tasker, Automate); **none** (Steps Recorder — it is a report, not a program) |
| **Repair when wrong** | re-record; hand-edit the artifact; add waits/state checks; switch from coordinates to element identity; abandon the recorder and write it by hand |

The last row is the one GKD should design against. **Every product's documented repair path is "edit the artifact", never "the recorder was right".**

### 1.2 Excel / Word — Record Macro (VBA)

- **Unit:** mouse clicks and keystrokes inside the Office app. Microsoft's wording: "When you create a macro, you are recording your mouse clicks and keystrokes" ([Microsoft](https://int.support.office.com/f1/article/741130ca-080d-49f5-9471-1e5fb3d581a8)).
- **Artifact:** VBA source code — "the macro recorder records all the steps in Visual Basic for Applications (VBA) code" ([Microsoft](https://int.support.office.com/f1/article/1e55959b-e077-4c88-a696-c3017600db44)). Excel for the web has a parallel **Automate → Record Actions** that produces Office Scripts ([Microsoft](https://support.microsoft.com/en-us/topic/8f09cbbf-bb85-413e-9720-d567180f61e4)).
- **Start/stop:** Developer tab (hidden by default) → Code group → **Record Macro** → name + optional shortcut → OK; **Stop Recording**, also offered on the status bar ([Microsoft](https://support.microsoft.com/en-us/topic/8f09cbbf-bb85-413e-9720-d567180f61e4), [Microsoft](https://support.microsoft.com/en-us/Excel/run-a-macro-in-excel)).
- **Edit afterwards:** Developer → Macros → **Edit** opens the recorded code in the VBA editor; Microsoft frames the work as "make minor changes" ([Microsoft](https://int.support.office.com/f1/article/741130ca-080d-49f5-9471-1e5fb3d581a8)).
- **When the recording is wrong — the single most quotable sentence in this note:** "The Macro Recorder captures almost every move you make. So if you make a mistake in your sequence, for example, clicking a button that you did not intend to click, the Macro Recorder will record it. **The resolution is to re-record the entire sequence, or modify the VBA code itself.**" ([Microsoft](https://int.support.office.com/f1/article/1e55959b-e077-4c88-a696-c3017600db44))

Excel is also the counter-case to "capture only what you meant": it over-captures, and the user pays for it.

### 1.3 AutoHotkey and the community recorder ecosystem

- **No first-party recorder.** AHK ships none; all recorders are third-party. The canonical community thread is titled "There's no official Macro Recorder for AH 2, correct?" ([AHK forum](https://www.autohotkey.com/boards/viewtopic.php?t=120403) — **fetch blocked by Cloudflare; title and gist from search snippet, unverified**).
- **De-facto recorder:** Pulover's Macro Creator (PMC). Its own feature list: "**Record your keystrokes and mouse movements and clicks relative to window or screen. Reproduce your macros with automatic window activation. Pause and play them step-by-step.**" … "**Use Control Commands to click and type on background windows.**" … "**Export your macros to working AHK scripts.**" ([macrocreator.com](https://www.macrocreator.com/))
- **Artifact:** plain AHK script text (`.ahk`) in an editor; the community recorder variant opens the file in an editor for direct edit ([AHK2-Macro-Recorder README](https://github.com/ArtyMcLabin/AHK2-Macro-Recorder/blob/master/README.md)).
- **Identity vs. coordinates is a first-class distinction in AHK itself.** `ControlClick` documents two modes: Mode 1 (X/Y relative to the window) and Mode 2 (**Control Identifier** — "the control's ClassNN, text or HWND"), with Mode 2 taking precedence ([AHK docs](https://www.autohotkey.com/docs/v2/lib/ControlClick.htm)). This is the same axis as GKD's selector-vs-`position`.
- **Timing is the escape hatch, and it is a bad one.** `Sleep`'s own docs admit "The actual delay time might wind up being longer than what was requested if the CPU is under load", with delays rounded up to 10–15.6 ms; the whole family of delay knobs (`SetKeyDelay`, `SetMouseDelay`, `SetControlDelay`, `SetWinDelay`) hangs off it ([AHK docs](https://www.autohotkey.com/docs/v2/lib/Sleep.htm)).
- **Community verdict (snippets only — the forum blocks programmatic fetch):** "The recorder is designed to be as accurate as possible but for a more reliable macro you should add the commands and logic manually" ([forum](https://www.autohotkey.com/boards/viewtopic.php?t=99382)); a fix-list thread recommending screen-relative coordinates and bulk-edited 300 ms pauses ([forum](https://www.autohotkey.com/boards/viewtopic.php?f=63&t=116510)); a thread recommending abandoning the recorder for `MouseClickDrag`/`ImageSearch`/`ControlClick`/`WinActivate` ([forum](https://www.autohotkey.com/board/topic/145609-hundreds-of-small-moves-and-pauses-recorded)); and "AutoHotkey — So useful yet SO unreliable", noting that a well-known recorder's author "uses sleeps all over the place to make the macro it records more stable" ([forum](https://www.autohotkey.com/boards/viewtopic.php?p=90385)). **Treat these as leads to re-read manually, not as quotable evidence.**

### 1.4 Windows Steps Recorder (PSR)

- **Correction worth stating: Win+H is Voice Typing, not Steps Recorder.** PSR is launched via `Start → psr`.
- **Unit:** screenshots plus a text description of each click — *not* typed input. "**Recorded steps don't capture anything that's typed during the recording.**" and "Some programs may not be captured accurately or may not provide details that are useful to Microsoft Support" ([Microsoft Learn](https://learn.microsoft.com/hu-hu/troubleshoot/microsoft-365-apps/settings/how-to-use-problem-steps-recorder) — a fetchable locale of the official article; the en-us original redirects).
- **Artifact:** a **`.zip`** — "The file is saved as a compressed file that uses the .zip file name extension" — which opens in a browser as an MHTML report ([Microsoft Learn](https://learn.microsoft.com/hu-hu/troubleshoot/microsoft-365-apps/settings/how-to-use-problem-steps-recorder)).
- **Start/stop:** Start Record → reproduce → Stop Record → Save As. Pause/resume supported; **Add Comment** highlights a screen region and attaches a note. Settings include output location, **Enable screen capture** (with the privacy note "if you take screenshots of a program that contains personal information, such as bank statements"), and a screen-capture count (default **25**, earlier shots overwritten) ([Microsoft Learn](https://learn.microsoft.com/hu-hu/troubleshoot/microsoft-365-apps/settings/how-to-use-problem-steps-recorder)).
- **Editability:** none. The output is a diagnostic report for a support engineer, not a program. The "Steps Recording window will float on your computer screen as it records" ([third-party](https://helpdeskgeek.com/how-to/how-to-use-steps-recorder-when-troubleshooting-windows/)).
- **Deprecation:** banner since the Windows 11 update of February 2024; Microsoft points to Snipping Tool / Xbox Game Bar / Clipchamp ([Microsoft](https://support.microsoft.com/en-us/help/22878)).
- **The "Play" button — important nuance.** The legacy Windows 7/8.1/10 review window had a green **Play** triangle that replayed the recording as a **3-second-per-step slide show of the captured screenshots and text**; it does *not* re-execute your actions. Windows 11 renamed the button "Stop and Review" and the slide show appears to have been dropped ([third-party tutorial](https://helpdeskgeek.com/how-to/how-to-use-steps-recorder-when-troubleshooting-windows/); the official Microsoft article documents no Play button at all). **Design lesson for GKD: even the "replay" affordance people cite for PSR replays *evidence*, not behaviour.**

### 1.5 MacroDroid — the most GKD-like shipped product

- **Model:** Triggers + Actions + Constraints. "Every macro is made of the same three ingredients… Triggers: the event that starts a macro… Actions: the work the macro performs, run in order from top to bottom… Constraints: optional conditions that must be true for the macro to run" ([MacroDroid wiki](https://wiki.macrodroid.com/wiki/index.php/Overview)).
- **Trigger semantics — directly relevant to GKD:** "A macro can have multiple triggers, and any single one firing will invoke it — **triggers combine with OR, never AND**… Combining triggers with AND is not possible because a trigger is an instantaneous event… To achieve 'trigger A while state B', **attach a constraint to the trigger instead**" ([MacroDroid wiki](https://wiki.macrodroid.com/wiki/index.php/Triggers)). This is the trigger/condition split stated as a design principle rather than a compromise.
- **Actions:** 200+, run sequentially; control-flow actions (If clause, Repeat, Iterate, Break, Continue); per-action constraints; **magic text** interpolation of live values into any text field ([MacroDroid wiki](https://wiki.macrodroid.com/wiki/index.php/Actions)).
- **Verify/replay:** "While editing, long press an action to test it immediately, or use **Test actions** from the editor menu to run the whole list without waiting for a trigger" ([MacroDroid wiki](https://wiki.macrodroid.com/wiki/index.php/Actions)); "While editing, you can test individual actions or the whole action list without waiting for the trigger to fire" ([Overview](https://wiki.macrodroid.com/wiki/index.php/Overview)). Note the shape: **the action half is testable without the trigger half.** That is precisely GKD's verification split.
- **No recorder.** Macros are assembled from pickers. There is instead an **AI Macro Builder**: "describe the macro you want in plain language and it will build a draft for you" ([Overview](https://wiki.macrodroid.com/wiki/index.php/Overview)).
- **The GKD-shaped feature:** the **Screen Content trigger** fires when matching text or a matching view id appears on screen, using MacroDroid's "UI Interaction" accessibility service. Supports wildcards, regex, magic text, per-app and activity scoping, with a 500 ms debounce between fires and a configurable "Read Screen Update Rate" (free tier ≥ 2 s). Documented limits: "Text rendered as graphics (such as in games or images) cannot be detected", and "**On Android 15+ apps can block access to reading the screen content via the accessibility service**" ([Screen content](https://macrodroidforum.com/wiki/index.php?title=Trigger:_Screen_content) — mirror of the official wiki page).
- **The matching action:** **UI Interaction** clicks a target **by text content or by view id** — not by coordinates — and exposes a "Click Result" boolean so the macro can detect that the target was not found ([UI Interaction](https://macrodroidforum.com/wiki/index.php?title=Action:_UI_Interaction)).
- **The closest thing to a recorder:** **Launch and Press** launches an app, records your touch events, and replays them each time the action runs; recording stops by pressing the power button; a one-time calibration screen identifies the touch hardware; it **requires root** (touches are injected as low-level input events), the recorded positions are **raw screen coordinates** so replay breaks if the layout or orientation changes, and it needs "Display over other apps" ([Launch and Press](https://wiki.macrodroid.com/wiki/index.php/Action:_Launch_and_Press)). A forum thread confirms the gap explicitly: MacroDroid has **no multi-tap recorder**; the alternatives offered are the UI Interaction "Identify in App" mode and third-party apps such as Clickmate and Smart AutoClicker ([forum](https://www.macrodroidforum.com/index.php?goto/post&id=54507)).
- **Permission friction (for any "record" feature):** MacroDroid needs **two** separately-granted accessibility services; "Android sometimes disables accessibility services (for example after an app update or when the system kills the app)", hence a "Keep Accessibility Services Running" option that requires root or `WRITE_SECURE_SETTINGS` via the ADB hack; privacy line: "The contents of your screen are only read to identify the correct view to interact with and are never stored" ([Permissions and special access](https://wiki.macrodroid.com/wiki/index.php/Permissions_and_Special_Access)).

### 1.6 Automate (LlamaLab)

- **Model:** a flow is a graph of blocks; a running instance is a "fiber". An **action block** has one outcome (`OK`); a **decision block** has `YES`/`NO` ([Automate flow docs](https://llamalab.com/automate/doc/flow.html)).
- **Explicit statement of the trigger/condition split:** "**Other automation apps differentiate between 'event triggers' and 'condition/constraint checks'. Automate combines the same functionally into single block, using different `Proceed` options.**" The `Proceed` option decides whether the fiber pauses and waits for a change, or checks instantly ([Automate flow docs](https://llamalab.com/automate/doc/flow.html)).
- **No recorder**; flows are built from a block catalogue. Fibers survive reboot and resume from the last block. The FAQ covers flows stopping, battery drain from polling, task killers, and the running notification ([Automate FAQ](https://llamalab.com/automate/doc/faq.html)).

### 1.7 Appium Inspector — the recorder that ships as generated code

- **Artifact:** executable Appium-client code. "The Recorder tab is used to record various Inspector interactions into executable code, for use with various Appium clients." A language dropdown re-renders already-recorded code; boilerplate toggle, copy, and clear buttons exist. "By default, the tab contents are empty, since recording must be manually enabled in the Inspector header." and "The clear button deletes all the currently recorded code. **Note that the recording state is not changed.**" ([Appium Inspector](https://appium.github.io/appium-inspector/latest/session-inspector/recorder/))
- **Unit:** "Actions for a specific element (tap/send keys/clear); **Generic tap/swipe actions on the application screenshot**; Mobile device system actions; Driver commands", and "Recording of custom gestures is not currently supported" ([Appium Inspector header](https://appium.github.io/appium-inspector/latest/session-inspector/header/)).
- **Selector inference surface (not a recorder):** the Source tab shows the app's UI XML with `class name`, `value`, `content-desc` visible by default; clicking an element highlights it in the Screenshot panel and opens the Selected Element panel ([Source tab](https://appium.github.io/appium-inspector/latest/session-inspector/source/), [Screenshot panel](https://appium.github.io/appium-inspector/latest/session-inspector/screenshot/)).

### 1.8 Tasker

- **Verify/replay affordance:** the Task Edit screen has a "Task Control Row" with a **Play** button ("will run the task right through till the end"), a **Step** button (one action per press), an arrow showing the current action and a faded arrow for the next, and a **green (action OK) or red (action failed) circle per action**. "Tasks run via the Play or Step button have priority 10." Long-pressing Play sets the priority, with the warning that "setting a lower priority can result in interference from other tasks running which are e.g. triggered by profiles" ([Tasker userguide](https://tasker.joaoapps.com/userguide/en/activity_taskedit.html)).
- **Model:** profiles = context (state / event / app / location) + task ([Tasker userguide](https://tasker.joaoapps.com/userguide/en/)).

### 1.9 Chrome DevTools Recorder — the best browser analogue

- **Artifact:** a JSON user flow of typed steps (`click`, `change`, `hover`, `keyUp`, `keyDown`, `scroll`, `navigate`, `waitForElement`, `waitForExpression`, `setViewport`), each with a **selector** and optional `timeout`, `target`, `frame` ([DevTools Recorder reference](https://developer.chrome.com/docs/devtools/recorder/reference)).
- **Start/stop/present:** open the Recorder panel from the Command Menu (`Cmd/Ctrl+Shift+P` → "Show Recorder panel") or ⋮ → More tools → Recorder panel; record, then Replay ([DevTools Recorder overview](https://developer.chrome.com/docs/devtools/recorder/overview)).
- **Edit afterwards — and this is the key affordance:** "**You can edit any step in the recording by clicking the button next to it, both during the recording and after.**" … "You can also **add** missing steps and **remove** accidentally recorded ones." … "the Recorder doesn't automatically capture `hover` events because this pollutes the recording" (so you add them by hand) ([DevTools Recorder reference](https://developer.chrome.com/docs/devtools/recorder/reference)).
- **Round-tripping:** JSON export → "Edit the human-readable JSON object and import the JSON file back to the Recorder."; Puppeteer-script export is one-way ("you can't import this script back to the Recorder") ([DevTools Recorder reference](https://developer.chrome.com/docs/devtools/recorder/reference)). **The JSON step list is the closest existing precedent for a non-code but still declarative artifact.**
- **Selector generalisation — the algorithm worth stealing:** priority is ARIA → `data-testid`/`data-test`/`data-qa`/`data-cy`/`data-test-id`/`data-qa-id`/`data-testing` → id → regular CSS → XPath → Pierce → shortest unique text. The stated reason for the ranking: "Your webpages may use dynamic classes or IDs that change. **Your selectors may break because of code or framework changes.**" A custom attribute can be configured to override ([DevTools Recorder reference](https://developer.chrome.com/docs/devtools/recorder/reference)).
- **Debugging:** slow replay speeds, breakpoints, "Execute one step", "Cancel replay", per-step timeout over a 5000 ms default ([DevTools Recorder reference](https://developer.chrome.com/docs/devtools/recorder/reference)).
- **Ecosystem:** `@puppeteer/replay` "provides an API to replay and stringify recordings created using Chrome DevTools Recorder" ([GitHub](https://github.com/puppeteer/replay)).

### 1.10 Power Automate Desktop — the most explicit "recorded = starting point" statement

- **Unit:** "The recorder keeps track of mouse and keyboard activity **in relation to UI elements**, and it records each action separately." Selector capture modes UIA (recommended) vs MSAA (legacy); UIA "offers more robust and detailed element information… improved hierarchy structures, and greater reliability". Used UI elements are auto-added to the flow's UI elements pane ([Microsoft](https://learn.microsoft.com/en-us/power-automate/desktop-flows/recording-flow)).
- **Start/stop:** Recorder in the flow designer → **Record** → perform → **Done** (converts recorded steps to flow actions); **Pause** and **Add a comment** exist. While recording, a **"Wait for action"** message instructs the user to wait for the recorder to insert the action ([Microsoft](https://learn.microsoft.com/en-us/power-automate/desktop-flows/recording-flow)).
- **The verdict — the best single citation in this note for GKD design:** "**Use the recorder to create the backbone of your flow. Certain simple tasks might require no further editing; however, most recorded tasks should be modified to achieve optimal results. Certain types of actions, like conditionals and loops, can't be recorded. Also, there might be redundant actions in a recording that should be removed.**" ([Microsoft](https://learn.microsoft.com/en-us/power-automate/desktop-flows/recording-flow))
- **Fallback when accessibility APIs are absent:** image-based recording using image recognition + Tesseract OCR ([Microsoft](https://learn.microsoft.com/en-us/power-automate/desktop-flows/recording-flow)).
- **Known issues:** Start-menu/system-tray steps may not be recorded; with image recording "the click might be sent to the wrong place" (workaround: lower the `Move mouse to image` tolerance) ([Microsoft](https://learn.microsoft.com/en-us/power-automate/desktop-flows/recording-flow)).
- **Erase-a-mistake:** the recorder preview has a delete icon to remove missteps ([Microsoft training](https://learn.microsoft.com/en-us/training/modules/pad-power-automate-desktop-recorder-features/2-recording-actions)).
- **AI variant:** "Record with Copilot" captures voice + mouse + keyboard and generates a flow you review and edit ([Microsoft](https://learn.microsoft.com/en-za/power-automate/desktop-flows/create-flow-using-ai-recorder)).

### 1.11 Playwright codegen and Selenium IDE — demonstration → resilient selector

- **Playwright:** "Playwright will look at your page and **figure out the best locator, prioritizing role, text and test id locators. If the generator finds multiple elements matching the locator, it will improve the locator to make it resilient that uniquely identify the target element.**" … "You can then inspect your `test-1.spec.ts` file and **manually improve it if needed**." A separate "Pick locator" button lets you hover elements and copy a locator ([Playwright codegen](https://playwright.dev/docs/codegen)).
- **Selenium IDE:** "**Selenium IDE records multiple locators for each element it interacts with. If one locator fails during playback, the others will be tried until one is successful.**" ([Selenium IDE](https://www.selenium.dev/selenium-ide/))

Both are the "demonstration → *generalised* selector" pattern, which is exactly the step GKD's recording entry has to perform on an `AccessibilityNodeInfo`.

### 1.12 Apple and Google — declaration, not demonstration

- **Apple Shortcuts:** a personal automation is literally trigger + shortcut: "You can add automations to shortcuts so they run automatically when a trigger, such a specific time of day or a notification is received" ([Apple](https://support.apple.com/guide/shortcuts/apdfbdbd7123/ios)).
- **Apple's testing guidance:** "Organizing actions in a thoughtful sequence is key to building a successful shortcut. However, as you add a sequence of actions and test the shortcut, you may find that the result is not what you expect… **Figuring out the right sequence for your goal may require experimentation and frequent testing.**" ([Apple](https://support.apple.com/guide/shortcuts/test-your-actions-apda75604f37/ios))
- **AppleScript Editor is Apple's "recorder"** (and only works for apps that are both scriptable *and* recordable): click **Record**, act, click **Stop**; "When you record your script, you can edit it to remove unnecessary steps, adjust the playback speed, or adjust the timing of an action" ([Apple](https://support.apple.com/guide/script-editor/record-actions-scpedt1009/mac)).
- **Google Home script editor:** automations as YAML with **starters / conditions / actions**, plus the honesty clause "Automations are for convenience only, not safety or security-critical use cases… **Automations may not always work.**" ([Google](https://support.google.com/googlenest/answer/13460475))

### 1.13 Summary table

| Product | Unit | Artifact | Start / stop | Edited afterwards | Repair path when wrong |
|---|---|---|---|---|---|
| Excel VBA | clicks + keystrokes | VBA source | Developer tab / Stop Recording | VBA editor | re-record **or** edit code |
| AHK + PMC | keys, mouse, clicks rel. window/screen | `.ahk` script | hotkey / stop | text editor | add waits, switch to `ControlClick`, hand-write |
| Steps Recorder | screenshots + click descriptions | `.zip` MHTML report | Start/Stop Record | **none** | re-record; it is a report, not a program |
| MacroDroid | *(no recorder)*; trigger/action pickers | macro list | n/a | dropdowns | edit the trigger/action |
| MacroDroid "Launch and Press" | raw touch coordinates | coordinate replay | app launch / power button | minimal | re-record; breaks on layout change |
| Automate | *(no recorder)* | flow graph | n/a | block catalogue | edit the flow |
| Appium Inspector | element actions + generic taps/swipes | generated client code | header toggle | code editor | edit code; prefer element actions |
| Tasker | *(no recorder)* | profile/task list | n/a | list UI | edit the task |
| Chrome DevTools Recorder | element-targeted steps | JSON step list (+ Puppeteer export) | panel / stop | **edit steps during and after** | add/remove/re-prioritise steps |
| Power Automate Desktop | actions **relative to UI elements** | flow + UI-element pane | Record / Done | flow designer; delete missteps | edit the flow; "backbone" framing |
| Playwright / Selenium IDE | click on element | spec code + inferred locator(s) | codegen / stop | code editor | "manually improve it" |
| Apple Shortcuts / Google | n/a (declared) | action list / YAML | n/a | catalogue | edit the declaration |

---

## 2. Where the macro model breaks for a declarative product

### 2.1 The gap, in GKD's own terms

GKD's rule is a *match → action* rule. `A11yContext.queryRule` returns **exactly one node** — the last `matches` hit, or the first `anyMatches` hit — and the action acts on that node (`gkd-app/src/androidMain/kotlin/li/gkd/app/a11y/A11yContext.kt`). There is no "then", no ordering guarantee between rules beyond `order`, and `preKeys` is a single-step prerequisite, not a sequence (`gkd-app/src/commonMain/kotlin/li/gkd/app/rule/ResolvedRule.kt`). The engine polls on window/content change events, not on a script timeline.

A macro, by contrast, is a *timed linear script*: step 1 then step 2 then step 3, each with an implicit "and now" between them.

The gap is therefore not "scripts are imprecise". It is: **a demonstration carries an order and a timing that a declarative rule cannot carry.** Four consequences follow, and every product in this note had to solve some subset of them.

### 2.2 What each product did about the gap

| Product | Their bridge | Source |
|---|---|---|
| Excel | re-record the sequence, or edit the VBA | [Microsoft](https://int.support.office.com/f1/article/1e55959b-e077-4c88-a696-c3017600db44) |
| Power Automate Desktop | "create the backbone… most recorded tasks should be modified" | [Microsoft](https://learn.microsoft.com/en-us/power-automate/desktop-flows/recording-flow) |
| Playwright | "manually improve it if needed" | [playwright.dev](https://playwright.dev/docs/codegen) |
| AHK / PMC | sleep tuning, switch to screen coordinates, or hand-write the script | [forum](https://www.autohotkey.com/boards/viewtopic.php?t=99382) *(snippet)* |
| Chrome Recorder | add/remove steps; semantic selector re-ranking; per-step timeouts | [DevTools](https://developer.chrome.com/docs/devtools/recorder/reference) |
| MacroDroid | absorb the "and" into **constraints**; triggers stay OR-only | [MacroDroid](https://wiki.macrodroid.com/wiki/index.php/Triggers) |
| Automate | merge event triggers and condition checks into one block with a `Proceed` option | [Automate](https://llamalab.com/automate/doc/flow.html) |
| Apple / Google | don't demonstrate at all — declare a trigger and actions from a catalogue | [Apple](https://support.apple.com/guide/shortcuts/apdfbdbd7123/ios), [Google](https://support.google.com/googlenest/answer/13460475) |

The last two rows are the ones that matter most for GKD. **MacroDroid and Automate both chose to model the declarative side properly and to accept that a demonstration would have to be *translated*, not replayed.** GKD's decision — recording substitutes the *pick* step only — is the same shape of decision.

### 2.3 Concrete failure evidence

- **The recording reproduces literally, including your mistakes.** Excel's recorder "captures almost every move you make" ([Microsoft](https://int.support.office.com/f1/article/1e55959b-e077-4c88-a696-c3017600db44)). The mirror-image failure is Appium's generic tap/swipe, recorded by screenshot coordinate ([Appium Inspector](https://appium.github.io/appium-inspector/latest/session-inspector/header/)).
- **Timing.** Recorded scripts hard-code the delays observed once. AHK's `Sleep` is imprecise under load ([AHK docs](https://www.autohotkey.com/docs/v2/lib/Sleep.htm)); the community consensus is "wait for state, don't sleep" (`WinWait`, `ImageSearch`, `ClipWait`) — i.e. move from timing to *conditions* ([forum](https://www.autohotkey.com/boards/viewtopic.php?p=90385) *(snippet)*). This is the exact move GKD has already made: GKD waits for a window/content change event, then matches.
- **The UI changed.** Dynamics 365 Task Recorder's error taxonomy is the sharpest statement available: an error step occurs because "the form or lookup needed by the step wasn't open", "the button or field needed by the step was disabled, not visible, or not present", "**the name of the form or name of the control changed**", or "**because of a framework change to the control**". Playback "automatically pauses when an Error step is encountered. This gives you the opportunity to record replacement steps" ([Microsoft](https://learn.microsoft.com/en-us/dynamics365/fin-ops-core/dev-itpro/user-interface/task-recorder-quick-reference)).
- **Coordinates rot.** Power Automate Desktop's image-based recording "the click might be sent to the wrong place" ([Microsoft](https://learn.microsoft.com/en-us/power-automate/desktop-flows/recording-flow)); Appium's generic tap/swipe is by screenshot coordinate ([Appium](https://appium.github.io/appium-inspector/latest/session-inspector/header/)).
- **The trigger model cannot express a fine-grained condition.** A MacroDroid user asking how to match the *second line* of a multi-line view was told: use "Read Screen Contents" to build a dictionary of view id → text, iterate it, then click via view id; the text-reading plugin "can fetch x/y coordinates" ([forum](https://www.macrodroidforum.com/index.php?threads/screen-content-and-text-content.8410/post-52696)). GKD's selector language has the same ceiling, and it is a ceiling of the *selector*, not of the *recording*.
- **Accessibility-driven interaction collides with the target app's own UI.** Enabling MacroDroid's UI Interaction breaks Chrome's search bar (the keyboard appears then immediately disappears); the accepted workaround is a `Wait 250` plus a "Keyboard Showing" constraint in a loop ([forum](https://www.macrodroidforum.com/index.php?goto/post&id=76008)). GKD has the same class of problem in a different form — GKD's own clicks appearing in the tree it is matching.

### 2.4 Did users accept the difference? Yes — by rewriting

Every primary vendor document positions the recorded artifact as a **first draft**, never as the deliverable (Excel, Power Automate Desktop, Playwright, Apple). Community evidence is uniformly "record, then rewrite": the AutoHotkey advice is to add commands and logic manually; the effective fix hierarchy is *wait-for-state > element identity > coordinates > longer sleeps > hand-written script* ([forum](https://www.autohotkey.com/boards/viewtopic.php?f=63&t=116510), [forum](https://www.autohotkey.com/board/topic/145609) *(snippets)*).

The products that shipped the declarative model *absorbed* the gap by adding constraints and state rather than by pretending it away: MacroDroid's OR-only triggers plus per-trigger constraints ([MacroDroid](https://wiki.macrodroid.com/wiki/index.php/Triggers)), and Automate's `Proceed` option merging event triggers with condition checks ([Automate](https://llamalab.com/automate/doc/flow.html)).

**The user-visible conclusion for GKD: users accept "the recording is a draft" and reject "the recording is a program".** A GKD recording entry that produced a script would be rejected; one that produced an editable candidate rule matches what the market has already taught users to expect.

### 2.5 The generalisation: the trigger model wins robustness, loses exactness

Supporting the robustness half:

- Chrome's selector priority deliberately prefers *semantic* selectors (ARIA, test-id) over syntactic ones and documents that "Your selectors may break because of code or framework changes" ([DevTools](https://developer.chrome.com/docs/devtools/recorder/reference)).
- Playwright "improve[s] the locator to make it resilient that uniquely identify the target element" ([Playwright](https://playwright.dev/docs/codegen)).
- Selenium IDE keeps multiple locators per element and falls back ([Selenium IDE](https://www.selenium.dev/selenium-ide/)).
- Power Automate Desktop prefers UIA element capture over MSAA over image/OCR ([Microsoft](https://learn.microsoft.com/en-us/power-automate/desktop-flows/recording-flow)).

Visible in the exactness half — the escape hatches of the same products:

- Appium's generic coordinate taps ([Appium](https://appium.github.io/appium-inspector/latest/session-inspector/header/)).
- Power Automate Desktop's image clicks landing in the wrong place ([Microsoft](https://learn.microsoft.com/en-us/power-automate/desktop-flows/recording-flow)).
- MacroDroid's inability to express "the second line" without an extra plugin ([forum](https://www.macrodroidforum.com/index.php?threads/screen-content-and-text-content.8410/post-52696)).
- Excel's over-capture of every stray move ([Microsoft](https://int.support.office.com/f1/article/1e55959b-e077-4c88-a696-c3017600db44)).

**A GKD rule is on the robust side of this trade by construction.** Its selector may be too broad (many matches) or too narrow (no match); it will not silently do the wrong thing at the wrong moment the way a mistimed script step can. That is a real argument for the declarative model over the macro model — and it is the argument GKD's recording entry inherits for free.

---

## 3. Systems that turn a demonstration into a declarative trigger-plus-action rule

### 3.1 Ranking by closeness to GKD's target

| System | Demonstration in | Declarative out | GKD analogue |
|---|---|---|---|
| **MacroDroid** Screen Content + UI Interaction | a11y service observes screen text / view id | trigger on text-or-id, action clicks by text-or-id | **near-identical model**, different UI |
| **Chrome DevTools Recorder** | browser events | JSON steps whose action is a **selector** | closest in *artifact shape* |
| **Power Automate Desktop** | mouse/keyboard | flow actions bound to **UIA element selectors** | closest in *capture mechanism* |
| **Dynamics 365 Task Recorder** | in-app clicks | steps carrying **UI-element identity**, with error-step replay | closest in *verify story* |
| **Playwright / Selenium IDE** | browser events | action + inferred resilient locator(s) | closest in *selector generalisation* |
| **Apple Shortcuts / Google Home** | none — declared | trigger + action list / YAML | the *opposite* choice: no demonstration at all |
| **Android `AccessibilityNodeInfo`** | — | the identity attributes a rule can match | the substrate |

### 3.2 MacroDroid: Screen Content trigger + UI Interaction action

The closest shipped analogue to GKD on Android. An accessibility service polls the screen and fires a trigger when matching text or a matching view id appears; the corresponding action clicks a target **by text content or by view id** ([Screen content](https://macrodroidforum.com/wiki/index.php?title=Trigger:_Screen_content), [UI Interaction](https://macrodroidforum.com/wiki/index.php?title=Action:_UI_Interaction)).

Its documented constraints are instructive because they are GKD's constraints too: 500 ms debounce between fires, a configurable screen-read rate, text rendered as graphics unreadable, and an Android 15 caveat about apps blocking accessibility reads. It also demonstrates the **constraint-not-AND** approach to stateful conditions, and it exposes a "Click Result" boolean so the macro can tell "not found" from "clicked" — a pattern GKD would want for its own "nothing captured" state.

Critically: **MacroDroid does not record demonstrations.** Its "Launch and Press" is a root-only coordinate replayer, and the community answer to "how do I record taps" is "you can't, in MacroDroid" ([forum](https://www.macrodroidforum.com/index.php?goto/post&id=54507)). A GKD recording entry would be, in this product family, a genuinely new thing.

### 3.3 Dynamics 365 Task Recorder

A demonstration → *steps that carry UI-element identity*, with playback that re-executes the steps against the live app and **pauses on error so you can record replacements**; it can also be saved as a "developer recording" (code) ([Microsoft](https://learn.microsoft.com/en-us/dynamics365/fin-ops-core/dev-itpro/user-interface/task-recorder-quick-reference)). It is the best "demonstration → element-identified steps → verify by replay" system found, and its error taxonomy (section 2.3) is the best public articulation of *why* recorded artifacts rot.

### 3.4 Power Automate Desktop

A demonstration → a flow whose clicks are expressed as **UIA/MSAA selectors** rather than coordinates, with an image+OCR fallback for apps with no accessibility API ([Microsoft](https://learn.microsoft.com/en-us/power-automate/desktop-flows/recording-flow)). This is a working, shipped "record a click → get a selector-based rule" pipeline on desktop, and the only one found that is production-grade rather than a developer tool.

### 3.5 Chrome DevTools Recorder

A demonstration → declarative JSON steps whose *action* is a **selector**, with a documented selector-priority algorithm and per-step timeouts ([DevTools reference](https://developer.chrome.com/docs/devtools/recorder/reference), [overview](https://developer.chrome.com/docs/devtools/recorder/overview)). Two properties make it the best *artifact* precedent for GKD: the JSON is human-readable and **round-trippable** (export → edit → import), and steps are individually editable during recording.

### 3.6 Playwright codegen and Selenium IDE

Demonstration → action + inferred *resilient* locator, or a set of locators with fallback ([Playwright](https://playwright.dev/docs/codegen), [Selenium IDE](https://www.selenium.dev/selenium-ide/)). These are the clearest statements of the generalisation step: **the recorded target is deliberately turned from "the thing I clicked" into "the kind of thing I clicked".**

### 3.7 Apple Shortcuts and Google Home routines

Declaration, not demonstration: you pick a trigger from a catalogue and pick actions from a catalogue, or write YAML `starters`/`conditions`/`actions` ([Apple](https://support.apple.com/guide/shortcuts/apdfbdbd7123/ios), [Google](https://support.google.com/googlenest/answer/13460475)). They deliberately do *not* learn from watching you. **This is the strongest evidence that "record my actions" was considered and rejected on the declarative side of the design space** — two of the largest consumer automation products chose a catalogue over a demonstration.

### 3.8 The Android substrate

`AccessibilityNodeInfo` exposes exactly the identity GKD matches on — `getViewIdResourceName()` ("Gets the fully qualified resource name of the source view's id"), plus `getText`, `getContentDescription`, `isClickable`, `isVisibleToUser`, `isEnabled`, `isCheckable`, `isScrollable`, `isLongClickable` ([Android reference](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo)). A recording entry would read these off the node carried by the click event and turn them into a selector — the same operation GKD's existing pick-resolution already performs.

### 3.9 Manual selector-inference surfaces

"Point at it, get the rule" without any recording: Appium Inspector's Source tab (click an element in the XML tree → attributes in the Selected Element panel) ([Appium](https://appium.github.io/appium-inspector/latest/session-inspector/source/)); Playwright's "Pick locator" ([Playwright](https://playwright.dev/docs/codegen)); Chrome DevTools' Elements-panel copy-selector/XPath. GKD already owns this shape as screenshot picking; the recording entry's novelty is that the *user's own tap* supplies the node instead of a screenshot.

### 3.10 The gap: nobody does "record a tap → emit an Android declarative rule"

No shipped product was found that offers a true "record a tap in an arbitrary Android app → emit a GKD-style declarative rule" feature. On Android, everything found either:
- records coordinates or images (auto-clicker apps, MacroDroid's "Launch and Press", image-based automation), or
- requires the user to author the selector by hand (MacroDroid, Tasker, Automagic), or
- lives on the web/desktop where the selector substrate is rich (Chrome Recorder, Playwright, Power Automate Desktop).

**This gap appears to be genuinely unoccupied.** Two unverified leads worth a manual look, both surfaced only as search summaries: **Pocket Automator** (`com.kriyanshii/pocket-automator`), reported to record taps *and* capture the accessibility tree per step and to export JSON with resource-id/content-desc/tree-path resolution — the closest open-source analogue to "demonstration → accessibility-selector rule"; and **Automagic** (`automagic4android.com`), a flowchart trigger/action tool, apparently unmaintained since ~2019.

---

## 4. What GKD can borrow

### 4.1 The entry point

| Pattern | Prior art | Fit for GKD |
|---|---|---|
| Toggle in the app chrome | Appium Inspector's "Toggle Recorder" in the header; recording continues even if the Recorder tab is not open ([Appium](https://appium.github.io/appium-inspector/latest/session-inspector/header/)) | Good: a GKD overlay affordance can stay armed while the user is in another app |
| A dedicated panel in a dev tool | Chrome Recorder, opened from the Command Menu or ⋮ → More tools ([DevTools](https://developer.chrome.com/docs/devtools/recorder/overview)) | Good for Desktop/desktop-parity, poor for an on-device flow where the target app must be foreground |
| Toolbar button + status-bar stop | Excel's Record Macro / Stop Recording ([Microsoft](https://support.microsoft.com/en-us/topic/8f09cbbf-bb85-413e-9720-d567180f61e4)) | Good: a single obvious toggle is the minimum viable entry |
| In-tool recorder inside an editor | Power Automate Desktop's Recorder in the flow designer ([Microsoft](https://learn.microsoft.com/en-us/power-automate/desktop-flows/recording-flow)) | Poor: GKD's recording must happen in the *target* app, not in GKD's editor |
| Global hotkey + floating window | AHK/PMC recorders; PSR's floating Steps Recording window with Pause / Add Comment / Stop ([third-party](https://helpdeskgeek.com/how-to/how-to-use-steps-recorder-when-troubleshooting-windows/)) | Medium: Android has no global hotkey; the floating-overlay equivalent is GKD's existing overlay |
| Trigger buttons + tiles | MacroDroid's **Floating Button**, **Overlay Bar**, **Widget Button** and **Quick Settings Tile** as trigger sources ([MacroDroid](https://wiki.macrodroid.com/wiki/index.php/Overview)) | **Directly relevant**: GKD already has a floating overlay and a quick-settings tile, so "arm recording" is a natural fit for the existing overlay button, gated behind a switch like the spec's floating rule button |

**Recommendation-shaped reading:** GKD's locked spec already has a floating rule button, off by default, enabled in settings next to the floating snapshot setting. Prior art says the recording entry should be **the same button in a second mode** rather than a new surface — a second floating button doubles the overlay's screen cost and splits the user's mental model.

### 4.2 The live-recording affordance

- **"I am busy, wait" feedback.** Power Automate Desktop shows a **"Wait for action"** message over the target while it captures the click, so the user does not click again ([Microsoft](https://learn.microsoft.com/en-us/power-automate/desktop-flows/recording-flow)). For a GKD recorder driven by an accessibility click event, the equivalent is a short "recording…" cue that appears *after* the tap is captured and disappears when the rule candidate is ready. Without it, users tap twice.
- **Edit while recording.** Chrome Recorder lets you edit, add and remove steps *during* the recording ([DevTools](https://developer.chrome.com/docs/devtools/recorder/reference)). For a one-demonstration-per-session model this matters less than it would for a multi-step recorder, but the "delete this misstep" case still exists (a user who taps the wrong thing first).
- **Always-visible recorder window.** PSR floats a small window over the screen with Pause / Add Comment / Stop ([third-party](https://helpdeskgeek.com/how-to/how-to-use-steps-recorder-when-troubleshooting-windows/)). On Android, GKD's existing overlay is the equivalent — but a GKD overlay sits above the target app and can itself perturb the target's layout, which is a real risk for the capture it is trying to observe.

### 4.3 The "replay to verify" trust story

This is where the prior art is richest and where GKD has the sharpest opportunity.

| Pattern | Prior art | What it gives GKD |
|---|---|---|
| **Test the action half independently of the trigger half** | MacroDroid: long-press an action to test it immediately; "Test actions" to run the whole list without waiting for the trigger ([MacroDroid](https://wiki.macrodroid.com/wiki/index.php/Actions), [Overview](https://wiki.macrodroid.com/wiki/index.php/Overview)) | Exactly GKD's split: a recorded candidate rule's action can be exercised against the current screen before the user commits |
| **Per-step success feedback** | Tasker: green (action OK) / red (action failed) circle per action, plus a **Step** button that advances one action per press ([Tasker](https://tasker.joaoapps.com/userguide/en/activity_taskedit.html)) | Per-rule pass/fail rather than a single opaque "it worked" |
| **Playback that pauses on error and lets you substitute** | Dynamics 365 Task Recorder: "Play next pending step / Play to selected step / Play all pending steps"; a failed step becomes an Error step, playback pauses, you record a replacement ([Microsoft](https://learn.microsoft.com/en-us/dynamics365/fin-ops-core/dev-itpro/user-interface/task-recorder-quick-reference)) | Turns a broken verification into a repair action rather than a dead end |
| **Replay as debugging, not as a green tick** | Chrome Recorder: slow-replay speeds, breakpoints, "Execute one step", "Cancel replay", per-step timeout over a 5000 ms default ([DevTools](https://developer.chrome.com/docs/devtools/recorder/reference)) | A GKD "verify" that shows *which* part of the rule failed, not just that it did |
| **Erase a mistake during recording** | PAD's recorder preview has a delete icon for missteps ([Microsoft training](https://learn.microsoft.com/en-us/training/modules/pad-power-automate-desktop-recorder-features/2-recording-actions)) | Cheap win if GKD ever allows more than one captured event per session |
| **Caveat worth internalising** | Windows Steps Recorder's famous Play button only replays the *screenshots* as a slide show; it never re-executes ([third-party](https://helpdeskgeek.com/how-to/how-to-use-steps-recorder-when-troubleshooting-windows/); the official article documents no Play button) | GKD's verify button must say plainly whether it **re-runs the rule** or merely **shows what matches** |

The last row is the most important correction to a common intuition. Users confuse "replay the evidence" with "replay the behaviour". A GKD rule has no behaviour to replay — it fires whenever it matches — so the honest analogue of "Play" is **"count the matches on this screen and act on the target"**, not "re-run my recording". The GKD web inspector already models the honest form: its diagnostics state that it "仅验证当前快照中的选择器、界面范围和排除条件，不模拟规则执行时序" — it validates selectors, scope and exclusions in the current snapshot and does not simulate rule execution timing.

### 4.4 The "nothing captured" state

- **Appium Inspector distinguishes three recorder states**, which is the cleanest model found: the tab "contents are empty, since recording must be manually enabled in the Inspector header"; the **Clear** button "deletes all the currently recorded code. **Note that the recording state is not changed.**" ([Appium](https://appium.github.io/appium-inspector/latest/session-inspector/recorder/)). So: *not recording* / *recording, nothing captured* / *recording, buffer cleared* are three different UI states, and the last does not silently return to the first.
- **PSR's partial-capture limits** are stated as facts, not errors: "Recorded steps don't capture anything that's typed during the recording"; only the last 25 screenshots are kept; "Some programs may not be captured accurately" ([Microsoft Learn](https://learn.microsoft.com/hu-hu/troubleshoot/microsoft-365-apps/settings/how-to-use-problem-steps-recorder)).
- **PAD's answer to an unobservable app is a different capture mode**, not an error: image recognition + OCR for apps with no accessibility API ([Microsoft](https://learn.microsoft.com/en-us/power-automate/desktop-flows/recording-flow)).
- **Excel is the counter-case:** it *over*-captures ("almost every move you make") ([Microsoft](https://int.support.office.com/f1/article/1e55959b-e077-4c88-a696-c3017600db44)). A recorder must decide explicitly between "capture everything" and "capture only what you meant". GKD's tap-and-long-press vocabulary already makes that decision.

GKD's map has already decided this: "**No event → honest 'nothing was recorded' state** that falls back to picking; no invented timeout, no coordinate fallback." The prior art supports that decision and adds one refinement worth carrying into the spec: **the "nothing recorded" state must be distinguishable from "recording finished with zero results"**, and a user who clears the buffer must not be silently returned to "not recording".

### 4.5 Known failure and abandonment patterns

**(a) It doesn't reproduce — and it doesn't reproduce *later*.**
Excel: mistakes are recorded; the remedy is re-record or edit code ([Microsoft](https://int.support.office.com/f1/article/1e55959b-e077-4c88-a696-c3017600db44)). PAD: "most recorded tasks should be modified"; image clicks land wrong; conditionals and loops cannot be recorded at all ([Microsoft](https://learn.microsoft.com/en-us/power-automate/desktop-flows/recording-flow)). Dynamics: steps break when "the name of the form or name of the control changed" or "because of a framework change to the control" ([Microsoft](https://learn.microsoft.com/en-us/dynamics365/fin-ops-core/dev-itpro/user-interface/task-recorder-quick-reference)). AHK community: the recorder is "designed to be as accurate as possible but for a more reliable macro you should add the commands and logic manually" ([forum](https://www.autohotkey.com/boards/viewtopic.php?t=99382) *(snippet)*).

**(b) The artifact is intimidating.**
Every classic recorder's artifact is *code*: VBA, `.ahk`, Java/Python/JUnit, Puppeteer. The modern pattern is to add a **non-code artifact alongside** — Chrome's JSON step list (round-trippable), PAD's visual flow, Automate's flowchart, MacroDroid's coloured trigger/action lists, Apple Shortcuts' action list. **GKD's candidate-rule-plus-conditions view is already the non-code artifact**; the risk is that it degrades into exposing the selector, which the locked spec explicitly reserves for the advanced editor.

**(c) Privacy and permission friction.**
PSR ships an explicit screen-capture privacy switch ([Microsoft Learn](https://learn.microsoft.com/hu-hu/troubleshoot/microsoft-365-apps/settings/how-to-use-problem-steps-recorder)). MacroDroid needs two separately-granted accessibility services plus notification access / usage access / overlay permission, warns that Android disables accessibility services "after an app update" (hence a keep-alive hack), and mitigates with the disclosure "The contents of your screen are only read to identify the correct view to interact with and are never stored" ([Permissions](https://wiki.macrodroid.com/wiki/index.php/Permissions_and_Special_Access)). MacroDroid also ships a "Don't read when MacroDroid is open" setting to avoid slowing itself down ([Screen content](https://macrodroidforum.com/wiki/index.php?title=Trigger:_Screen_content)).

**(d) The app updates and the rule rots.**
Chrome: "Your selectors may break because of code or framework changes" ([DevTools](https://developer.chrome.com/docs/devtools/recorder/reference)). Dynamics: the error taxonomy above. MacroDroid: accessibility services get disabled after app updates ([Permissions](https://wiki.macrodroid.com/wiki/index.php/Permissions_and_Special_Access)).

**(e) The target app can opt out.**
MacroDroid's documented caveat that "On Android 15+ apps can block access to reading the screen content via the accessibility service" ([Screen content](https://macrodroidforum.com/wiki/index.php?title=Trigger:_Screen_content) *(see open questions — the underlying official Android document was not located)*). GKD faces the same class of limit, and the map's decision to have "no coordinate fallback" makes "the click event never arrived" a terminal state rather than a degraded one — which is the honest behaviour, but only if the empty state is clearly explained.

### 4.6 Mapping onto the map's locked decisions

| Map decision ([#11](https://github.com/fktpp2022/gkd/issues/11)) | What prior art says | Verdict |
|---|---|---|
| Recording is an alternative **entry**, substituting the *pick* step only | Chrome Recorder and PAD substitute capture but keep a *list* of steps; MacroDroid and Automate do not capture at all | **Supported.** Prior art's substitution is at the *action* level; GKD's is at the *node* level, which is finer and safer |
| One demonstration per session → one candidate rule | Chrome Recorder and PAD produce *many* steps and then need heavy editing; every vendor says the multi-step artifact is where the pain is | **Supported, and stronger than prior art.** GKD's one-shot model sidesteps the editing burden that makes recorders feel unfinished |
| Recorded rule fires whenever its selector matches; no sequence semantics | MacroDroid: triggers OR, constraints carry state. Automate: `Proceed` merges triggers and conditions | **Supported.** The declarative model is the mainstream choice, not a compromise |
| Vocabulary is tap and long-press only | Excel over-captures "almost every move"; PAD cannot record conditionals or loops; Chrome Recorder drops `hover` to avoid pollution | **Supported.** Narrowing the vocabulary is the standard remedy for over-capture |
| While a session is live, GKD's engine does not act | MacroDroid's workaround for the Chrome-search-bar collision is a `Wait 250` + constraint loop — a hack | **Supported.** GKD's guard is cleaner than the prior art's workaround |
| No event → honest "nothing was recorded", no coordinate fallback | Excel over-captures, PAD adds an image fallback, Appium records generic coordinate taps — all three are the behaviours GKD is refusing | **Supported.** The refusal is the differentiator; PAD's image fallback is precisely the degradation GKD should not copy |
| No provenance field | Excel/PAD/Chrome all keep the recorded artifact as the deliverable; no vendor drops provenance | **Neutral.** Prior art does not answer this; the map's choice stands |
| Evidence bar is device evidence of real click events | Chrome, PAD and Playwright all made the same claim on desktop, where the substrate is richer | **Consistent.** No desktop prior art substitutes for Android device evidence — Desktop remains a replay harness |

---

## 5. Open questions and thin evidence

1. **The AutoHotkey community forum is unreachable programmatically** (Cloudflare 403 on every variant tried). The four AHK community quotes above come from search-result snippets and should be re-read manually before being quoted in a design document.
2. **Windows Steps Recorder's "Play" button is only documented by third parties.** The official Microsoft article documents no Play button; the slide-show behaviour is described only by tutorial sites and appears to have been removed in the Windows 11 rewrite. The claim that PSR's Play replays screenshots rather than re-executing actions is inferred from those tutorials plus the official article's silence. Worth confirming by running PSR on Windows 10.
3. **No official Android documentation was found for "On Android 15+ apps can block access to reading the screen content via the accessibility service."** It appears only in MacroDroid's wiki. The documented Android mechanisms are narrower: `FLAG_SECURE` per-window protection, `RECEIVE_SENSITIVE_NOTIFICATIONS` role-gating for notification listeners, and Android 15 "Sensitive Content Protection" / accessibility sandboxing for *unverified* accessibility apps ([developer.android.com/security/fraud-prevention/activities](https://developer.android.com/security/fraud-prevention/activities), [Android 15 behaviour changes](https://developer.android.com/about/versions/15/behavior-changes-all)). GKD's own experience with `FLAG_SECURE` screens is a better local source.
4. **Pocket Automator and Automagic are unverified leads.** Pocket Automator (`com.kriyanshii/pocket-automator`) is reported to record taps *and* capture the accessibility tree per step, export JSON, and replay by resource-id/content-desc/tree-path — the closest open-source analogue to "demonstration → accessibility-selector rule" — but only from a search summary. Automagic (`automagic4android.com`) appears unmaintained since ~2019. Neither was fetched.
5. **Automate's "no recorder" is an inference** from its block-catalogue documentation; no explicit statement that recording is unsupported was found.
6. **No quantitative evidence** on recorder abandonment rates, recorded-artifact survival after app updates, or the share of users who abandon the recorder and rewrite by hand. Section 4.5 is vendor-authored and anecdotal throughout.
7. **No product was found that offers "record a tap in an arbitrary Android app → emit a declarative accessibility rule".** The closest are Power Automate Desktop (desktop, UIA/MSAA selectors), Chrome Recorder (web, CSS/ARIA selectors), Playwright (web, role/text locators) and MacroDroid's manually-authored Screen Content trigger. The gap appears genuine; a manual check of the Android app store for "accessibility auto click recorder" products was not done.

---

## Sources

### GKD local source (this repository, `main`)

- Rule resolution and the single target node: `gkd-app/src/androidMain/kotlin/li/gkd/app/a11y/A11yContext.kt` (`queryRule`)
- Rule fields, `order`, `preKeys`, `matchTime`, `actionMaximum`: `gkd-app/src/commonMain/kotlin/li/gkd/app/rule/ResolvedRule.kt`
- Engine event handling: `gkd-app/src/androidMain/kotlin/li/gkd/app/a11y/A11yRuleEngine.kt`
- Action vocabulary: `gkd-app/src/androidMain/kotlin/li/gkd/app/data/GkdAction.kt`
- Selector engine: `gkd-selector/src/commonMain/kotlin/gkd/selector/`

### GKD tracker

- Wayfinder map: https://github.com/fktpp2022/gkd/issues/11
- This ticket: https://github.com/fktpp2022/gkd/issues/13

### Desktop and Windows recorders

- Excel: recorder records mouse clicks and keystrokes: https://int.support.office.com/f1/article/741130ca-080d-49f5-9471-1e5fb3d581a8
- Excel: recorder captures mistakes; re-record or edit VBA: https://int.support.office.com/f1/article/1e55959b-e077-4c88-a696-c3017600db44
- Excel: Record Macro / Stop Recording, Excel-web Record Actions: https://support.microsoft.com/en-us/topic/8f09cbbf-bb85-413e-9720-d567180f61e4
- Excel: Stop Recording on the status bar: https://support.microsoft.com/en-us/Excel/run-a-macro-in-excel
- Pulover's Macro Creator: record, pause/play, export AHK: https://www.macrocreator.com/
- AHK ControlClick: coordinates vs Control Identifier: https://www.autohotkey.com/docs/v2/lib/ControlClick.htm
- AHK Sleep: imprecision under load: https://www.autohotkey.com/docs/v2/lib/Sleep.htm
- AHK forum: no official AHK v2 recorder (fetch blocked): https://www.autohotkey.com/boards/viewtopic.php?t=120403
- AHK forum: PMC unreliable, add commands manually (snippet): https://www.autohotkey.com/boards/viewtopic.php?t=99382
- AHK forum: getting better recordings (snippet): https://www.autohotkey.com/boards/viewtopic.php?f=63&t=116510
- AHK forum: abandoning the recorder (snippet): https://www.autohotkey.com/board/topic/145609-hundreds-of-small-moves-and-pauses-recorded
- AHK forum: sleeps everywhere to stabilise recordings (snippet): https://www.autohotkey.com/boards/viewtopic.php?p=90385
- AHK community recorder (README): https://github.com/ArtyMcLabin/AHK2-Macro-Recorder/blob/master/README.md
- Windows Steps Recorder: unit, .zip artifact, settings, limits: https://learn.microsoft.com/hu-hu/troubleshoot/microsoft-365-apps/settings/how-to-use-problem-steps-recorder
- Steps Recorder deprecation (Feb 2024 Win11 update): https://support.microsoft.com/en-us/help/22878
- Steps Recorder slide-show Play and floating window (third-party): https://helpdeskgeek.com/how-to/how-to-use-steps-recorder-when-troubleshooting-windows/

### Android automation products

- MacroDroid overview: triggers, actions, constraints, test affordances: https://wiki.macrodroid.com/wiki/index.php/Overview
- MacroDroid triggers: OR-only, constraints instead of AND: https://wiki.macrodroid.com/wiki/index.php/Triggers
- MacroDroid actions: sequential actions, control flow, "Test actions": https://wiki.macrodroid.com/wiki/index.php/Actions
- MacroDroid permissions and special access: services, keep-alive, privacy line: https://wiki.macrodroid.com/wiki/index.php/Permissions_and_Special_Access
- MacroDroid "Launch and Press": root-only coordinate recording: https://wiki.macrodroid.com/wiki/index.php/Action:_Launch_and_Press
- MacroDroid Screen Content trigger (GKD analogue): https://macrodroidforum.com/wiki/index.php?title=Trigger:_Screen_content
- MacroDroid UI Interaction action: click by text or view id: https://macrodroidforum.com/wiki/index.php?title=Action:_UI_Interaction
- MacroDroid forum: no multi-tap recorder; alternatives: https://www.macrodroidforum.com/index.php?goto/post&id=54507
- MacroDroid forum: limits of text triggers: https://www.macrodroidforum.com/index.php?threads/screen-content-and-text-content.8410/post-52696
- MacroDroid forum: accessibility interaction breaks Chrome's search bar: https://www.macrodroidforum.com/index.php?goto/post&id=76008
- Automate flow docs: blocks, fibers, Proceed merges triggers/conditions: https://llamalab.com/automate/doc/flow.html
- Automate FAQ: flows stopping, battery, running notification: https://llamalab.com/automate/doc/faq.html
- Appium Inspector Recorder tab: https://appium.github.io/appium-inspector/latest/session-inspector/recorder/
- Appium Inspector header: what is recordable, generic taps: https://appium.github.io/appium-inspector/latest/session-inspector/header/
- Appium Inspector Source tab: element attributes: https://appium.github.io/appium-inspector/latest/session-inspector/source/
- Tasker userguide: Play/Step buttons, per-action OK/fail circles: https://tasker.joaoapps.com/userguide/en/activity_taskedit.html
- Tasker userguide index (profiles, contexts): https://tasker.joaoapps.com/userguide/en/

### Demonstration → declarative rule systems

- Chrome DevTools Recorder overview: panel, record/replay: https://developer.chrome.com/docs/devtools/recorder/overview
- Chrome DevTools Recorder reference: steps, selector priority, export/import, timeouts: https://developer.chrome.com/docs/devtools/recorder/reference
- @puppeteer/replay: replay and stringify recordings: https://github.com/puppeteer/replay
- Power Automate Desktop recording: UIA/MSAA, "backbone", known issues: https://learn.microsoft.com/en-us/power-automate/desktop-flows/recording-flow
- Power Automate Desktop "Record with Copilot": https://learn.microsoft.com/en-za/power-automate/desktop-flows/create-flow-using-ai-recorder
- Power Automate Desktop training: recorder preview, delete missteps: https://learn.microsoft.com/en-us/training/modules/pad-power-automate-desktop-recorder-features/2-recording-actions
- Dynamics 365 Task Recorder: playback controls, error-step taxonomy: https://learn.microsoft.com/en-us/dynamics365/fin-ops-core/dev-itpro/user-interface/task-recorder-quick-reference
- Playwright codegen: locator inference priority, "manually improve it": https://playwright.dev/docs/codegen
- Selenium IDE: multiple locators with fallback: https://www.selenium.dev/selenium-ide/
- Apple Shortcuts: personal automation = trigger + shortcut: https://support.apple.com/guide/shortcuts/apdfbdbd7123/ios
- Apple Shortcuts: testing your actions: https://support.apple.com/guide/shortcuts/test-your-actions-apda75604f37/ios
- AppleScript Editor: Record/Stop, edit timing afterwards: https://support.apple.com/guide/script-editor/record-actions-scpedt1009/mac
- Google Home script editor: YAML starters/conditions/actions: https://support.google.com/googlenest/answer/13460475

### Android platform

- AccessibilityNodeInfo: the identity attributes a rule can match: https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo
- Android fraud-prevention: sensitive content protection and accessibility restrictions: https://developer.android.com/security/fraud-prevention/activities
- Android 15 behaviour changes: https://developer.android.com/about/versions/15/behavior-changes-all
