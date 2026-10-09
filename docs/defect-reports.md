# Defect reports

Four defects that this project found in its own bank while the suite was being built. None of
them was planted. They are written up the way they would be filed in a tracker.

All four are fixed. Each fix is guarded by the test named under "Regression test".

---

## BANK-1 Parallel deposits fail with HTTP 500

| | |
|---|---|
| Severity | Critical. Money movements fail at random under normal load, and the customer gets a server error with no explanation |
| Found by | `AccountFreezeTest` failing once in a parallel run, reported by the contract check as "POST /accounts/{accountId}/deposits answered 500, which openapi.yaml does not document" |
| Rule broken | TRF-10 |

**Steps to reproduce**

1. Register 12 customers and open one GEL account for each.
2. Send one deposit of 5.00 to each account, all at the same moment.

**Expected:** twelve answers with status 201.

**Actual:** some of the requests answer 500 `INTERNAL_ERROR` after about one second. The bank log
shows `deadlock detected` from PostgreSQL. In an ordinary parallel test run the same failure
appears now and then on a single deposit, which is how it was first seen.

**Root cause**

A deposit first inserts a row into `transfers`. That row has foreign keys to the source and
target account, so PostgreSQL takes a `FOR KEY SHARE` lock on both account rows. Then the bank
locked the same rows with `SELECT ... FOR UPDATE`, which conflicts with key share locks held by
other transactions. All deposits of one currency share one source row, the bank's cash account:

1. Transaction A inserts its transfer and holds a key share lock on the cash account.
2. Transaction B does the same.
3. A asks for `FOR UPDATE` on the cash account and waits for B's key share lock.
4. B asks for `FOR UPDATE` on the cash account and waits for A's. Deadlock.

**Fix:** lock with `FOR NO KEY UPDATE`. A balance update does not change a key, and this lock
mode does not conflict with key share locks. See `sut/src/services/transfers.js`.

**Regression test:** `ConcurrencyTest.deposits_of_different_customers_do_not_block_each_other`.
The old behaviour is kept as the seeded defect `DEADLOCK_PRONE_LOCK`, which proves that this test
fails when the defect comes back.

**What it shows:** the ordering of locks by account id, which was already in place, protects
against the textbook deadlock between two transfers in opposite directions. It does not protect
against locks the database takes on its own. Only a test with real parallel requests against a
real database finds this.

---

## BANK-2 A wrong password shows no message on the sign in page

| | |
|---|---|
| Severity | Major. The customer cannot tell that the sign in failed |
| Found by | `SignInUiTest.a_wrong_password_is_reported_and_the_customer_stays_on_the_sign_in_page` |
| Rule broken | UI-03 |

**Steps to reproduce**

1. Open the sign in page.
2. Enter a registered email and a wrong password.
3. Press Sign in.

**Expected:** the alert "Email or password is incorrect."

**Actual:** nothing happens. The form stays as it is and no message appears. The API answers 401
`INVALID_CREDENTIALS` correctly.

**Root cause:** the client has one handler for unexpected failures, and that handler stays silent
on every 401 because an expired session is announced elsewhere. A failed login is also a 401 and
fell into the same branch.

**Fix:** the sign in form handles `INVALID_CREDENTIALS` itself, shows the message and moves the
focus to the password field. See `sut/public/app.js`.

**Regression test:** the test that found it.

**What it shows:** the API test for the same case was green. The defect existed only in the browser.

---

## BANK-3 The dashboard scrolls sideways on a narrow screen

| | |
|---|---|
| Severity | Major. Fails WCAG 2.1 success criterion 1.4.10 Reflow, level AA |
| Found by | `AccessibilityUiTest.the_dashboard_fits_a_narrow_phone_without_scrolling_sideways` |
| Rule broken | UI-06 |

**Steps to reproduce**

1. Sign in as a customer with at least one account.
2. Set the browser window to a width of 320 CSS pixels, or zoom a 1280 pixel window to 400 percent.

**Expected:** the page content fits the width. Only the vertical scrollbar is needed.

**Actual:** the page is 395 pixels wider than the screen, because the tables do not shrink
below the width of their columns.

**Fix:** each table sits in its own region that scrolls horizontally, has an accessible name and
can be focused with the keyboard. The page itself no longer scrolls sideways. See
`sut/public/index.html` and `sut/public/styles.css`.

**Regression test:** the test that found it.

**What it shows:** axe has no rule for reflow. Rule based scans do not measure layout, so this
criterion needs a test of its own.

---

## BANK-4 The Sign out button is shown to visitors who are not signed in

| | |
|---|---|
| Severity | Minor. Confusing, no functional harm |
| Found by | Exploratory look at the first screenshot of the sign in page |
| Rule broken | UI-01 |

**Steps to reproduce**

1. Open the sign in page without a session.

**Expected:** the header shows only the name of the bank.

**Actual:** the header also shows a Sign out button.

**Root cause:** the session area carries the `hidden` attribute, and a later style rule sets
`display: flex` on it. An author style for `display` wins over the browser's default style for
`hidden`.

**Fix:** one rule that makes `hidden` win, `[hidden] { display: none !important; }`.

**Regression test:** `SignInUiTest.a_visitor_who_is_not_signed_in_is_not_offered_to_sign_out`,
written after the fix. It was checked against the old stylesheet, where it fails.

**What it shows:** not every finding comes from automation. This one took a pair of eyes and ten seconds.
