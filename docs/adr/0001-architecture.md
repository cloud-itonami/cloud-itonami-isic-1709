# ADR-0001: PaperArticlesAdvisor ⊣ Paper Articles Plant Operations Governor architecture

## Status

Accepted. `cloud-itonami-isic-1709` promoted from `:spec` to
`:implemented` in the `kotoba-lang/industry` registry, following the
verified fresh-scaffold protocol established by prior actors in this
fleet.

## Context

`cloud-itonami-isic-1709` publishes an OSS blueprint for ISIC Rev.5
1709's residual "other articles of paper and paperboard" category
(paper tableware, filter paper, wallpaper, and similar converted
paper goods not covered by 1701 pulp/paper/paperboard or 1702
corrugated paper/paperboard containers). Like every actor in this
fleet, the blueprint alone is not an implementation: this ADR records
the governed-actor architecture that promotes it to real, tested
code, following the same langgraph StateGraph + independent Governor
+ Phase 0->3 rollout pattern established across the cloud-itonami
fleet.

The closest domain analogs are `cloud-itonami-isic-1701` (Manufacture
of pulp, paper and paperboard) and `cloud-itonami-isic-1702`
(Manufacture of corrugated paper and paperboard and of containers of
paper and paperboard): all three are back-office coordination actors
for a fixed processing PLANT (not a field site) with converting-line
equipment and a physical-safety dimension, and a central ground-truth
**production batch** entity independently gated alongside an
**equipment** entity. Unlike 1701's chemical/mechanical pulping
process (which carries a distinct, separately-regulated
environmental-discharge concern) or 1702's corrugator/converting-line
(printer-slotter/flexo-folder-gluer/die-cutter) equipment set, ISIC
1709 is a RESIDUAL n.e.c. category with no single product line --
this build resolves that by picking ONE concrete illustration (see
Decision 1) while keeping the coordination surface general enough for
the category's other n.e.c. product lines. This build therefore
implements a SINGLE PERMANENT block on the proposal's own `:effect`
(any proposal that would directly control converting-line equipment),
the same shape as 1702's single permanent block (not 1701's two
independent permanent blocks, since this task brief does not call for
a separate discharge-authorization axis here either).

This vertical has NO pre-existing `kotoba-lang/paperarticles`-style
capability library to wrap (verified: no such repo exists). This build
therefore uses self-contained domain logic -- pure functions in
`paperarticles.registry` (equipment/batch verification, shipment-
quantity recompute, product-grade validation, basis-weight (grammage)
and moisture-content plausibility validation) are re-verified
independently by the governor, the same "ground truth, not
self-report" discipline established across prior actors (most
directly `cloud-itonami-isic-1701`'s `pulppaper.registry` and
`cloud-itonami-isic-1702`'s `corrugated.registry`).

This blueprint's own `:itonami.blueprint/governor` keyword,
`:paper-articles-plant-operations-governor`, is grep-verified UNIQUE
fleet-wide (`gh search code "paper-articles-plant-operations-governor"
--owner cloud-itonami`, zero hits before this repo was created).

## Decision

### Decision 1: A residual (n.e.c.) ISIC category resolved via one concrete illustrative product line

ISIC 1709 has no single product line the way 1701 (pulp/paper) or
1702 (corrugated containers) do -- it is explicitly the "n.e.c."
bucket for paper/paperboard articles: paper tableware, filter paper,
wallpaper, and similar converted goods. This build picks **molded-
pulp/paperboard paper-tableware manufacturing** (paper plates, bowls
and trays formed on a pulp-molding press; coated and uncoated
paperboard cups finished on a die-cutting/folding line) as the
concrete illustration driving the sample data, demo narrative, and
docstrings, while keeping the op set, governor invariants, and
coordination shape unchanged across the category's other n.e.c.
product lines -- none of the four ops or ten governor checks turn on
which specific paper article is being formed. `paperarticles.
registry/valid-product-grades` includes representative grades from
paper tableware AND filter paper AND wallpaper base stock so the
closed-allowlist discipline is visible across the category, not just
the illustrated product line.

### Decision 2: Self-contained domain logic (no external paper-articles capability library to wrap)

Unlike actors that delegate to pre-existing domain libraries, this
paper-articles vertical has NO pre-existing capability library to
wrap. The equipment/batch-verification / shipment-quantity / grade /
basis-weight / moisture-content validation functions live as pure
functions in `paperarticles.registry` and are re-verified
independently by `paperarticles.governor` -- the same "ground truth,
not self-report" discipline established across prior actors (most
directly `cloud-itonami-isic-1701`'s `pulppaper.registry` and
`cloud-itonami-isic-1702`'s `corrugated.registry`).

### Decision 3: Coordination, not control -- scope boundary at the back-office

This actor is **strictly back-office coordination** of paper-article
plant operations. It does NOT:
- Control the pulp-molding press, die-cutter, or any converting-line equipment directly
- Make plant-safety or quality-defect-disposition decisions (exclusive to the human plant supervisor)

All proposals are `:effect :propose` only. The advisor proposes; the
governor validates; escalation paths funnel to human plant-supervisor
approval. This is not a replacement for the supervisor's authority --
it is a proposal-screening and documentation layer.

### Decision 4: Safety-concern escalation -- always human sign-off

