// Settings of the Allure report.
// Used by "mvn allure:serve" on a workstation and by "npx allure generate" in the CI workflow.
export default {
  name: "Sandbox Bank test report",
  output: "./target/allure-report",
  // The CI workflow restores this file from the previous run, which is what draws the trend.
  historyPath: "./target/allure-history/history.jsonl",
  // Technical labels that tell a reader of the report nothing.
  hideLabels: ["junit.platform.uniqueid", "host", "thread", "framework", "language", "package", "testClass", "testMethod"],
  plugins: {
    awesome: {
      options: {
        reportLanguage: "en",
        groupBy: ["epic", "feature"],
      },
    },
  },
  // Sorts failures by the question a reader asks first: is it the product, the contract or the environment?
  categories: [
    {
      name: "Environment problems",
      matchedStatuses: ["failed", "broken"],
      messageRegex: ".*Sandbox Bank is not (reachable|healthy).*",
    },
    {
      name: "Contract violations",
      matchedStatuses: ["failed", "broken"],
      messageRegex: ".*(breaks the contract|which openapi\\.yaml does not document).*",
    },
    {
      name: "Accessibility violations",
      matchedStatuses: ["failed"],
      messageRegex: ".*WCAG 2\\.1 A and AA violations.*",
    },
    { name: "Product defects", matchedStatuses: ["failed"] },
    { name: "Test defects", matchedStatuses: ["broken"] },
  ],
};
