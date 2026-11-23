# shared: Jackdaw core

All app logic lives here (Kotlin Multiplatform, `commonMain`). The Compose and SwiftUI apps only render state and forward user input. The module reads and writes Jackdaw packs ([`format/README.md`](../format/README.md) is the normative spec), keeps all learning progress in SQLite (SQLDelight), schedules reviews and runs study sessions. There is no network code.

Package root `ch.orellbuehler.jackdaw`. On iOS the module is the `shared` framework, consumed through SKIE.

| Package | Content |
|---|---|
| `JackdawSdk` | The one entry point (see below). |
| `db` | `DatabaseDriverFactory` (expect/actual), SQLDelight schema (`Pack.sq`, `Review.sq`, `Setting.sq`, `Sync.sq`). |
| `format` | Pack reader (tolerant, spec section 11), markup/cloze parsers, answer normalisation. Public: `RichText`, `TextSpan`, `ItemType`, `Import*` types. Everything else is `internal`. |
| `study` | Scheduler, planner, exercise factory and the `StudySession` state machine. |
| `store` | Repositories and the read models the UIs render (`PackSummary`, `Stats`, ...). |
| `sync` | Platform independent half of iCloud sync: what changed locally, applying fetched records, merge rules (see "Sync"). Still no network code. |
| `samples` | The bundled sample packs. |

## Conventions

- **Time** is always `Long` epoch milliseconds (`lastStudiedAt`, `dueAt`, `importedAt`, `durationMs`). `0` means "never" / "not scheduled". Days are `Int` epoch days plus an ISO date string (`"2026-11-20"`) in the device time zone.
- **No nullable primitives.** Optional numbers use `0` (`PackDetail.revision == 0` means none). Optional strings are `String?`.
- **Section ids**: `""` is the implicit "ungrouped" group (its `SectionSummary.isUngrouped` is true and its title is empty: localise it in the UI). `null` in `SessionConfig.sectionId` means the whole pack.
- **Rich text** is `RichText(spans: [TextSpan])`, each span has `text` and the flags `bold`, `italic`, `code` (styles never nest). Newlines are kept inside span text. `plain` gives the text without styling. Render spans as text, never as HTML or links.
- **Ids are strings** (pack, section, item, option, entry ids). Option and entry ids of an exercise are only valid for that exercise instance.
- Progress is tracked per **facet**: `Facet.FORWARD` and, for reversible cards, `Facet.REVERSE`. Everything else has only a forward facet. Counts in `PackSummary` are facet counts, `itemCount` is the number of items.
- Public functions never throw for misuse. Wrong-state calls on a `StudySession` are ignored. Import problems are returned as values (`ImportResult.Failure`).

## JackdawSdk

```kotlin
val sdk = JackdawSdk(DatabaseDriverFactory(context))   // Android
val sdk = JackdawSdk(driverFactory: DatabaseDriverFactory())  // iOS (Swift)
```

Create exactly one instance per process and keep it alive (application scope). Opening the database is lazy and cheap.

### Library and packs

| Member | Returns | Notes |
|---|---|---|
| `observePacks()` | `Flow<List<PackSummary>>` | Sorted by title. Updates after every import, answer, reset, delete. |
| `observePack(packId)` | `Flow<PackDetail?>` | `null` when the pack is not installed. |
| `observeItems(packId)` / `items(packId)` | `Flow<List<ItemSummary>>` / `List<ItemSummary>` | Browse list in pack order. |
| `previewImport(text)` | `ImportPreview` | Validates and diffs, writes nothing. |
| `importPack(text)` | `ImportResult` | Installs or updates. Refuses revision downgrades. |
| `importPackAllowingDowngrade(text)` | `ImportResult` | Use after the user confirmed a downgrade. |
| `deletePack(packId)` | | Removes pack and progress. Activity history (streak, stats) is kept. |
| `resetProgress(packId)` | | All facets back to new. Activity history is kept. |
| `exportPack(packId)` | `String?` | The pack JSON as it was imported (content only, no progress). |
| `samplePacks()` | `List<SamplePack>` | Bundled packs (from `format/examples`). Not suspending. |
| `importSample(sampleId)` | `ImportResult` | `sampleId` is the pack id (`SamplePack.id`) of a bundled pack. |
| `catalogUrl()` | `String` | Where the online pack list is (`format/examples/index.json` on `main`). The UI downloads it; `shared` has no network code. |
| `readCatalog(text)` | `List<SamplePack>?` | The sample library for a downloaded list, or `null` when the text is not a catalogue this version understands. Not suspending. |

