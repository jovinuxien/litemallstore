package org.linlinjava.litemall.order.interfaces.util;

/**
 * Wave-5 shared invite-code contract (see {@code docs/handoff-register-invite.md}):
 * {@code "A" + base36(uid)} uppercase — uid 7 → {@code A7}, uid 42 → {@code A16}.
 * Derived, no DB column; gateway-api decodes the same way at registration. Kept in
 * lock-step with that decoder — change one and you change both.
 */
public final class InviteCodes {

    private InviteCodes() {
    }

    /** uid → invite code ({@code A} + uppercase base36). */
    public static String encode(int uid) {
        return "A" + Integer.toString(uid, 36).toUpperCase();
    }

    /**
     * Invite code → uid, or {@code null} for anything malformed (wrong prefix,
     * empty tail, non-base36 chars, overflow, non-positive uid). Mirrors the
     * registration-side rule that a bad code is a non-event, never an error.
     */
    public static Integer decode(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        if (trimmed.length() < 2 || !(trimmed.charAt(0) == 'A' || trimmed.charAt(0) == 'a')) {
            return null;
        }
        try {
            int uid = Integer.parseInt(trimmed.substring(1), 36);
            return uid > 0 ? uid : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
