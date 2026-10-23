# Observed user taps — emulator device evidence

**Status:** device evidence complete, 2026-10-23. Input to the recording-authoring go/no-go (map issue #11); not a spec.

**Scope:** what an accessibility service actually receives for `TYPE_VIEW_CLICKED` / `TYPE_VIEW_LONG_CLICKED` on a running Android device, across native Views, Compose, WebView and canvas, for a user tap, a long press, GKD's own `performAction` click, an accessibility gesture and a self-deleting target. Raw log: [`observed-user-taps.log`](observed-user-taps.log).

## Setup

- Emulator `gkd_api37`: Android 17 / API 37, x86_64, Google APIs image, WHPX acceleration.
- Probe app (throwaway, `.local/tmp/tap-probe`, not committed): an accessibility observer subscribed to `typeViewClicked|typeViewLongClicked` plus:
  - a native `Button`, a long-clickable `TextView` and a button that sets itself `GONE` on click;
  - a `WebView` containing an HTML button and a canvas;
  - buttons that trigger `performAction(ACTION_CLICK)` and `dispatchGesture` from the observer;
  - a broadcast receiver for triggering an accessibility action on an arbitrary node by text.
- GKD debug APK (Compose surface) and Android Settings (native Views) on the same emulator.
- User taps are `adb shell input tap`; long press is `adb shell input swipe` with 1000 ms. Both are InputManager-injected touches; the accessibility event path they exercise is the same one a physical touchscreen uses.
- Observer logs: event type, `getAction()`, package, class, event text, source node text / contentDescription / viewId / class / bounds / clickable / visible, and a 1.5 s `refresh()` re-check of the source.

## Results

| Surface / origin | Event | `getAction()` | Source node | Note |
|---|---|---|---|---|
| Native `Button` (user tap) | `TYPE_VIEW_CLICKED` | 0 | text `NATIVE TAP`, viewId, bounds | full identity |
| Native `TextView` long press | `TYPE_VIEW_LONG_CLICKED` | 0 | same node, text already updated | listener runs before event |
| WebView HTML button (user tap) | `TYPE_VIEW_CLICKED` | 0 | text `Web Tap`, `viewId=web_btn`, clickable | DOM id exposed as `viewIdResourceName` |
| WebView canvas (user tap) | `TYPE_VIEW_CLICKED` | 0 | `viewId=c`, no text/desc, clickable=false | canvas event has no usable semantic identity |
| Self-dismissing button (user tap) | **none** | — | — | view becomes `GONE` before event emission |
| GKD `performAction(ACTION_CLICK)` on native Button | `TYPE_VIEW_CLICKED` | **16** | target button | the live discriminator on API 37 |
| Accessibility `dispatchGesture` tap | `TYPE_VIEW_CLICKED` | **0** | target button | indistinguishable from a user/injected tap |
| Settings row `Network & internet` (user tap) | `TYPE_VIEW_CLICKED` | 0 | blank `LinearLayout`: no text, desc or viewId | event text carries row title + summary |
| GKD Compose dialog button (user tap) | **none** | — | — | tapping "同意" closed the dialog; no event |
| GKD Compose row (same row, accessibility `ACTION_CLICK`) | `TYPE_VIEW_CLICKED` | **16** | bare `android.view.View`: no text, desc or viewId | opened the same panel as the user tap; source no longer resolved after 1.5 s |

### Compose inversion, side by side

On GKD's "无障碍" row, both interactions opened the same accessibility panel:

- **User tap** (`input tap`): panel opened; the observer received **no click event**.
- **Accessibility `ACTION_CLICK`**: panel opened; the observer received `TYPE_VIEW_CLICKED` with `action=16`; the source was a bare `android.view.View` at the row's bounds, and `refresh()` returned **false** 1.5 s later.

This confirms the predicted inversion: a Compose user tap is silent, while GKD's own accessibility click on Compose is visible.

### Node identity and survival

- **Settings** tapped a row whose source node was a blank `LinearLayout` (no text, contentDescription or viewId). The event text carried the row title and summary. The source still `refresh()`ed 1.5 s after Settings navigated to the Internet page, and still reported `visible=true`.
- **Compose** source nodes were bare `android.view.View`s and stopped resolving after the panel navigation.
- Resolvability therefore differs by view system and is not proof that the node is on the current screen. Uniqueness and identity must be measured against the pre-tap capture; the event text may be the only usable identity when the source is a container.

## Conclusions

1. The emulator reproduces every decision-relevant click-event semantic: native taps, long presses, WebView taps, canvas taps, Compose's silent user tap, the Compose accessibility-click inversion, self-deleting targets, and the action discriminator.
2. The WebView unknown is resolved for this provider: a tap produces `TYPE_VIEW_CLICKED`, and DOM ids are exposed as `viewIdResourceName`.
3. Recording via click events remains a native-View path. Compose user taps emit nothing; accessibility-initiated Compose clicks do emit, so the rule-engine-disabled guard during a recording session is load-bearing there too.
4. `action=16` separates GKD's `performAction` click from gesture/injected taps on Android 12+; gesture and injected taps both read 0. Before Android 12 the field is absent, so the guard, not the action field, is the separator there.
5. A demonstration's event source may be a bare container; the meaningful picked node may have to be recovered from the event text and the pre-tap tree. Source resolvability is not a reliable proxy for being on the current screen.

## Limits

- OEM/vendor ROM modification cannot be reproduced on the emulator; this is the one check that inherently needs a second physical device.
- Literal physical-finger/hardware behavior is not exercised. The event emission point and the injected-tap path are verified identical at the framework level; the residual risk is OEM modification, not the tap mechanism.
- Android 8–11 is not covered by the API 37 AVD. The emulator can run an older system image (e.g. API 30 = Android 11); this is a setup task, not a hardware limitation. Source research already establishes that `getAction()` did not exist before Android 12.
- Third-party apps that refuse emulator/root or cannot run under ARM translation remain outside this evidence; app-family semantics were probed with equivalent first-party/probe surfaces.
- WebView provider variation remains; this session used Google WebView 145.0.7632.218.

## Recommendation

The emulator is sufficient device evidence for the framework-semantics questions in ticket #14, and the emulator evidence is accepted for the map. Carry OEM variation and literal physical-finger validation as residual risks into the go/no-go (#18); an older AVD can add Android 8–11 evidence if the decision needs it.