`SamplePack`: `id, title, descriptionText, icon, color, itemCount, revision, downloadUrl`. `downloadUrl == null` means the bundled copy is current: call `importSample(id)`. Otherwise download the text from `downloadUrl` and pass it to `importPack`. In a list from `readCatalog` a bundled pack keeps `downloadUrl == null` unless the catalogue has a higher revision, and bundled packs the catalogue does not list are appended. Entries whose `file` is not a plain `name.jackdaw` are dropped, so every `downloadUrl` points into `format/examples`. A sample is installed when `PackSummary.id` matches, and has an update when `SamplePack.revision > PackSummary.revision`.
| `authoringPrompt()` | `String` | The copy-paste chatbot prompt of `format/prompt.txt` (also embedded in `AUTHORING.md`): offer it under "Create with AI". Not suspending. |
| `refresh()` | | Re-emits all flows. Call it when the app returns to the foreground: "due" depends on the clock. |

The text passed to `previewImport`/`importPack` is the file decoded as UTF-8 (a BOM is fine). Decode errors are the UI's to report.

`PackSummary`: `id, title, descriptionText, icon (emoji or null), color ("#RRGGBB" or null), language, itemCount, facetCount, newCount, learningCount, reviewCount, masteredCount, dueCount, progress (0..1), lastStudiedAt, importedAt, updatedAt, revision (0 when the pack has none)`. `newCount + learningCount + reviewCount + masteredCount == facetCount`; `dueCount` overlaps them.

- **new**: never answered. **learning**: failed or rated Hard recently, comes back within minutes. **review**: graduated, waiting for its due date. **mastered**: review facet with an interval of at least 21 days.
- **due**: a learning facet due within the next 20 minutes, or a review facet whose due *day* is today or earlier (review facets become due at the start of their day, device time zone).
- **progress** = (mastered + 0.5 x (seen but not mastered)) / facets.

`PackDetail`: `summary`, `author, license, source, tags, revision`, `newPerSession`, `randomOrder`, `sections: [SectionSummary]` (declared order, then ungrouped; each with its own counts and `progress`), `typeCounts: [TypeCount(type, count)]`, and `practiceFilters: [PracticeFilter]`, the filters that probably have content (`ALL` is always there; use `previewSession` for the exact number).

`ItemSummary`: `id, position, type: ItemType, sectionId, title, subtitle` (plain text, one line, max 200 chars: card front/back, question/answer, cloze text with gaps shown as "…" / filled in, true/false shows ✓ or ✗), `status` (`NEW, LEARNING, REVIEW, MASTERED`; reversible cards: worst facet), `isDue`, `facets: [FacetInfo(facet, status, intervalDays, dueAt, reps, lapses)]`.

### Importing

```
previewImport(text) -> ImportPreview.Valid | ImportPreview.Invalid(error)
importPack(text)    -> ImportResult.Success | ImportResult.Failure(error)
```

`ImportResult.Success`: `packId, title, itemCount, skippedCount, isUpdate, counts (addedItems, keptItems, resetItems, removedItems), warnings`. A pack with warnings is still imported: show them. `ImportPreview.Valid` has the same information plus `description, icon, color, language, revision, sectionCount, typeCounts, installedTitle, installedRevision, isDowngrade`.

Errors and warnings carry a stable `code` for localisation and an English `message` as fallback.

| `ImportErrorCode` | Meaning |
|---|---|
| `NOT_JSON`, `NOT_AN_OBJECT`, `NOT_A_PACK` | Not a Jackdaw file. |
| `VERSION_INVALID`, `VERSION_TOO_NEW` | Format version unreadable / newer than supported: tell the user to update the app. |
| `ID_INVALID`, `TITLE_INVALID`, `ITEMS_MISSING`, `NO_VALID_ITEMS` | Required pack properties missing or no item survived validation. |
| `TOO_LARGE`, `TOO_MANY_ITEMS` | Over 25 MB / 50,000 items. |
| `REVISION_DOWNGRADE` | Installed revision is higher. Ask the user, then call `importPackAllowingDowngrade`. |
| `UNKNOWN_SAMPLE`, `STORAGE_ERROR` | `importSample` with an unknown id / the database write failed. |

