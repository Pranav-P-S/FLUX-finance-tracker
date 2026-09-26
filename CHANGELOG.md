# Changelog

All notable changes to Flux are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-09-26

### Added

- Notification capture engine: bank and UPI alerts are parsed as they arrive,
  with no SMS permissions and no network access.
- Universal notification parser: amount, direction, payee, account hint and
  date extraction, with confidence scoring and a low-confidence review path.
- Transaction deduplication via SHA-256 over epoch minute, amount and payee.
- Two-level on-device categorization: merchant dictionary plus a Naive Bayes
  model that retrains from every user correction; decisions below 80%
  confidence are queued in the Inbox for manual review.
- Inbox review queue with swipe-to-approve and swipe-to-discard triage.
- Pulse dashboard: net balance, credit/debit totals and recent activity.
- Lens analytics: category donut chart and daily spend histogram per month.
- Vault: capture-engine status, category management, biometric lock (local_auth),
  and full-state JSON export/import.
- Demo mode for web rendering against a static dataset.
- Kotlin JVM test suite for the parser, deduplication and classifier;
  Flutter widget tests for the shared components.
- CI pipeline running static analysis, formatting checks, widget tests and
  JVM tests on every push and pull request.
