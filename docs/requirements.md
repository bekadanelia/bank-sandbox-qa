# Business rules of Sandbox Bank

These are the rules the test suite verifies. Every automated test names the rules it covers with
`@Requirement("ID")`, and `RequirementsTraceabilityTest` fails the build when a rule has no test
or a test names a rule that is not listed here.

The current rule to test mapping is in [traceability.md](traceability.md).

## Customers and authentication

| ID | Rule |
|----|------|
| <a id="auth-01"></a>AUTH-01 | A visitor registers with an email, a full name and a password. An email can be registered once, compared without case. |
| <a id="auth-02"></a>AUTH-02 | Registration input is validated: a well formed email of at most 254 characters, a name of 2 to 100 characters, a password of 8 to 72 characters. All problems are reported in one response. |
| <a id="auth-03"></a>AUTH-03 | Login with email and password returns a bearer token that is valid for 30 minutes. |
| <a id="auth-04"></a>AUTH-04 | A failed login never reveals whether the email is registered. |
| <a id="auth-05"></a>AUTH-05 | Every endpoint except registration and login requires a valid token. Expired, altered and unsigned tokens are refused. |
| <a id="auth-06"></a>AUTH-06 | Passwords are stored as salted hashes, never as text. |

## Accounts

| ID | Rule |
|----|------|
| <a id="acc-01"></a>ACC-01 | A customer opens accounts in GEL, USD or EUR. A new account is active and has a zero balance. |
| <a id="acc-02"></a>ACC-02 | A customer holds at most 5 accounts. |
| <a id="acc-03"></a>ACC-03 | Account numbers are unique, follow the IBAN layout and carry valid ISO 13616 check digits. |
| <a id="acc-04"></a>ACC-04 | A customer can see and use only their own accounts and transfers. Those of other customers look exactly like ones that do not exist. |
| <a id="acc-05"></a>ACC-05 | An account can be frozen and unfrozen. A frozen account neither sends nor receives money. Freezing a frozen account changes nothing. |

## Deposits

| ID | Rule |
|----|------|
| <a id="dep-01"></a>DEP-01 | A deposit credits an own account in the currency of the account. The amount is positive and has at most two decimals. |
| <a id="dep-02"></a>DEP-02 | A single deposit is at most 100000.00. |

## Transfers

| ID | Rule |
|----|------|
| <a id="trf-01"></a>TRF-01 | A transfer moves exactly the requested amount from an own account to any account identified by its number. |
| <a id="trf-02"></a>TRF-02 | Amounts are sent as text, are positive and have at most two decimals. Invalid input is refused with the name of the field and the problem. |
| <a id="trf-03"></a>TRF-03 | Source and target must be different accounts. |
| <a id="trf-04"></a>TRF-04 | Source and target must both be active. |
| <a id="trf-05"></a>TRF-05 | A single transfer is at most 10000.00 in the currency of the source account. |
| <a id="trf-06"></a>TRF-06 | The source account must hold the full amount. A customer balance never goes below zero. |
| <a id="trf-07"></a>TRF-07 | When several rules are broken, the first one in this order is reported: same account, account not active, limit, amount too small to convert, insufficient funds. |
| <a id="trf-08"></a>TRF-08 | A transfer that breaks a business rule is stored as rejected with the reason and moves no money. |
| <a id="trf-09"></a>TRF-09 | A target number with a wrong layout or wrong check digits is refused as invalid input. A well formed number of no account is reported as not found. |
| <a id="trf-10"></a>TRF-10 | Simultaneous money movements never overdraw an account, never lose an update and never block each other forever. |

## Idempotency

| ID | Rule |
|----|------|
| <a id="idm-01"></a>IDM-01 | Every deposit and transfer carries an `Idempotency-Key` header of 8 to 64 letters, digits, hyphens or underscores. |
| <a id="idm-02"></a>IDM-02 | Repeating a request with the same key returns the first answer, marked as a replay, and moves no money again. |
| <a id="idm-03"></a>IDM-03 | Using a key again with a different request is a conflict. |
| <a id="idm-04"></a>IDM-04 | Keys belong to one customer. Two customers can use the same key independently. |