| `ImportWarningCode` | Fields |
|---|---|
| `ITEM_SKIPPED`, `ITEM_DUPLICATE_ID` | `itemId` (may be null), `itemIndex` (0-based position in the file), `detail` (reason). |
| `ITEM_UNKNOWN_TYPE` | Aggregated per type: `detail` = the type name, `count` = how many items. |
| `ITEM_UNKNOWN_SECTION` | The item is shown ungrouped. `detail` = the unknown section id. |
| `PROPERTY_IGNORED`, `ENTRIES_DROPPED` | An optional property or some list entries were invalid. `detail` = property path, `count` for dropped entries. |
| `SECTION_DROPPED` | A malformed or duplicate section. |
| `MORE_WARNINGS` | Only the first 100 warnings are listed, `count` is the rest. |

Updates follow spec section 4.2: same pack id replaces metadata, sections and hints; items are matched by id; progress is kept (also when the text changed) unless the item's type changed (then it restarts); removed items drop out of the schedule, and their progress comes back if an item with that id and type is added again (the schedule is always rebuilt from the review log, see "Sync").

### Stats and settings

`observeStats()` / `observeStats(days)` (default 91, max 730) emit `Stats`: `currentStreak` (consecutive days with at least one answer, ending today, or yesterday if nothing was answered yet today), `longestStreak`, `reviewsToday`, `correctToday`, `totalReviews`, `accuracy` (0..1, first attempts, all time), `facetsLearned` (no longer new), `facetsMastered`, `dueToday` (all packs), `dailyGoal`, `goalReached`, `activity: [DayActivity(date, epochDay, reviews, correct)]` (one entry per day, oldest first, today last, zero days included: ready for a heatmap).

Every first answer in a session counts as a review (practice too). Re-asked items in the same session do not.

Settings: `observeSettings()`, `settings()`, `setDailyGoal(n)` (1..1000), `setDefaultSessionSize(n)` (1..500), `setTypoTolerance(bool)`, `setTheme(ThemePreference.SYSTEM/LIGHT/DARK)`, `setOnboardingSeen(bool)`. Defaults: goal 20, session size 20, typo tolerance on, SYSTEM, onboarding not seen. `AppSettings` has the five values.

**Typo tolerance** (read when a session starts). With it on, a typed answer (type exercise, text item, cloze gap) that does not match by the spec's normal form still counts as correct when it is a likely slip of an accepted answer: a letter replaced by a neighbouring key (QWERTY, QWERTZ or AZERTY), two letters swapped, a letter missing or doubled inside a word, a space or hyphen confused. One slip for answers of 4 to 8 characters, two from 9, none below 4. Never a typo: a key that is far away, a changed word ending (`gato`/`gatos`), any change to a digit, anything on a `caseSensitive` item, and an input that is itself an answer of another item in the pack (`pero`/`perro`). Such an answer is recorded as correct, and the feedback has `typo = true` so the UI can show the right spelling.

## Sync

`sdk.sync` (`JackdawSync`) is the part of iCloud sync that does not depend on CloudKit. Android does not use it and keeps no sync bookkeeping: tracking is off until `sync.enableTracking()` is called (the iOS app does that once at launch). The CloudKit layer in `iosApp/Jackdaw/SDK/` is the only caller.

What exists as a record (stable, device independent ids):

| Kind | Record name | Content | Merge rule |
|---|---|---|---|
| `pack` | `pack.<packId>` | the pack's source JSON, `importedAt`, `contentAt`, `resetAt`, `deletedAt` | content: later `contentAt` wins, ties by source text. `resetAt` and `deletedAt` only grow (max). Live when `source != null` and `contentAt > deletedAt`. Deleting a pack keeps a tombstone (delete and reset time, no source) instead of removing the record, so a stale device can never bring it back, and importing it again starts without the old progress. |
| `review` | `log.<random 128 bit id>` | one first answer: `ts`, `day`, pack, item, facet, exercise, grade, `correct`, `practice`, `reschedule`, `itemType` | immutable, union by id |
| `setting` | `setting.<name>` | `value`, `updatedAt` | later `updatedAt` wins, ties by value. Only `daily_goal`, `default_session_size` and `typo_tolerance` are synced; `theme` and `onboarding_seen` stay on the device. |

