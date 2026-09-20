# Plan — order worktree close-out of the lifecycle assignment (2026-09-20)

Status: **APPROVED (Option 1) + BUILT 2026-09-20.** Code commit `5bb240b23`:
`CjFulfillmentService.cancelAtCjIfDeletable` now enqueues one `CjOpsNotifier`
mail in both not-cancelled branches (subject "CJ order <id> still live after
<context> — order <sn>"); constructor gains the notifier (single call site =
the test). Suite **400 run / 0 failures** (baseline 395, +5 in
`CjFulfillmentServiceTest`). Dev boot on :18085: started in 18 s, Flyway
validated 65, 0 ERROR lines, `/actuator/health` 200. D5 = decided "no
automatic CJ dispute"; F13 = closed as documented. Deploy = MAIN (order
container rebuild + recreate; carries the still-undeployed `2cc0ba6a4`).
Drafted against `fix/order` == master `d67029cde`.

## 0. Where the assignment stands (verified today against the tree)

The commissioned scope — `plan-order-lifecycle-e2e.md` §3, packages A → B → C
and the raises D — is built, merged and deployed (2026-09-05). The two
order-scoped follow-ups (`plan-order-followups-2026-09-06.md`: prod SQL DEBUG
pin + F3 stock restore after commit) are built and merged to master
`2cc0ba6a4`, pushed to origin 2026-09-13.

Facts a reader should not have to re-derive:

- **The 2026-09-13 merge has no deploy record.** The worktree block says
  "deploy = MAIN"; nothing in CLAUDE.md or git says the order container was
  rebuilt since 2026-09-05. Until it is, prod still logs every MyBatis
  statement with bound customer data, and the cancel path still restores
  stock before its own commit. I cannot verify prod from a worktree.
- **Module suite baseline today:** litemall-order **395 run / 0 failures /
  0 skipped** (litemall-db 24 / 0 in the same reactor run; `mvn -o -pl
  litemall-order -am test -Dskip.installnodenpm -Dskip.npm`). Matches the
  2026-09-13 record exactly — nothing has moved under the branch.
- **The plan's real acceptance has still not happened.** Plan §4 says the
  actual acceptance is one real EUR order end to end in prod (pay → approve →
  CJ pay-from-balance → ship → tracking → delivered mail). That is user-side.
  As of the last recorded look (2026-09-05) no CJ order had ever been placed
  in prod and `litemall_mail_outbox` had never held a row.

Of the eighteen findings, everything order-scoped is closed except the two
the approved plan parked on purpose: **F13** and **F15/D5**. Both re-verified
today; details in §1.

## 1. What is still order-scoped and unbuilt

### 1.1 F15 / D5 — refund approved after CJ has been paid from balance

Verified in the tree today:

- `LitemallOrderOrchestratorService.approveRefund` (:1354-1371) settles the
  money to the paying tender, flips the order to REFUNDED, then calls
  `CjFulfillmentService.cancelAtCjIfDeletable(order, "refund approved")`;
  the aftersale approval path (:1446) does the same.
- `cancelAtCjIfDeletable` (:147-175) deletes the CJ order only while CJ status
  is CREATED / IN_CART / UNPAID. Past that it writes a same-status `cj_sync`
  timeline hop — "CJ-side fulfilment continues; use the dispute flow or the
  CJ dashboard" — and returns. A delete that CJ refuses gets a similar hop.
  **Neither branch notifies ops.** The timeline is the only record, and the
  admin who just approved the refund is not shown it.
- The CJ dispute vertical (V28, `cj-dispute-vertical.md`) is customer-initiated
  only, needs per-line ids and quantities, requires a CJ-side reason, and its
  refund goes to the CJ balance (`dispute-refund-type` 1). Nothing links a
  store-side refund to it, in either direction.

So today the store can lose twice on one order: the customer is refunded AND
CJ ships goods already paid from the balance, and the only signal is a
timeline line. That is exactly the class of "CJ went wrong" event package B
made loud everywhere else (stall, park, CJ-cancelled all reach `CJ_OPS_MAIL`).

**D5 recommendation: do NOT open a CJ dispute automatically.** A CJ dispute
is a product-problem claim with a reason and evidence; "we refunded our
customer" is not one, and before shipment the correct CJ-side action is a
dashboard cancel, not a dispute. Automating it would file false claims.
Recommend closing D5 as "no automation; ops alert instead":