## Currency conversion

| ID | Rule |
|----|------|
| <a id="fx-01"></a>FX-01 | A transfer between accounts in different currencies is converted with the published rate of that currency pair. |
| <a id="fx-02"></a>FX-02 | The converted amount is rounded half up to two decimals. |
| <a id="fx-03"></a>FX-03 | A transfer whose converted amount rounds to zero is rejected. |
| <a id="fx-04"></a>FX-04 | The two rates of a currency pair include a spread. Converting there and back never returns more than the customer started with. |

## Statements

| ID | Rule |
|----|------|
| <a id="stm-01"></a>STM-01 | The statement of an account lists its ledger entries newest first, each with the balance after it. |
| <a id="stm-02"></a>STM-02 | The statement is paged. Pages start at 0, the page size is 1 to 100 and 20 when not given. |

## Ledger

| ID | Rule |
|----|------|
| <a id="led-01"></a>LED-01 | Double entry: in every transfer the debits equal the credits in each currency. |
| <a id="led-02"></a>LED-02 | The stored balance of every account equals the sum of its ledger entries. |
| <a id="led-03"></a>LED-03 | Every ledger entry records the balance after it, and that balance continues from the previous entry of the account. |
| <a id="led-04"></a>LED-04 | Money is neither created nor destroyed. All balances of one currency, the bank's own accounts included, add up to zero. |
| <a id="led-05"></a>LED-05 | No customer account is or ever was overdrawn. The database refuses a negative customer balance. |
| <a id="led-06"></a>LED-06 | A completed transfer has two ledger entries, or four when it converts currency. A rejected transfer has none. |
| <a id="led-07"></a>LED-07 | No transfer is left in a pending state. |
| <a id="led-08"></a>LED-08 | Ledger entries are posted to the accounts, in the amounts and in the currencies that their transfer names. |
| <a id="led-09"></a>LED-09 | The ledger is append only. Entries cannot be changed or deleted, and each one has a positive amount and an existing account. |

## Audit trail

| ID | Rule |
|----|------|
| <a id="aud-01"></a>AUD-01 | Every completed or rejected transfer leaves exactly one audit event that says who did what. |
| <a id="aud-02"></a>AUD-02 | Every audit event points at a row that exists. |

## API conventions

| ID | Rule |
|----|------|
| <a id="api-01"></a>API-01 | Every response matches the published OpenAPI contract: documented status codes only, no missing and no extra fields. |
| <a id="api-02"></a>API-02 | Every error has the same shape with a stable code and the request id, and reveals nothing about the implementation. |
| <a id="api-03"></a>API-03 | Malformed, oversized and wrongly typed requests get a 4xx answer. Input can never cause a 5xx. |
| <a id="api-04"></a>API-04 | Responses carry security headers: API data is not cached, content types are not sniffed, the server technology is not disclosed and the web client has a content security policy. |

## Web client

| ID | Rule |
|----|------|
| <a id="ui-01"></a>UI-01 | A customer signs in, stays signed in across a reload and signs out. An expired session leads back to sign in with an explanation. |
| <a id="ui-02"></a>UI-02 | After a money movement the balances and the statement on screen are up to date without a reload. |
| <a id="ui-03"></a>UI-03 | Refusals and invalid input are explained in words, at the field they concern where there is one. |
| <a id="ui-04"></a>UI-04 | A payment cannot be made twice by accident, neither by a double click nor by sending again after an answer was lost. |
| <a id="ui-05"></a>UI-05 | Text entered by customers is displayed as text and never interpreted as HTML. |
| <a id="ui-06"></a>UI-06 | The client meets WCAG 2.1 level AA in automated checks, works with the keyboard alone and announces messages to assistive technology. |