The schedule of a facet is never merged, it is recomputed: replay of the facet's `reschedule` log entries (oldest first, ties by id) that are newer than the pack's `resetAt` and were recorded for the item's current type, starting from a new facet. Local answers apply the same step in place, so both ways agree. Review ids came from the old autoincrement column; `review_log.id` is now a random hex string.

Calls (all `suspend`, called by the platform layer):

- `observeLocalChanges()` emits whenever a record became dirty; `pendingRecords()` lists the dirty record names (durable in the database, so nothing is lost when the app is killed before a send finishes).
- `packRecord / reviewRecord / settingRecord(name)` return the current content to upload, with a `version` (null when the record is not dirty). After a successful send call `markSent(name, version, systemFields)`: the record stays dirty if it changed since the snapshot. `systemFields` is an opaque string the platform keeps per record (CloudKit change tags), see `systemFields` / `setSystemFields`.
- `apply(packs, reviews, settings)` merges fetched records. Records that arrive this way are not marked dirty (no echo); `SyncApplyResult.needsUpload` names the records whose merged state differs from what was fetched, they are dirty and must be sent. `notes` are human readable warnings (for example a pack in a newer format).
- `queueEverything()` marks every record dirty and forgets the stored system fields (first sync, and after the server side data was deleted). Merging makes this safe for data that already exists remotely.
- `meta / setMeta` store small platform values (engine state, last sync time, last error, account id).

## Study sessions

```kotlin
suspend fun previewSession(packId: String, config: SessionConfig): SessionPreview   // dueCount, newCount, totalCount
suspend fun startSession(packId: String, config: SessionConfig): StudySession
```

`SessionConfig(mode, sectionId, size, newLimit, filter, rescheduleInPractice)`; shortcuts `SessionConfig(mode)`, `SessionConfig.learn()`, `SessionConfig.practice()` and the copies `withSection(id)`, `withSize(n)`, `withNewLimit(n)`, `withFilter(f)`, `withReschedule(b)`.

| Field | Meaning |
|---|---|
| `mode` | `LEARN`: due facets first (oldest due first), then new ones, in the pack's `study.order` (sequential or random), at most `newLimit`. Exercise kind follows maturity. `PRACTICE`: any items, shuffled. |
| `sectionId` | Restrict to a section (`""` = ungrouped), `null` = whole pack. |
| `size` | Maximum number of items. `0` = the default from settings. |
| `newLimit` | New items in `LEARN`. `-1` = the pack's `newPerSession`. |
| `filter` | `PRACTICE` only. `ALL`, `FLASHCARDS` (cards as flip, open questions), `QUIZ` (choice items, true/false, order, cards as multiple choice), `TYPING` (text, cloze, cards typed). Respects the card's allowed exercise kinds. |
| `rescheduleInPractice` | `false` (default): practice answers go to the activity log (streak, stats, `lastStudiedAt`) but do not change the schedule. `true`: they count like in `LEARN`. |

An empty plan (nothing due, no new items, no match) starts in `Finished` with `summary.total == 0`. Check `previewSession` first to disable the button.

Exercise kind choice (cards only; every other type has exactly one kind): new facet = flip, learning or interval under 7 days = multiple choice, mature = typing, always limited to the kinds the card allows and that are feasible (choice needs 3 options, spec 7.2), with flip as the fallback. An item appears at most once per session (plus retries of wrong answers); the reverse facet of a card is introduced after its forward facet was learned.

### StudySession

```kotlin
val state: StateFlow<SessionState>
fun revealHint()                    // Question: show item.hint
fun reveal()                        // Question of a FLIP/OPEN exercise -> Feedback (needsSelfGrade)
suspend fun submit(answer: Answer)  // Question of an auto-graded exercise -> Feedback
suspend fun selfGrade(grade: Grade) // Feedback (needsSelfGrade) -> next Question / Finished
fun next()                          // Feedback (auto-graded) -> next Question / Finished
fun finish()                        // any state -> Finished(abandoned = true)
val packId: String, val mode: SessionMode
```