**Proposed change (small):** in `cancelAtCjIfDeletable`, the two
not-cancelled branches (status past deletable; delete refused) also enqueue
one ops mail through the existing `CjOpsNotifier`: subject "Refund approved
but CJ order <cjOrderId> still live — order <sn>", body = local status, CJ
status, amount refunded, and the two honest next steps (cancel in the CJ
dashboard while CJ still allows it; otherwise the goods will ship and the
store has paid twice). `CjOpsNotifier.notify` inserts a `litemall_mail_outbox`
row inside the caller's transaction, so if the refund rolls back the alert
rolls back with it — no false alarm. Blank `CJ_OPS_MAIL` stays log-only as
today. No change to the delete-succeeded branch, no change for orders never
placed at CJ (early return unchanged — customer cancel and unpaid auto-cancel
never reach this), no response-shape change for gateway-admin.

Tests (new, beside the existing `CjFulfillmentService` tests): ops mail
enqueued exactly once when the CJ status is past deletable; enqueued once
when CJ refuses the delete; NOT enqueued when the delete succeeds; NOT
enqueued for a non-CJ order or one with no `cj_order_id`; subject carries
the CJ order id and the order sn.

### 1.2 F13 — JVM-local duplicate-placement guard

Verified: `CjPlacementService.inFlight` (:84, :138, :195) is a
`ConcurrentHashMap` key set, so two order instances could both attempt the
same placement. Mitigations already in the tree: the sweep path calls
`adoptExistingCjOrder` first (CJ dedupes on our `order_sn` and
`getOrderDetail` accepts it), and `recordCjPlacement` is the durable write.
The compose stack runs one order instance by design (the mail outbox sweep
already assumes it). A durable claim would need a column (V65) for a
scenario the deployment does not have.

**Recommendation: keep F13 documented, build nothing.** Revisit only if the
order service is ever scaled past one instance.

## 2. Found today, outside this worktree — raised, not built

- **GROUP_EXPIRED auto-refund (Wave 21) is dead in prod.** Diagnosed
  2026-09-06 and still unfixed: promotion's yml reads `KAFKA_BROKERS` while
  the prod compose sets `LITEMALL_KAFKA_BROKERS`, so promotion dials
  `localhost:9092` and has never produced the event; order's consumer is fine
  and waiting. Fastest fix is one env line on the promotion compose block +
  recreate (MAIN / promotion scope). Also why promotion shows "unhealthy".
- **Deploy of `2cc0ba6a4`** (SQL DEBUG pin + F3) is MAIN's, still pending per
  the record.
- gateway-api raises stand as recorded: `/pay/:orderId` mounts no Stripe
  Elements (every unpaid order is a dead end from the order list); the
  tokenized guest order view needs a backend half — if commissioned, the
  order side would mint a signed, expiring order-view token into the
  confirmation mail. Not part of this assignment.

## 3. Proposal (pick one)

- **Option 1 (recommended):** build §1.1 (ops alert on refund-with-live-CJ-
  order), record D5 as decided "no automatic dispute", close F13 as
  documented, rewrite the worktree block, hand deploy to MAIN together with
  the pending `2cc0ba6a4` rebuild (one order image, no migration, no
  core/db change).
- **Option 2:** build nothing; record D5 and F13 as closed-by-decision and
  rewrite the block to "no active assignment" with the §2 raises.

## 4. Deliberately NOT proposed

- Automatic CJ dispute on refund (see §1.1 — false claims).
- A durable placement claim for F13 (needs V65 for a non-existent topology).
- Any prod verification or deploy from this worktree.
- The one real EUR order — user-side, and the actual acceptance of the whole
  plan.

## 5. Acceptance (Option 1)

- Module suite green with real "Tests run:" counts: baseline 395 / 0;
  expect 400 / 0 (+5).
- Unit tests as in §1.1; dev boot on :18085 unchanged (context starts, no
  new beans).
- Deploy = MAIN: order container rebuild + recreate; prod proof for the
  new alert is only reachable by approving a refund on a CJ-paid order, so
  the honest prod check is "no boot error + the existing 2026-09-13 proof
  (0 `Preparing:` lines in `docker logs order`)".
