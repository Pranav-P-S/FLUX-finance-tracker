# Changelog

All notable changes to Flux are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.1] - 2026-09-26

### Fixed

- Raw alert text is scrubbed from the database 72 hours after capture, once a
  transaction has left the Inbox. Only the structured fields (amount, payee,
  category, date) are retained long-term.
- Training samples for the categorizer no longer contain the raw alert
  payload — payee plus purpose words only, with digit runs (references,
  amounts, account digits) stripped before insertion.
- JSON archives omit raw alert text entirely. Exports land in shared storage
  where any app with storage access can read them; the archive now carries the
  structured transaction fields only.
- Chart animations no longer restart on unrelated rebuilds: slice and bar
  comparisons use value equality instead of list identity.
- File-picker cancellation during import no longer surfaces a spurious error.

### Added

- Accessibility: transaction tiles read as a single utterance to screen
  readers; both charts carry semantic labels.

### Changed

- User category corrections now fold into the Naive Bayes model incrementally
  (copy-on-write) instead of rebuilding from the full training corpus on the
  ingestion path.
- Removed the unused whole-store fetch from the platform bridge.

## [1.1.0] - 2026-09-26

### Fixed

- Two genuine purchases sharing an amount, payee and minute no longer collide:
  transaction identity is now SHA-256 of the raw alert text and posting app
  instead of the parsed minute/amount/payee triple.
- Refunds no longer inflate income. They offset their category's spend, so a
  returned item cancels part of the original expense.
- Dashboard and analytics aggregates ignore pending authorization holds.

### Added

- Pending-hold lifecycle: holds are stored separately, settle away
  automatically when the final charge posts from the same payee, and expire
  72 hours after capture if they never do.
- Base currency preference (Vault -> Preferences). Alerts in any other
  currency queue in the Inbox for review, since no exchange rates are applied.
- Streamed JSON export and import: archives are written and read page by page
  straight from disk, so memory stays flat regardless of history size.
- Lifecycle tags (refund / pending) on transaction tiles.
- Room migration 1 -> 2 for the new `kind` column.

### Changed

- Import now takes a file path and streams on the native side; archive
  content never passes through Dart memory.

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
