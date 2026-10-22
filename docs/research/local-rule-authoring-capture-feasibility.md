# Local rule authoring from a captured screen — feasibility

Scope: on-device flow where a user sees another app's screen and authors a GKD rule from its
accessibility node tree. Read-only investigation; paths relative to repo root.

## 1. What a Snapshot contains, where it lives, when capture runs

- `ComplexSnapshot` = `id, appId, activityId, screenWidth/Height, isLandscape, appInfo,
  gkdAppInfo, device, nodes` (`gkd-app/src/commonMain/kotlin/li/gkd/app/model/ComplexSnapshot.kt:8-19`).
- `nodes` is a **flat pre-order list**, not nested JSON. `NodeInfo(id, pid, idQf, textQf, attr)`
  reconstructs the tree via parent id (`model/NodeInfo.kt:6-12`). `AttrInfo` carries
  `id/vid/name/text/desc`, boolean flags, screen-pixel bounds `left/top/right/bottom`,
  `width/height`, `childCount`, `index`, `depth` (`model/AttrInfo.kt:6-32`).
- Collection starts at the active-window root (`A11yRuntime.getRoot` → `ruleEngine.safeActiveWindow`,
  `androidMain/.../a11y/A11yRuntime.kt:59-60`), traverses `getChildren` and truncates past
  `MAX_KEEP_SIZE` (`data/NodeInfoCapture.kt:46-75`). Only that one root is captured; other
  windows (dialog windows, IME, system bars) are not merged.
- Capture writes `snapshot.json` + `min_snapshot.json` (nodes stripped) + screenshot, then inserts
  a DB row; files live under `appStorage().snapshot` (`SnapshotStore.kt:36-51`). No TTL — retained
  until `SnapshotStore.delete` (`SnapshotStore.kt:101`).
- Capture is **not HTTP-only**. Triggers: inspection server `/captureSnapshot`; Quick Settings tile;
  floating snapshot button; privileged expose broadcast `ACTION_CAPTURE_SNAPSHOT`
  (`service/ExposeService.kt:36,52`); Desktop debug replay endpoint. All production paths funnel
  through `SnapshotCaptureHost.capture` (`snapshot/SnapshotCaptureHost.kt:268-305`), which requires
  a connected a11y service and takes nodes + screenshot concurrently.

## 2. Retained recent node trees?

No. The only history is `A11yEventLog`: `id, ctime, type, appId, name(className), desc, text:
List<String>` (`gkd-db/.../A11yEventLog.kt:17-24`) — summary text, no nodes. `A11yEvent` wraps the
live `AccessibilityEvent` plus transient source (`androidMain/.../a11y/A11yExt.kt:119-133`).
`A11yContext` keeps only a current-screen root/children cache used by matching
(`androidMain/.../a11y/A11yContext.kt:510-563`). There is no ring buffer of past screens.

## 3. Floating overlay

- `OverlayWindowService` (`androidMain/.../service/OverlayWindowService.kt:112`) is the shared
  Compose-in-overlay host; subclasses: `ButtonService` (snapshot button), `ActivityService`,
  `EventService` (same files). Window params: `TYPE_APPLICATION_OVERLAY`, `WRAP_CONTENT`,
  `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_NO_LIMITS | FLAG_LAYOUT_IN_SCREEN`
  (`OverlayWindowService.kt:262-275`). It has its own touch listener/drag/tap logic
  (`:325-380`), so it is **touchable** (not `FLAG_NOT_TOUCHABLE`).
- `ButtonService` renders a 40dp focus icon (`ButtonService.kt:37-47`); tap hides all GKD overlays
  and captures, long-press stops the service (`ButtonService.kt:25-34`). It coexists with the target
  app because it is a small non-focusable overlay; the a11y service still reads the target app's
  node tree. GKD also tracks overlay permission loss and warns
  (`OverlayWindowService.kt:88-108`, `strings.xml:471`).
- A full-screen picker overlay is different: a focusable/touchable full-screen window can become
  the active window and intercept/consume touches. Safer design: capture the tree first, then draw
  highlights from the stored bounds on a touchable overlay and hit-test taps against those nodes
  (no live root needed while picking).

## 4. System surfaces that can trigger capture over another app

