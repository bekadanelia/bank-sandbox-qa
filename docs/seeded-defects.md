# Seeded defects

A green test run says that nothing the tests look at is broken. It does not say how much the
tests look at. This project answers that question directly: the bank can be started with a known
defect switched on, and the suite has to notice.

## How it works

The bank reads the environment variable `SANDBOX_FAULTS`. Each name turns on one realistic defect,
implemented as one deliberately wrong branch in the code (see `sut/src/faults.js`). With the
variable empty, which is the default, the bank behaves correctly.

```
SANDBOX_FAULTS=FX_TRUNCATE docker compose up -d --build --wait
mvn -f tests/pom.xml test -Dgroups="api | db"      # must fail
```

`scripts/seeded-defects.csv` lists for every defect the rule it breaks and the tests that must
fail. `scripts/seeded-defects.sh check DEFECT` starts the bank with the defect, runs the suites
and succeeds only if those tests failed. The pipeline runs this for all nine defects in a matrix
after the healthy run has passed.

## The defects and who catches them

Result of running the full suite against each defect. A mark means at least one test of that
layer fails.

| Defect | What the bank does wrong | Rule | API | DB | UI |
|--------|--------------------------|------|:---:|:--:|:--:|
| `FX_TRUNCATE` | Cuts converted amounts off instead of rounding half up | FX-02 | ✔ | ✔ | ✔ |
| `LIMIT_OFF_BY_ONE` | Rejects a transfer of exactly the limit | TRF-05 | ✔ | | |
| `IDEMPOTENCY_IGNORED` | Executes a repeated request a second time | IDM-02 | ✔ | ✔ | ✔ |
| `NO_ROW_LOCK` | Reads balances without locking, so parallel transfers overwrite each other | TRF-10 | ✔ | ✔ | |
| `DEADLOCK_PRONE_LOCK` | Locks accounts in a way that deadlocks parallel deposits | TRF-10 | ✔ | | |
| `FX_POSITION_SKIPPED` | Leaves the bank's side of a conversion out of the ledger | LED-01 | | ✔ | |
| `IDOR_ACCOUNT_READ` | Lets any signed in customer read any account | ACC-04 | ✔ | | |
| `STATEMENT_ORDER` | Returns statements oldest first | STM-01 | ✔ | | ✔ |
| `UI_STALE_BALANCE` | Does not refresh balances in the browser after a transfer | UI-02 | | | ✔ |

Three things are worth reading out of this table.

**The layers are not redundant.** `FX_POSITION_SKIPPED` passes every API and UI test, because the
customer's balances are right. Only the SQL reconciliation sees that the bank's books no longer
add up. `UI_STALE_BALANCE` is the mirror image: the API and the database are correct and only a
browser test can see the problem.

**Boundaries need the exact value.** `LIMIT_OFF_BY_ONE` is caught by one kind of test only, the
one that sends exactly 10000.00. A test with 9999.00 and 10001.00 would have stayed green.

**One defect was not planned.** `DEADLOCK_PRONE_LOCK` reproduces a defect that this suite found
in the first version of the bank. It was kept as a switch after the fix, so the regression test
that guards it is proven to work. The report is in [defect-reports.md](defect-reports.md).

## Running it

```
scripts/seeded-defects.sh list          # the nine names
scripts/seeded-defects.sh check NO_ROW_LOCK
scripts/seeded-defects.sh check-all     # about ten minutes
```

The script needs bash, jq, Docker and Maven. On Windows it runs in Git Bash or WSL.

## Adding a defect

1. Add a name and a description to `sut/src/faults.js` and one wrong branch where the rule is implemented.
2. Start the bank with it and run the suite. If nothing fails, a test is missing. Write that test first.
3. Add a line to `scripts/seeded-defects.csv` with the tests that must fail.
4. Add the name to the matrix in `.github/workflows/ci.yml`.

`SeededDefectsManifestTest` fails when the list in the bank and the list in the manifest differ,
or when the manifest names a test or a rule that no longer exists.

## What this is not

It is not mutation testing. Mutation tools change the code in thousands of small mechanical ways.
Here there are nine defects, chosen by hand because each is a mistake that real payment systems
have made. The result is a smaller but more readable statement: these are the bugs this suite is
known to catch.
