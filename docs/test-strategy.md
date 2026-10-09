# Test strategy

## 1. What is under test

Sandbox Bank is a small retail banking backend with a web client:

* customers register and sign in
* they open accounts in GEL, USD and EUR and pay cash in
* they move money between accounts, with currency conversion where needed
* every movement is written to a double entry ledger and an audit trail

The business rules are listed with ids in [requirements.md](requirements.md). The system is small
on purpose. What makes it worth testing is that it handles money: a wrong cent, a payment made
twice or a balance that disagrees with the ledger is a defect of the highest severity.

## 2. Risks first

Testing effort follows risk. The table is ordered by what a failure would cost.

| Risk | Example | Where it is covered |
|------|---------|---------------------|
| Money is created, lost or counted twice | A conversion credits the customer but never debits the bank's position | SQL reconciliation over the whole ledger (LED-01 to LED-08) |
| A payment is executed twice | The client retries after a timeout, the customer double clicks | Idempotency tests at API level, lost answer and double click tests in the browser (IDM, UI-04) |
| Race conditions under parallel requests | Two transfers spend the same balance, deposits deadlock | Concurrency tests that fire requests at the same instant (TRF-10) |
| One customer reaches another customer's money or data | Reading a foreign account by guessing its id | Access control tests with two real customers (ACC-04), token tests (AUTH-05) |
| Wrong amounts | Rounding of converted amounts, off by one at a limit | Boundary tests and a table of conversions with hand calculated results (FX-02, TRF-05, TRF-06) |
| The API drifts from its documentation | A new field or status code that clients do not expect | Every response of every test is checked against openapi.yaml (API-01) |
| Bad input crashes the service | Broken JSON, oversized bodies, control characters, SQL in text fields | Negative tests that expect a 4xx and never a 5xx (API-03) |
| The web client misleads the customer | A stale balance after a transfer, a refusal without a reason | UI tests on what the customer sees after each action (UI-02, UI-03) |
| The web client excludes customers | Missing labels, low contrast, keyboard traps | axe scans against WCAG 2.1 AA, keyboard and reflow tests (UI-06) |

## 3. Test levels

A rule is tested at the lowest level that can prove it. Higher levels only prove that the
pieces are wired together.

| Level | Tool | Proves | Tests |
|-------|------|--------|------:|
| Unit, inside the bank | node:test | Money arithmetic, check digits, fault switches | 34 |
| Framework self tests | JUnit | The contract checker and the oracles can fail when they should | 21 |
| API | REST Assured | Business rules, validation, security, idempotency, concurrency | 238 |
| Database | JDBC and SQL | Ledger invariants, persistence, constraints | 33 |
| UI | Playwright | What the customer sees and can do, accessibility | 37 |
| End to end | Playwright, REST Assured, SQL | Two journeys across all layers | 2 |

Most rules live in the API suite because that is where they can be checked fast and precisely.
The UI suite does not repeat them. It checks behaviour that only exists in the browser: what is
shown after an action, how errors are explained, what happens on a double click.

The database suite exists because some defects are invisible from outside. When the seeded defect
`FX_POSITION_SKIPPED` is on, every API and UI test passes and only the SQL checks fail.

## 4. Test design techniques

| Technique | Where it is used |
|-----------|------------------|
| Equivalence partitioning | Valid and invalid classes of amounts, emails, currencies, tokens, paging values |
| Boundary value analysis | Each limit gets a value just inside, on and just outside: 9999.99, 10000.00, 10000.01 |
| Decision table | Which rejection is reported when several rules are broken at once (TRF-07) |
| State transition | Account active and frozen, transfer completed and rejected, session valid and expired |
| Invariants | Statements that must hold for the whole ledger whatever happened before |
| Error guessing | SQL and HTML in text fields, NUL characters, undecodable URLs, an answer lost on the wire |
| Checklist | OWASP API Security Top 10 for access control, WCAG 2.1 AA for the web client |

Expected values never come from the system under test. Conversion results are calculated by
hand, check digits by an independent implementation that is itself tested against the published
ISO example, and ledger rules are recomputed in SQL.

## 5. Test data

* Every test creates the customers and accounts it needs through the public API.
* Emails and idempotency keys are unique per test, so tests can run in parallel and in any order.
* Nothing is cleaned up. The database lives in memory and is gone when the containers stop.
* Exchange rates are fixed reference data, which keeps expected amounts stable.
* No test depends on the demo customer or on data another test left behind.

## 6. Quality gates in the pipeline

A build is green only when all of these hold:

1. The bank's own unit tests pass.
2. Framework self tests and smoke tests pass. Nothing else runs if they do not.
3. API, UI, end to end and database suites pass with zero failures.
4. Every response matched the OpenAPI contract.
5. Every rule in requirements.md is named by at least one test, and no test names a rule that does not exist.
6. Outside pull requests: each of the nine seeded defects is caught by the tests that guard its rule.

## 7. Flaky tests

* There are no retries. Surefire is configured with `rerunFailingTestsCount` 0. A retry turns an
  intermittent defect into a green build.
* There are no sleeps. UI tests wait on conditions through Playwright's assertions, API tests are synchronous.
* Tests share no data, which removes the most common cause of flakiness.
* A test that fails intermittently is treated as a defect in the test or in the product, and is
  fixed or deleted. The first version of this suite had such a test. It turned out to be a real
  deadlock in the bank, see [defect-reports.md](defect-reports.md).

## 8. Reporting

* Allure report with steps, the HTTP exchange of every API call, the SQL of every reconciliation
  check and, for failed UI tests, a screenshot and a Playwright trace.
* Failures are sorted into environment problems, contract violations, accessibility violations,
  product defects and test defects.
* Each test links to the rules it verifies.
* The Authorization header is masked in the report.

## 9. Defects

A defect report states the broken rule, the shortest way to reproduce it, expected and actual
behaviour, evidence and a severity with its reason. Examples from this project are in
[defect-reports.md](defect-reports.md), the template is in `.github/ISSUE_TEMPLATE/defect.md`.

| Severity | Meaning here |
|----------|--------------|
| Blocker | Money is wrong or the books do not balance |
| Critical | A customer can reach foreign data or pay twice, or a core flow is unusable |
| Major | A rule is enforced wrongly without money being at risk, or a customer is misled |
| Minor | Cosmetic or inconvenient |

## 10. Out of scope, and what would come next

| Not covered | Reason | Next step in a real project |
|-------------|--------|-----------------------------|
| Load and performance | A laptop size sandbox gives no meaningful numbers | k6 or Gatling against a production like environment, with latency budgets per endpoint |
| Security scanning | The suite tests authorization and input handling by design, not by scanner | OWASP ZAP baseline scan and dependency audit as pipeline stages |
| Brute force protection | The bank has no lockout or rate limit, which is a product gap and is recorded here as a known risk | Raise as a defect, then test lockout thresholds |
| Browsers other than Chromium in CI | The framework runs Firefox and WebKit with `-Dbrowser`, the pipeline does not | A nightly browser matrix |
| Screen reader behaviour | An automated scan finds only a part of all accessibility problems | Manual sessions with NVDA and VoiceOver on the main flows |
| Breaking changes of the contract | The suite checks that responses match the contract, not that the contract stayed compatible | A diff of openapi.yaml against the main branch on every pull request |