| State | Calls that do something | Next state |
|---|---|---|
| `Question` | `revealHint()` | same `Question`, `hint` filled in |
| `Question` (flip, open) | `reveal()` | `Feedback`, `needsSelfGrade = true` |
| `Question` (all other kinds) | `submit(answer)` | `Feedback`, `verdict` CORRECT or WRONG |
| `Feedback`, `needsSelfGrade` | `selfGrade(grade)` | next `Question`, or `Finished` |
| `Feedback`, auto-graded | `next()` | next `Question`, or `Finished` |
| any, not yet `Finished` | `finish()` | `Finished`, `summary.abandoned = true` |

**Walkthrough.** `state` starts at the first `Question` (or `Finished` if nothing to do).

1. `Question(exercise, position, total, isRetry, hasHint, hint, figure)`: render `exercise`, with `figure` above it when it is not null. `position` is 1-based progress over `total` distinct items (`completed + 1`), so a progress bar is `(position - 1) / total`. `hasHint` tells whether to offer a hint button; `hint` is `null` until `revealHint()`.
2. Auto-graded kinds (choice, type, cloze, true/false, order): call `submit(answer)`. Self-graded kinds (flip, open): call `reveal()`.
3. `Feedback(...)`: show the result. Auto-graded: `verdict` is `CORRECT` or `WRONG`, `needsSelfGrade` is false, show a "Continue" button that calls `next()`. Self-graded: `verdict` is `PENDING_SELF_GRADE` and `needsSelfGrade` is true: show the answer (and for open questions the key point checklist), then call `selfGrade(grade)`. There is no further Feedback after `selfGrade`; it moves straight to the next question.
4. Wrong answers (`WRONG`, or a self-grade of `AGAIN`) are re-asked 3 items later (immediately if it was the last one), with `isRetry = true`, until answered right once. Only the **first** attempt is recorded (review log and schedule). `willRepeat` on the feedback tells the UI.
5. `Finished(summary)`: `SessionSummary(mode, total, answered, correct, wrong, retries, newFacets, accuracy, durationMs, abandoned)`. `correct`/`wrong`/`accuracy` count first attempts, `newFacets` the facets that were new before this session (not counted in non-rescheduling practice), `retries` the repeated attempts.

Every first answer is written to the database before `submit`/`selfGrade` returns, so abandoning a session (app killed, `finish()`) loses nothing. Calls made in the wrong state, with an answer of the wrong kind, or while another call is in flight are ignored.

### Exercises and answers

| `Exercise` | Show | Input to send | Grading |
|---|---|---|---|
| `FlipExercise(prompt)` | prompt, then "reveal" | `reveal()`, then `selfGrade(AGAIN/HARD/GOOD/EASY)` | self |
| `ChoiceExercise(question, options, multiSelect)` | options as radio buttons / checkboxes (`multiSelect`: "select all that apply") | `submit(ChoiceAnswer(selectedIds))` (option ids) | the selected set must equal the correct set |
| `TypeExercise(prompt)` | text field | `submit(TextAnswer(text))` | spec section 9 normal form; empty is wrong |
| `ClozeExercise(segments, gapCount)` | `ClozeSegment.Text(text: RichText)` and `ClozeSegment.Gap(index, hint)` inline, all gaps at once | `submit(ClozeAnswer(gaps))` (`gaps[i]` answers gap `index == i`) | every gap must match (`hint` is plain text shown with the gap) |
| `TrueFalseExercise(statement)` | two buttons | `submit(TrueFalseAnswer(value))` | |
| `OrderExercise(prompt, entries)` | entries (already shuffled, never in the right order), user arranges | `submit(OrderAnswer(entryIds))` (all entry ids in the arranged order) | position by position |
| `OpenExercise(question)` | question, "reveal" | `reveal()`, then `selfGrade(grade)` | self (use `feedback.suggestedGrade(tickedKeyPoints)` as the default) |

Every exercise has `itemId`, `facet` and `type: ExerciseType`.

