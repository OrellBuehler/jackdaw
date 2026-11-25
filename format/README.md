# Jackdaw Pack Format

**Format version 3** · specification revision 3.0 (2026-11-25)

A **Jackdaw pack** is a single UTF-8 JSON file that describes a learning dataset: foreign-language vocabulary, history facts, exam questions, interview preparation, anything you can ask a question about. The Jackdaw app imports packs, schedules the learning (spaced repetition) and keeps the learning progress. The file format is open and documented so that people, scripts and AI assistants can create packs without the app.

| | |
|---|---|
| File extension | `.jackdaw` (also accepted: `.jackdaw.json`, `.json`) |
| Media type | `application/vnd.jackdaw.pack+json` |
| Encoding | UTF-8, JSON ([RFC 8259](https://www.rfc-editor.org/rfc/rfc8259)) |
| Schema | [`jackdaw-pack.schema.json`](jackdaw-pack.schema.json) (JSON Schema 2020-12) |
| Writing guide for AIs and tool authors | [`AUTHORING.md`](AUTHORING.md) |
| Examples | [`examples/`](examples/) |
| Validator | [`validate.py`](validate.py) |
| Conformance vectors | [`test-vectors.json`](test-vectors.json) |

## Contents

1. [Overview](#1-overview)
2. [Minimal example](#2-minimal-example)
3. [Conventions](#3-conventions)
4. [The pack object](#4-the-pack-object)
5. [Sections](#5-sections)
6. [Study hints](#6-study-hints)
7. [Items](#7-items)
8. [Text: plain text, inline markup and cloze gaps](#8-text-plain-text-inline-markup-and-cloze-gaps)
9. [Answer matching](#9-answer-matching)
10. [Compatibility and extensibility](#10-compatibility-and-extensibility)
11. [What a reader does with invalid input](#11-what-a-reader-does-with-invalid-input)
12. [Limits and size guidance](#12-limits-and-size-guidance)
13. [Validation](#13-validation)
14. [Versioning policy](#14-versioning-policy)
15. [Security considerations](#15-security-considerations)
16. [Complete example](#16-complete-example)
17. [FAQ: choosing item types](#17-faq-choosing-item-types)

---

## 1. Overview

A pack has some metadata, an optional list of **sections** (chapters) and a list of **items**. Every item has a `type`, and the type decides how the item is learned:

| Type | What it is | How it is learned |
|---|---|---|
| [`card`](#72-card) | Two-sided flashcard (`front`, `back`) | Flip and self-grade, app-built multiple choice, or typing the back. Optionally also back to front. |
| [`choice`](#73-choice) | Explicit multiple-choice question | Pick the correct option (or options, if several are correct). |
| [`text`](#74-text) | Question with a short typed answer | Type the answer; it is compared with the accepted answers. |
| [`cloze`](#75-cloze) | Sentence with gaps `{{answer}}` | Fill in the gaps. |
| [`truefalse`](#76-truefalse) | A statement | Decide whether it is true or false. |
| [`order`](#77-order) | Entries given in the correct order | Arrange the shuffled entries. |
| [`open`](#78-open) | Open question with model answer and key points | Answer in your head, reveal, tick off the key points you covered. |

Design rules that explain many details below:

- **Easy for humans and language models to write.** No indices (multiple-choice options carry their own `correct` flag), no cross-references except section ids, flat objects, plain-text answers.
- **Strict writers, forgiving readers.** The JSON Schema is strict so that authors catch mistakes. A reader (the app) skips what it cannot use and imports the rest, and tells the user what it skipped ([section 11](#11-what-a-reader-does-with-invalid-input)).
- **Content only.** Learning progress and spaced-repetition scheduling are **not** part of a pack. They belong to the app and are keyed by pack `id` and item `id`.
- **Forward compatible.** Unknown properties are ignored, unknown item types are skipped, and `x-` properties are free for extensions ([section 10](#10-compatibility-and-extensibility)).

## 2. Minimal example

The smallest useful pack: required properties only.

```json pack
{
  "format": "jackdaw-pack",
  "version": 1,
  "id": "com.example.european-capitals",
  "title": "European Capitals",
  "items": [
    { "id": "france", "type": "card", "front": "France", "back": "Paris" },
    { "id": "spain", "type": "card", "front": "Spain", "back": "Madrid" }
  ]
}
```

A [complete example](#16-complete-example) that uses every property except maps ([8.5](#85-maps), which has its own examples) is at the end of this document.

## 3. Conventions

- The key words **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT** and **MAY** are used as in [RFC 2119](https://www.rfc-editor.org/rfc/rfc2119).
- A **writer** is anything that produces a pack (a person, a script, an AI assistant, an exporter). A **reader** is anything that consumes one (the Jackdaw app, a converter, a validator). Requirements on writers are what the JSON Schema checks. Requirements on readers say how to behave when a file does not meet them.
- Property tables list the JSON type, whether the property is required (**yes**/no) and its default. Types: *string*, *integer*, *boolean*, *array*, *object*. Two kinds of strings exist: **plain text** (taken literally) and **rich text** (a few inline markup characters are interpreted, see [section 8](#8-text-plain-text-inline-markup-and-cloze-gaps)). Every table says which one a property is.
- Property names are case-sensitive and use lowerCamelCase. The order of properties inside an object is not significant. Within arrays, order is significant.
- "Unicode whitespace" means the characters listed in [section 9.1](#91-whitespace).

---

## 4. The pack object

The top level of the file is one JSON object.

| Property | Type | Required | Default | Description |
|---|---|---|---|---|
| `$schema` | string | no | | URL of the JSON Schema. Lets editors validate and complete. Ignored by readers. |
| `format` | string | **yes** | | Constant `"jackdaw-pack"`. |
| `version` | integer | **yes** | | Format version: the lowest one that has everything the pack uses ([section 14](#14-versioning-policy)). `1`; `2` for a pack with figures ([8.4](#84-figures)); `3` for a pack with maps ([8.5](#85-maps)). |
| `id` | string | **yes** | | Stable, globally unique identifier of the pack ([4.1](#41-identifiers)). |
| `title` | string | **yes** | | Name of the pack. Plain text, one line, 1 to 200 characters. |
| `description` | string | no | | What the pack covers. Plain text, newlines allowed, at most 2000 characters. |
| `language` | string | no | | [BCP 47](https://www.rfc-editor.org/info/bcp47) tag of the subject matter, e.g. `"es"`, `"en"`, `"de-CH"`. For a language-learning pack this is the language being learned. |
| `author` | string | no | | Author or organisation. Plain text. |
| `license` | string | no | | License of the content. An [SPDX identifier](https://spdx.org/licenses/) such as `"CC-BY-4.0"` is recommended. |
| `source` | string | no | | `http(s)` URL of the origin of the material. Readers MUST NOT fetch it automatically. |
| `tags` | array of plain text | no | | Free-form labels for search and filtering (short, usually lowercase). At most 50. |
| `icon` | string | no | | A single emoji. |
| `color` | string | no | | Accent color as `#RRGGBB`, e.g. `"#C2410C"`. |
| `revision` | integer | no | | Content revision, at least 1. Increase it by one with every update of the same pack ([4.2](#42-updating-a-pack)). |
| `created` | string | no | | ISO 8601 date (`"2026-11-20"`) or date-time with zone (`"2026-11-20T14:30:00Z"`). |
| `updated` | string | no | | Same format as `created`. |
| `sections` | array | no | none | Ordered groups of items ([section 5](#5-sections)). |
| `study` | object | no | `{}` | Hints about how the pack should be learned ([section 6](#6-study-hints)). |
| `items` | array | **yes** | | The learning items ([section 7](#7-items)). At least one. |

Notes:

- `language` is a hint about the *content*, not about the user interface. The language in which prompts and explanations are written is not declared in version 1.
- `icon` and `color` are cosmetic. A reader that cannot make sense of them ignores them.
- `revision` is the revision of the *content*. It is unrelated to `version`, the version of the *format*.

### 4.1 Identifiers

`id` (pack), and `id` of sections and items, share one syntax:

- Characters: `A-Z a-z 0-9 . _ -` only (ASCII), first character a letter or digit.
- Length: pack ids 1 to 128 characters, section and item ids 1 to 64.
- Comparison: exact, case-sensitive, no normalisation.

Pack ids SHOULD be globally unique. Use a reverse-domain name you control (`com.example.spanish-basics`) or a random UUID (`3f2b8c1e-9a4d-4c55-8f0e-5d6a7b8c9d01`). Item and section ids only need to be unique **within the pack**; different packs may use the same item ids. Lowercase slugs that describe the content (`capital-france`, `ww1-armistice-year`) are recommended for item ids.

Item ids MUST be stable ([4.2](#42-updating-a-pack)): they are the key under which the app stores learning progress.

### 4.2 Updating a pack

Importing a pack whose `id` is already installed **updates** that pack instead of adding a second copy:

1. The installed pack's metadata, sections and study hints are replaced by those of the new file.
2. Items are matched by item `id`. For every item whose `id` exists in both versions, learning progress is **kept**, even if the content changed. If the `type` of such an item changed, the reader SHOULD reset that item's progress, because the old progress belongs to a different kind of exercise.
3. Items that are new are added as new. Items that disappeared are removed from the pack. A reader MAY keep the progress of removed items, so that it is restored if an item with that id comes back.
4. If both the installed pack and the new file carry a `revision` and the new one is lower, the reader SHOULD ask the user before replacing (it looks like a downgrade). With an equal or higher revision, or if either side has none, the update proceeds.

Rules for writers:

- Never change a pack's `id`. A new `id` is a different pack with fresh progress.
- Keep item ids unchanged when you fix typos or improve wording.
- Give an item a **new** id when its meaning changes so much that old progress would be misleading (for example the question now asks for a different fact).
- Never reuse the id of a deleted item for different content.
- Increase `revision` by one and update `updated`.

---

## 5. Sections

`sections` is an optional ordered array of objects that group items into chapters or topics.

| Property | Type | Required | Description |
|---|---|---|---|
| `id` | string | **yes** | Section id ([4.1](#41-identifiers)), unique among the sections. |
| `title` | string | **yes** | Display name. Plain text, one line, 1 to 200 characters. |
| `description` | string | no | One or two sentences. Plain text, at most 1000 characters. |

An item belongs to a section by naming the section's `id` in its `section` property.

- The array order is the display order of the sections.
- Sections only group items. They do **not** change the order in which new items are introduced (that is the order of the `items` array, see [6](#6-study-hints)). Writers SHOULD keep the items of a section together and in the same order as `sections`.
- Items without `section`, or with a `section` that does not exist, are shown in an implicit group "ungrouped" after the declared sections.
- Writers SHOULD either assign every item to a section or declare no sections at all.

```json
"sections": [
  { "id": "greetings", "title": "Greetings", "description": "Words for meeting and parting." },
  { "id": "numbers", "title": "Numbers 1-10" }
]
```

---

## 6. Study hints

`study` is an optional object with the author's recommendations for learning the pack. They are defaults: the app and the user may override them. All properties are optional.

| Property | Type | Default | Description |
|---|---|---|---|
| `order` | string | `"sequential"` | Order in which **new** items are introduced. `"sequential"`: the order of the `items` array. `"random"`: the app picks at random. The order in which already-learned items are reviewed is decided by the scheduler. |
| `newPerSession` | integer 1 to 500 | `10` | Suggested number of new items per study session. |
| `reversible` | boolean | `false` | Default for the `reversible` property of `card` items. |
| `ignoreDiacritics` | boolean | `false` | When `true`, typed answers are compared with combining diacritical marks removed ([9.2](#92-normal-form)). Applies to all typed answers (card, text, cloze). |
| `exercises` | object | `{}` | Allowed exercise kinds per item type. In version 1 only the key `card` is meaningful: an array with a non-empty subset of `"flip"`, `"choice"`, `"type"`. Other keys are reserved and ignored. |

```json
"study": {
  "order": "sequential",
  "newPerSession": 8,
  "reversible": true,
  "ignoreDiacritics": true,
  "exercises": { "card": ["flip", "choice", "type"] }
}
```

How the hints combine with the item's own properties (explained in detail for cards in [7.2](#72-card)):

- **Reversibility of a card**: the card's `reversible`, else `study.reversible`, else `false`.
- **Exercise kinds of a card**: the card's `exercises`, else `study.exercises.card`, else all three (`flip`, `choice`, `type`).
- Only the `card` type has several exercise kinds. All other types have exactly one way of being exercised.

The scheduling algorithm (intervals, ease, lapses) is deliberately not configurable from a pack.

---

## 7. Items

`items` is an array of objects. Each item has the **common properties** below plus the properties of its `type`.

### 7.1 Common properties

| Property | Type | Required | Description |
|---|---|---|---|
| `id` | string | **yes** | Item id ([4.1](#41-identifiers)), unique within the pack and stable across updates. |
| `type` | string | **yes** | One of `card`, `choice`, `text`, `cloze`, `truefalse`, `order`, `open`. |
| `section` | string | no | `id` of a declared section. |
| `tags` | array of plain text | no | Free-form labels, at most 50. |
| `hint` | rich text | no | Help shown **on request, before answering**. A nudge (context, first letter, mnemonic), not the answer. |
| `explanation` | rich text | no | Shown **after answering**: why the answer is right, background, an example. |
| `figure` | figure | no | A drawing shown **with the question** ([8.4](#84-figures)). *Version 2.* |
| `answerFigure` | figure | no | A drawing shown **after answering**, in place of `figure` ([8.4](#84-figures)). *Version 2.* |
| `map` | map | no | Places on a world map shown **with the question** ([8.5](#85-maps)). Not together with `figure`. *Version 3.* |
| `answerMap` | map | no | Places on a world map shown **after answering**, in place of `map` or `figure` ([8.5](#85-maps)). Not together with `answerFigure`. *Version 3.* |

Items of an unknown `type` are skipped by readers ([section 10](#10-compatibility-and-extensibility)).

### 7.2 `card`

A two-sided flashcard. Use it for vocabulary, definitions, terms and any "this belongs to that" pair.

| Property | Type | Required | Default | Description |
|---|---|---|---|---|
| `front` | rich text | **yes** | | The prompt side. |
| `back` | rich text | **yes** | | The answer side. This is what the user types in the `type` exercise, so keep it short and typeable. |
| `reversible` | boolean | no | `study.reversible`, else `false` | Also ask back to front. |
| `alternatives` | array of plain text | no | none | Additional accepted typed answers for the back (synonyms, spelling variants, the answer without article). `back` itself is always accepted. |
| `frontAlternatives` | array of plain text | no | none | Additional accepted typed answers for the **front**, used in the reverse direction. `front` itself is always accepted. |
| `distractors` | array of rich text | no | none | Explicit **wrong** options for the `choice` exercise. Three is a good number. |
| `frontDistractors` | array of rich text | no | none | Explicit wrong options for `choice` in the reverse direction. |
| `exercises` | array | no | `study.exercises.card`, else all | Allowed exercise kinds: a non-empty subset of `"flip"`, `"choice"`, `"type"`. |
| `caseSensitive` | boolean | no | `false` | Typed answers must match upper and lower case exactly. |

**How it is learned.** The three exercise kinds:

- `flip`: the prompt is shown, the user thinks, reveals the other side and grades themselves (right or wrong; the grading scale is up to the app).
- `choice`: the app builds a multiple-choice question. The correct option is the answer side; the wrong options come from `distractors` (or `frontDistractors`) and are topped up automatically, see below.
- `type`: the user types the answer, which is compared as described in [section 9](#9-answer-matching).

**Directions.** The *forward* direction shows `front` and asks for `back`. When the card is reversible, the *reverse* direction shows `back` and asks for `front`; there the accepted typed answers are `front` plus `frontAlternatives`, and wrong choice options come from `frontDistractors`. The app tracks the two directions as separate learning units, both stored under the card's item `id`. A `hint` is shown in both directions, so write direction-neutral hints for reversible cards.

**Typed answers.** In the forward direction the accepted answers are the plain-text rendering of `back` ([8.2](#82-inline-markup)) and every entry of `alternatives`. A `back` such as `**hola**` therefore accepts `hola`.

**Choosing the exercise.** The set of exercise kinds is *allowed*, not a sequence: the app picks a kind for each review (typically easier kinds for new cards, harder kinds for well-known ones) and the order of the array has no meaning. The app MUST only use allowed kinds, except for the fallback below.

**Automatic distractors.** For a `choice` exercise the app assembles the wrong options as follows:

1. Start with the explicit `distractors` (or `frontDistractors`). If there are more than needed, pick randomly.
2. Fill up with the answer sides of other `card` items (backs in the forward direction, fronts in the reverse direction): first from the **same section**, then from the rest of the pack. Candidates that equal the correct answer or one of its accepted alternatives after normalisation ([9.2](#92-normal-form)), and duplicates, are excluded.
3. The number of options is up to the app (**recommended: 4**). If fewer than 3 distinct options (1 correct + 2 wrong) can be assembled, `choice` is **not feasible** for this card and direction.

**Fallback.** If none of the allowed kinds is feasible, the app falls back to `flip`, which is always feasible.

```json item
{
  "id": "el-agua",
  "type": "card",
  "front": "el agua",
  "back": "water",
  "alternatives": ["the water"],
  "frontAlternatives": ["agua"],
  "reversible": true,
  "explanation": "*Agua* is feminine but takes *el* because it starts with a stressed *a*."
}
```

### 7.3 `choice`

An explicit multiple-choice question. Options carry their own `correct` flag. There are no indices, because indices are easy to get wrong.

| Property | Type | Required | Default | Description |
|---|---|---|---|---|
| `question` | rich text | **yes** | | The question. |
| `options` | array of option objects | **yes** | | 2 to 10 options, at least one with `"correct": true`. |
| `shuffle` | boolean | no | `true` | Show the options in random order. When `false`, the array order is kept. |

An **option** object:

| Property | Type | Required | Default | Description |
|---|---|---|---|---|
| `text` | rich text | **yes** | | The option text. |
| `correct` | boolean | no | `false` | `true` if this option is a correct answer. Must be a JSON boolean. |
| `explanation` | rich text | no | | Why this option is right or wrong. |

**How it is learned.**

- Exactly one correct option: **single select**.
- Two or more correct options: **multi select**. The app SHOULD tell the user that several options may be correct (for example "select all that apply"). The answer is correct only if the selected options are exactly the correct options; there is no partial credit in the grade, but the app may show which individual choices were right.
- After answering, the app SHOULD show the `explanation` of every selected option and of every correct option, besides the item's `explanation`.
- Use `"shuffle": false` only when the order carries meaning (numbers in ascending order, "both of the above"). Writers SHOULD avoid options such as "all of the above".
- Recommended: 3 to 5 options; at least one wrong option.

```json item
{
  "id": "http-429",
  "type": "choice",
  "question": "Which HTTP status code means \"Too Many Requests\"?",
  "options": [
    { "text": "401", "explanation": "Unauthorized: credentials are missing or invalid." },
    { "text": "403", "explanation": "Forbidden: the client is known but not allowed." },
    { "text": "429", "correct": true, "explanation": "The client sent too many requests in a given time." },
    { "text": "503", "explanation": "Service Unavailable." }
  ]
}
```

### 7.4 `text`

A question with a short typed answer: a name, a number, a term.

| Property | Type | Required | Default | Description |
|---|---|---|---|---|
| `question` | rich text | **yes** | | The question. It must have a short, unambiguous answer. |
| `answers` | array of plain text | **yes** | | Accepted answers, at least one. The first one is the canonical answer shown after answering. |
| `caseSensitive` | boolean | no | `false` | Typed answers must match upper and lower case exactly. |

**How it is learned.** The user types the answer. It is correct if, after normalisation ([9.2](#92-normal-form)), it equals one of `answers`. Everything that should count as correct but differs after normalisation must be listed explicitly: spelling variants (`"colour"`, `"color"`), abbreviations (`"UN"`, `"United Nations"`), written-out numbers (`"1969"`, `"nineteen sixty-nine"`).

```json item
{
  "id": "mussolini",
  "type": "text",
  "question": "Which Italian dictator came to power in 1922 after the March on Rome?",
  "answers": ["Mussolini", "Benito Mussolini"],
  "hint": "His title was *Il Duce*."
}
```

### 7.5 `cloze`

A sentence or short passage with gaps to fill in.

| Property | Type | Required | Default | Description |
|---|---|---|---|---|
| `text` | rich text with gaps | **yes** | | The text. Contains at least one gap written `{{answer}}`, see below. |
| `caseSensitive` | boolean | no | `false` | Typed gap answers must match upper and lower case exactly. |

**Gap syntax** (formal rules in [8.3](#83-cloze-gaps)):

| Written | Meaning |
|---|---|
| `{{Paris}}` | A gap whose answer is `Paris`. |
| `{{Paris::capital of France}}` | The same gap with a hint, shown with the gap. |
| `{{colour\|color}}` | Alternatives inside one gap are separated by `\|` (a vertical bar). The first one is canonical. |
| `{{colour\|color::British or US spelling}}` | Alternatives and a hint. |
| `\{\{` | A literal `{{` in the text, not a gap (escape with a backslash). |

**How it is learned.** The text is shown with the gaps as input fields (or blanks). **All gaps of an item are shown and answered together.** Each gap is compared separately with its alternatives ([section 9](#9-answer-matching)); the item is correct if **all** gaps are correct, and the app shows which gaps were wrong. After answering, each gap is replaced by its canonical (first) answer. A gap lies on one line. Keep to 1 to 3 gaps per item; for more, write several items. To learn a single gap per card, create one item per gap.

```json item
{
  "id": "versailles",
  "type": "cloze",
  "text": "The Treaty of {{Versailles::city near Paris}} was signed in {{1919::year}} and ended the war with Germany."
}
```

Remember that a backslash must be written twice inside JSON strings: the gap `{{a\|b}}` (one answer `a|b`) is written `"{{a\\|b}}"` in a pack file.

### 7.6 `truefalse`

| Property | Type | Required | Description |
|---|---|---|---|
| `statement` | rich text | **yes** | A self-contained statement that is clearly true or clearly false. |
| `answer` | boolean | **yes** | `true` if the statement is true. Must be a JSON boolean. |

**How it is learned.** The user chooses *True* or *False*. Add an `explanation`, especially for false statements: say what is actually true.

```json item
{
  "id": "gandhi-pm",
  "type": "truefalse",
  "statement": "Mahatma Gandhi became India's first prime minister in 1947.",
  "answer": false,
  "explanation": "Jawaharlal Nehru was India's first prime minister."
}
```

### 7.7 `order`

Put things in the right order: chronology, process steps, rankings.

| Property | Type | Required | Description |
|---|---|---|---|
| `prompt` | rich text | **yes** | What to order and by which criterion ("Put these events in chronological order, earliest first."). |
| `sequence` | array of rich text | **yes** | The entries in the **correct** order. 2 to 20 entries (3 to 7 recommended), all different. |

**How it is learned.** The app shows the entries shuffled. If the entries are all different it MUST make sure the shuffled order differs from the correct one. The user arranges them. The answer is correct if the arranged entries equal `sequence` position by position (comparing the text of the entries). Do not put the ordering criterion (years, ranks) into the entries if that gives the order away; mention it in `explanation`.

```json item
{
  "id": "interwar-order",
  "type": "order",
  "prompt": "Put these events in chronological order, earliest first.",
  "sequence": ["Treaty of Versailles is signed", "Wall Street Crash", "Hitler becomes German Chancellor"],
  "explanation": "1919, 1929, 1933."
}
```

### 7.8 `open`

An open question that the learner answers in their head or aloud and then grades themselves. Ideal for interview preparation, essay recall and "explain in your own words" questions.

| Property | Type | Required | Description |
|---|---|---|---|
| `question` | rich text | **yes** | The open question. |
| `answer` | rich text | no | A model answer, revealed after the learner has answered. May be several paragraphs. |
| `keyPoints` | array of rich text | no | A checklist of the points a good answer covers (3 to 7, one short sentence each). |

**How it is learned.** The app shows the question. The learner formulates an answer, then reveals the model `answer` and the `keyPoints`. The learner ticks off the key points they covered and rates themselves; the app may derive the self-grade from the share of ticked points. An `open` item should have at least one of `answer`, `keyPoints` or `explanation`, otherwise there is nothing to reveal.

```json item
{
  "id": "idempotency",
  "type": "open",
  "question": "What does it mean for an API operation to be idempotent, and why does it matter?",
  "answer": "Repeating the call has the same effect as calling it once. That makes retries after timeouts safe.",
  "keyPoints": [
    "Repeating the call has the same effect as one call",
    "Makes retries after network failures safe",
    "GET, PUT and DELETE are idempotent by definition; POST is not"
  ]
}
```

---

## 8. Text: plain text, inline markup and cloze gaps

### 8.1 Plain text and rich text

Every string in a pack is either **plain text** or **rich text**.

- **Plain text** is taken literally. No character has a special meaning, not even the backslash. Plain text properties: `title`, `description`, `author`, `license`, section `title` and `description`, `tags`, and the answer lists `alternatives`, `frontAlternatives` and `answers`.
- **Rich text** supports the tiny inline markup of [8.2](#82-inline-markup). All other string properties that are shown to the learner are rich text: `front`, `back`, `distractors`, `frontDistractors`, `question`, option `text`, `statement`, `prompt`, `sequence` entries, `answer`, `keyPoints`, `hint`, `explanation`, and the text of a `cloze` item outside of its gaps.

There is **no HTML**, no links, no images and no other Markdown (headings, lists, tables) in text. A reader MUST display characters like `<`, `&`, `[` and `#` as they are. Drawings are attached to an item as a figure ([8.4](#84-figures)) and places as a map ([8.5](#85-maps)), never embedded in text. Photos and audio are not part of the format; the property name `media` is reserved for them ([section 10](#10-compatibility-and-extensibility)).

A newline is the character U+000A, written `\n` in JSON. Readers normalise `\r\n` and `\r` to U+000A before interpreting rich text. Rich text may contain several lines; single-line properties (titles, ids, answers) SHOULD NOT contain newlines.

### 8.2 Inline markup

| Write | Shows |
|---|---|
| `**text**` | **bold** |
| `*text*` | *italic* |
| `` `text` `` | `code` (monospace, content shown literally) |
| a newline (`\n` in JSON) | a line break |

To show one of the marker characters literally, put a backslash in front of it: `\*`, `` \` ``, `\\`, `\{`, `\}`, `\|` and `\:` stand for the character itself (rule 1 below).

Rules (normative). A reader scans a rich text string from left to right. At each position it applies the **first** rule that matches; if none matches, the character is shown literally and scanning continues with the next character.

1. **Escape.** A backslash followed by one of the seven characters `\` `*` `` ` `` `{` `}` `|` `:` stands for that character, literally. A backslash before any other character is a literal backslash.
2. **Code.** A backtick, then one or more characters that are neither a backtick nor a newline, then a backtick. The content is shown in code style, with no escapes and no markup processed inside.
3. **Bold.** `**`, then content, then `**`. The content has no newline, does not start or end with whitespace and is as short as possible. Inside it only escapes (rule 1) are resolved; asterisks and backticks in it are literal.
4. **Italic.** `*`, then content, then `*`. The content contains no `*` and no newline and does not start or end with whitespace. Inside it only escapes are resolved.
5. Anything else is shown literally. In particular an unmatched `*`, `**` or backtick stays as it is, and so does a `*` that is surrounded by whitespace (`5 * 3 * 2`).

Consequences, deliberately simple: styles **do not nest** (`**bold and *italic***` is not supported; avoid `***`); a style never spans a line break; markup never crosses a cloze gap. `2*3*4` shows "234" with the 3 in italics, so write `2\*3\*4` when you mean the asterisks. Escape each marker character separately (`\*\*`, not `\**`).

The same rules as regular expressions, each anchored at the current position (`\s` is the whitespace set of [9.1](#91-whitespace), `\S` is every other character; build the character class by hand where the regex engine's own `\s` differs):

```
code    `([^`\n]+)`
bold    \*\*(\S(?:[^\n]*?\S)??)\*\*
italic  \*([^\s*](?:[^*\n]*[^\s*])?)\*
```

[`test-vectors.json`](test-vectors.json) contains the expected results for edge cases. A reader MUST reproduce them.

JSON note: inside a JSON string every backslash is written twice, so the escape `\*` is written `"\\*"` in a pack file. Writers that can avoid literal asterisks and backticks SHOULD do so.

### 8.3 Cloze gaps

A **gap** is `{{`, content, `}}` on a single line. The text of a `cloze` item is processed in two steps.

**Step 1: find the gaps.** Scan the text from left to right.

- A backslash followed by one of the seven escapable characters (rule 1 of [8.2](#82-inline-markup)) is an escape pair. It is skipped as a unit and stays in the surrounding text unchanged (the markup step resolves it later).
- At a `{{` look for the end: scan on, skipping escape pairs, until the first `}}`. If a newline is reached first, or the text ends, this `{{` is **not** a gap and is literal text.
- The characters between `{{` and `}}` are the **gap content**.

**Step 2: parse the gap content.**

1. Split at the **first** `::` that is not part of an escape pair: the left part is the **answer list**, the rest (which may itself contain `::`) is the **hint**.
2. Split the answer list at every `|` that is not part of an escape pair. These are the **alternatives**; the first is the canonical answer.
3. Resolve escapes in every alternative and in the hint (`\|` becomes `|`, `\:` becomes `:`, `\}` becomes `}`, `\\` becomes `\`, and so on) and strip surrounding whitespace.
4. Drop empty alternatives. If **no** alternative is left (`{{}}`, `{{ }}`, `{{::hint}}`), the gap is invalid: the characters are literal text and not a gap.

Gap answers and hints are plain text: no markup is interpreted inside a gap. The text between gaps is rich text, interpreted piece by piece (markup cannot span a gap). A cloze item without any valid gap is invalid.

Examples, as they appear in the decoded text (more in [`test-vectors.json`](test-vectors.json)):

- `{{Paris}}` and `{{ Paris }}`: one answer, `Paris`.
- `{{colour|color::British or US}}`: answers `colour` and `color`, hint `British or US`.
- `{{a\|b}}`: one answer, `a|b` (the escaped bar belongs to the answer).
- `{{a::b::c}}`: answer `a`, hint `b::c` (only the first `::` separates).
- `{{|a|}}`: one answer, `a` (empty alternatives are dropped).
- `{{}}`, `{{ }}`, `{{::hint}}`: not gaps, shown as literal text.

### 8.4 Figures

*Format version 2. A pack with a figure declares `"version": 2`; an app that only reads version 1 does not import it and asks the user to update ([section 14](#14-versioning-policy)).*

An item can carry a drawing: a map, a diagram, a formula, a circuit, an animation of a process. A figure is an object stored **inside the pack**:

| Property | Type | Required | Description |
|---|---|---|---|
| `svg` | string | **yes** | One SVG document: the complete source text, starting with the `<svg>` element. At most 200,000 characters. |
| `alt` | plain text | recommended (the schema requires it) | What the figure shows, for screen readers and for apps that cannot draw it. At most 500 characters. |

```json item
{
  "id": "largest-angle",
  "type": "text",
  "question": "How many degrees is the marked angle?",
  "answers": ["90", "90°", "ninety"],
  "figure": {
    "alt": "A right triangle. The angle between the two short sides is marked.",
    "svg": "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 120 90'><path d='M10 80H110L10 10Z' fill='none' stroke='currentColor' stroke-width='3'/><path d='M10 66H24V80' fill='none' stroke='#E5484D' stroke-width='3'/></svg>"
  }
}
```

**Where it is shown.** `figure` is shown with the question, above the prompt, and stays visible after answering. If the item also has `answerFigure`, that one replaces it after answering (the blank map becomes the labelled map, the still picture becomes the animation). An item with only `answerFigure` shows a drawing only after answering. For reversible cards the figures are the same in both directions.

**Size and colours.** The app scales the figure to the available width and keeps the proportions of the `viewBox`; `width` and `height` on the root element are ignored. The background is transparent and the app is used in light and dark mode: the CSS `color` of the figure is set to the app's text colour, so draw lines and labels with `currentColor` (`stroke="currentColor"`, `fill="currentColor"`) and choose other colours that are readable on a light and on a dark background.

**Animation.** Figures may be animated with SVG animation elements (`animate`, `animateTransform`, `animateMotion`, `set`) and with CSS animations and transitions in a `style` element. There is no interaction: the learner cannot click, hover or type into a figure, and scripts never run.

**The safe subset.** A figure must be self-contained and passive. A reader checks the `svg` text with the following rules and **drops a figure that breaks any of them** (the item itself is kept, [section 11](#11-what-a-reader-does-with-invalid-input)). The rules are lexical on purpose, they work on the characters of the text without an XML parser, so that every implementation decides the same; they are stricter than necessary in rare cases. "Whitespace" here is space, tab, line feed, carriage return and form feed, and "ignoring case" means after lowercasing the text.

1. The text is at most 200,000 characters (UTF-16 code units).
2. Surrounding whitespace is removed. One leading XML declaration (`<?xml ... ?>`) is removed, with the whitespace after it. No other `<?` may occur.
3. Ignoring case, the text does not contain `<!doctype`, `<!entity`, `javascript:` or `@import`.
4. Every `<` is the start of a comment (`<!--`), of a CDATA section (`<![CDATA[`), or of a tag: `<` or `</`, directly followed by an element name from the list below. The name is the longest run of the characters `A-Z a-z 0-9 : _ . -` and is compared case-sensitively. This also holds inside comments and CDATA sections. Consequently a literal `<` in a label must be written `&lt;`.
5. The first tag is the opening `<svg` tag, and it is closed by a `>`. The text between them, the root tag, contains a `viewBox` attribute: whitespace, `viewBox`, optional whitespace, `=`, optional whitespace, and a value in single or double quotes. The value consists of four numbers separated by whitespace or commas; a number uses only the characters `0-9 + - . e E`. The third and fourth number (width and height) are greater than zero.
6. No event handlers: ignoring case, there is no `on` followed by one or more letters `a-z`, optional whitespace and `=`, where the `on` comes directly after whitespace, a quote (`"` or `'`) or `/`.
7. No references to anything outside the figure: ignoring case, every `href` that is followed by optional whitespace and `=` continues with optional whitespace, a quote, optional whitespace and `#`; and every `url(` continues with optional whitespace, an optional quote, optional whitespace and `#`.

Allowed elements: `svg`, `g`, `defs`, `symbol`, `use`, `title`, `desc`, `path`, `rect`, `circle`, `ellipse`, `line`, `polyline`, `polygon`, `text`, `tspan`, `textPath`, `linearGradient`, `radialGradient`, `stop`, `pattern`, `clipPath`, `mask`, `marker`, `style`, `animate`, `animateTransform`, `animateMotion`, `mpath`, `set`, `filter` and the filter primitives `feBlend`, `feColorMatrix`, `feComponentTransfer`, `feComposite`, `feConvolveMatrix`, `feDiffuseLighting`, `feDisplacementMap`, `feDistantLight`, `feDropShadow`, `feFlood`, `feFuncA`, `feFuncB`, `feFuncG`, `feFuncR`, `feGaussianBlur`, `feMerge`, `feMergeNode`, `feMorphology`, `feOffset`, `fePointLight`, `feSpecularLighting`, `feSpotLight`, `feTile`, `feTurbulence`. Not allowed, among others: `script`, `foreignObject`, `a`, `image`, `feImage`, `iframe`, `audio`, `video`, and the editor-specific elements that drawing programs add (export as "plain" or "optimised" SVG). Fonts cannot be loaded; use generic families (`sans-serif`, `serif`, `monospace`).

The **aspect ratio** of a figure is the third number of the `viewBox` divided by the fourth. [`test-vectors.json`](test-vectors.json) lists figures with the expected decision and aspect ratio; a reader MUST reproduce them.

A writer MUST additionally produce well-formed XML. A reader need not verify that: it hands the text to an SVG renderer that MUST have scripting and network access switched off ([section 15](#15-security-considerations)). A reader that cannot draw SVG shows `alt` instead.

**A pack with a figure is a version 2 pack.** A writer MUST declare `"version": 2` as soon as one item has a `figure` or an `answerFigure`, so that an app without figures asks for an update instead of showing questions with the drawing missing. A pack without figures stays `"version": 1` and opens in every app. Readers are forgiving here as everywhere: a reader that supports version 2 draws the figures of a pack whatever version it declares.

### 8.5 Maps

*Format version 3. A pack with a map declares `"version": 3`; an app that only reads version 1 or 2 does not import it and asks the user to update ([section 14](#14-versioning-policy)).*

A map shows where something happened or lies: the place of a battle, where a person was born and died, where a river rises and where it ends. The pack contains **only the places**. The map itself is not part of the pack: the app draws its own built-in world map and puts the pins on it. A map is an object:

| Property | Type | Required | Default | Description |
|---|---|---|---|---|
| `pins` | array of pins | **yes** | | The places: 1 to 20 pins. |
| `line` | boolean | no | `false` | When `true` the pins are connected in array order with a line that shows the direction (birth to death, source to mouth, route of a journey). |
| `alt` | plain text | recommended (the schema requires it) | | What the map shows, for screen readers and for apps that cannot draw it. At most 500 characters. |

A **pin** is an object:

| Property | Type | Required | Description |
|---|---|---|---|
| `lat` | number | **yes** | Latitude in decimal degrees (WGS 84), from -90 to 90, positive north. |
| `lng` | number | **yes** | Longitude in decimal degrees, from -180 to 180, positive east. |
| `label` | plain text | no | A short name shown next to the pin. The schema allows 60 characters; an app may show less (the Jackdaw apps show the first 30). Shown literally, no markup. |

`lat` and `lng` must be JSON numbers (not strings, not `true`). Two or three decimals (about 100 m) are plenty; more are allowed. `180` and `-180` are the same meridian.

```json item
{
  "id": "napoleon-exile",
  "type": "text",
  "question": "On which island did Napoleon die in exile in 1821?",
  "answers": ["Saint Helena", "St. Helena"],
  "answerMap": {
    "alt": "A map of the Atlantic: Napoleon was born in Ajaccio on Corsica and died on Saint Helena, far off the coast of Africa.",
    "line": true,
    "pins": [
      { "lat": 41.92, "lng": 8.74, "label": "Ajaccio" },
      { "lat": -15.96, "lng": -5.71, "label": "Saint Helena" }
    ]
  }
}
```

**Where it is shown.** Exactly like a figure ([8.4](#84-figures)): `map` is shown with the question, above the prompt, and stays visible after answering; `answerMap` replaces what the question side showed (a `map` or a `figure`) once the learner has answered. An item with only `answerMap` shows a map only after answering ("Where was the Battle of Waterloo?"). For reversible cards the maps are the same in both directions. A `map` must not give away the answer of its own question: put the labelled map into `answerMap`.

**A map and a figure on the same side.** An item has at most one drawing per side: `figure` or `map`, and `answerFigure` or `answerMap`. A `figure` with an `answerMap` is fine (the figure is replaced by the map after answering), and so is a `map` with an `answerFigure`. The schema rejects `figure` together with `map`, and `answerFigure` together with `answerMap`. A reader keeps the figure, ignores the map of that side and warns ([section 11](#11-what-a-reader-does-with-invalid-input)).

**What the app draws.** The look is up to the app; what a writer can rely on:

- A built-in world map with the land, the coasts and the country borders of today's world, without names. It is the same for every pack and is not part of the file. Borders are those of today: a map shows *where*, it does not show historical borders, so do not ask a question that depends on them.
- The view is framed by the app so that **every pin is visible**, with enough of the surrounding geography to recognise the region. A single pin is not zoomed in to one coastline: the view shows a region at least about 40 degrees of longitude wide. A pin in the middle of an ocean (an island far from any coast) gets a wider view, so that enough land is in it to tell where on Earth the place is. Pins that lie far apart give a larger view, up to the whole world. Pins on both sides of the 180th meridian are framed the short way round (Fiji and Samoa give a small view, not one that spans the globe). The map uses the Web Mercator projection; a pin north of about 85 or south of about -85 degrees is drawn at that latitude.
- Each pin is a clearly visible dot, with its `label` next to it where there is room. Pins that are close together keep their labels apart.
- With `line` the pins are connected in array order with straight lines (in the projection of the map, not along the shortest path on the globe), with a small arrow that shows the direction. A `line` with a single pin draws nothing extra.
- The map is drawn on a transparent background and readable in light and dark mode. There is no interaction.
- An app that cannot draw the map shows `alt`.

**Invalid maps.** A reader never fails because of a map. A `map` or `answerMap` that is not an object, has no `pins` array or no valid pin left is ignored and the item is imported without it. A pin that is not an object or has a `lat` or `lng` that is missing, not a JSON number (including `NaN` and infinity) or out of range is dropped, the other pins stay; more than 20 pins: the first 20 valid ones are used. A `line` that is not a boolean, an `alt` that is not text, and a `label` that is not text are ignored. All of this produces a warning ([section 11](#11-what-a-reader-does-with-invalid-input)). The `map` vectors of [`test-vectors.json`](test-vectors.json) give the expected result for each case; a reader MUST reproduce them.

**A pack with a map is a version 3 pack.** A writer MUST declare `"version": 3` as soon as one item has a `map` or an `answerMap`, so that an app without maps asks for an update instead of showing questions with the map missing. A pack without maps stays at version 1 or 2 and opens in every app that reads that version. Readers are forgiving here as everywhere: a reader that supports version 3 draws the maps of a pack whatever version it declares.

---

## 9. Answer matching

Typed answers (`type` exercise of cards, `text` items, `cloze` gaps) are graded by comparing **normal forms**. The rules below are exact so that every implementation grades the same. [`test-vectors.json`](test-vectors.json) lists test cases that a reader MUST pass.

### 9.1 Whitespace

"Whitespace" means the Unicode `White_Space` characters: U+0009 to U+000D, U+0020, U+0085, U+00A0, U+1680, U+2000 to U+200A, U+2028, U+2029, U+202F, U+205F and U+3000. (Zero-width characters such as U+200B are not whitespace.)

### 9.2 Normal form

`normalize(s)` for a string `s`, with the flags `caseSensitive` (default `false`) and `ignoreDiacritics` (from `study`, default `false`). The steps run in this order:

1. **Unify quotes and dashes.** U+2018, U+2019, U+201B, U+02BC and U+2032 become `'` (U+0027). U+201C, U+201D, U+201E, U+201F and U+2033 become `"` (U+0022). U+2010, U+2011, U+2012, U+2013 and U+2212 become `-` (U+002D). (Phone keyboards insert typographic characters; the user must not be penalised.)
2. **Diacritics.** If `ignoreDiacritics` is `true`: convert to Unicode NFD, then delete every code point in U+0300 to U+036F (combining diacritical marks). Letters that have no decomposition (`ø`, `ł`, `ß`, `æ`) are not changed.
3. **Case.** If `caseSensitive` is `false`: convert to lowercase with the locale-independent Unicode lowercase mapping (Kotlin `lowercase()`, Swift `lowercased()`, JavaScript `toLowerCase()`, Python `lower()`). Full case folding is not required: `ß` stays `ß`, and `STRASSE` and `Straße` are different.
4. **Unicode NFC.** Convert to Unicode normalization form C.
5. **Whitespace.** Replace every run of whitespace ([9.1](#91-whitespace)) by a single U+0020 and remove U+0020 at the start and end.
6. **Surrounding punctuation.** Let `t` be `s` with all leading `¿` (U+00BF), `¡` (U+00A1) and whitespace removed, and all trailing characters from `. , ; : ! ? …` and the CJK/fullwidth marks `。 、 ． ， ； ： ！ ？` (U+3002, U+3001, U+FF0E, U+FF0C, U+FF1B, U+FF1A, U+FF01, U+FF1F) and whitespace removed. If `t` is not empty the result is `t`, otherwise the result is `s` (a string that consists only of punctuation is kept as it is).

Other characters are never touched: inner punctuation, brackets, closing quotes, hyphens and digits stay (`U.S.A.` becomes `u.s.a`). Examples:

| Input | Normal form |
|---|---|
| `  Hello,   World!  ` | `hello, world` |
| `¿Cómo estás?` | `cómo estás` |
| `Zürich` | `zürich`, or `zurich` with `ignoreDiacritics` |
| `don’t` | `don't` |
| `Na` with `caseSensitive` | `Na` |
| `?!` | `?!` (kept: nothing else would be left) |

### 9.3 Matching

A typed answer is correct if its normal form **equals** the normal form of at least one accepted answer. The accepted answers are:

| Exercise | Accepted answers |
|---|---|
| `card`, forward | the plain-text rendering of `back`, and every entry of `alternatives` |
| `card`, reverse | the plain-text rendering of `front`, and every entry of `frontAlternatives` |
| `text` | every entry of `answers` |
| `cloze` | every alternative of the gap being answered |

The **plain-text rendering** of rich text is the concatenation of its text after interpreting [8.2](#82-inline-markup), i.e. without the markers and with escapes resolved (`**hola**` renders `hola`). Entries of `alternatives`, `frontAlternatives` and `answers`, and gap alternatives, are used as they are. What the user types is never interpreted as markup. An empty input is never correct.

Nothing else is equal. In particular these are **not** matched automatically; list them explicitly as alternatives: spelling variants (`colour`/`color`), synonyms, digits versus words (`3`/`three`), a missing article (`el pan`/`pan`), hyphen versus space, abbreviations. Accents are significant unless the pack sets `ignoreDiacritics`.

An app MAY offer additional leniency (for example tolerating a typo) as long as the result defined here stays the baseline: an answer accepted only through leniency must be shown visibly differently from a correct answer, together with the correct spelling, and the user must be able to switch the leniency off. Leniency MUST NOT apply to items with `caseSensitive`, and it MUST NOT change, add or remove a digit.

---

## 10. Compatibility and extensibility

These rules let the format grow without breaking existing apps and packs.

- **Unknown properties MUST be ignored.** This holds for every object (the pack, sections, `study`, items, options) at every level. A reader never rejects a file, and never skips an item, because of a property it does not know.
- **`x-` properties are for extensions.** A property whose name starts with `x-` (for example `x-my-tool`) is reserved for vendor and tool extensions, at every level. The format will never define a property starting with `x-`. Its value can be any JSON. Readers ignore extensions they do not understand. Tools SHOULD pick a distinctive name (`x-acme-source-line`).
- **Unknown item types MUST be skipped.** An item whose `type` the reader does not know is not imported, the rest of the pack is. The reader SHOULD report a warning ("skipped 3 items of unknown type 'matching'"). A new item type comes with a new format version ([section 14](#14-versioning-policy)); this rule is for packs that declare a lower version than they need.
- **Unknown values of optional enumerated properties are ignored** (the property is treated as absent): an unknown `study.order`, an unknown kind inside `exercises` (the remaining known kinds are used; if none is left the default applies).
- **Packs stay readable for good.** Every app that reads Jackdaw packs, now and in the future, MUST import a valid version 1 pack with all of its items, and so for every later version it supports. Later versions only add to the format ([section 14](#14-versioning-policy)); they never remove a property, never change what an existing property means and never make an existing valid pack invalid. The packs in [`compat/`](compat/) are frozen copies from each version; an implementation must keep importing them completely.
- **Reserved names.** `media` (on the pack and on items) is reserved for a future version that will attach photos and audio; writers MUST NOT use it for anything else, readers ignore it. Property names that start with `$` are reserved for the format.
- **Version check.** A reader MUST reject a file whose `version` is greater than the highest version it supports and tell the user to update the app. A reader that supports version N MUST also read every version below N.
- **The schema is stricter than readers.** The JSON Schema is for authors and tools. It rejects things a reader would tolerate: unknown item types (to catch typos like `"multiple-choice"`), length and count limits, malformed optional properties. A file that fails the schema may still be imported by a forgiving reader, but writers MUST aim to pass it.
- **Progress is not content.** Packs MUST NOT contain learning progress, scheduling data, review history or user settings. An exporter in the app writes content only.

---

## 11. What a reader does with invalid input

Readers MUST NOT crash, hang or run out of memory on invalid files. They apply the first matching action:

| Problem | Action |
|---|---|
| The file is not valid JSON or not UTF-8, the top level is not an object (a leading BOM is tolerated) | **Reject** the file. |
| `format` is missing or not `"jackdaw-pack"` | **Reject** (not a pack). |
| `version` is missing, not an integer of at least 1 (`1.0` MAY be accepted as `1`), or greater than supported | **Reject** (suggest updating the app if it is too high). |
| `id` is missing or violates [4.1](#41-identifiers) | **Reject**. |
| `title` is missing, not a string or blank | **Reject**. |
| `items` is missing, not an array, or no valid item is left after the checks below | **Reject**. |
| An item is not an object, has a missing, invalid or **duplicate** `id` (the first occurrence wins), or a missing, non-string or unknown `type` | **Skip the item**, warn. |
| An item lacks a required property of its type or it has the wrong JSON type or is blank | **Skip the item**, warn. |
| `choice`: fewer than 2 valid options (objects with a non-blank `text`) or no option with `"correct": true`. A non-boolean `correct` counts as `false`. | **Skip the item**, warn. |
| `text`: no non-blank entry in `answers`. `cloze`: no valid gap ([8.3](#83-cloze-gaps)). `order`: fewer than 2 entries. `truefalse`: `answer` is not a boolean. | **Skip the item**, warn. |
| An optional property has the wrong JSON type or an invalid value (including `icon`, `color`, dates, `exercises`) | **Ignore that property** (treat it as absent), warn. |
| Entries of an array are invalid (for example a number inside `alternatives`) | **Drop those entries**; if none are left treat the property as absent. |
| A `figure` or `answerFigure` is not an object, has no `svg` string, or its `svg` breaks a rule of [8.4](#84-figures) | **Ignore that property**: the item is imported without that figure, warn. |
| A `map` or `answerMap` is not an object, has no `pins` array, or no valid pin is left ([8.5](#85-maps)) | **Ignore that property**: the item is imported without that map, warn. |
| A pin of a map is not an object, or its `lat` or `lng` is missing, not a number or out of range; more than 20 pins | **Drop that pin** (the first 20 valid pins are used), warn. |
| The `line`, `alt` or a pin's `label` of a map has the wrong type | **Ignore that property**, warn. |
| An item has a `figure` and a `map` (or an `answerFigure` and an `answerMap`) | **Keep the figure, ignore the map**, warn. |
| A section is not an object, lacks `id` or `title`, or has a duplicate `id` (first wins) | **Drop that section**, warn. |
| An item names a `section` that does not exist | Treat the item as **ungrouped**, warn. |
| Unknown properties or unknown item types | Ignore / skip as described in [section 10](#10-compatibility-and-extensibility). |

The import result SHOULD be shown to the user as a short report: how many items were imported and what was skipped and why. A pack with warnings is still imported.

Two things are **not** errors for a reader, although a writer should avoid them: items whose `section` property is missing although sections exist, and unused sections.

A reader can be built in this order: (1) parse the JSON, tolerating a BOM; (2) check `format`, `version`, `id`, `title`; (3) read `sections`, `study` and the cosmetic properties, ignoring what is invalid; (4) walk `items`, switch on `type`, validate each item as in the table and keep a list of warnings; (5) reject if no item is left, otherwise install or update by pack `id` ([4.2](#42-updating-a-pack)); (6) report. The markup parser ([8.2](#82-inline-markup)), the gap parser ([8.3](#83-cloze-gaps)) and the answer normalisation ([9.2](#92-normal-form)) are small pure functions that can be tested against [`test-vectors.json`](test-vectors.json).

---

## 12. Limits and size guidance

| | Recommended maximum |
|---|---|
| File size | 5 MB |
| Items per pack | 5,000 |
| Text properties (`front`, `back`, `question`, `text`, `explanation`, ...) | 4,000 characters |
| Answers, alternatives | 500 characters each |
| `svg` of a figure | 200,000 characters (most figures need less than 5,000) |
| Pins of a map | 20 |
| `label` of a pin | 60 characters |
| `alt` of a figure or a map | 500 characters |
| `title` | 200 characters |
| `description` | 2,000 characters |
| Options of a `choice` item | 10 |
| Entries of an `order` item | 20 |
| Tags | 50 per pack or item, 50 characters each |

Readers MUST import every pack that stays within these limits. Beyond them behaviour is up to the reader: it MAY accept more, and it MAY reject files larger than 25 MB or with more than 50,000 items, and then MUST tell the user why. The limits for single text properties are guidance for authors and validators (the schema enforces them); a reader SHOULD NOT refuse a pack just because a text is longer. Large datasets SHOULD be split into several packs (by topic or chapter).

Small items learn better: keep prompts short and answers atomic ([`AUTHORING.md`](AUTHORING.md)).

---

## 13. Validation

There are three layers:

1. **JSON Schema** ([`jackdaw-pack.schema.json`](jackdaw-pack.schema.json), draft 2020-12): structure, types, required properties, enumerations, patterns, limits. Add `"$schema": "https://raw.githubusercontent.com/OrellBuehler/jackdaw/main/format/jackdaw-pack.schema.json"` to a pack to get completion and validation in editors that support it.
2. **Semantic rules** that a schema cannot express. A valid pack satisfies:
   - item ids are unique; section ids are unique;
   - every `section` of an item names a declared section;
   - every `choice` has at least one correct option;
   - every `cloze` text has at least one valid gap, and no gap is empty;
   - every typed answer is non-empty after normalisation;
   - `order` entries are different from each other;
   - every figure passes the rules of [8.4](#84-figures) and is well-formed XML;
   - every map has 1 to 20 pins with `lat` and `lng` in range, and no item has a figure and a map on the same side ([8.5](#85-maps));
   - `version` is the lowest version that has everything the pack uses (`3` with maps, `2` with figures, otherwise `1`);
   - the JSON has no duplicate property names within an object.
3. **Reader behaviour** ([section 11](#11-what-a-reader-does-with-invalid-input)): what the app does with whatever it receives.

[`validate.py`](validate.py) checks layers 1 and 2 and prints warnings for dubious content (all options correct, `open` without anything to reveal, unbalanced markup characters, more than 5,000 items, ...). It also contains a small reference implementation of markup parsing, cloze parsing and answer normalisation, which is checked against [`test-vectors.json`](test-vectors.json).

```
pip install jsonschema        # once, ideally in a throwaway virtualenv
python3 validate.py my-pack.jackdaw
python3 validate.py           # the examples, the packs in this document, the test vectors
```

Any other JSON Schema 2020-12 validator (for example `ajv`) can check layer 1.

---

## 14. Versioning policy

- `version` is the version of the format, an integer. This specification describes versions 1, 2 and 3.
- **A pack declares the lowest version that has everything it uses.** A pack without figures and maps is `"version": 1` and opens in every app; a pack with a figure is `"version": 2`; a pack with a map is `"version": 3`.
- **What the learner would miss gets a new version:** an addition that changes what the learner sees or is asked (figures, maps, a new item type, a new exercise kind) increases `version`. An app that does not know that version rejects the pack and asks the user to update ([section 10](#10-compatibility-and-extensibility)), instead of showing a pack with parts missing.
- **What can be ignored does not:** a new optional property that an older app can leave out without the learner noticing (metadata, information for tools) keeps the version. Readers cope with it through the rules of [section 10](#10-compatibility-and-extensibility).
- **Breaking changes increase `version`** as well: removing or renaming a property, changing the meaning or default of a property, making an optional property required, changing the rules for ids, normalisation or grading. A pack that needs a breaking feature declares the higher version, and older apps reject it with a clear message.
- A reader that supports version N MUST also read all earlier versions.
- **Compatibility promise.** A pack that is valid under any version stays valid, and keeps its meaning, under every later version. Each version says where it adds something ("version 3") and adds a frozen pack to [`compat/`](compat/) that uses it. The validator checks that the frozen packs are unchanged and still valid, and the reference reader is tested to import them without skipping anything.
- Version history: **1** first release. **2** figures (`figure`, `answerFigure`, [8.4](#84-figures)). **3** maps (`map`, `answerMap`, [8.5](#85-maps)).
- The JSON Schema describes every version and checks that a pack does not use more than the version it declares.
- Editorial and clarifying changes of this document only change the *specification revision* shown at the top.

---

## 15. Security considerations

A pack is untrusted input, possibly written by a stranger or generated by a program.

- Render all text as text. Never interpret it as HTML, a URL to open, a file path or code. Do not turn text into links automatically.
- **Figures are the one place where pack content reaches a renderer.** Apply the rules of [8.4](#84-figures) when importing, and do not rely on them alone: draw the SVG in a renderer that has scripting disabled, cannot reach the network, the file system or other app content, and cannot navigate (in a web view: JavaScript off, network loads blocked, a content security policy of `default-src 'none'; style-src 'unsafe-inline'`, no handling of links). Bound the size of the figure on screen.
- A map ([8.5](#85-maps)) is data, not a drawing: the app turns the pins into a picture with its own code and its own map data, and escapes every label. Treat `label` and `alt` as plain text like any other.
- Do not fetch `source` or any other URL automatically.
- Bound the memory and time used for parsing (file size, nesting depth, number of items, length of strings) as in [section 12](#12-limits-and-size-guidance). Use a JSON parser with a nesting limit.
- Importing a pack never executes anything and never needs permissions beyond reading the file.
- Do not trust `x-` properties; ignore them unless you know them, and treat their content as untrusted as well.
- Ids are restricted to a safe ASCII set so that they can be used in database keys and file names. Still, escape them wherever you use them.

---

## 16. Complete example

This pack uses every property of the format (except maps, see [8.5](#85-maps), which would make it a version 3 pack) and every item type. It is also stored as [`examples/format-tour.jackdaw`](examples/format-tour.jackdaw), and doubles as a short quiz about the format.

```json pack
{
  "$schema": "https://raw.githubusercontent.com/OrellBuehler/jackdaw/main/format/jackdaw-pack.schema.json",
  "format": "jackdaw-pack",
  "version": 2,
  "id": "com.github.orellbuehler.jackdaw.examples.format-tour",
  "title": "Jackdaw Format Tour",
  "description": "A complete example that uses every property of the format and every item type. It doubles as a small quiz about the format itself.",
  "language": "en",
  "author": "Jackdaw project",
  "license": "CC0-1.0",
  "source": "https://github.com/OrellBuehler/jackdaw/tree/main/format",
  "tags": ["jackdaw", "format", "example"],
  "icon": "🧭",
  "color": "#7C3AED",
  "revision": 4,
  "created": "2026-11-01T09:00:00Z",
  "updated": "2026-11-20T14:30:00+01:00",
  "sections": [
    {"id": "basics", "title": "Basics", "description": "Files, ids and updates."},
    {"id": "item-types", "title": "Item types", "description": "How the seven item types are written."}
  ],
  "study": {"order": "sequential", "newPerSession": 5, "reversible": false, "ignoreDiacritics": false, "exercises": {"card": ["flip", "choice", "type"]}},
  "x-tool": {"name": "written by hand", "note": "Properties starting with x- are vendor extensions and may hold any JSON value."},
  "items": [
    {
      "id": "card-extension",
      "type": "card",
      "section": "basics",
      "tags": ["file"],
      "front": "What is the file extension of a Jackdaw pack?",
      "back": ".jackdaw",
      "alternatives": ["jackdaw"],
      "distractors": [".buf", ".pack", ".zip"],
      "exercises": ["flip", "choice", "type"],
      "hint": "It starts with a dot and uses the ASCII spelling of the app's name.",
      "explanation": "`.jackdaw.json` and `.json` are accepted as well."
    },
    {
      "id": "card-format-value",
      "type": "card",
      "section": "basics",
      "tags": ["file"],
      "front": "`format` value",
      "back": "`jackdaw-pack`",
      "reversible": true,
      "caseSensitive": true,
      "frontAlternatives": ["format", "format property"],
      "distractors": ["jackdaw", "jackdaw-file", "jackdaw-pack"],
      "frontDistractors": ["`version` value", "`id` value", "`type` value"],
      "exercises": ["flip", "choice", "type"],
      "hint": "A constant marker at the top level of every pack.",
      "explanation": "Readers refuse files whose `format` is anything else."
    },
    {
      "id": "choice-single",
      "type": "choice",
      "section": "item-types",
      "question": "Which item type is meant for questions that the learner answers in their own words and then grades themselves?",
      "options": [
        {"text": "`open`", "correct": true, "explanation": "Open items offer a model answer and a checklist of key points for self-assessment."},
        {"text": "`text`", "explanation": "Text items expect a short typed answer that is compared automatically."},
        {"text": "`cloze`", "explanation": "Cloze items are sentences with gaps to fill in."},
        {"text": "`order`", "explanation": "Order items are arranged into the right sequence."}
      ]
    },
    {
      "id": "choice-multi",
      "type": "choice",
      "section": "item-types",
      "question": "Which of these are item types in format version 1? (Select all that apply.)",
      "options": [
        {"text": "`card`", "correct": true},
        {"text": "`cloze`", "correct": true},
        {"text": "`order`", "correct": true},
        {"text": "`matching`", "explanation": "Not part of version 1."},
        {"text": "`video`", "explanation": "Media is reserved for a future version."}
      ],
      "explanation": "Version 1 has seven item types: card, choice, text, cloze, truefalse, order and open."
    },
    {
      "id": "choice-min-options",
      "type": "choice",
      "section": "item-types",
      "shuffle": false,
      "question": "How many options does a `choice` item need at minimum?",
      "options": [
        {"text": "1"},
        {"text": "2", "correct": true, "explanation": "At least two options, and at least one of them marked correct."},
        {"text": "3"},
        {"text": "4"}
      ]
    },
    {
      "id": "text-extension-prefix",
      "type": "text",
      "section": "basics",
      "question": "Property names starting with which prefix are reserved for vendor extensions?",
      "answers": ["x-"],
      "hint": "Two characters: a letter and a hyphen.",
      "explanation": "Readers ignore extensions they do not understand, like the `x-tool` property of this pack."
    },
    {
      "id": "text-exact-case",
      "type": "text",
      "section": "item-types",
      "caseSensitive": true,
      "question": "Type the value of the `type` property of a true/false item, in lowercase.",
      "answers": ["truefalse"],
      "explanation": "Item types are lowercase words without separators."
    },
    {
      "id": "cloze-syntax",
      "type": "cloze",
      "section": "item-types",
      "text": "A gap is written between {{two::how many?}} curly braces, and alternatives inside one gap are separated by the character {{\\|::the pipe}}.",
      "explanation": "To use a literal pipe inside a gap, escape it with a backslash: `\\|`."
    },
    {
      "id": "cloze-case",
      "type": "cloze",
      "section": "item-types",
      "caseSensitive": true,
      "text": "The chemical symbol for sodium is {{Na::two letters}}.",
      "explanation": "`caseSensitive` is available on card, text and cloze items."
    },
    {
      "id": "tf-progress",
      "type": "truefalse",
      "section": "basics",
      "statement": "Learning progress is stored inside the pack file.",
      "answer": false,
      "explanation": "Progress and scheduling belong to the app. A pack only describes content, so it can be shared and updated freely."
    },
    {
      "id": "order-update",
      "type": "order",
      "section": "basics",
      "prompt": "Put the steps of updating a pack in the right order.",
      "sequence": [
        "Edit the items and keep the ids of unchanged items",
        "Increase `revision` by one",
        "Save the file with the same pack `id`",
        "Import the file again; progress for unchanged item ids is kept"
      ],
      "explanation": "Importing a pack with an id that is already installed updates that pack."
    },
    {
      "id": "open-stable-ids",
      "type": "open",
      "section": "basics",
      "x-source-page": 12,
      "question": "Why must item ids stay stable between versions of a pack?",
      "answer": "Learning progress is stored per item id. If an id changes, the app treats the item as new and the learner loses its history. If an id is reused for different content, the old progress wrongly applies to it.",
      "keyPoints": [
        "Progress is stored per item id",
        "A changed id makes the item look new",
        "A reused id passes old progress on to different content",
        "Fixing a typo does not require a new id"
      ]
    },
    {
      "id": "figure-svg",
      "type": "truefalse",
      "section": "item-types",
      "statement": "A figure is stored inside the pack as inline SVG.",
      "answer": true,
      "figure": {
        "alt": "A dot moving left and right inside a rounded frame.",
        "svg": "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 240 80'><rect x='4' y='4' width='232' height='72' rx='12' fill='none' stroke='currentColor' stroke-width='2'/><circle cx='40' cy='40' r='12' fill='#7C5CFF'><animate attributeName='cx' values='40;200;40' dur='4s' repeatCount='indefinite'/></circle></svg>"
      },
      "explanation": "`figure` is shown with the question, `answerFigure` after answering. Both are self-contained SVG and may be animated."
    }
  ]
}
```

More examples in [`examples/`](examples/): [`spanish-basics.jackdaw`](examples/spanish-basics.jackdaw) (reversible vocabulary cards), [`world-history-20th-century.jackdaw`](examples/world-history-20th-century.jackdaw) (mixed question types), [`interview-prep-software-engineer.jackdaw`](examples/interview-prep-software-engineer.jackdaw) (open questions with key points) [`swiss-cantons.jackdaw`](examples/swiss-cantons.jackdaw) and [`european-capitals.jackdaw`](examples/european-capitals.jackdaw) (capitals as choice and typed answers), [`french-basics.jackdaw`](examples/french-basics.jackdaw), [`chemical-elements.jackdaw`](examples/chemical-elements.jackdaw) (symbols asked both ways), [`git-essentials.jackdaw`](examples/git-essentials.jackdaw) (commands as typed answers), [`solar-system.jackdaw`](examples/solar-system.jackdaw), [`schweizer-staatskunde.jackdaw`](examples/schweizer-staatskunde.jackdaw) (a pack in German), [`world-capitals.jackdaw`](examples/world-capitals.jackdaw), [`flags-of-the-world.jackdaw`](examples/flags-of-the-world.jackdaw) (a figure on every card), and [`japanese-hiragana.jackdaw`](examples/japanese-hiragana.jackdaw) and [`japanese-katakana.jackdaw`](examples/japanese-katakana.jackdaw) (a script, asked both ways). [`examples/index.json`](examples/index.json) lists them for the sample library of the Jackdaw apps; it is not part of the pack format and is rewritten with `python3 validate.py --write-index`.

---

## 17. FAQ: choosing item types

**Which type should I use?**

| I want the learner to ... | Use |
|---|---|
| learn that A goes with B (vocabulary, term and definition, country and capital) | `card` |
| also be asked B for A | `card` with `reversible: true` |
| type one short factual answer to a question | `text` |
| recognise the right answer among plausible wrong ones, or pick several right answers | `choice` |
| supply a missing word in a sentence or formula | `cloze` |
| judge a claim | `truefalse` |
| know a sequence (dates, steps, rankings) | `order` |
| explain something in their own words (concepts, interview answers, essay recall) | `open` with `keyPoints` |

**`card` or `text`?** A card is a symmetric pair that can be reversed and shown as flip, choice or typing. A `text` item is a real question with a typed answer, one direction only, and it can list several accepted answers. "Capital of France?" can be either; "Which crisis of 1962 brought the world close to nuclear war?" is a `text`.

**`card` with the `choice` exercise or a `choice` item?** Let a card build its own multiple choice when the wrong options can simply be other cards' answers (countries and capitals, vocabulary). Write a `choice` item when you want to craft the wrong options, ask a question that is not a pair, or need several correct options.

**`truefalse` or `choice`?** True/false has a 50 percent guess rate and teaches little by itself. Use it for myths and common misconceptions, and always add an `explanation`. For recall of facts prefer `text`, `card` or `choice`.

**Can an item have several typed answers, like "name three causes"?** Not as one `text` item. Use `open` with `keyPoints`, a `cloze` with several gaps, or one `text` item per fact.

**Is there matching of pairs, drawing, audio or images?** Drawings yes: any item can show an SVG figure ([8.4](#84-figures)), which makes the pack a version 2 pack. Places on a world map too: a `map` with pins ([8.5](#85-maps)), which makes it a version 3 pack. Matching of pairs, drawing by the learner, photos and audio are not part of the format. Pairs are best as separate `card` items (the app builds multiple choice from them).

**How do I mark a formula or code?** Use backticks: `` `E = mc^2` ``. Everything is plain Unicode text; there is no math typesetting.

**How long should a pack be?** 20 to 500 items is typical. Split by topic when a pack grows beyond a few hundred items ([`AUTHORING.md`](AUTHORING.md)).

**What happens to my progress when I update a pack?** It is kept for every item whose `id` did not change ([4.2](#42-updating-a-pack)).

**Can I write packs with an AI assistant?** Yes, that is a design goal. [`AUTHORING.md`](AUTHORING.md) contains a prompt you can paste into any chatbot, and validation rules to check the result.
