## What changes

<!-- One or two sentences. -->

## Rules touched

<!-- Ids from docs/requirements.md, for example TRF-05. Write "none" for refactoring. -->

## Checklist

* [ ] New or changed behaviour has a test at the lowest layer that can prove it
* [ ] The test names the rule with `@Requirement`
* [ ] `mvn test` passes locally against `docker compose up`
* [ ] A new rule is in `docs/requirements.md`, a changed endpoint is in `sut/openapi.yaml`
* [ ] No sleeps, no retries, no shared test data