`:flag-safety-concern` (equipment hazard, quality-defect concern,
crew exposure) ALWAYS escalates, never auto-commits. This is not a
"low-stakes proposal" -- it is a circuit-breaker that must reach human
authority.

### Decision 5: Two independent verified/registered gates (equipment AND batch), not one

Mirroring `cloud-itonami-isic-1701`/`cloud-itonami-isic-1702`'s own
structure, this vertical has TWO entity kinds each gating a different
op: `:schedule-maintenance` independently verifies the referenced
**equipment** unit's own `:verified?`/`:registered?` fields;
`:coordinate-shipment` independently verifies the referenced
**batch**'s own `:verified?`/`:registered?` fields. Both are the same
"plant/batch record must be independently verified/registered before
any action" HARD invariant applied to the two distinct record kinds
this domain actually has. `:coordinate-shipment` additionally
independently recomputes whether a batch's own recorded
shipped-to-date quantity plus the proposal's own claimed quantity
would exceed the batch's own recorded production quantity -- never
taken on the advisor's self-report.

### Decision 6: A single PERMANENT block on the proposal's own `:effect` -- no separate discharge-authorization axis

Like `cloud-itonami-isic-1702`, and unlike `cloud-itonami-isic-1701`,
this vertical's task brief calls for exactly ONE permanent,
unconditional block: "any proposal touching converting-line-equipment
control is a hard, permanent block." `equipment-control-blocked-
violations` implements this by checking the proposal's own `:effect`
against the closed propose-shaped effect allowlist (`:batch/upsert`,
`:maintenance/schedule`, `:safety-concern/flag`, `:shipment/propose`)
-- any other value (e.g. a hallucinated `:pulp-molding-press/actuate`)
is blocked unconditionally. Because the deterministic mock advisor
always emits a fixed `:effect` per op, this check is not reachable
via the normal actor-graph path with the shipped advisor; it is
exercised directly against `paperarticles.governor/check` in
`paperarticles.governor-contract-test`'s
`equipment-control-blocked-is-held-and-permanently-blocked`, the same
way a compromised/hallucinating advisor's output would be censored.

### Decision 7: HARD invariants (no override)

Four HARD governor invariants (elaborated into ten concrete checks in
`paperarticles.governor`, mirroring `cloud-itonami-isic-1701`/
`cloud-itonami-isic-1702`'s own elaboration of their HARD invariants
into concrete checks) block proposals and cannot be overridden by
human approval:
1. Plant/batch record (equipment for maintenance, batch for shipment) must be independently verified/registered before any action is taken against it, and a shipment's quantity must independently recompute within the batch's own logged production quantity
2. Proposals must be `:effect :propose` only (never direct equipment control)
3. Direct converting-line-equipment control is permanently blocked
4. The op allowlist is closed -- `:log-production-batch`/`:schedule-maintenance`/`:flag-safety-concern`/`:coordinate-shipment` only

## Consequences

(+) Paper-article plant operations back-office now has a documented,
governed, auditable coordination layer that funnels all decisions
through independent validation before human approval.

(+) The "coordination, not control" boundary is explicit in code: all
`:effect :propose`, all real-world actuation requires human plant-
supervisor sign-off.

(+) Scope is bounded and verifiable: four HARD invariants (elaborated
into ten concrete governor checks) protect against scope creep into
unauthorized equipment operation. Safety concerns are a
circuit-breaker, not a threshold.

(+) Quality-plausibility discipline is explicit: basis-weight
(grammage) and moisture-content readings are independently
range-checked, never trusted as self-reported sensor data.

(+) The residual/n.e.c. category is resolved concretely (paper
tableware as the illustration) without narrowing the actor's
applicability to the category's other product lines (filter paper,
wallpaper).

(-) Still a simulation/proposal layer, not a real plant-operations
control system. Equipment actuation decisions remain human-controlled
via external channels.

(-) No integration with real plant-management databases (equipment
telemetry, batch tracking, freight dispatch) -- this is a standalone
coordinator blueprint.

## Verification

- `cloud-itonami-isic-1709`: `clojure -M:test` green (see the
  superproject ADR and `kotoba-lang/industry` registry entry for the
  exact re-verification output, run from an independent fresh clone at
  the merge commit), `clojure -M:dev:run` demo narrative exercises
  proposal submission, escalation, and every HARD-hold scenario
  directly (not-propose-effect, unknown-op, equipment-not-verified,
  batch-not-verified, shipment-quantity-exceeded, already-scheduled,
  invalid-grade, invalid-basis-weight, invalid-moisture-content), plus
  a direct governor-level check for equipment-control-blocked (not
  reachable via the deterministic advisor).
- All source is `.cljc` (portable ClojureScript / JVM / nbb) -- no
  JVM-only interop; the actor graph is invoked exclusively via
  `langgraph.graph/run*` (not `.invoke`, which is not cljs-portable).
- Audit ledger is append-only, all decisions are traced; every settled
  request (commit or hold) leaves exactly one ledger fact.
- `deps.edn` pins `io.github.kotoba-lang/langgraph` and
  `io.github.kotoba-lang/langchain` via `:local/root` directly in the
  top-level `:deps` (not only under a `:dev` alias), so a bare
  `clojure -M:test` resolves offline inside the monorepo checkout.
