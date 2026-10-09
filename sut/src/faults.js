// Seeded defects. Each switch turns one realistic bug on so the test suite can prove it
// detects that bug. Nothing here is enabled unless SANDBOX_FAULTS names it explicitly.
export const FAULTS = Object.freeze({
  FX_TRUNCATE: 'Converted amounts are truncated instead of rounded half up',
  LIMIT_OFF_BY_ONE: 'A transfer of exactly the limit amount is rejected',
  IDEMPOTENCY_IGNORED: 'A repeated Idempotency-Key creates a second transfer',
  NO_ROW_LOCK: 'Balances are read without row locks, so concurrent transfers can overdraw',
  DEADLOCK_PRONE_LOCK: 'Accounts are locked FOR UPDATE, which deadlocks with foreign key locks under parallel deposits',
  FX_POSITION_SKIPPED: 'The bank side ledger entries of a conversion are not written',
  IDOR_ACCOUNT_READ: 'Any authenticated customer can read any account by id',
  STATEMENT_ORDER: 'Statements are returned oldest first',
  UI_STALE_BALANCE: 'The web client does not refresh balances after a transfer',
});

export function parseFaults(raw) {
  const names = (raw ?? '')
    .split(',')
    .map((name) => name.trim().toUpperCase())
    .filter(Boolean);
  const unknown = names.filter((name) => !(name in FAULTS));
  if (unknown.length > 0) {
    throw new Error(
      `Unknown fault(s) in SANDBOX_FAULTS: ${unknown.join(', ')}. Known faults: ${Object.keys(FAULTS).join(', ')}`,
    );
  }
  const active = new Set(names);
  return Object.freeze({
    active: [...active].sort(),
    has: (name) => active.has(name),
  });
}