**Figures** (spec 8.4). `Question.figure` is the item's `figure`; `Feedback.figure` is its `answerFigure`, or else its `figure`. A `Figure` has passed the import rules and carries `svg`, `alt` and `aspectRatio` (viewBox width / height). Draw it with `figure.html(textColor)` in a web view: the document scales the SVG to the view, has a transparent background and a content security policy that blocks scripts and every network load. `textColor` is a CSS colour for `currentColor` (`"#1B1A22"`), or `null` to follow the web view's light or dark scheme. The web view itself must also have JavaScript and network access off and take no touches (`FigureView.kt`, `FigureView.swift`). A figure that breaks a rule is dropped at import with a `PROPERTY_IGNORED` warning and the item is kept.

Auto-built multiple choice for cards (spec 7.2) arrives as a normal `ChoiceExercise` with one correct option.

`Feedback` fields: `exercise`, `position`, `total`, `verdict`, `correct` (verdict == CORRECT), `needsSelfGrade`, `willRepeat`, `correctAnswer: RichText` (flip: the other side; choice: the correct options, one per line; type: canonical answer; cloze: the full text with answers in bold; order: the sequence, one per line; open: the model answer; true/false: empty, use `statementIsTrue`), `typo` (correct only thanks to typo tolerance: show `correctAnswer` as the right spelling), `userAnswer: String`, `explanation: RichText?` (the item's explanation), and per-kind detail (empty lists otherwise): `choiceResults` (option `id`, `text`, `correct`, `selected`, `explanation`: set for options that are selected or correct, as spec 7.3 suggests), `figure: Figure?` (the item's `answerFigure`, else its `figure`), `gapResults` (`index, given, canonical, correct, typo`), `orderResults` (`position` (1-based), `arranged`, `expected`, `correct`), `keyPoints` (open), `statementIsTrue`.

`Grade`: `AGAIN, HARD, GOOD, EASY`. Auto-graded answers map to `GOOD` (right) and `AGAIN` (wrong). Hard counts as right for requeuing.

### Scheduler

Pure and deterministic (`study/Scheduler.kt`), SM-2 family: facets go `NEW -> LEARNING -> REVIEW`. `AGAIN`/`HARD` on a new facet: learning, due in 10 minutes. `GOOD` graduates to a 1 day interval (`EASY`: 4 days). Review: `HARD` x1.2 (ease -150), `GOOD` x ease (start 2.5), `EASY` x ease x1.3 (ease +150), intervals grow by at least a day, overdue days earn partial credit, interval cap 100 years, ease floor 1.3. A lapse (`AGAIN` on a review) goes back to `LEARNING` (ease -200, interval x0.25, min 1 day). The clock is injected, never read inside the scheduler.

## Threading

- Everything that touches the database is a `suspend` function (runs on `Dispatchers.Default` internally) or a `Flow` (computed on `Dispatchers.Default`). Call suspend functions from any thread; results arrive on the caller's context.
- Non-suspending members never touch the disk. `StudySession.reveal/revealHint/next/finish`, `refresh()` and `samplePacks()`/`authoringPrompt()` are cheap and safe on the main thread. The first `samplePacks()` call parses the bundled JSON files (a few ms).
- `StudySession.state` is a `StateFlow`: collect on the main thread. A session is meant to be driven by one UI at a time; overlapping calls are ignored instead of queued.
- Flows are cold: each collector recomputes on every change. Cancel collection when the screen goes away.

## Using it from Compose

```kotlin
val packs by sdk.observePacks().collectAsStateWithLifecycle(emptyList())
val session = remember { mutableStateOf<StudySession?>(null) }
LaunchedEffect(packId) { session.value = sdk.startSession(packId, SessionConfig.learn()) }
val state by session.value?.state?.collectAsState() ?: return
when (val s = state) {
    is SessionState.Question -> ...
    is SessionState.Feedback -> ...
    is SessionState.Finished -> ...
}
```

## Using it from SwiftUI (SKIE)

The framework module is `shared`. With Swift 6 language mode add `@preconcurrency import shared`: Kotlin classes are not `Sendable`, and `async` calls on them from `@MainActor` code otherwise produce data-race diagnostics.

