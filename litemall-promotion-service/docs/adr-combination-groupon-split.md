# ADR: Combination (group-buy / 拼团) ownership split between promotion and order

- **Status:** SUPERSEDED in part (Wave 2, 2026-07-07) — see the addendum below.
  The Phase-1 decision text is kept for history.
- **Original status:** Accepted (Phase 1, 2026-06)

## Addendum (Wave 2, 2026-07-07) — promotion now owns participation too

The Wave-2 revive task requires start-group / join-group / my-groups / expiry
sweep to be **live in the promotion service**, and the order module cannot be
edited from this worktree. Decision (user-approved 2026-07-07):

- **Promotion owns combination participation** via a new promotion-owned table
  `litemall_combination_pink` (Flyway **V30**, crmeb `StorePink` pattern:
  leader row `head_id = 0`, members reference the leader row id, snapshots of
  `required_members`/`expire_time` at group start). Implemented as
  `LitemallCombinationPinkAggregate` + repository + start/join/my/monitoring
  endpoints and a scheduled expiry sweep
  (`litemall.promotion.groupon.*` typed properties).
- **Order's legacy groupon (`litemall_groupon`/`litemall_groupon_rules`) is
  untouched.** It remains the owner of the *legacy* pink flow until order
  migrates; the two do not share tables, so write-ownership stays single per
  table. Order integrates with the new participation via the
  **groupon-priced-submit contract** (`docs/spec-groupon-priced-submit-contract.md`):
  at submit, order validates the buyer's pink and prices the line at the
  campaign's combination price.
- Point 3 of the original consequences ("no pink/join logic in promotion") is
  therefore obsolete; points 1–2 are replaced by the handoff specs under
  `docs/`.
- **Context worktree:** `promotion` (`fix/promotion`)
- **Related:** litemall-order groupon (`litemall-order/.../domain/service/groupon/**`,
  `domain/model/agregates/LitemallGroupon*Aggregate`), litemall-db
  `litemall_groupon` / `litemall_groupon_rules`.

## Context

Phase 1 of the promotion engine adds a crmeb-style **combination** (group-buy)
mechanic (`StoreCombination` / `StorePink`). Group-buy already exists in the
**order** module: `LitemallGrouponRulesAggregate` (the offer/rules) **and**
`LitemallGrouponAggregate` (participation/pink), backed by
`litemall_groupon_rules` and `litemall_groupon`. Duplicating any of that would
create two sources of truth for the same concept.

The CLAUDE.md task required us to either (a) have promotion own the *campaign
definition* while order owns *participation/pink*, or (b) fold entirely, and to
document the decision rather than build a parallel groupon.

## Decision

**Promotion owns the combination *campaign definition* only. Order keeps
participation/pink. No second participation implementation is built in
promotion.**

Concretely:

- **Promotion (this module)** owns `LitemallCombinationAggregate` — the offer
  template: goods, group price vs original price, `requiredMembers`, validity
  window, and lifecycle (`DRAFT → ACTIVE → EXPIRED/OFFLINE`). It is persisted in
  a new promotion-owned table `litemall_combination` (Flyway `V17`), exposed via
  an admin management surface (`/srv/private/admin/promotion/combination`, gated
  `ROLE_ADMIN`) and a customer read surface (`/srv/promotion/combination`). It
  emits `COMBINATION_DEFINED/ACTIVATED/EXPIRED` domain events.
- **Order (unchanged)** keeps `LitemallGrouponAggregate` + `litemall_groupon`
  for *participation*: creating/joining a group (pink), the post-payment
  full-group success transition, share URLs, and the customer "my groups"
  endpoints. Order's existing `litemall_groupon_rules` continues to back the
  current pink flow as-is.

### Why not reuse `litemall_groupon_rules` directly?

`litemall_groupon_rules` is owned and written by the order module today. Having
promotion also write it would put two modules on one table — exactly the
duplication we were told to avoid. A distinct `litemall_combination` table keeps
write-ownership single and lets the campaign definition carry forward-looking
fields (budget/cap, criteria links) needed by Phase 2 targeting without
perturbing order's schema.

### Why not fold entirely?

The promotion engine's Phase 2 targeting layer needs a first-class, queryable
*campaign definition* it owns (criteria, schedule, budget/cap). Folding would
leave the campaign concept trapped inside order's participation model.

## Consequences / follow-ups (raised, not done here)

1. **Order worktree follow-up:** when a group-buy is started, order's
   participation should reference a promotion `combinationId` (campaign id)
   instead of, or alongside, `groupon_rules.id`. The contract is a read of the
   active combination definition (REST `GET /srv/promotion/combination/{id}` or
   a Feign client), keeping order as the participation owner. Until then the two
   coexist: order's legacy `groupon_rules` for the live pink flow, promotion's
   `litemall_combination` for the marketing-engine campaign definition.
2. **Gateway routing** for `/srv/promotion/**` and
   `/srv/private/admin/promotion/**` is a gateway-worktree follow-up.
3. **No pink/join logic** was added to promotion. If a future phase migrates
   participation, it must move (not copy) order's pink model.
