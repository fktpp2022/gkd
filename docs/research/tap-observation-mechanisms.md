# Observing a user's tap when the app is not built on native Views

**Status:** research complete, October 2026. Input to the recording-mechanism decision (map issue #11) and the go/no-go ticket (#18); not a spec. Companion to `docs/research/click-event-semantics.md` (issue #12) — that note establishes that native-View taps emit `TYPE_VIEW_CLICKED` and that Compose user taps emit no click event at all. This note investigates everything else that could identify the node a user touched, and what each option costs.

**Scope:** privileged input observation, tree-diff inference, the event types GKD does not subscribe to today, touch exploration / gesture channels, and what `AccessibilityEvent.getAction()` can and cannot disambiguate. For each: does it identify the **node**, does it work **without root**, does it work in **Compose / custom-drawn / WebView**, and what it costs.

**Method:** read AOSP `frameworks/base` from the `aosp-mirror` GitHub mirror (`refs/heads/main`, plus tag `android-11.0.0_r1` for one version check), AndroidX Compose from the `androidx/androidx` mirror (`androidx-main`), the locally installed Android SDK's `platforms/android-37.0` public-API stubs and `data/api-versions.xml`, and GKD's own plumbing (`a11y_info.xml`, `A11yExt.kt`, `A11yRuleEngine.kt`, `CompatInputManager.kt`, `PrivilegeContext.kt`, `UiAutomationServiceInfo.kt`, `InputManagerHidden.java`, `build.gradle.kts`). No device was used. Chromium/WebView behaviour is cited from the second pass of the issue-12 research, not re-read here.

**Evidence limits:** `android.googlesource.com` and `developer.android.com` were unreachable from this machine; AOSP files were read from the `aosp-mirror` mirror and the canonical googlesource URLs are given alongside. Anything not visible in the sources read is marked **UNKNOWN** and needs device evidence.

**Legend:** **DOCUMENTED** — stated in official javadoc. **OBSERVED** — read in framework/library source, not stated in docs. **UNKNOWN** — not established.

---

## TL;DR

1. **Only four signals can name the tapped node, and only two are shippable.** The click-event path (native Views, already the map's mechanism), the touch-exploration hover/focus pipeline (works in Compose too — but it rewrites the user's gestures while enabled), a privileged coordinate + hit-test (raw input streams that a non-system process cannot observe without consuming them), and per-node *typed* content-change events (identifies only taps that flip exposed state). Everything else — tree-diff heuristics, scroll/focus/window events, `onGesture` — either identifies nothing node-level or is too ambiguous to act on.
2. **There is no transparent raw-input observation channel.** The two Android mechanisms that deliver raw `MotionEvent`s to an app are both interception, not observation: `AccessibilityService.onMotionEvent` (API 34+) consumes the events it receives — "MotionEvents from sources in `getMotionEventSources()` are **not sent to the rest of the system**" — and `TouchInteractionController` (API 33+) stops delivering events the moment the interaction is delegated back to the apps. `InputManager.monitorGestureInput` is `@hide` and `@deprecated` ("Gesture Monitors are deprecated, in favor of spy windows") behind a system-level permission GKD cannot grant even in privileged mode. GKD's privileged layer is inject-only today; it would have to grow an interception-and-replay engine, not a listener.
3. **The touch-exploration channels identify the node in Compose** — Compose's accessibility delegate hit-tests the hover point and sends `TYPE_VIEW_HOVER_ENTER` naming the hovered virtual view — but the whole pipeline is gated on touch exploration being enabled, and Compose explicitly returns no hover events without it. Enabling touch exploration to record a tap makes the demonstration a foreign gesture language (explore, then double-tap to activate). This is a mode change with a user-facing cost, not a background listener.
4. **The partial discriminator is version-gated.** `event.getAction()` (0 = user tap, `ACTION_CLICK` = GKD's node click) does not exist before Android 12: `mPerformingAction` is absent from `AccessibilityManager.java` at tag `android-11.0.0_r1`. GKD's `minSdk` is 26, so on Android 8–11 no service-side signal at all separates GKD's own click from a user's. It is implementation behaviour, not a documented contract, and Compose never sets it. The engine-pause guard remains the only separator on old APIs.
5. **Recommendation for #18:** assume the click-event path as the only v1 mechanism and scope recording to native-View apps, with WebView decided by one device experiment (Chromium's `WebContentsAccessibilityImpl.handleClicked` does send `TYPE_VIEW_CLICKED`; whether a finger tap reaches it is UNKNOWN). Compose and custom-drawn apps fall into the already-planned honest "nothing was recorded" state, and their users keep picking. Do not gate the go decision on privileged observation or touch exploration: the first is interception with Shizuku/root-level reach, the second changes the gesture the user is trying to demonstrate.

---

## 1. The candidate signals, one by one

### 1.1 Privileged input monitoring — interception, not observation

What GKD's privileged layer offers today (`gkd-app/src/androidMain/kotlin/li/gkd/app/priv/`):

- `CompatInputManager` **injects** input: `tap`/`swipe`/`keyevent` shell out to `cmd input tap` on Android 12+ (`value.asBinder().shellCommand("tap", …)`) or build and inject `MotionEvent`s through `IInputManager.injectInputEvent` on Android 8–11 (`LegacyInputInjector`). Nothing in it reads events back.
- `PrivilegeContext` gates injection on `INJECT_EVENTS` only (`injectInputIfPermitted`). A repo-wide grep for `MONITOR_INPUT` finds nothing.
- GKD's hidden-API input stub is a single constant: `gkd-hidden-api/src/main/java/android/hardware/input/InputManagerHidden.java` declares only `INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH`.

What Android offers a process that wants to *see* touches, per the AOSP sources:

| Mechanism | What it delivers | The catch |
|---|---|---|
| `AccessibilityService.onMotionEvent(MotionEvent)` (API 34+) | Raw `MotionEvent`s of the requested `InputDevice` sources, coordinates included, any app | **Consuming.** DOCUMENTED: "`MotionEvent`s from sources in `getMotionEventSources()` are not sent to the rest of the system." Requesting `SOURCE_TOUCHSCREEN` would swallow every user touch device-wide; the service would have to replay each one itself. Touchscreen is excluded from the `@MotionEventSources` annotation at tag `android-14.0.0_r1` and present in `main`; either way the consume rule applies. Additionally DOCUMENTED: "If this or any enabled service is using `FLAG_REQUEST_TOUCH_EXPLORATION_MODE` then `onMotionEvent` will not receive touchscreen events." |
| `setObservedMotionEventSources` (framework `main`, behind flag `motionEventObserving`) | The non-consuming variant: events "sent to the rest of the input pipeline without being consumed. This service will still be able to see them." | `@TestApi` + `@FlaggedApi` — not public API (absent from the SDK's `api-versions.xml`). Unreachable. |
| `TouchInteractionController` (API 33+) | Display-level motion stream via `registerCallback` | Requires `FLAG_REQUEST_TOUCH_EXPLORATION_MODE` + the `canRequestTouchExplorationMode` manifest capability. DOCUMENTED (class javadoc): events arrive only "when not touch-exploring or delegating. If the service classifies this interaction as touch exploration or delegating the framework will **stop sending motion events to the service for the duration of this interaction**." And: "If `FLAG_REQUEST_TOUCH_EXPLORATION_MODE` is disabled this controller will not receive any motion events because touch interactions are being passed through to the input pipeline unaltered." Observation and pass-through are mutually exclusive per interaction — a recording flow on top of it must hold every gesture (blocking the app), then re-dispatch it. That is interception-and-replay with added latency, not a listener. |
| `InputManager.monitorGestureInput(name, displayId)` | An `InputMonitor` receiving all events on a display | `@hide` `@deprecated` (DOCUMENTED: "Gesture Monitors are deprecated, in favor of spy windows… the gesture monitor receives all events on the display"). Access is system-level; the related `pilferPointers` is `@RequiresPermission(MONITOR_INPUT)`. `MONITOR_INPUT` is not a runtime-grantable permission, so even GKD's root/Shizuku privilege layer cannot `pm grant` it. |
| `/dev/input/event*` raw reads | evdev `ABS_MT_POSITION_X/Y` etc., coordinates in device units | Needs the `input` group (shell has it — this is why `adb shell getevent` works; a normal app uid does not). Root or Shizuku could read it; a non-privileged build cannot. Raw evdev means per-device panel decoding, protocol-B slot tracking, and coordinate mapping that AOSP does not publish as an API. |
| `dumpsys input` / ANR traces | Post-hoc dispatcher state | Retention and rotation make it a race against the queue; no API contract. **UNKNOWN** as a usable channel; not proposed. |

**Node identification.** Raw input gives **coordinates**, not a node. Converting a coordinate to a node means hit-testing the captured accessibility tree — `getRootInActiveWindow()` then walking `getChild()` comparing `getBoundsInScreen()`. There is no public framework "deepest node containing (x, y)" helper (absent from the public `AccessibilityService` / `AccessibilityNodeInfo` surface in `api-versions.xml`); the nearest shipped analogue is inside Compose's own delegate (`hitTestSemanticsAndInteropAt(event.x, event.y)`). GKD already knows the cost of tree reads: its own comments record `safeActiveWindow` taking 554 ms in some apps, and `getTimeoutActiveWindow()` abandons the read after 500 ms. Hit-testing needs the pre-tap tree anyway, which the recording flow already captures — so the incremental cost is the walk, not a new capture. The hit-test answers "deepest node whose bounds contain the point", which in Compose and WebView is a virtual node inside the host — workable, but it is a coordinate guess dressed as a node, and it inherits every overlap/offset ambiguity of bounds matching.

**Verdict.** Privileged observation exists only as interception: consume the gesture and replay it, or read raw evdev with root. Both are new privileged capabilities with large code surface (per AGENTS.md, hidden-API additions in this area carry remap/R8 obligations), both reach only the Shizuku/root subset of users, and the replay path changes the timing of the very gesture being demonstrated. Battery/latency are fine (event-push, session-scoped); reach and code surface are the killers.

### 1.2 Tree-diff inference — identifies *a* changed node, not *the* tapped node

The idea: capture the tree before the tap (the flow already does), watch `TYPE_WINDOW_CONTENT_CHANGED` / `TYPE_WINDOW_STATE_CHANGED` after it (GKD already subscribes), and diff.

What the sources say about how precise the "changed node" can be:

- **Native Views:** `View.notifyViewAccessibilityStateChangedIfNeeded` sends `TYPE_WINDOW_CONTENT_CHANGED` with the changed view as source; ViewRootImpl coalesces per window. A tap that changes a view's content, text, checked state, or visibility produces an event naming that view — but so does every animation, countdown, and list update on screen. GKD's own engine has to throttle exactly this storm (`A11yRuleEngine`: "防止 content 类型事件过快" — content events throttled at 100 ms, and its debug log records negative-timestamp events from some apps).
- **Compose (read from `AndroidComposeViewAccessibilityDelegateCompat.android.kt`, androidx-main):** the delegate maps *specific semantics property changes* to typed, node-naming events: `SemanticsProperties.ToggleableState` → `TYPE_WINDOW_CONTENT_CHANGED` with `CONTENT_CHANGE_TYPE_CHECKED` on that node's virtual view id; `StateDescription` → `CONTENT_CHANGE_TYPE_STATE_DESCRIPTION`; `Selected` on a `Role.Tab` → a selection event; structural changes → `CONTENT_CHANGE_TYPE_SUBTREE` on the nearest semantics-merging ancestor, deduplicated (`subtreeChangedSemanticsNodesIds`). So a tap that flips a Compose checkbox produces an event that **names the node** — no diff needed. But a tap on a plain `Modifier.clickable` button changes no semantics property, and `Modifier.clickable` attaches only `onClick` + role to semantics — so it produces **nothing**: no click event (verified in the Compose artifacts, issue-12 research), no hover without touch exploration (§1.3), no state change. The tree does not change, and there is nothing to diff.
- **Ambiguity without coordinates:** when several nodes do change (a tap dismisses a banner, reorders a list, and flips a checkbox), nothing in the events says which change the user caused. The only ways to pick are heuristics (nearest clickable ancestor of… nothing), which is exactly the guesswork the map's "no invented fallback" decision forbids.

**Cost.** One extra full-tree snapshot + diff per tap: `getChild()` is per-node IPC, prefetch (`FLAG_PREFETCH_*`, `MAX_NUMBER_OF_PREFETCHED_NODES`, API 33+) mitigates but does not remove it, and GKD's measured 554 ms worst-case root fetch bounds the latency. False positives high (animations, countdowns); battery minor during a session; code surface large (snapshot, diff, ranking, ambiguity UI); reach: every app in principle, reliable in none.

**Verdict.** Tree-diff is a corroborating hint at best and a misidentification machine at worst. The one piece worth keeping is free and already in the stream: **typed content-change events** — if a tap flips `ToggleableState`/`Selected`/state description, the event names the node. That is a node-identifying signal with zero new permissions or subscriptions (GKD already receives `CONTENT_CHANGED`), usable as a *bonus* when it happens to fire — never as the primary mechanism, because for the most common target (a plain button) it never fires.

### 1.3 Event types GKD does not subscribe to today

GKD subscribes to `typeWindowContentChanged|typeWindowStateChanged` (`a11y_info.xml`); `A11yExt.isUseful()` drops everything else. Going through the rest:

| Event type | Fires for a plain finger tap? | Identifies the node? |
|---|---|---|
| `TYPE_VIEW_HOVER_ENTER` / `TYPE_VIEW_HOVER_EXIT` (API 14+) | **Only under touch exploration** (or stylus/mouse). `View` sends them from `ACTION_HOVER_ENTER/MOVE/EXIT` (`View.java`, `sendAccessibilityHoverEvent`); the framework converts touches to hovers only in touch-exploration mode (TouchInteractionController javadoc, §1.1). Compose: `dispatchHoverEvent` begins `if (!isTouchExplorationEnabled) { … return false }` — **no hover events at all otherwise**; with it, Compose hit-tests the point and sends `TYPE_VIEW_HOVER_ENTER` naming the hovered virtual view. | **Yes** — source/hovered virtual view is the node under the finger. The strongest non-click node signal; entirely touch-exploration-gated. |
| `TYPE_VIEW_ACCESSIBILITY_FOCUSED` (API 16+) | Only when accessibility focus is set, and `View.requestAccessibilityFocus()` **refuses unless touch exploration is enabled** (`if (!manager.isEnabled() || !manager.isTouchExplorationEnabled()) return false;` — javadoc: "Avoid setting accessibility focus. This is intended to be controlled by screen readers."). Services can set focus via `performAction`; Compose sends it post-scroll for TalkBack. | Yes, but it follows a screen reader's focus, not the user's finger. Only meaningful for TalkBack users, whose device is already in touch exploration. |
| `TYPE_VIEW_SCROLLED` (API 14+) | On scrolling, not on tap; Compose pairs it with a focus event post-scroll for TalkBack. | The scrolling view, not a tapped node; indistinguishable from inertial scrolls. No. |
| `TYPE_WINDOW_FOCUS_CHANGED` | On window focus changes. | Window-level only. No. |
| `TYPE_VIEW_SELECTED` (API 1) | On selection widgets (AdapterView-style). | The selected view in widgets that support selection — a narrow special case, already covered by typed content changes where relevant. |
| `TYPE_TOUCH_INTERACTION_START` / `END` (API 17+) | Fired by the touch-exploration machinery when touch interaction starts/ends. | No source node — "a touch happened" at best, and again touch-exploration-side. |
| `TYPE_GESTURE_DETECTION_START` / `END` (API 14+) | Touch-exploration gesture machinery. | No. |
| `TYPE_VIEW_FOCUSED` / `TYPE_VIEW_TEXT_SELECTION_CHANGED` etc. | Input-focus events, not touch. | No. |

**No `AccessibilityNodeInfo` flag marks "the node just interacted with."** The public identity fields are `getViewIdResourceName()` (API 18, needs `FLAG_REPORT_VIEW_IDS` — GKD already sets `flagReportViewIds`) and `getUniqueId()` (API 33). They identify a node, never an interaction.

**Verdict.** There is no free node-identifying event hiding in the un-subscribed set. The only valuable one — hover — is the touch-exploration channel in disguise, so it is costed under §1.4.

### 1.4 Touch exploration and gesture channels — node-accurate, but they rewrite the demonstration

Requirements, all DOCUMENTED:

- `onGesture(AccessibilityGestureEvent)` (API 30; the `onGesture(int)` form since API 16, deprecated at 30): "**To receive gestures an accessibility service must request that the device is in touch exploration mode** by setting `FLAG_REQUEST_TOUCH_EXPLORATION_MODE`." The payload is a gesture id (`GESTURE_SWIPE_UP`, `GESTURE_DOUBLE_TAP`, …, `GESTURE_PASSTHROUGH = -1`) plus, via `AccessibilityGestureEvent.getMotionEvents()` (API 31), the gesture's motion events. It identifies a **screen-direction gesture, never a node**.
- `FLAG_SEND_MOTION_EVENTS` (API 31): with touch exploration enabled, the service receives the motion events of each detected gesture (and `GESTURE_INVALID` events with their motion events for cancelled ones). Same gate, same non-node payload.
- `TouchInteractionController` (API 33+) and the hover pipeline: see §1.1 and §1.3. `FLAG_REQUEST_TOUCH_EXPLORATION_MODE` needs the `canRequestTouchExplorationMode` capability declared in the service metadata and user confirmation; GKD's `a11y_info.xml` declares neither today.

What it buys when the mode **is** on: the explored node is identified exactly — native Views get hover events naming themselves; Compose hit-tests and names the virtual view; the touch becomes explore, and activation needs a double-tap.

What it costs: the user's demonstration gesture changes (single tap no longer activates; double-tap does), the system shows touch-exploration state, screen readers may already occupy the mode (touch exploration is a global setting — `isTouchExplorationEnabled()` — that the service requests but does not own), and the flow becomes "explore the button, then double-tap it," which is a different motion than the gesture the rule will accompany. Latency and battery are fine; the UX and reach (API 16+ for the hover route, 33+ for the controller route) are the cost; Compose/WebView end-to-end behaviour needs device evidence.

**Verdict.** Technically the most accurate non-click signal, and the only one that names nodes in Compose. But it converts recording from "do the thing you wanted to do" into "use a screen-reader gesture language while GKD watches" — a different feature with a much smaller audience, not a background mechanism. It needs the mode toggle regardless of API level, so it cannot be the invisible default path.

### 1.5 `AccessibilityEvent.getAction()` — a partial filter, Android 12+ only

Restating with the version boundary verified:

- `mPerformingAction` / `event.setAction(mPerformingAction)` are **absent from `AccessibilityManager.java` at tag `android-11.0.0_r1`** (0 occurrences; verified this pass) and present from Android 12 (`android-12.0.0_r34`, per the issue-12 research). GKD's `minSdk` is 26 (`build.gradle.kts`), so on Android 8–11 **every** click event arrives with `getAction() == 0` and no service-side signal at all separates GKD's `performAction` click from the user's tap — including this mechanism's own baseline.
- Even on 12+, it is implementation behaviour, not a documented contract, and Compose never sets `event.action` (issue-12 research), so the Compose case is untouched.
- The hidden origin markers (`MotionEvent.FLAG_INJECTED_FROM_ACCESSIBILITY_TOOL`, `isInjectedFromAccessibilityService()` / `isInjectedFromAccessibilityTool()`) live in the target app's `MotionEvent`. An accessibility event carries no `MotionEvent` and no origin flag — a service-side recorder cannot use them to tell an injected tap from a real one. This also rules out telling GKD's own gesture/privileged taps from user taps by any marker.

**Verdict.** Keep it as the cheap extra filter for the node-click path on Android 12+ (the map already has the engine-pause guard as the real separator); it is a filter on *who* clicked, never an identifier of *what* was clicked.

---

## 2. The five questions

### 2.1 Which signals can identify the node the user touched?

1. **`TYPE_VIEW_CLICKED` / `TYPE_VIEW_LONG_CLICKED`** — native Views (and, pending one device experiment, WebView via Chromium's click handler). The baseline; not restated further.
2. **Touch-exploration hover/accessibility-focus** — names the node under the finger, in native Views and in Compose (Compose hit-tests and names the virtual view; verified in the delegate source, gated on `isTouchExplorationEnabled`). WebView under touch exploration: **UNKNOWN** (Chromium's hover handling not read).
3. **Privileged raw input + hit-test against the captured tree** — yields coordinates, and a node only after a bounds walk of the captured tree. Interception-only channels (§1.1); the hit-test is a coordinate guess, exact only where the a11y tree's bounds match what is on screen.
4. **Typed content-change events** (`CONTENT_CHANGE_TYPE_CHECKED` / `STATE_DESCRIPTION` / selection, source = the changed node) — identifies the node **only when the tap flips exposed state**; never fires for plain buttons. Compose verified; native Views fire the same typed events for their own state changes; WebView: **UNKNOWN**.

Tree-diff beyond those typed events identifies *candidates*, not the node (§1.2). `onGesture` identifies gestures, never nodes (§1.4). No `AccessibilityNodeInfo` flag marks the interacted node (§1.3).

### 2.2 Which work without root, and which are privileged-only?

- **Without root:** click events; typed content-change events; tree-diff; the touch-exploration channels (hover, focus, controller, gestures) — all ordinary accessibility-service surface. Touch exploration needs the `canRequestTouchExplorationMode` capability and user confirmation, not privileges.
- **Privileged-only:** `/dev/input` reads (`input` group — root; shell/Shizuku has the group, a normal app does not); `monitorGestureInput`/spy windows (`MONITOR_INPUT`-class access — **not** obtainable by GKD's privilege layer, which today grants runtime/development-grantable permissions only; `INJECT_EVENTS` does not help: injecting and observing are different capabilities). Note `onMotionEvent` and `TouchInteractionController` are *not* privileged but are consuming/intercepting (§1.1), which is the practical equivalent of privileged for flow design.

### 2.3 Which work in Compose, custom-drawn apps, and WebView?

| Signal | Native Views | Compose | Custom-drawn | WebView |
|---|---|---|---|---|
| Click events | Yes | **No** (no event for user taps; GKD's `performAction` click is the only one) | Only if the app calls `performClick()` | **UNKNOWN** — Chromium's `WebContentsAccessibilityImpl.handleClicked(id)` sends `TYPE_VIEW_CLICKED` (Blink `ax::mojom::Event::kClicked`); whether a *finger tap* reaches that path needs a device test |
| Typed content changes naming the node | Yes, when state flips | Yes for `ToggleableState`/`Selected`/state-description/text changes; **nothing for plain buttons** | Same as any View that changes exposed state | **UNKNOWN** |
| Touch-exploration hover / focus | Yes | **Yes** (delegate hit-tests and names the virtual view; source-read, gated on `isTouchExplorationEnabled`) — needs device confirmation end-to-end | Only for hoverable (clickable-flagged) views; inert canvas gets nothing | **UNKNOWN** (interop hover forwarding exists in the Compose delegate for embedded views; Chromium hover behaviour not read) |
| Raw input + hit-test | Yes (bounds walk) | Yes (Compose nodes expose bounds) | Yes, if the a11y tree exposes the control at all | Yes, on the WebView's/virtual nodes' bounds |
| Tree-diff | Partial, ambiguous | Partial — semantics changes do surface (typed events above), plain clicks change nothing | Partial | Partial, **UNKNOWN** |

WebView remains the one mechanism-level unknown: if the finger-tap → Chromium click path holds, the map's existing click-event mechanism covers WebView unchanged; if not, WebView joins Compose in the honest dead end. One device experiment settles it.

### 2.4 What each costs

"Battery" is minor everywhere — every candidate is event-push or session-scoped polling, none holds a wakelock or polls continuously. The real currencies are UX, correctness, and code surface:

| Mechanism | Battery | Latency | False positives | Code surface | Reach |
|---|---|---|---|---|---|
| Click events (baseline) | none extra | event delivery (~10–300 ms; GKD's logs show slow apps) | none for the node; origin confusion only | manifest + routing (already decided) | all users; native-View apps (+WebView?) |
| Typed content-change capture | none extra (already subscribed) | same stream | low where it fires; **silent for plain buttons** | small: read `getContentChangeTypes()` (API 19+) + source | all users; state-changing controls only |
| Tree-diff | session-scoped CPU | +1 full-tree read per tap (GKD-measured 554 ms worst-case root fetch) | **high** (animations, countdowns — GKD throttles this storm today) | large: snapshot, diff, ranking, ambiguity UI | all users in principle, reliable in none |
| Touch exploration (hover/focus/controller) | none extra | real-time (hover fires during the gesture) | low for the node; **high for UX** — rewrites the demonstration gesture, mode may be occupied by screen readers | medium: `canRequestTouchExplorationMode` + controller/hover wiring + per-toolkit verification | API 16+ hover / 33+ controller; needs mode toggle + user confirmation |
| `onMotionEvent` (API 34+) | none extra | real-time | none for coordinates | large: consumes all requested-source events device-wide; must replay them | all users, Android 14+ — **and it breaks the device while on** |
| Privileged raw input (evdev / monitors) | session-scoped | real-time + decode | none for coordinates; hit-test ambiguity as in §1.1 | **largest**: new privileged capability, hidden API (remap/R8 obligations), per-device evdev decoding, display mapping | Shizuku/root subset only; monitors effectively unreachable |

### 2.5 What the recorder does when nothing identifies the node

The map already decided this, and the evidence confirms the decision is load-bearing rather than a courtesy: **show the honest "nothing was recorded" state and fall back to picking.** No invented timeout, no coordinate fallback — and the rule language gives no way to fake one either: a selector cannot express a coordinate, so a tap that produces no node-identifying signal has nothing the rule model could hold. Per-toolkit expectations for the empty state: Compose plain buttons and inert custom-drawn canvases never emit anything (state-flip controls excepted); WebView is pending the device experiment. One cheap improvement to the message, not the mechanism: the recorder already knows it heard no click event in a session scoped to the captured app, so it can say so without inferring the app's toolkit.

---

## 3. Recommendation for the go/no-go (#18)

**Assume the click-event path and nothing else.** Concretely:

1. **v1 mechanism = the map's standing decision** (`typeViewClicked|typeViewLongClicked` subscription, recorder-only routing, engine paused during the session). Scope recording honestly to native-View apps; that is where real taps are observable today.
2. **Run one device experiment on WebView** (tap in a WebView, check for `TYPE_VIEW_CLICKED` naming a virtual node). Positive → WebView is in scope for free; negative or ambiguous → WebView joins the dead end. This is the only cheap unknown left.
3. **Do not build privileged observation.** There is no transparent channel to build on: `onMotionEvent` consumes the stream it watches, `TouchInteractionController` stops at delegation, gesture monitors are hidden/deprecated/system-gated, and raw evdev is root/Shizuku territory with per-device decoding. The reach (Shizuku/root subset) cannot justify an interception engine plus its R8/remap obligations.
4. **Do not build the touch-exploration route.** It is the only signal that names nodes in Compose, but it costs the mode toggle, a capability flag, a user confirmation, and the natural demonstration gesture — a different, smaller feature, not a background upgrade to recording.
5. **Optional, non-gating, later:** capture typed content-change events (already in the subscription) as a *bonus* node signal for taps that flip exposed state (Compose checkboxes/toggles/switches, native checked views). It never fires for plain buttons, so it can extend coverage slightly after the click-event path ships; it must not be the reason for a go.
6. **Keep the old-API gap in mind for the guard, not the mechanism:** `getAction()` does not exist before Android 12 (API 31), so on Android 8–11 the engine-pause guard is the only thing preventing GKD's own clicks from being recorded. It must be treated as mandatory there, not belt-and-braces.

Net: recording ships as a native-View (and possibly WebView) feature with a truthful boundary at Compose and custom-drawn apps. The alternatives investigated here either identify no node, require privileges GKD cannot grant, require consuming the user's input, or require changing the gesture the user is trying to demonstrate. None of them changes the go/no-go calculus; only the WebView experiment can, marginally, enlarge the "go" reach.

---

## Sources

AOSP `frameworks/base` — read from the `aosp-mirror` GitHub mirror (`refs/heads/main` unless tagged):

- `core/java/android/accessibilityservice/AccessibilityService.java` — `onMotionEvent` javadoc ("not sent to the rest of the system"; touchscreen excluded when touch exploration is requested), `sendMotionEventToCallback`, `onGesture(AccessibilityGestureEvent)` / deprecated `onGesture(int)` ("must request… touch exploration mode"), `GESTURE_PASSTHROUGH` — https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/refs/heads/main/core/java/android/accessibilityservice/AccessibilityService.java — canonical: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/accessibilityservice/AccessibilityService.java
- `core/java/android/accessibilityservice/TouchInteractionController.java` — class javadoc: events only "when not touch-exploring or delegating"; "If `FLAG_REQUEST_TOUCH_EXPLORATION_MODE` is disabled this controller will not receive any motion events"; states — https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/refs/heads/main/core/java/android/accessibilityservice/TouchInteractionController.java
- `core/java/android/accessibilityservice/AccessibilityServiceInfo.java` — `setMotionEventSources` / `getMotionEventSources`, non-consuming `setObservedMotionEventSources` (`@TestApi`, flag `motionEventObserving`), `FLAG_SEND_MOTION_EVENTS`, `FLAG_REQUEST_TOUCH_EXPLORATION_MODE` — https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/refs/heads/main/core/java/android/accessibilityservice/AccessibilityServiceInfo.java (tag `android-14.0.0_r1` for the 14-era `@MotionEventSources` set)
- `core/java/android/view/View.java` — hover emission (`sendAccessibilityHoverEvent`, `TYPE_VIEW_HOVER_ENTER`/`EXIT`), `requestAccessibilityFocus()` gated on `isTouchExplorationEnabled()`, `POPULATING_ACCESSIBILITY_EVENT_TYPES` — https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/refs/heads/main/core/java/android/view/View.java
- `core/java/android/view/accessibility/AccessibilityManager.java` — tag `android-11.0.0_r1`: zero occurrences of `mPerformingAction` (the `getAction()` discriminator does not exist before Android 12) — https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/refs/tags/android-11.0.0_r1/core/java/android/view/accessibility/AccessibilityManager.java
- `core/java/android/hardware/input/InputManager.java` — `monitorGestureInput` (`@hide` `@deprecated`, "Gesture Monitors are deprecated, in favor of spy windows… receives all events on the display"), `pilferPointers` (`@RequiresPermission(MONITOR_INPUT)`) — https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/refs/heads/main/core/java/android/hardware/input/InputManager.java

AndroidX Compose — `androidx/androidx`, branch `androidx-main`:

- `compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/AndroidComposeViewAccessibilityDelegateCompat.android.kt` — `dispatchHoverEvent` gated on `isTouchExplorationEnabled` → `hitTestSemanticsAndInteropAt(event.x, event.y)` → `updateHoveredVirtualView` → `TYPE_VIEW_HOVER_ENTER`/`EXIT`; typed `TYPE_WINDOW_CONTENT_CHANGED` mapping (`ToggleableState` → `CONTENT_CHANGE_TYPE_CHECKED`, `StateDescription`, `Selected`+`Role.Tab`, `CONTENT_CHANGE_TYPE_SUBTREE` on the merging ancestor, `subtreeChangedSemanticsNodesIds` dedup); `sendEventForVirtualView(id, TYPE_VIEW_CLICKED)` — https://raw.githubusercontent.com/androidx/androidx/refs/heads/androidx-main/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/AndroidComposeViewAccessibilityDelegateCompat.android.kt

Local Android SDK (public API surface and API levels) — `C:/Users/Lenovo/AppData/Local/Android/Sdk/platforms/android-37.0/`:

- `data/api-versions.xml` — `onMotionEvent` / `setMotionEventSources` since 34; `TouchInteractionController` since 33; `onGesture(AccessibilityGestureEvent)` since 30, `onGesture(int)` since 16 (deprecated 30); `FLAG_REQUEST_TOUCH_EXPLORATION_MODE` since 16, `FLAG_SEND_MOTION_EVENTS` since 31; `TYPE_VIEW_HOVER_ENTER` since 14, `TYPE_VIEW_ACCESSIBILITY_FOCUSED` since 16, `TYPE_TOUCH_INTERACTION_START` since 17; `getContentChangeTypes()` since 19, `CONTENT_CHANGE_TYPE_STATE_DESCRIPTION` since 30, `CONTENT_CHANGE_TYPE_CHECKED` since 36 (public); prefetch flags and `MAX_NUMBER_OF_PREFETCHED_NODES` since 33; `getUniqueId()` since 33; `getViewIdResourceName()` since 18; **no `onTouchExplored` anywhere in the public API**.
- `android-stubs-src.jar` — `AccessibilityService`, `AccessibilityServiceInfo`, `AccessibilityEvent`, `AccessibilityNodeInfo`, `MotionEvent` public signatures.

GKD sources read for this note:

- `gkd-android/src/main/res/xml/a11y_info.xml` — subscribes `typeWindowContentChanged|typeWindowStateChanged`; flags `flagReportViewIds|flagIncludeNotImportantViews|flagDefault|flagRetrieveInteractiveWindows`; `canPerformGestures`, `canRetrieveWindowContent`, `canTakeScreenshot`; **no** `canRequestTouchExplorationMode` capability.
- `gkd-app/src/androidMain/kotlin/li/gkd/app/a11y/A11yExt.kt` — `isUseful()` accepts only `TYPE_WINDOW_STATE_CHANGED | TYPE_WINDOW_CONTENT_CHANGED`.
- `gkd-app/src/androidMain/kotlin/li/gkd/app/a11y/A11yRuleEngine.kt` — content-event throttling, event-time ordering, 500 ms root-fetch guard, engine self-click paths.
- `gkd-app/src/androidMain/kotlin/li/gkd/app/priv/CompatInputManager.kt` — injection only (`cmd input tap` on S+, `IInputManager.injectInputEvent` on 8–11).
- `gkd-app/src/androidMain/kotlin/li/gkd/app/priv/PrivilegeContext.kt` — injection gated on `INJECT_EVENTS`; granted permissions (`DUMP`, `WRITE_SECURE_SETTINGS`, runtime grants) — no input-monitoring permission.
- `gkd-hidden-api/src/main/java/android/hardware/input/InputManagerHidden.java` — the entire current hidden input surface is one inject-mode constant.
- `build.gradle.kts` — `minSdk = 26` (Android 8–11 are in support range).

Cited from the sibling research (issue #12) and its second pass, not re-read here:

- Compose 1.12.1 artifact check: no `sendAccessibilityEvent` in `androidx.compose.ui`/`foundation`; `performClick()` = `playClickSound(); onClick()`; Compose's delegate sends `TYPE_VIEW_CLICKED` only for `performAction`.
- `AccessibilityManager.sendAccessibilityEvent` fills `event.setAction(mPerformingAction)` from `android-12.0.0_r34`; Compose never sets `event.action`.
- Hidden origin markers (`FLAG_INJECTED_FROM_ACCESSIBILITY_TOOL`, `isInjectedFromAccessibilityService()`) live in the target app's `MotionEvent`; the accessibility event carries neither.
- Chromium: `WebContentsAccessibilityImpl.handleClicked(int id)` sends `TYPE_VIEW_CLICKED`, driven by Blink `ax::mojom::Event::kClicked` (`chromium.googlesource.com`, `content/public/android/java/src/org/chromium/content/browser/accessibility/WebContentsAccessibilityImpl.java`); the finger-tap path to it is UNKNOWN.

Companion notes in this repo:

- `docs/research/click-event-semantics.md` (branch `research/click-event-semantics`) — the native click-event baseline this note builds on.
- `docs/research/macro-recorder-prior-art.md` (branch `research/macro-recorder-prior-art`) — no shipped product observes taps beyond native click surfaces either.
