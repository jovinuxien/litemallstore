/**
 * Wave-5 affiliate invite capture. A `?invite=<code>` query param on ANY
 * landing route (e.g. `/product/42?invite=A7`) is stashed here so it survives
 * browsing until the visitor registers. Register.tsx surfaces it as a
 * dismissible chip, sends it with the register call, and clears the stash on
 * success (or on dismiss = opt out). sessionStorage on purpose: the capture is
 * per-tab and dies with the session — attribution is decided server-side at
 * registration, never from a long-lived cookie (locked v1 decision).
 */
const INVITE_KEY = 'inviteCode';

export const stashInviteCode = (code: string): void => sessionStorage.setItem(INVITE_KEY, code);

export const getStashedInviteCode = (): string | null => sessionStorage.getItem(INVITE_KEY);

export const clearStashedInviteCode = (): void => sessionStorage.removeItem(INVITE_KEY);
