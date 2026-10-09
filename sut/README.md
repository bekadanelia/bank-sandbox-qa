# Sandbox Bank

The system under test. A small retail banking backend with a double entry ledger, written to be
tested: it is deterministic, starts from an empty database and can be told to misbehave.

| | |
|---|---|
| Runtime | Node 22, Express 5 |
| Database | PostgreSQL 16 |
| Web client | Static HTML and ES modules in `public/`, no build step |
| API contract | `openapi.yaml`, served at `/openapi.yaml`, browsable at `/docs/` |

## Run it

With Docker, from the repository root:

```
docker compose up -d --build --wait
```

Without Docker, against a local PostgreSQL that has a database `sandbox_bank` owned by a role `sandbox`:

```
npm ci
DATABASE_URL=postgres://sandbox:sandbox@localhost:5432/sandbox_bank npm start
```

The test suite also needs the read only role. Create it once by running `db/init/01_qa_readonly.sql` as a superuser.

Then open http://localhost:8080 and sign in as `demo@sandbox.bank` with the password `Sandbox#2026`.

## Settings

| Variable | Default | Meaning |
|----------|---------|---------|
| `PORT` | 8080 | HTTP port |
| `DATABASE_URL` | postgres://sandbox:sandbox@localhost:5432/sandbox_bank | PostgreSQL connection |
| `JWT_SECRET` | local-sandbox-secret | Key that signs the bearer tokens |
| `JWT_TTL_SECONDS` | 1800 | Lifetime of a token |
| `SEED_DEMO_CUSTOMER` | true | Create the demo customer on first start |
| `LOG_REQUESTS` | true | One JSON log line per request |
| `SANDBOX_FAULTS` | empty | Seeded defects to switch on, see `src/faults.js` |

## How money moves

Every deposit and transfer runs in one database transaction (`src/services/transfers.js`):

1. Claim the idempotency key. A second request with the same key waits here and then replays the first answer.
2. Lock the accounts involved, always in the same order.
3. Check the business rules in a fixed order. A broken rule stores the transfer as rejected and stops.
4. Write the ledger entries and update the balances. A conversion passes through the bank's FX position accounts, so every currency balances on its own.
5. Mark the transfer as completed and write an audit event.

Amounts are handled as integer minor units and travel as text, so no floating point number ever touches money.

## Unit tests

```
npm test
```

They cover the pure functions: money arithmetic and rounding, account number check digits and the parsing of fault switches.
Everything that needs a running bank is tested from `../tests`.
