# Accessibility click event semantics — what a recorder may rely on

**Status:** research complete, October 2026. Input to the recording-mechanism decision (map issue #11); not a spec.

**Scope:** what an accessibility service actually receives for `TYPE_VIEW_CLICKED` / `TYPE_VIEW_LONG_CLICKED`: when each of GKD's own action paths produces one, what the event's source node carries and whether it survives navigation, what happens in Compose and in WebView/canvas surfaces, and whether a user tap can be told apart from a tap GKD caused.

**Method:** read AOSP `frameworks/base` source and the locally installed Android SDK's public-API stubs plus `data/api-versions.xml`; read GKD's own a11y plumbing (`a11y_info.xml`, `A11yExt.kt`, `A11yRuleEngine.kt`, `GkdAction.kt`, `CompatInputManager.kt`, `UiAutomationServiceInfo.kt`). No device was used.

**Evidence limits:** every platform statement below comes from source that was read, not from a running device. Anything whose behaviour is not visible in the source — notably Chromium/WebView — is marked **UNKNOWN** and needs device evidence. `android.googlesource.com` was unreachable from this machine, so AOSP files were read from the `aosp-mirror` GitHub mirror of `platform/frameworks/base` (`refs/heads/main`, i.e. the Android `main` branch); the canonical googlesource URL is given alongside each mirror URL.

**Legend:** **DOCUMENTED** — stated in official javadoc. **OBSERVED** — read in framework source, not stated in docs. **UNKNOWN** — not established by this research.

---

## TL;DR

1. **There is exactly one native emission point.** `View.performClick()` calls `sendAccessibilityEvent(TYPE_VIEW_CLICKED)`; `View.performLongClickInternal()` calls `sendAccessibilityEvent(TYPE_VIEW_LONG_CLICKED)`. A user tap, an accessibility-service `performAction(ACTION_CLICK)`, an accessibility gesture, and a privileged injected tap all funnel through those two methods. For a classic View app **all four produce the same event**, and the event does not say which one caused it.
2. **Compose breaks that symmetry, in the wrong direction.** A user tap in a Compose app produces **no click event at all** — `Modifier.clickable`'s `performClick()` plays a sound and calls `onClick()`, and nothing else. An accessibility-initiated `ACTION_CLICK` **does** produce one: Compose's accessibility delegate sends `TYPE_VIEW_CLICKED` explicitly. So in Compose, GKD's own node click is visible to a recorder and the user's tap is not — the inverse of what a recorder needs.
3. **The event's source node is not a snapshot.** `event.getSource()` re-queries the app process for the recorded node id at the moment the service calls it. After the tap navigated away, that node may no longer exist and `getSource()` can return null. Read the node promptly; and note the click listener has already run, so the content reflects post-click state.
4. **One documented discriminator exists, and it is partial and version-gated.** `AccessibilityEvent.getAction()` — "Gets the performed action that triggered this event" — is set to `AccessibilityNodeInfo.ACTION_CLICK` when the click was triggered by an accessibility service's `performAction(ACTION_CLICK)`, and stays `0` for a user tap. It does **not** distinguish a `dispatchGesture` tap or a privileged injected tap from a user tap, and the mechanism that fills it in **does not exist before Android 12** (`mPerformingAction` is absent from `AccessibilityManager.java` at `android-11.0.0_r1`). GKD's `minSdk` is 26, so on Android 8–11 there is no discriminator at all.
5. **Long click and click differ in ordering.** `performLongClickInternal()` sends the event **before** invoking the long-click listener; `performClick()` sends it **after** invoking the click listener. For `TYPE_VIEW_CLICKED` the app has already reacted (and may already have navigated); for `TYPE_VIEW_LONG_CLICKED` it has not.
6. **Consequence for the map's mechanism decision:** "subscribe to `typeViewClicked|typeViewLongClicked` and record" is *not* a general "record the user's tap" mechanism. It records native View taps, and it records GKD's own node clicks with a distinguishing `event.action`. It does not record Compose user taps at all, and it records gesture/privileged taps indistinguishably from user taps.
7. **The event can be lost entirely.** If the click removes its own target view from the hierarchy, `View.sendAccessibilityEventUncheckedInternal` posts the event with a delay and sends it only if the view becomes shown again — so a tap that deletes its own target can produce no event at all (§2.3).

---

## 1. Where the event is created

### 1.1 The single native emission point

`frameworks/base/core/java/android/view/View.java`:

```java
public boolean performClick() {
    notifyAutofillManagerOnClick();
    final boolean result;
    final ListenerInfo li = mListenerInfo;
    if (li != null && li.mOnClickListener != null) {
        playSoundEffect(SoundEffectConstants.CLICK);
        li.mOnClickListener.onClick(this);
        result = true;
    } else {
        result = false;
    }
    sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
    notifyEnterOrExitForAutoFillIfNeeded(true);
    return result;
}
```

and

```java
private boolean performLongClickInternal(float x, float y) {
    sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
    ...
}
```

**OBSERVED.** Two consequences that matter for a recorder:

- The click event is sent **unconditionally**, even when the view has no `OnClickListener` (`result` is then `false` but the event is still sent).
- `performClick()` fires the listener **before** the event; `performLongClickInternal()` fires the event **before** the listener.

The touch path into `performClick()` is `View.onTouchEvent`, `case MotionEvent.ACTION_UP`, which posts a `PerformClick` runnable calling `performClickInternal()` — and only for views that are clickable / long-clickable / context-clickable:

```java
final boolean clickable = ((viewFlags & CLICKABLE) == CLICKABLE
        || (viewFlags & LONG_CLICKABLE) == LONG_CLICKABLE)
        || (viewFlags & CONTEXT_CLICKABLE) == CONTEXT_CLICKABLE;
...
if (clickable || (viewFlags & TOOLTIP) == TOOLTIP) {
    switch (action) {
        case MotionEvent.ACTION_UP:
            ...
            if (!mHasPerformedLongPress && !mIgnoreNextUpEvent) {
                ...
                if (!post(mPerformClick)) { performClickInternal(); }
```

**DOCUMENTED** (`View.onTouchEvent` javadoc): "If this method is used to detect click actions, it is recommended that the actions be performed by implementing and calling `performClick()`. This will ensure consistent system behavior, including: obeying click sound preferences, dispatching OnClickListener calls, **handling `AccessibilityNodeInfo#ACTION_CLICK` when accessibility features are enabled**."

### 1.2 The four ways a click can be caused

| Trigger | Path | `TYPE_VIEW_CLICKED`? | Evidence |
|---|---|---|---|
| **User tap** | `View.onTouchEvent` ACTION_UP → `PerformClick` → `performClickInternal()` → `performClick()` | **Yes**, for native clickable Views | OBSERVED |
| **Accessibility service `performAction(ACTION_CLICK)`** | `AccessibilityNodeInfo.performAction` → `AccessibilityInteractionClient.performAccessibilityAction` → app-process `AccessibilityInteractionController.performAccessibilityActionUiThread` → `View.performAccessibilityAction` → `performAccessibilityActionInternal` → `performClickInternal()` | **Yes**, same event, same code path | OBSERVED |
| **Accessibility gesture (`dispatchGesture`)** | `AccessibilityService.dispatchGesture` → `GestureDescription.MotionEventGenerator` → `TouchPoint` streams → system injects real `MotionEvent`s → ordinary input dispatch → the app's view hierarchy | **Yes**, for native clickable Views (it is an ordinary touch at the View level) | OBSERVED |
| **Privileged input injection** (`InputManager.injectInputEvent`, `cmd input tap`) | `InputManagerService.injectInputEvent` → native input pipeline → ordinary input dispatch → the app's view hierarchy | **Yes**, for native clickable Views | OBSERVED |

(`performGlobalAction` — back/home/recents — produces **no** click event; it is not a view interaction.)

The accessibility-service path in `View.java`:

```java
public boolean performAccessibilityActionInternal(int action, @Nullable Bundle arguments) {
    ...
    switch (action) {
        case AccessibilityNodeInfo.ACTION_CLICK: {
            if (isClickable()) {
                performClickInternal();
                return true;
            }
        } break;
        case AccessibilityNodeInfo.ACTION_LONG_CLICK: {
            if (isLongClickable()) {
                performLongClick();
                return true;
            }
        } break;
```

**DOCUMENTED** (`AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK` javadoc): the action means "the UI element that implements this should send a `AccessibilityEvent.TYPE_VIEW_CLICKED` event … the default handling of this action is to call `View.performClick()`". So the framework's own contract is that an accessibility-initiated click produces exactly the same event as a user click.

and the app-process dispatcher in `core/java/android/view/AccessibilityInteractionController.java`:

```java
final View target = findViewByAccessibilityId(accessibilityViewId);
if (target != null && isShown(target) && isVisibleToAccessibilityService(target)) {
    mA11yManager.notifyPerformingAction(action);
    if (action == R.id.accessibilityActionClickOnClickableSpan) {
        succeeded = handleClickableSpanActionUiThread(target, virtualDescendantId, arguments);
    } else {
        AccessibilityNodeProvider provider = target.getAccessibilityNodeProvider();
        if (provider != null) {
            succeeded = provider.performAction(virtualDescendantId, action, arguments);
        } else if (virtualDescendantId == AccessibilityNodeProvider.HOST_VIEW_ID) {
            succeeded = target.performAccessibilityAction(action, arguments);
        }
    }
    mA11yManager.notifyPerformingAction(0);
}
```

Two things to note: the action only runs if the target `isClickable()` (otherwise `performAction` returns false and **no event is produced**), and the `notifyPerformingAction(...)` bracket is what gives `event.getAction()` its value (see §4).

`AccessibilityService.dispatchGesture` does **not** go through that bracket — it hands `TouchPoint` streams to the system, which injects real touch events:

```java
public final boolean dispatchGesture(@NonNull GestureDescription gesture,
        @Nullable GestureResultCallback callback, @Nullable Handler handler) {
    ...
    List<GestureDescription.GestureStep> steps =
            MotionEventGenerator.getGestureStepsFromGestureDescription(gesture, sampleTimeMs);
    ...
    connection.dispatchGesture(mGestureStatusCallbackSequence,
            new ParceledListSlice<>(steps), gesture.getDisplayId());
```

**DOCUMENTED** (`AccessibilityService.dispatchGesture` javadoc): the gesture is "dispatched as if it were performed directly on the screen by a user". The system injects the synthesized `MotionEvent`s with the hidden policy flag `FLAG_INJECTED_FROM_ACCESSIBILITY`, but that flag lives on the `MotionEvent` inside the target app and never reaches the accessibility event.

### 1.3 Which of GKD's own action paths emit a click event

From `gkd-app/src/androidMain/kotlin/li/gkd/app/data/GkdAction.kt`:

| GKD action | Mechanism | Emits a click event? |
|---|---|---|
| `ClickNode` | `node.performAction(AccessibilityNodeInfo.ACTION_CLICK)` | **Yes** for native Views (same `performClick()` path as a user tap); **yes** for Compose (via Compose's own delegate, §3.1) |
| `Click` | `ClickNode` if `node.isClickable`, else `ClickCenter` | Depends on the branch taken |
| `ClickCenter` | privileged `tap(x, y)` if the privileged context is available, else `AccessibilityService.dispatchGesture` | **Yes** for native Views (both branches are ordinary touch at the View level); **no** for Compose (both branches are touch, and Compose does not emit a click event for touch) |
| `LongClickNode` | `node.performAction(ACTION_LONG_CLICK)` | **Yes** (`TYPE_VIEW_LONG_CLICKED`), if the node `isLongClickable` |
| `LongClickCenter` | privileged long tap, else `dispatchGesture` with a 500 ms stroke | **Yes** for native Views |

The privileged tap is a genuine touch injection and therefore behaves like a user tap at the View level: on Android 12+ it shells out to `cmd input tap` (`CompatInputManager.tap` → `value.asBinder().shellCommand("tap", …)`), and on Android 8–11 it injects `MotionEvent`s built with `MotionEvent.obtain(…)` and no special flags through `IInputManager.injectInputEvent` (`CompatInputManager.LegacyInputInjector`). Neither path sets any marker that survives into the accessibility event.

**OBSERVED.** So for a native View app, *every* GKD click path produces exactly the event a user tap produces. The only path that is distinguishable at all is the `performAction` path, and only via `event.getAction()` (§4).

---

## 2. What the event carries, and whether the node survives

### 2.1 What is filled in

`View.onInitializeAccessibilityEventInternal` (OBSERVED) fills the event before it is dispatched:

```java
public void onInitializeAccessibilityEventInternal(AccessibilityEvent event) {
    event.setSource(this);
    event.setClassName(getAccessibilityClassName());
    event.setPackageName(getContext().getPackageName());
    event.setEnabled(isEnabled());
    event.setContentDescription(mContentDescription);
    event.setScrollX(getScrollX());
    event.setScrollY(getScrollY());
    ...
```

`event.setText()` is filled separately: `View` declares

```java
private static final int POPULATING_ACCESSIBILITY_EVENT_TYPES =
        AccessibilityEvent.TYPE_VIEW_CLICKED
        | AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
        | AccessibilityEvent.TYPE_VIEW_SELECTED
        | AccessibilityEvent.TYPE_VIEW_FOCUSED
        | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        | ...
```

and `sendAccessibilityEventUncheckedInternal` calls `dispatchPopulateAccessibilityEvent(event)` for those types, so a click event's text list carries the clicked view's text content.

Summary of the fields a recorder can read off a native-View click event:

| Field | Value | Note |
|---|---|---|
| `getEventType()` | `TYPE_VIEW_CLICKED` (1) / `TYPE_VIEW_LONG_CLICKED` (2) | |
| `getEventTime()` | `SystemClock.uptimeMillis()` at send time | **not** wall clock — `AccessibilityManager.sendAccessibilityEvent` does `event.setEventTime(SystemClock.uptimeMillis())` (OBSERVED) |
| `getPackageName()` | the target app's package | same for a user tap and a GKD tap |
| `getClassName()` | the clicked View's class name | e.g. `android.widget.TextView` |
| `getContentDescription()` | the view's `contentDescription` | |
| `getText()` | the view's text content | populated for click types |
| `getAction()` | the action that triggered the event | see §4 |
| `getSource()` | the source node | see §2.2 |
| bounds | **not on the event** | read from `getSource().getBoundsInScreen()` |

GKD already relies on `eventTime` being monotonic: `A11yRuleEngine.onA11yEvent` discards events whose `event.eventTime < lastEventTime`, with the comment "某些应用会发送负时间事件" (some apps send negative-time events).

### 2.2 The source node is not a snapshot

**OBSERVED, and this is the load-bearing finding for §"is the node still usable after the tap navigated away".**

`AccessibilityEvent` extends `AccessibilityRecord` (true back to at least Android 9 — `public final class AccessibilityEvent extends AccessibilityRecord implements Parcelable` in `android-9.0.0_r1`), and `AccessibilityRecord` stores a **node id**, not a node:

```java
public void setSource(@Nullable View source) {
    setSource(source, AccessibilityNodeProvider.HOST_VIEW_ID);
}

public void setSource(@Nullable View root, int virtualDescendantId) {
    ...
    mSourceNodeId = AccessibilityNodeInfo.makeNodeId(rootViewId, virtualDescendantId);
}
```

and `getSource()` performs a fresh IPC query against the app process at the moment the service calls it:

```java
public @Nullable AccessibilityNodeInfo getSource() {
    return getSource(GET_SOURCE_PREFETCH_FLAGS);
}

public @Nullable AccessibilityNodeInfo getSource(int prefetchingStrategy) {
    if (mConnectionId == UNDEFINED_CONNECTION_ID
            || (mSourceWindowId == AccessibilityWindowInfo.UNDEFINED_WINDOW_ID)
            || (AccessibilityNodeInfo.getAccessibilityViewId(mSourceNodeId)
                    == AccessibilityNodeInfo.UNDEFINED_ITEM_ID)) {
        return null;
    }
    AccessibilityInteractionClient client = AccessibilityInteractionClient.getInstance();
    return client.findAccessibilityNodeInfoByAccessibilityId(mConnectionId, mSourceWindowId,
            mSourceNodeId, false, prefetchingStrategy, null);
}
```

and the app-process side only fills a node in if the target view is still shown:

```java
if (requestedView != null && isShown(requestedView)) { ... populate ... }
```

Consequences, all OBSERVED from the source:

- After the tap navigated away, the recorded node id may no longer resolve. `getSource()` can return **null**, or a node that is no longer the thing the user tapped.
- The node's content is fetched **when the service reads it**, not when the event was created — so the text/description/bounds a recorder sees are the *current* ones, not necessarily the ones on screen at tap time. Since `performClick()` runs the listener **before** sending the event, the app has already reacted by the time the event exists.
- `AccessibilityNodeInfo` has **no public staleness flag**: `isStale()` does not exist in the public SDK. The `api-versions.xml` shipped with the SDK lists `isStale()Z` exactly once in the whole public API, and it belongs to `org.apache.http.HttpConnection`; the string does not occur in AOSP's `AccessibilityNodeInfo.java` at `android-11.0.0_r1` or `android-16.0.0_r3` either. The public way to ask "is this node still in the tree" is `AccessibilityNodeInfo.refresh()` — **DOCUMENTED** as "Refreshes this info with the latest state of the view it represents", returning false when the view is no longer in the view tree.
- There is no public `android.view.accessibility.AccessibilityNode` class (the reference page is a 404 and there has been no such source file since Android 11). The public node-identity equivalents are `AccessibilityNodeInfo.getUniqueId()` (API 33) and `getViewIdResourceName()` (API 18, and only with `FLAG_REPORT_VIEW_IDS`, which GKD already sets in `a11y_info.xml`).

GKD already has to cope with the fragility of this: `A11yExt.safeSource` wraps `source` in a try/catch for "Cannot perform this action on a not sealed instance", and `A11yEvent.safeSource` re-reads `event.source` on demand rather than caching it.

**DOCUMENTED caveat on the event object itself:** `AccessibilityEvent` is recycled/reused — `AccessibilityEvent.recycle()` exists and GKD's `A11yExt` comments that "AccessibilityEvent 的 clear 方法会在后续时间被某些系统调用导致内部数据丢失". A recorder must copy what it needs out of the event synchronously, on the service's callback thread.

### 2.3 The event can be dropped or delayed after the click

**OBSERVED** in `View.sendAccessibilityEventUncheckedInternal`:

```java
boolean detached = detached();
if (!isShown() && !isWindowDisappearedEvent && !detached) {
    return;
}
...
SendAccessibilityEventThrottle throttle = getThrottleForAccessibilityEvent(event);
if (throttle != null) {
    throttle.post(event);
} else if (!isWindowDisappearedEvent && detached) {
    // Views could be attached soon later. Accessibility events during this temporarily
    // detached period should be sent too.
    postDelayed(() -> {
        if (AccessibilityManager.getInstance(mContext).isEnabled() && isShown()) {
            requestParentSendAccessibilityEvent(event);
        }
    }, ViewConfiguration.getSendRecurringAccessibilityEventsInterval());
} else {
    requestParentSendAccessibilityEvent(event);
}
```

Two consequences for a recorder:

- If the click **removed the clicked view from the hierarchy** (a list row deleted, a dialog dismissing itself), the view is detached, and the click event is posted with a delay and then sent **only if the view is shown again**. In the common case it never is, so **the click event is lost**. A tap that removes its own target can therefore produce no event at all.
- If the view is still attached but not shown, the event is dropped outright.

A navigation that starts a new activity usually leaves the old activity's views attached and visible (`isShown()` is about view visibility, not about the window being on screen), so the event is delivered normally in that case. The loss case is specifically "the click destroys its own target view".

### 2.4 Compose and WebView specifics are in §3

---

## 3. Compose, WebView, and canvas surfaces

### 3.1 Compose: the user's tap produces no click event; the service's click does

**OBSERVED** from `androidx/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/AndroidComposeViewAccessibilityDelegateCompat.android.kt`:

```kotlin
AccessibilityNodeInfoCompat.ACTION_CLICK -> {
    val result =
        node.unmergedConfig.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
    sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
    return result ?: false
}
AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> {
    return node.unmergedConfig.getOrNull(SemanticsActions.OnLongClick)?.action?.invoke()
        ?: false
}
```

and from `androidx/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/Clickable.kt`:

```kotlin
protected fun performClick() {
    playClickSound()
    onClick()
}
```

Consequences:

- **A real user tap in a Compose app emits no `TYPE_VIEW_CLICKED`.** `Modifier.clickable`'s `performClick()` plays a sound and invokes `onClick()`; it does not send an accessibility event, and `AndroidComposeView` never calls `View.performClick()` (its touch handling goes through `getOrCreateGestureDetector().onTouchEvent(motionEvent)` and Compose's pointer-input layer).
- **A `performAction(ACTION_CLICK)` against a Compose node does emit `TYPE_VIEW_CLICKED`** — Compose's delegate sends it explicitly. This is GKD's `ClickNode` path. So in a Compose app a recorder would see GKD's own click and *not* the user's.
- Compose's `ACTION_LONG_CLICK` sends **no** event at all (the branch above returns without sending one).
- Compose's event construction (`createEvent`) sets `className` to the Compose host's class name, `packageName` to the app package, and `setSource(view, virtualViewId)` — a virtual descendant id inside the host view. It does **not** set `event.action`, so the `getAction()` discriminator of §4 does not work for Compose either.

This is the single most consequential finding for the map's stated mechanism ("add `typeViewClicked|typeViewLongClicked` to the service subscription and route those events to a recorder only"). In a Compose app that subscription records GKD's own clicks and ignores the user's.

### 3.2 WebView: the provider does send the event; whether a finger tap produces one is UNKNOWN

AOSP's `core/java/android/webkit/WebView.java` shows only delegation:

```java
public boolean onTouchEvent(MotionEvent event) {
    return mProvider.getViewDelegate().onTouchEvent(event);
}
...
public boolean performAccessibilityActionInternal(int action, Bundle arguments) {
    return mProvider.getViewDelegate().performAccessibilityAction(action, arguments);
}
```

The WebView `View` itself never calls `performClick()`, so any event must come from the WebView provider — Chromium. **OBSERVED** in Chromium's `content/public/android/java/src/org/chromium/content/browser/accessibility/WebContentsAccessibilityImpl.java`:

```java
@CalledByNative
private void handleClicked(int id) {
    sendAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_CLICKED);
}
```

`handleClicked` is called from native when Blink's accessibility tree reports an `ax::mojom::Event::kClicked` (`content/browser/accessibility/browser_accessibility_manager_android.cc`). So the WebView provider **does** emit `TYPE_VIEW_CLICKED` for accessibility click events, naming the virtual descendant id inside the WebView.

**UNKNOWN:** whether a plain finger tap on a web element makes Blink fire that AX click event at all (Blink's AX event-firing code was not reachable from this machine), and which node a service would see. This needs device evidence. (GKD's own history notes WebView/Flutter/Compose limitations in gkd-kit/gkd discussion #526.)

### 3.3 Custom canvas Views: DOCUMENTED + OBSERVED

A custom `View` that draws its own UI and overrides `onTouchEvent` **without** calling `performClick()` produces **no** click event — `performClick()` is the only thing that sends it, and the framework does not call it on the app's behalf. The `View.onTouchEvent` javadoc says so explicitly (§1.1): the app is expected to call `performClick()` so that `ACTION_CLICK` works when accessibility is enabled. A canvas game or a custom-drawn control therefore has no click event to record, even though the accessibility tree may expose a clickable node with an `ACTION_CLICK` action.

Conversely, a canvas control that *does* call `performClick()` behaves like any other View and does produce the event.

---

## 4. Telling a user's tap from a tap GKD caused

| Signal | Available? | Evidence |
|---|---|---|
| `event.getPackageName()` | **No** — it is the target app's package in both cases | OBSERVED (`View.onInitializeAccessibilityEventInternal` sets it from the view's context) |
| Source node identity | **No** — it is the same node | OBSERVED |
| `event.getEventTime()` | **Partially** — it is `SystemClock.uptimeMillis()` at send time, so it can be compared against the timestamp of an action GKD itself issued | OBSERVED |
| `event.getAction()` | **Yes, for the `performAction` path only, and only on Android 12+** | DOCUMENTED + OBSERVED (see §4.1) |
| `AccessibilityNodeInfo.getUniqueId()` / `getViewIdResourceName()` | **No** — they identify the node, not the origin of the event | OBSERVED (`getUniqueId()` is API 33; `getViewIdResourceName()` is API 18 and needs `FLAG_REPORT_VIEW_IDS`) |
| `MotionEvent.FLAG_IS_GENERATED_GESTURE`, `FLAG_INJECTED_FROM_ACCESSIBILITY_TOOL`, `MotionEvent.isInjectedFromAccessibilityService()` / `isInjectedFromAccessibilityTool()` | **No** — all are hidden (`@hide` / `@TestApi`) and live on the `MotionEvent` inside the target app; the accessibility event carries no `MotionEvent` and has no origin flag | OBSERVED |
| `MotionEvent.isFromRealUser()` | **Does not exist** in the public SDK | OBSERVED (zero occurrences of `isFromRealUser` in the SDK's `api-versions.xml`; not present in AOSP `MotionEvent.java` or `InputEvent.java`) |

### 4.1 `event.getAction()` — the one documented discriminator

**DOCUMENTED** javadoc in `AccessibilityEvent.java`:

```java
/**
 * Sets the performed action that triggered this event.
 * <p>
 * Valid actions are defined in {@link AccessibilityNodeInfo}:
 * <ul>
 * <li>{@link AccessibilityNodeInfo#ACTION_ACCESSIBILITY_FOCUS}
 * <li>{@link AccessibilityNodeInfo#ACTION_CLEAR_ACCESSIBILITY_FOCUS}
 * <li>{@link AccessibilityNodeInfo#ACTION_CLEAR_FOCUS}
 * <li>{@link AccessibilityNodeInfo#ACTION_CLEAR_SELECTION}
 * <li>{@link AccessibilityNodeInfo#ACTION_CLICK}
 * <li>etc.
 * </ul>
 * ...
 */
/**
 * Gets the performed action that triggered this event.
 *
 * @return The action.
 */
public int getAction() { return mAction; }
```

**OBSERVED** mechanism: `AccessibilityManager.sendAccessibilityEvent` fills it in when the platform knows which accessibility action is running:

```java
public void sendAccessibilityEvent(AccessibilityEvent event) {
    ...
    event.setEventTime(SystemClock.uptimeMillis());
    if (event.getAction() == 0) {
        event.setAction(mPerformingAction);
    }
    ...
```

`mPerformingAction` is set by the hidden `AccessibilityManager.notifyPerformingAction(int actionId)`, which `AccessibilityInteractionController.performAccessibilityActionUiThread` calls with the action id for the duration of the action (§1.2) and resets to `0` afterwards. `View.performClick()` does not set an action itself.

So, for a native View app:

- **User tap** → `event.getAction() == 0` (no action was being performed).
- **GKD `ClickNode` / `Click` (node path)** → `event.getAction() == AccessibilityNodeInfo.ACTION_CLICK` (16).
- **GKD `ClickCenter`** (privileged `tap`, or `dispatchGesture`) → `event.getAction() == 0` — **indistinguishable from a user tap**.
- **Compose** → `event.getAction() == 0` in all cases (Compose's `createEvent` never sets it).

Caveat: `mPerformingAction` is a single field on the app process's `AccessibilityManager`, set for the whole duration of the action. The click event is dispatched synchronously inside that window (`ViewRootImpl.requestSendAccessibilityEvent` → `mAccessibilityManager.sendAccessibilityEvent(event)` on the app's UI thread), so the value is normally correct — but any *other* accessibility event emitted by the app during the same window would also inherit the action id.

**Version gate — the discriminator only exists on Android 12+.** The `mPerformingAction` field and the `event.setAction(mPerformingAction)` line are **absent** from `AccessibilityManager.java` at tag `android-11.0.0_r1` and **present** from `android-12.0.0_r34` onwards (`android-12.0.0_r34`: field at line 243, `event.setAction` at line 619, `notifyPerformingAction` at line 1064; `android-13.0.0_r1`: 256 / 657 / 1146). GKD's `minSdk` is 26 (`build.gradle.kts:36`), so on **Android 8–11 the discriminator does not exist at all** and every click event arrives with `getAction() == 0`. On those versions the only way to keep GKD's own clicks out of a recording is the "engine does not act during the session" guard.

Also note this is **implementation behaviour, not a documented contract**: `getAction()` is not listed among the properties the documentation defines for `TYPE_VIEW_CLICKED`, so OEM builds are free to differ — another reason it can only be a cheap extra filter, never the primary mechanism.

### 4.2 Time correlation

`event.getEventTime()` is `SystemClock.uptimeMillis()` at the moment the framework sends the event, so it is directly comparable with GKD's own timestamps if they also use `SystemClock.uptimeMillis()`. This is the only remaining lever, and it is a heuristic rather than a platform guarantee: GKD would have to remember the window in which each of its own actions was in flight. Note also that GKD already discards "negative time" events (`A11yRuleEngine.onA11yEvent`), so the event-time stream is not perfectly monotonic across all apps.

---

## 5. What this means for the recording decision

Mapped onto the standing decisions in map issue #11:

1. **"Add `typeViewClicked|typeViewLongClicked` to the service subscription and route those events to a recorder only"** works for native View apps, and the `UiAutomation` path inherits it for free (`UiAutomationServiceInfo.createUiAutomationServiceInfo()` copies `rawInfo.eventTypes` from the manifest, so one manifest change covers both service modes). The matcher still ignores the events (`A11yExt.isUseful()` only accepts `STATE_CHANGED or CONTENT_CHANGED`).
2. **"While a recording session is live, GKD's rule engine does not act"** is not optional. GKD's own `ClickNode` produces a `TYPE_VIEW_CLICKED` with `getAction() == ACTION_CLICK` on Android 12+; `ClickCenter` produces one with `getAction() == 0`, identical to a user tap. The guard is the only thing that separates them on the gesture/privileged paths, and on Android 8–11 it is the *only* thing that separates them at all, because the `getAction()` marker does not exist there (§4.1).
3. **The "evidence bar" (real taps in native, Compose and WebView apps) now has a concrete obstacle to test:** in a Compose app the user's tap produces no event at all, while GKD's own node click does. If the recording session is scoped to the captured app and GKD's engine is paused during the session, a Compose app would record *nothing* — which the map already plans for as an honest "nothing was recorded" state, but the reason is now known: it is a platform fact, not a bug.
4. **"The event's node goes through map #1's existing pick-resolution"** must account for §2.2: the source node is resolved by a fresh IPC query when the service reads it, and the click listener has already run. The pre-tap capture remains the reliable basis for uniqueness (as the map already decided); the event node is best-effort.
5. **Vocabulary: tap and long-press only.** Long press via `performAction(ACTION_LONG_CLICK)` produces `TYPE_VIEW_LONG_CLICKED`; the `getAction()` value would be `ACTION_LONG_CLICK` (32). But Compose's `ACTION_LONG_CLICK` branch sends **no** event, and Compose's user long-press sends none either.
6. **A recording session can miss taps entirely (§2.3).** A tap that removes its own target view (a deleted list row, a self-dismissing dialog) produces no click event, because the framework posts the event with a delay and only sends it if the view becomes shown again. Any recording flow needs the map's planned honest "nothing was recorded" state to be reachable for this reason too, not only for Compose.

---

## 6. Open questions that need device evidence

- **[device]** Does a user tap on a web element make Blink fire the AX click event that Chromium turns into `TYPE_VIEW_CLICKED`, and which node does a service see (the WebView node, or a virtual descendant)?
- **[device]** Does a real user long-press in a native app produce `TYPE_VIEW_LONG_CLICKED` with `getAction() == 0`?
- **[device]** Does `getAction()` really read `ACTION_CLICK` for a service-initiated `performAction` on a real Android 12+ device, and `0` for a user tap? (The source says so; the source is the platform, but OEM builds differ and the behaviour is not a documented contract.)
- **[device]** Is the source node still resolvable via `getSource()` after the tap navigated away, on the Android versions GKD supports? (The source says it is re-queried by node id and only populated when the target view `isShown`; whether the node id survives depends on the app and the OEM.)
- **[device]** Do OEM-modified Compose builds (e.g. Chinese ROM forks) emit click events for user taps, unlike AOSP Compose?

---

## Sources

AOSP `frameworks/base` — read from the `aosp-mirror` GitHub mirror (`refs/heads/main`); the canonical URL is given in each row.

- `core/java/android/view/View.java` — https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/refs/heads/main/core/java/android/view/View.java — canonical: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/View.java
  (mirror of `android-9.0.0_r1` / `android-13..16` used for the version comparison)
- `core/java/android/view/AccessibilityInteractionController.java` — https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/refs/heads/main/core/java/android/view/AccessibilityInteractionController.java
- `core/java/android/view/ViewRootImpl.java` — https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/refs/heads/main/core/java/android/view/ViewRootImpl.java
- `core/java/android/view/accessibility/AccessibilityEvent.java` — https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/refs/heads/main/core/java/android/view/accessibility/AccessibilityEvent.java (and tags `android-9.0.0_r1` … `android-16.0.0_r1` under `refs/tags/`)
- `core/java/android/view/accessibility/AccessibilityRecord.java` — https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/refs/heads/main/core/java/android/view/accessibility/AccessibilityRecord.java (and tags `android-9.0.0_r1`, `android-11.0.0_r1`, `android-12.0.0_r1`)
- `core/java/android/view/accessibility/AccessibilityNodeInfo.java` — https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/refs/heads/main/core/java/android/view/accessibility/AccessibilityNodeInfo.java
- `core/java/android/view/accessibility/AccessibilityManager.java` — https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/refs/heads/main/core/java/android/view/accessibility/AccessibilityManager.java
- `core/java/android/accessibilityservice/AccessibilityService.java` — https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/refs/heads/main/core/java/android/accessibilityservice/AccessibilityService.java
- `core/java/android/accessibilityservice/GestureDescription.java` — https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/refs/heads/main/core/java/android/accessibilityservice/GestureDescription.java
- `core/java/android/view/MotionEvent.java` — https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/refs/heads/main/core/java/android/view/MotionEvent.java
- `core/java/android/webkit/WebView.java` — https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/refs/heads/main/core/java/android/webkit/WebView.java
- `services/core/java/com/android/server/input/InputManagerService.java` — https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/refs/heads/main/services/core/java/com/android/server/input/InputManagerService.java
- `core/java/android/view/accessibility/AccessibilityManager.java` at tag `android-11.0.0_r1` — https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/refs/tags/android-11.0.0_r1/core/java/android/view/accessibility/AccessibilityManager.java (no `mPerformingAction`)
- `core/java/android/view/accessibility/AccessibilityManager.java` at tag `android-12.0.0_r34` — https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/refs/tags/android-12.0.0_r34/core/java/android/view/accessibility/AccessibilityManager.java (first appearance of the `event.setAction(mPerformingAction)` marker)

AndroidX / Jetpack Compose — `androidx/androidx`, branch `androidx-main`:

- `compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/AndroidComposeViewAccessibilityDelegateCompat.android.kt` — https://raw.githubusercontent.com/androidx/androidx/refs/heads/androidx-main/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/AndroidComposeViewAccessibilityDelegateCompat.android.kt
- `compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/AndroidComposeView.android.kt` — https://raw.githubusercontent.com/androidx/androidx/refs/heads/androidx-main/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/AndroidComposeView.android.kt
- `compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/Clickable.kt` — https://raw.githubusercontent.com/androidx/androidx/refs/heads/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/Clickable.kt

Chromium (the Android WebView provider) — `chromium/chromium`, branch `main`:

- `content/public/android/java/src/org/chromium/content/browser/accessibility/WebContentsAccessibilityImpl.java` — https://raw.githubusercontent.com/chromium/chromium/refs/heads/main/content/public/android/java/src/org/chromium/content/browser/accessibility/WebContentsAccessibilityImpl.java (`handleClicked(int)` → `sendAccessibilityEvent(id, TYPE_VIEW_CLICKED)`)
- `content/browser/accessibility/browser_accessibility_manager_android.cc` — https://raw.githubusercontent.com/chromium/chromium/refs/heads/main/content/browser/accessibility/browser_accessibility_manager_android.cc (`ax::mojom::Event::kClicked` → `handleClicked`)

Local Android SDK (public API surface and API levels):

- `platforms/android-37.0/android-stubs-src.jar` (public API stubs for `android.view.accessibility.*`, `android.view.MotionEvent`)
- `platforms/android-37.0/data/api-versions.xml` (authoritative "added in API level" / "removed in API level" for every public method and field)

Official documentation (referenced by URL; the developer.android.com site was unreachable from this machine, so the javadoc wording quoted above was read from the AOSP source files themselves):

- AccessibilityEvent — https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent
- AccessibilityNodeInfo — https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo
- AccessibilityService — https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- AccessibilityServiceInfo (`android:accessibilityEventTypes`) — https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo
- Writing an accessibility service — https://developer.android.com/guide/topics/ui/accessibility/service

GKD sources read for this note:

- `gkd-android/src/main/res/xml/a11y_info.xml` — the service subscribes to `typeWindowContentChanged|typeWindowStateChanged` only
- `gkd-app/src/androidMain/kotlin/li/gkd/app/a11y/A11yExt.kt` — `isUseful()` accepts only `STATE_CHANGED or CONTENT_CHANGED`; `safeSource` reads `event.source` on demand
- `gkd-app/src/androidMain/kotlin/li/gkd/app/a11y/A11yRuleEngine.kt` — event-time ordering and the "negative time" filter
- `gkd-app/src/androidMain/kotlin/li/gkd/app/data/GkdAction.kt` — the click/long-click action paths
- `gkd-app/src/androidMain/kotlin/li/gkd/app/priv/CompatInputManager.kt` — privileged tap / swipe injection
- `gkd-app/src/androidMain/kotlin/li/gkd/app/priv/UiAutomationServiceInfo.kt` — the `UiAutomation` path copies the manifest's event types
