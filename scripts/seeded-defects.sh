#!/usr/bin/env bash
# Proves that the test suite catches the defects that can be switched on in the bank.
#
#   scripts/seeded-defects.sh list             names of the seeded defects
#   scripts/seeded-defects.sh check DEFECT     start the bank with the defect, run its suites and
#                                              expect the tests that guard its rule to fail
#   scripts/seeded-defects.sh check-all        the same for every defect, one after another
#   scripts/seeded-defects.sh verify DEFECT    only judge the results of the last test run
#
# Exit code 0 means the defect was caught. 1 means it escaped, which is a hole in the suite.
# Needs bash, jq, Docker and Maven.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT/scripts/seeded-defects.csv"
RESULTS="$ROOT/tests/target/allure-results"

list() {
  grep -vE '^(#|$)' "$MANIFEST" | cut -d';' -f1
}

row() {
  grep -E "^$1;" "$MANIFEST" || { echo "Unknown defect '$1'. Known defects: $(list | tr '\n' ' ')" >&2; exit 2; }
}

# Prints a line and, in GitHub Actions, also adds it to the summary page of the job.
say() {
  echo "$1"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then echo "$1" >> "$GITHUB_STEP_SUMMARY"; fi
}

say_list() {
  local item
  while IFS= read -r item; do say "* $item"; done <<< "$1"
}

# Reads the Allure results of the last run and decides whether the defect was caught.
verify() {
  local defect=$1 suites rule expected failed test missing=""
  row "$defect" > /dev/null
  IFS=';' read -r _ suites rule expected <<< "$(row "$defect")"

  failed=$(jq -r 'select(.status == "failed" or .status == "broken") | .fullName' "$RESULTS"/*-result.json 2>/dev/null \
    | sed -E 's/^.*\.tests\.[a-z0-9]+\.//' | sort -u || true)

  IFS=',' read -ra wanted <<< "$expected"
  for test in "${wanted[@]}"; do
    grep -qxF "$test" <<< "$failed" || missing+="$test "
  done

  if [ -z "$failed" ]; then
    say "### $defect escaped"
    say "The suites \`$suites\` passed although the bank breaks rule $rule. No test noticed."
    return 1
  fi
  if [ -n "$missing" ]; then
    say "### $defect was not caught where it should be"
    say "Rule $rule. These tests were expected to fail and did not: \`$missing\`"
    say ""
    say "Tests that failed instead:"
    say_list "$failed"
    return 1
  fi
  say "### $defect caught"
  say "Rule $rule, suites \`$suites\`. $(wc -l <<< "$failed" | tr -d ' ') tests failed, among them every test that must catch this defect:"
  say ""
  say_list "$failed"
}

check() {
  local defect=$1 suites
  row "$defect" > /dev/null
  IFS=';' read -r _ suites _ _ <<< "$(row "$defect")"

  echo ">>> Starting the bank with $defect"
  docker compose -f "$ROOT/docker-compose.yml" down --remove-orphans > /dev/null 2>&1 || true
  SANDBOX_FAULTS="$defect" docker compose -f "$ROOT/docker-compose.yml" up -d --build --wait

  echo ">>> Running the suites: $suites"
  rm -rf "$RESULTS"
  if mvn -B -ntp -f "$ROOT/tests/pom.xml" test -Dgroups="$suites"; then
    say "### $defect escaped"
    say "The suites \`$suites\` passed although the defect is switched on."
    return 1
  fi
  verify "$defect"
}

check_all() {
  local escaped=""
  for defect in $(list); do
    check "$defect" || escaped+="$defect "
  done
  docker compose -f "$ROOT/docker-compose.yml" down --remove-orphans > /dev/null 2>&1 || true
  if [ -n "$escaped" ]; then
    echo "Defects that escaped: $escaped" >&2
    return 1
  fi
  echo "Every seeded defect was caught. The bank has been stopped."
}

case "${1:-}" in
  list) list ;;
  check) check "${2:?name a defect, see: $0 list}" ;;
  check-all) check_all ;;
  verify) verify "${2:?name a defect, see: $0 list}" ;;
  *) sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
esac