- `SnapshotTileService` exists and triggers `execSnapshot()`
  (`androidMain/.../service/SnapshotTileService.kt:15-19`). It presses Back until the foreground
  package changes, then captures the **new** foreground app (`:29-69`) — because the shade covers
  the app; it cannot capture the original screen. This is a UX constraint, not an API one.
- Notifications: `NotificationDispatcher` adds only a "stop service" action
  (`androidMain/.../notif/NotificationDispatcher.kt:46-56`). No capture action exists. Adding one
  would still fire behind the open shade (a11y active window = SystemUI), so it needs the same
  dismiss-and-return workaround as the tile.
- By contrast, the floating button captures the current foreground app directly and is the only
  existing system-ish surface that does so.

## 5. Verdicts

| Option | Verdict | Why / constraints |
| --- | --- | --- |
| A. Overlay node picker over target app | **Feasible-with-constraints** | A11y service can enumerate windows (`A11yRuntime.compatWindows()`, `A11yRuntime.kt:62-68`; `A11yService.windowInfos`, `A11yService.kt:47`) and every node has screen bounds. Recommended: capture → show touchable overlay with stored-bounds hit-test. Risks: (1) full-screen focusable overlay may steal active-window status from `getRoot` (needs device test; current button is `FLAG_NOT_FOCUSABLE`); (2) touchable overlay consumes the tap, so the target app never receives it — fine for "pick", not for replay; (3) merged/obscured nodes in Compose/Flutter/WebView/Canvas apps; (4) Android 12+ untrusted-touch rules affect pass-through overlays (GKD already documents blocked taps under track overlay, `strings.xml:915`); (5) screenshot may be blank on `FLAG_SECURE`, but nodes usually remain readable. |
| B. Capture-then-author (tile/notification/floating button) | **Feasible** | Already 80% built: floating button captures foreground app and stores the full tree permanently (`ButtonService.kt:25-34`, `SnapshotStore.kt:46-51`). Missing: an authoring surface that loads a stored `ComplexSnapshot` into the rule editor and derives selectors; today's snapshot page is screenshot-centric and `/execSelector` tests against the live tree only. Constraints: must capture while target app is foreground; one active-window root only (no dialog-window tree); screenshot optional for authoring; tile/notification capture original screen is not possible without dismissing the shade. |
| C. Author from retained recent screens | **Not feasible today** | No historical node trees exist (see §2); `A11yEventLog` stores text summaries only. Would require a new always-on capture/ring-buffer mechanism (battery, privacy, storage) or explicit user-triggered captures. |
| D. Desktop dev-host simulation | **Feasible-with-constraints** | Desktop can host the authoring UI and persist real snapshots, but it has no foreign app a11y trees: production Desktop HTTP exposes `captureSnapshot` as unsupported (`jvmMain/.../network/inspection/DesktopHttpService.kt:81`), while a debug endpoint can **replay/inject** a `ComplexSnapshot` (`jvmMain/.../DesktopSnapshotCapture.kt:16-47`). So: build/test editor + node picker against fixture snapshots; do not claim live "other app" capture or real a11y behavior. |

## Android version / API notes

- `AccessibilityService.getWindows()` requires API 21 and `flagRetrieveInteractiveWindows`;
  window types include application, IME, system, a11y overlay.
- `TYPE_ACCESSIBILITY_OVERLAY` (API 22+) needs no `SYSTEM_ALERT_WINDOW` but must be added by the
  a11y service; current GKD overlays use `TYPE_APPLICATION_OVERLAY` (API 26+) + overlay permission.
- `FLAG_NOT_TOUCHABLE` passes touches through but cannot receive them; `FLAG_NOT_FOCUSABLE` keeps
  the app focused. A picker must choose one; both cannot be true simultaneously for tap capture.
- Android 12+ blocks touches through untrusted overlays above an opacity threshold; Android 13+
  restricted settings gate a11y enablement for sideloaded installs. A11y screenshots use
  `takeScreenshot` (API 30+) as in `A11yService.screenshot()`.
- Device-test required: active-window/root behavior with a full-screen picker overlay; node bounds
  and hit-test accuracy across Compose/WebView/Flutter; OEM untrusted-touch and secure-window
  behavior; tile capture timing after shade dismissal.
