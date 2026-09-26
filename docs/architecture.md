# Architecture

Flux is a two-process design. A native Android engine owns capture, storage and
categorization; a Flutter UI owns presentation. They communicate over a single
MethodChannel with a typed, paginated contract.

```
+------------------------------------------------------------+
| Android engine (Kotlin)                                     |
|                                                             |
|  FluxNotificationListener                                   |
|        |  alert text                                        |
|        v                                                    |
|  TransactionEngine                                          |
|        1. noise filter (OTP, promos)                        |
|        2. UniversalParser    -> amount, payee, date, ...    |
|        3. Dedup              -> SHA-256 identity            |
|        4. Categorizer        -> dictionary, Naive Bayes     |
|        |                                                    |
|        v                                                    |
|  Room (transactions, categories, training, settings)        |
|        |  change events                                     |
+--------|----------------------------------------------------+
         v
  MethodChannel "flux.native_bridge"   (paginated reads, writes, events)
         |
         v
+------------------------------------------------------------+
| Flutter UI (Dart, Riverpod)                                 |
|  Pulse   Inbox   Lens   Vault                               |
+------------------------------------------------------------+
```

## Capture pipeline

Every posted notification is filtered, parsed, deduplicated, categorized and
stored in that order. Ingests are serialized behind a mutex so concurrent
notifications keep insertion order and the classifier is built exactly once.

1. **Filter.** Bodies shorter than eight characters, OTP messages and other
   known noise are dropped before any parsing work.
2. **Parse.** `UniversalParser` locates the amount (prefix or suffix currency
   marker, comma-grouped), the direction (credit vs debit verb heuristics), the
   payee (connector-phrase capture, UPI handles, capitalized runs), an optional
   masked account hint and an inline date. When direction cannot be determined
   the parse is marked heuristic and receives 0.5 confidence.
3. **Deduplicate.** `SHA-256(raw alert text | posting app)` is the unique index
   on the transactions table. Conflicting inserts return `-1` and are reported
   as duplicates; nothing is written twice. Two genuine purchases that share an
   amount, payee and minute carry different authorization codes in their text
   and are both kept — only byte-identical re-delivered alerts collapse.
4. **Categorize and classify.** The parser also detects two special lifecycles
   (see below). See the categorization policy section.
5. **Store and notify.** The row is written, a change event is pushed to any
   attached Flutter engine, and the source notification is withdrawn.

## Categorization policy

| Level | Mechanism | Confidence | Applied automatically |
|---|---|---|---|
| 1 | Merchant dictionary (longest keyword match) | 0.95 | yes |
| 2 | Naive Bayes posterior over merchant + alert text | model output | only if >= 0.80 |
| — | Heuristic parse, no model verdict, or foreign currency | <= 0.80 | no — queued in Inbox |

A decision is applied only when `decision.auto` is true **and** the parse itself
was confident **and** the alert's currency matches the base currency
(configurable in Vault; default INR — foreign-currency alerts always wait for
review, since no exchange rates are applied). Manual approvals are recorded as
training samples and the model retrains immediately, so corrections generalize
to future alerts.

The classifier is multinomial Naive Bayes with Laplace smoothing over a
lowercased token vocabulary with digit runs collapsed. It ships with a bundled
corpus of ~50 labeled alerts and rebuilds in memory on every update.

## Transaction lifecycle

Every row carries a `kind` that decides how it is accounted:

| Kind | When | Accounting |
|---|---|---|
| `purchase` | ordinary debit or credit | full participant |
| `refund` | credit marked "refund"/"reversal" | excluded from income; subtracted from its category's spend |
| `pending` | authorization hold or "pending" entry | excluded from every aggregate |

Pending holds are settled away automatically when a later debit from the same
payee (for an amount at or below the hold) is captured — the fuel-pump pattern,
where a pump authorizes its maximum and the final charge posts lower. Holds
that never settle are expired 72 hours after capture by a sweep that runs
whenever the process wakes up; no scheduler is involved.

## Data model (Room, v1)

| Table | Purpose |
|---|---|
| `transactions` | One money event. `hash` is unique (dedup). `amount` is signed. `needsReview` gates Inbox routing. |
| `categories` | Label, color, icon, keyword list, `isDefault` guard for the eleven seeded rows. |
| `training_samples` | Labeled text for the Naive Bayes model; grows with each manual approval. |
| `settings` | Key/value store (biometric lock, onboarding flags). |

## Channel contract

Channel: `flux.native_bridge`. All lists are paginated at 50 items.

| Method | Arguments | Returns |
|---|---|---|
| `getPulseSummary` | — | net balance, credit, debit, counts |
| `getTransactionsPage` | `page`, `pageSize` | items, `hasMore`, total |
| `getInbox` | `limit` | transactions with `needsReview = 1` |
| `categorizeTransaction` | `id`, `categoryId` | applies choice, records training sample, retrains |
| `deleteTransaction` | `id` | — |
| `getSpendingByCategory` | `startMs`, `endMs` | totals per category |
| `getDailySpend` | `startMs`, `endMs` | totals per day |
| `getCategories` / `addCategory` / `updateCategory` / `deleteCategory` | category fields | CRUD; deleting reassigns transactions to `uncategorized` |
| `exportState` / `importState` | path (import streams from disk) | `flux_export_version: 1` archives |
| `getBaseCurrency` / `setBaseCurrency` | currency code | foreign-currency alerts queue in the Inbox |
| `isNotificationAccessGranted`, `openNotificationAccessSettings` | — | capture permission state |
| `isIgnoringBatteryOptimizations`, `requestIgnoreBatteryOptimizations` | — | reliability state |
| `simulateNotification` | `text`, `packageName` | feeds the real pipeline (support/testing) |
| `setBiometricEnabled` / `getBiometricEnabled` | `enabled` | Vault lock preference |

Native to Dart: `onTransactionsChanged` fires after any store mutation; the UI
refreshes its providers from that single signal.

## Demo mode

On web, `bridgeProvider` binds `DemoBridge`, an in-memory `FluxBridge` with a
static dataset. The widget tree is identical to the Android build; only the
data source differs. This keeps the UI developable and demonstrable without a
device or notification access.

## Testing strategy

- **Parser, deduplication, classifier** are pure JVM Kotlin and are covered by
  JUnit tests (`android/app/src/test`) — no emulator required.
- **Widgets and provider wiring** are covered by Flutter widget tests with
  fake controllers overriding the providers.
- **End-to-end capture** is exercised manually or via `simulateNotification`
  on an emulator with notification access granted.
