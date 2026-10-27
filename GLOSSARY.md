# GKD

GKD is an Android accessibility rule engine: it watches app screens, matches declarative rules against the accessibility node tree, and acts on matches (tap, long-press). This glossary defines the language of that domain — rules, subscriptions, selectors, and guided rule authoring.

## Language

### Rule model

**Rule**:
One declarative unit: "when the screen contains a node matching these selectors, act." Fires whenever it matches — there is no sequence, no "then", and no memory of how the rule was created.
_Avoid_: macro, script, step

**Group**:
A named, ordered set of rules within a subscription, enabled and disabled together.
_Avoid_: category, collection

**Subscription**:
A distributable set of groups. The synthetic **local subscription** holds the user's own groups and rules on-device.
_Avoid_: config, rule pack

**Selector**:
The string expression a rule matches against the node tree. The **canonical selector** is its compiled-then-printed form — the persisted contract.
_Avoid_: query, XPath, pattern

**Match**:
The event of a rule's selectors resolving to a node on the current screen; the matched node is the node the rule acts on.
_Avoid_: hit, trigger (as a noun)

**Action**:
What a rule does to its matched node: tap, long-press, and the like.
_Avoid_: command, gesture

### Guided authoring

**Authoring session**:
One bounded run of guided rule creation, from a capture to a saved rule, a raw-editor handoff, or an abandon.
_Avoid_: wizard, flow

**Authoring capture**:
The transient, in-memory (never persisted) screenshot plus node tree taken for one authoring session, held by `GuidedCaptureStore`. Judged against for picking or demonstration; never added to the snapshot gallery; deleted when the session ends (save, abandon, reset). Contrast with a **snapshot**: a persisted, id'd `ComplexSnapshot` stored on disk and browsable in the gallery.
_Avoid_: evidence capture, baseline, demo capture, conflating with a persisted snapshot

**Authoring group**:
The per-app local group with the reserved localized name where guided rules are saved. Found by name or created on first save.
_Avoid_: my-rules folder

**Picked node**:
The node the user designates as the rule's target — by tapping it on the authoring capture, or by performing the action on it in the live app during recording. How it was designated is a session-level fact, never a rule-level one.
_Avoid_: selected node, target node (as a distinct kind), demonstrated node

**Target element**:
The node the rule acts on: the picked node, or the nearest clickable ancestor when the picked node is not clickable and carries no usable text or description.
_Avoid_: actable node (use only as a synonym), click target

**Condition row**:
One AND-ed plain-language predicate over text, description, id, class, clickability, or visibility, compiled into the canonical selector.
_Avoid_: criterion, filter

**Guided-representable rule**:
A rule wholly inside the subset the guided editor can express and re-open; anything outside it opens in the raw JSON5 editor.
_Avoid_: simple rule

### Recording

**Recording session**:
An authoring session phase in which GKD watches the captured app for one tap or long-press on the captured screen, with the rule engine idle. It ends at the first such event from the captured app — whether or not it yields a demonstration — when a screen change invalidates the capture, or when the user gives up. An authoring session holds at most one demonstration but may run more than one recording session after failed attempts.
_Avoid_: macro recording, capture mode

**Demonstration**:
One user-performed tap or long-press on a node in the captured app during a recording session, from which the picked node is derived. A tap from which no node of the authoring capture can be located is not a demonstration. At most one per authoring session; never persisted; ceases to exist when the session ends.
_Avoid_: record step, user action (in tracker copy), gesture

**Recorded rule**:
Not a kind of rule. Informal shorthand only for "a rule whose authoring session included a demonstration". Nothing in the app distinguishes it from any other rule after saving, by design.
_Avoid_: treating as a category with behaviour; provenance markers
