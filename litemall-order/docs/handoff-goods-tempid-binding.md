# Handoff → goods-management: goods ↔ freight-template binding (Wave 4)

Freight templates (order-owned, `docs/adr-freight-templates.md`) bind to goods through
the pre-existing `litemall_goods.temp_id` column (V2; `0 = unbound`). Wave 4 mapped it
in the shared litemall-db (`LitemallGoods.getTempId()` + all `LitemallGoodsMapper.xml`
slots), so it round-trips through every existing goods read/update path.

## Contract

- `temp_id = 0` (default): the goods prices via the admin-set DEFAULT template
  (`is_default`), else the legacy flat value. Nothing to do for existing goods.
- `temp_id = <id>`: the goods prices via that template at quote and submit.
- A template CANNOT be deleted while any live (on-sale, not deleted) goods references
  it — order's `/srv/private/admin/freight/delete` refuses with 422. Delisting or
  rebinding the goods first is the admin flow.
- Order reads the binding directly from litemall-db at pricing time (5-min cached
  template data, uncached temp_id read) — no goods-management API involvement, no new
  goods-management endpoint REQUIRED for pricing to work.

## What goods-management SHOULD add (its Wave-4 "freight tempId surface")

1. Admin goods create/update: accept + persist `tempId` (the litemall-db mapper slots
   already exist — it is one field on the admin DTO).
2. Admin goods read (detail/list): return `tempId` so the binding UI can render it.
   The template picker feeds from order's `GET /srv/private/admin/freight/selectlist`
   (BARE array `[{id, name, isDefault}]`).
3. Optional: expose `tempId` on the internal goods detail JSON the order facade reads —
   order does not need it (it reads litemall-db), but it would let the facade carry the
   binding if pricing ever moves off the shared DB.

Weight/volume: `litemall_goods.weight/volume` are also mapped now, but freight billing
is piece-only v1 (plan §4.4) — surfacing them in admin forms is optional this wave.