```swift
@preconcurrency import shared

let sdk = JackdawSdk(driverFactory: DatabaseDriverFactory())   // keep one instance for the app's lifetime

// Flow -> AsyncSequence
.task {
    for await packs in sdk.observePacks() { self.packs = packs }   // [PackSummary]
}

// suspend -> async throws
let result = try await sdk.importPack(text: text)
switch onEnum(of: result) {
case .success(let ok):  show(ok.title, ok.warnings)               // ok.itemCount, ok.counts.addedItems ...
case .failure(let bad): show(bad.error.code, bad.error.message)   // ImportErrorCode enum
}

// enums are Swift enums: PracticeFilter, Grade, ItemType, ThemePreference ...
let config = SessionConfig(mode: .practice).withFilter(filter: .quiz).withSize(size: 15)
let session = try await sdk.startSession(packId: id, config: config)

// StateFlow -> value + AsyncSequence
for await state in session.state {
    switch onEnum(of: state) {
    case .question(let q):
        switch onEnum(of: q.exercise) {          // sealed class Exercise
        case .flipExercise(let e):   ...         // e.prompt.spans, q.hint, q.position / q.total
        case .choiceExercise(let e): ...         // e.options, e.multiSelect
        case .typeExercise: ... case .clozeExercise: ... case .trueFalseExercise: ...
        case .orderExercise: ... case .openExercise: ...
        }
    case .feedback(let f):  ...                  // f.verdict, f.correctAnswer, f.needsSelfGrade
    case .finished(let done): ...                // done.summary
    }
}

// user actions
session.reveal()
try await session.submit(answer: ChoiceAnswer(selectedIds: ["1"]))
try await session.selfGrade(grade: .good)
session.next()
```

Notes for Swift: `List<T>` arrives as `[T]` (no public API uses `List<Int>`/`List<Boolean>`, so there is no `KotlinInt` boxing; ids are `[String]`); `Long` is `Int64`, `Int` is `Int32`; nullable properties are optionals; sealed class case names derive from the subclass name (check Xcode's completion for the exact spelling, e.g. `.flipExercise`); a Kotlin property called `type` is `type`; an enum inside a `List` (`PackDetail.practiceFilters`) may arrive as the Kotlin class `__PracticeFilter` instead of the Swift enum, because SKIE does not swap enums in generics (`toSwiftEnum()` converts it; `Mapping` matches it by name). Rich text is an array of spans:

```swift
func attributed(_ text: RichText) -> AttributedString {
    text.spans.reduce(into: AttributedString()) { result, span in
        var part = AttributedString(span.text)
        if span.bold { part.inlinePresentationIntent = .stronglyEmphasized }
        if span.italic { part.inlinePresentationIntent = .emphasized }
        if span.code { part.inlinePresentationIntent = .code }
        result.append(part)
    }
}
```

## Development

```
source ~/.sdkman/bin/sdkman-init.sh && export ANDROID_HOME=~/android-sdk
./gradlew ktlintCheck shared:allTests androidApp:assembleDebug androidApp:lintDebug androidApp:testDebugUnitTest
./gradlew shared:compileKotlinIosSimulatorArm64 shared:compileTestKotlinIosSimulatorArm64   # type-checks common + iOS code on Linux
```

- `commonTest` has the pure logic tests (reader, markup, normalisation, scheduler, planner, session engine, every vector of `format/test-vectors.json`) and the sync tests (two simulated devices on in-memory databases, `newTestDriver()` is an expect/actual), and runs on the JVM and on iOS. `androidUnitTest` has the database tests (in-memory JDBC SQLite, fake clock and time zone, seeded `Random`).
- The sample packs and the test vectors are not copied anywhere: the `generateFormatResources` Gradle task turns `format/examples/*.jackdaw`, `format/prompt.txt` and `format/test-vectors.json` into Kotlin string constants under `shared/build/generated/formatResources` (compiled for every target, ignored by ktlint).
- The database schema is version 1. `src/commonMain/sqldelight/databases/1.db` is the schema snapshot used by `verifyCommonMainJackdawDatabaseMigration`. Until the first release, regenerate it after editing a `.sq` table (`./gradlew shared:generateCommonMainJackdawDatabaseSchema`); afterwards add `1.sqm` migrations instead.
- Unicode NFC/NFD (needed for `ignoreDiacritics` and answer comparison) is an expect/actual: `java.text.Normalizer` on Android, `NSString` canonical mapping on iOS.
- Test seams (all `internal`): `JackdawSdk(driver, clock, timeZone, random, dispatcher)` takes a SQLDelight driver, `kotlin.time.Clock`, a time zone provider, a `kotlin.random.Random` and the coroutine dispatcher for the database work.
