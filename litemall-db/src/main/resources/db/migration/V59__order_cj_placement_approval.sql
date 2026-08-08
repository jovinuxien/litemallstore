-- V59__order_cj_placement_approval.sql
--
-- Wave 23 (admin-gated CJ placement): paid CJ orders no longer auto-place at
-- CJ — in placement-mode 'manual' (the default) the placement sweep only
-- places orders an admin has explicitly approved in the admin panel. The
-- approval is a stamp on the order row itself, matching the existing
-- order-row-as-durable-job placement design: the sweep predicate simply
-- gains "and cj_placement_approved_time is not null" in manual mode.
--
-- cj_placement_approved_time: when the admin approved the order for CJ
-- fulfilment; NULL = not (yet) approved. Set exactly once (CAS on NULL).
-- cj_placement_approved_by: the approving admin (X-User-Id), audit trail —
-- same shape as verified_by (V35 write-off audit stamp).

ALTER TABLE litemall_order
    ADD COLUMN cj_placement_approved_time DATETIME NULL
        COMMENT 'admin approval stamp for CJ placement (Wave 23); NULL = awaiting approval in manual mode'
        AFTER pink_id,
    ADD COLUMN cj_placement_approved_by VARCHAR(63) NULL
        COMMENT 'admin (X-User-Id) who approved CJ placement'
        AFTER cj_placement_approved_time;
