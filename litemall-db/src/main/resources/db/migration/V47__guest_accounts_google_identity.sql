-- V47__guest_accounts_google_identity.sql
--
-- Identity & onboarding (Wave 16, gateway-api edge auth).
--
-- 1) Guest checkout via shadow accounts: /checkout works logged-out by
--    auto-provisioning a password-less account keyed to the buyer's email.
--    is_guest marks such accounts; it is cleared when the buyer claims the
--    account by setting a password (or signs in with Google on that email).
--    Orders attach to a real user id, so the order service is unchanged.
--
-- 2) Google Sign-In: the edge verifies a Google ID token server-side and
--    provisions or links by VERIFIED email. google_sub stores Google's stable
--    subject identifier (the durable key Google documents for account
--    linking — emails can change on Google's side). Unique when present;
--    NULLs don't collide. Mirrors the existing weixin_openid precedent.

ALTER TABLE litemall_user
    ADD COLUMN is_guest tinyint(1) NOT NULL DEFAULT 0
        COMMENT 'Wave 16: shadow account auto-provisioned at guest checkout; cleared on claim',
    ADD COLUMN google_sub varchar(64) NULL DEFAULT NULL
        COMMENT 'Wave 16: Google ID-token subject for Sign in with Google account linking';

ALTER TABLE litemall_user
    ADD UNIQUE KEY uk_litemall_user_google_sub (google_sub);
