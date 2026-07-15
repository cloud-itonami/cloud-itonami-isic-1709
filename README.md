# cloud-itonami-isic-1709: Manufacture of other articles of paper and paperboard

Open Business Blueprint for **ISIC Rev.5 1709**: manufacture of other articles of paper and paperboard — an autonomous "actor" (LLM advisor behind an independent Governor, langgraph-clj StateGraph, append-only audit ledger) that coordinates back-office paper-article **plant operations**: production-batch data logging (product grade, quantity, basis-weight/moisture-content quality data), converting-line-equipment maintenance scheduling, safety-concern flagging, and outbound product shipment coordination.

## Chosen concrete illustration of this residual (n.e.c.) category

ISIC 1709 is a residual "not elsewhere classified" category covering paper/paperboard articles that don't belong to 1701 (pulp/paper/paperboard) or 1702 (corrugated paper/paperboard containers) — e.g. paper tableware, filter paper, wallpaper, and similar converted paper goods. This repository's domain logic, sample data, and demo pick one concrete product line to illustrate the category: **molded-pulp/paperboard paper-tableware manufacturing** (paper plates, bowls and trays formed on a pulp-molding press; coated and uncoated paperboard cups finished on a die-cutting/folding line). The op set, governor invariants and coordination shape apply unchanged to this category's other n.e.c. product lines (filter paper, wallpaper base stock) since none of them turn on which specific paper article is being formed — `paperarticles.registry/valid-product-grades` includes representative grades from all three so the closed-allowlist discipline is visible across the category, not just the illustrated product line.

This repository designs a forkable OSS business for paper-article plant
operations: run by a qualified operator so a plant keeps its own
operating records instead of renting a closed SaaS.

## What this actor does

Proposes **plant operations coordination**, not equipment operation:
- `:log-production-batch` — converting/forming batch, output-quality (basis-weight, moisture-content) data logging (administrative, not an operational decision)
- `:schedule-maintenance` — converting-line-equipment (pulp-molding press, die-cutter) maintenance scheduling proposal
- `:flag-safety-concern` — surface an equipment-safety/quality-defect concern (always escalates)
- `:coordinate-shipment` — outbound product shipment coordination proposal

## What this actor does NOT do

**CRITICAL SCOPE BOUNDARY** — this actor coordinates back-office
records for a plant with converting-line machinery (pulp-molding
presses, die-cutters, folder-gluers):

- Does NOT control the pulp-molding press, die-cutter, or any converting-line equipment directly
- Does NOT make plant-safety or quality-defect-disposition decisions (that's the plant supervisor's exclusive human authority)
- ONLY proposes/coordinates operations back-office; all actuation requires explicit human approval
- Safety-concern flagging ALWAYS escalates — never auto-decided, no confidence threshold or phase below escalation

## Architecture

Classic governed-actor pattern (`paperarticles.operation/build`, a langgraph-clj StateGraph):
1. **`paperarticles.advisor`** (sealed intelligence node, `PaperArticlesAdvisor`): proposes decisions only, never commits
2. **`paperarticles.governor`** (independent, `Paper Articles Plant Operations Governor`): validates against domain rules, re-derived from `paperarticles.registry`'s pure functions and `paperarticles.store`'s SSoT -- never trusts the advisor's own self-report
   - HARD invariants (always `:hold`, no override):
     - Plant/batch record must be independently verified/registered (`:verified?` AND `:registered?`) before any action is taken against it (equipment before maintenance scheduling, batch before shipment coordination)
     - The request's own `:effect` must be `:propose` (never a direct-write bypass)
     - `:op` must be in the closed four-op allowlist
     - The proposal's own `:effect` must be one of the four propose-shaped effects — any proposal touching converting-line-equipment control is a hard, PERMANENT, unconditional block
     - A shipment may not push a batch's own recorded shipped quantity past its own logged production quantity (independently recomputed)
     - No double-scheduling the same maintenance record
     - No fabricated `:grade` value on a production-batch patch
     - No physically implausible `:basis-weight-gsm` (grammage) value on a production-batch patch
     - No physically implausible `:moisture-content-pct` value on a production-batch patch
   - ESCALATE (always human sign-off, overridable by a human):
     - `:flag-safety-concern` always escalates, regardless of confidence
     - Low-confidence proposals
3. **`paperarticles.phase`** (Phase 0->3 rollout): `:schedule-maintenance`/`:flag-safety-concern`/`:coordinate-shipment` are NEVER in any phase's `:auto` set (permanent, matching the governor's own posture); only `:log-production-batch` may auto-commit at phase 3 when clean
4. **`paperarticles.store`** (append-only audit ledger + SSoT): a single `MemStore` backend behind a `Store` protocol (see ns docstring for why a second Datomic-backed backend is out of scope for this build)

## Development

```bash
# Run tests (top-level deps.edn already pins langgraph+langchain local/root)
clojure -M:test

# Run tests via the workspace :dev override alias (equivalent, kept for sibling-repo parity)
clojure -M:dev:test

# Run the demo
clojure -M:dev:run

# Lint
clojure -M:lint
```

## Status

`:implemented` — `governor.cljc`/`store.cljc`/`advisor.cljc`/`registry.cljc` + `deps.edn` complete the module set; tests green, demo runnable, langgraph-clj integration verified.

## License

AGPL-3.0-or-later
