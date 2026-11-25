# Authoring Jackdaw Packs

A guide for **AI assistants, language models and tool authors** (and the people who prompt them) on turning source material into a good Jackdaw pack. It assumes you know nothing about Jackdaw. The normative specification is [`README.md`](README.md); the machine-readable definition is [`jackdaw-pack.schema.json`](jackdaw-pack.schema.json).

Jump to: [the 60-second version](#1-the-60-second-version) · [workflow](#2-workflow) · [choosing item types](#3-choosing-item-types) · [writing good items](#4-writing-good-items) · [ids](#5-ids) · [language packs](#6-language-learning-packs) · [size and structure](#7-size-and-structure) · [common mistakes](#8-common-mistakes) · [JSON pitfalls](#9-json-pitfalls) · [checking your work](#10-checking-your-work) · [notes for tool authors](#11-notes-for-tool-authors) · [**copy-paste prompt**](#12-copy-paste-prompt-for-any-chatbot)

---

## 1. The 60-second version

A pack is **one JSON file** (extension `.jackdaw`). Minimal shape:

```json pack
{
  "format": "jackdaw-pack",
  "version": 1,
  "id": "pack.capitals.9c41ab",
  "title": "Capitals",
  "items": [
    { "id": "france", "type": "card", "front": "France", "back": "Paris" },
    { "id": "largest-ocean", "type": "text", "question": "Which is the largest ocean?", "answers": ["Pacific", "Pacific Ocean"] }
  ]
}
```

Seven item types decide how things are learned:

| `type` | Shape | Best for |
|---|---|---|
| `card` | `front`, `back` | vocabulary, terms, pairs (can be asked both ways) |
| `choice` | `question`, `options: [{text, correct?}]` | recognising the right answer among plausible ones |
| `text` | `question`, `answers: [...]` | one short typed answer |
| `cloze` | `text` with `{{gaps}}` | missing word in a sentence |
| `truefalse` | `statement`, `answer: true/false` | myths and misconceptions |
| `order` | `prompt`, `sequence: [...]` in correct order | chronology, steps, rankings |
| `open` | `question`, `answer?`, `keyPoints?` | explain-in-your-own-words, interview answers |

Every item also has a unique `id` and may have `section`, `hint` (shown before answering) and `explanation` (shown after).

## 2. Workflow

1. **Understand the goal.** Who learns this, at what level, for what purpose (exam, trip, interview)? What must they be able to *do* afterwards: recall a word, state a date, explain a concept? Ask the user if it is unclear; do not guess the language of the content.
2. **Gather the facts.** Use the source material the user gave you. When you add facts from your own knowledge, include only things you are sure about. A wrong fact in a learning pack is worse than a missing one, because the learner will memorise it. If you are unsure, leave it out and say so.
3. **Atomise.** Break the material into single facts or skills ([4.1](#41-one-fact-per-item)).
4. **Pick a type per fact** ([section 3](#3-choosing-item-types)). Mix types when it helps, but do not use a type just for variety.
5. **Group** the items into sections (chapters) when there are more than about 20 ([section 7](#7-size-and-structure)).
6. **Write** the items with stable ids ([section 5](#5-ids)), hints for hard items and explanations that teach.
7. **Check** the result against the checklist in [section 10](#10-checking-your-work) and, if you can run code, with `validate.py`.
8. **Deliver** the JSON only, as one file or one code block, without commentary inside the file. Tell the user what you included, what you left out and any facts they should double check.

## 3. Choosing item types

Ask: *what should the learner do?*

| The learner should ... | Type | Example |
|---|---|---|
| connect a term to its meaning, in one or both directions | `card` | `hola` / `hello` |
| type a short fact in answer to a question | `text` | "In which year did Apollo 11 land on the Moon?" |
| pick the right answer from plausible alternatives | `choice` | "Which of these is not an ACID property?" |
| complete a sentence | `cloze` | "The Berlin Wall fell in {{1989}}." |
| spot a false claim | `truefalse` | "Gandhi was India's first prime minister." |
| know a sequence | `order` | steps of a process, events by date |
| explain, compare, justify | `open` with `keyPoints` | "What is the CAP theorem?" |

Guidance:

- **Vocabulary and pairs are cards.** Mark them `reversible` (pack-wide with `study.reversible: true`) when both directions are useful. The app builds the multiple-choice versions itself from the other cards, so you usually need no distractors.
- **Questions with a short answer are `text`**, not `card`: a `text` item can list several accepted answers and is asked in one direction only.
- **Use `choice` when the wrong options matter**, when you want several correct options (multi-select), or when the question is not a clean pair. Do not turn every fact into a multiple-choice question; recognising is easier than recalling.
- **Use `cloze` for sentences, formulas and code** where the missing word is the point. One or two gaps per item.
- **Use `open` for anything that cannot be reduced to a short string.** The `keyPoints` checklist is what makes self-grading honest, so write them carefully.
- **Use `truefalse` sparingly**, for common misconceptions, and always with an `explanation`. A learner guesses half of them right.
- **Use `order` only for sequences that have one defensible correct order.** 3 to 7 entries.

## 4. Writing good items

### 4.1 One fact per item

Atomic items are learned faster and scheduled more precisely. Split anything with "and".

| Bad | Good |
|---|---|
| "What were the causes of World War I?" (as a `text` item) | an `open` item with `keyPoints`, or several `text`/`choice` items, one per cause |
| `back`: "Paris, on the Seine, about 2 million inhabitants, founded by the Parisii" | `back`: "Paris"; put the extras into `explanation` |
| One cloze with five gaps | five cloze items, or one with two gaps |

Keep prompts short, unambiguous and answerable without seeing the options. Never write a prompt that is only answerable by the exact wording of the source ("According to the text, ...").

### 4.2 Cards

- `front` is the prompt, `back` the answer. For the `type` exercise the user types `back`, so make it short and typeable. Put background in `explanation`, not in `back`.
- Put **accepted variants** into `alternatives` (synonyms, the answer without the article, `UN`/`United Nations`). They are plain text and case does not matter.
- For reversible cards, `frontAlternatives` does the same for the other direction. Write `hint`s that work in both directions.
- Provide `distractors` only when automatic ones would be too easy or misleading, for example for false friends or numbers. Give three plausible wrong answers of the same kind as the right one.
- Restrict `exercises` per card when some kinds make no sense: remove `type` for long backs.

### 4.3 Multiple choice

- Write **plausible distractors**: typical misconceptions, near neighbours, same category and similar length as the right answer. Not jokes, not obviously absurd, not "None of the above" or "All of the above".
- **3 to 5 options.** Four is the sweet spot. Do not pad.
- **Do not reveal the answer through form**: the correct option should not be the longest, the only specific one, or the only one that matches the grammar of the question.
- Vary which option is correct. The app shuffles the options by default (`shuffle` is `true`), so the order in your file does not matter unless you set `"shuffle": false`.
- For **multi-select**, say so in the question ("Select all that apply") and mark every correct option. Avoid questions where all options are correct.
- Add `explanation` to options when the reason a distractor is wrong is educational. These are shown to the learner after answering.
- Avoid negations ("Which is NOT ...") unless the negation is the point; if you use them, capitalise NOT.

### 4.4 Typed answers (`text`, cloze gaps, card typing)

- The answer must be a **short, unambiguous string**: a name, a year, a term. If several wordings are equally right, list them all in `answers` (first is the canonical one).
- Typed answers are compared **after normalisation**: case is ignored (unless `caseSensitive`), surrounding and repeated spaces collapse, trailing `. , ; : ! ? …` and leading `¿ ¡` are ignored, typographic quotes and dashes are unified. Everything else must match, so **list**: spelling variants (`colour`/`color`), digits and words (`1969`/`nineteen sixty-nine`), abbreviations, with and without article, with and without first name.
- **Accents matter** (`é` is not `e`). For languages with accents set `"study": {"ignoreDiacritics": true}` for the whole pack, or list the unaccented spelling as an alternative.
- **The question must not contain the answer.** "Which crisis of 1962 brought the world close to nuclear war over missiles in Cuba?" gives away `Cuban Missile Crisis`; ask for it by description and move the telling detail into `hint` or `explanation`.
- **Names from other scripts or languages** have several correct spellings. List the common transliterations and the local form: `Gorbachev`, `Gorbatchev`, `Gorbatschow`; `Kyiv`, `Kiev`.
- Do not ask for answers that are long, multi-part or open to paraphrase. That is what `open` is for.
- Do not accept answers that are wrong just to be lenient. Alternatives must all be *correct*. Do not list misspellings either: the app can forgive a typo itself ([spec 9.3](README.md#93-matching)), except on `caseSensitive` items and in numbers.

### 4.5 Cloze

- Write a natural sentence and blank out the **key word**, not a function word or something guessable from grammar alone.
- **Leave enough context to identify every gap.** All gaps of an item are blank at the same time, so never blank out the subject together with its facts: `The {{Berlin Wall}} was built in {{1961}} and fell in {{1989}}.` leaves "The … was built in … and fell in …". Keep the subject: `The Berlin Wall was built in {{1961::year}} and fell in {{1989::year}}.`
- Add a hint in the gap when several words would fit, and to say what kind of answer is expected: `{{Versailles::city near Paris}}`, `{{1919::year}}`.
- Alternatives inside a gap are separated by `|`: `{{colour|color}}`. The first is shown as the canonical answer.
- Do not wrap gaps in bold or italics, do not put a gap across a line break, and avoid more than three gaps in one item.
- To write a literal `{{`, `|`, `:` or `\` in a gap, escape it with a backslash, and remember that a backslash is doubled in JSON: `"{{a\\|b}}"`. Better: rephrase.

### 4.6 True or false

- Make the statement **self-contained and unambiguously true or false**. Avoid "always", "never" and "usually".
- Prefer false statements that reflect real misconceptions, and explain what is actually true in `explanation`.
- Do not mix many claims in one statement.

### 4.7 Order

- One clear criterion, named in the prompt ("by date, earliest first").
- Entries should be short and recognisable. **Do not put the dates or numbers that determine the order into the entries** if that gives the answer away; put them in `explanation`.
- Make sure the order is not disputed, and that no two entries share a date or rank.

### 4.8 Open questions

- Write the question the way an interviewer or examiner would ask it.
- `answer` is a compact **model answer**: a few sentences, not a lecture.
- `keyPoints` is a checklist of the **3 to 7 points** a good answer must hit, each one short and checkable ("Mentions that threads share memory"). The learner ticks the points they covered, so every point must be independent and observable.
- Use `hint` for a structure to follow ("Present, past, future").

### 4.9 Hints and explanations

- `hint` helps *before* answering: context, a first letter, a mnemonic. It must not give the answer away.
- `explanation` teaches *after* answering: why it is so, an example sentence, a memory aid, the correction of a misconception. One to three sentences.
- Both are rich text: `**bold**`, `*italic*`, `` `code` ``, newlines. No HTML, no links, no Markdown headings or lists.

### 4.10 Figures

Any item can show a drawing ([spec 8.4](README.md#84-figures)): `figure` with the question, `answerFigure` after answering. Both are inline SVG inside the pack.

```json item
{
  "id": "earth-orbit",
  "type": "text",
  "question": "About how many days does the Earth need to orbit the Sun once?",
  "answers": ["365", "365 days", "365.25"],
  "answerFigure": {
    "alt": "The Earth circling the Sun.",
    "svg": "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 200 120'><ellipse cx='100' cy='60' rx='85' ry='45' fill='none' stroke='currentColor' stroke-opacity='0.4' stroke-dasharray='4 5'/><circle cx='100' cy='60' r='14' fill='#F5A524'/><circle r='6' fill='#3E8EF7'><animateMotion dur='10s' repeatCount='indefinite' path='M185 60A85 45 0 1 1 15 60A85 45 0 1 1 185 60'/></circle></svg>"
  }
}
```

- **Use a figure when a drawing carries the information**: maps, anatomy, geometry, circuits, graphs, molecules, the steps of a process. Do not decorate.
- **A figure must not answer its own question.** Show the unlabelled drawing in `figure` and the labelled or animated one in `answerFigure`.
- **A pack with a figure is `"version": 2`.** Apps that only read version 1 do not import it and ask the user to update. Leave figures out of a pack that has to open everywhere, and keep `"version": 1` for a pack without them.
- **Light and dark.** The background is transparent. Draw lines and labels with `currentColor` and pick other colours with enough contrast on white and on near-black. Never draw a background rectangle in a fixed colour.
- **Always a `viewBox`**, no fixed size: the app scales the figure to the width of the screen (a phone: roughly 340 points wide, at most about 240 high), so wide or square formats work best and labels below about a twentieth of the viewBox height become hard to read.
- **Animation** with `<animate>`, `<animateTransform>`, `<animateMotion>`, `<set>` or CSS `@keyframes` in a `<style>` element. Loop it (`repeatCount='indefinite'`, `infinite`) and keep it slow: the learner looks at it for a few seconds. Nothing is interactive.
- **Self-contained and passive**: no `<script>`, `<foreignObject>`, `<a>`, `<image>`, no `on...` attributes, no web fonts, and `href` / `url()` only to a `#id` in the same figure. Write `&lt;` for a literal `<`. The app drops a figure that breaks a rule and keeps the item.
- **Single quotes** for SVG attributes, so that the JSON string needs no escaping. Export from drawing programs as plain or optimised SVG (SVGO), then check with `validate.py`.
- Write `alt`: one sentence that says what the figure shows, without giving the answer away when it is a `figure`.

### 4.11 Maps

Any item can show places on a world map ([spec 8.5](README.md#85-maps)): `map` with the question, `answerMap` after answering. Use it for an event that took place at a location, for the birth and death place of a person, for the source and the mouth of a river, for the route of a journey. You write only the coordinates; the app draws the map.

```json item
{
  "id": "waterloo-place",
  "type": "text",
  "question": "Near which village in present-day Belgium was Napoleon defeated in 1815?",
  "answers": ["Waterloo"],
  "answerMap": {
    "alt": "A map of western Europe with a pin at Waterloo, south of Brussels.",
    "pins": [{ "lat": 50.68, "lng": 4.41, "label": "Waterloo" }]
  }
}
```

- **`pins`**: 1 to 20 places, each with `lat` (-90 to 90, north positive) and `lng` (-180 to 180, east positive) as JSON numbers, and an optional short `label` (at most about 30 characters are shown). Two to four decimals are plenty. Coordinates are easy to get wrong (swapped, or a wrong sign for west and south): give only coordinates you are sure about and check the sign of each one.
- **`line: true`** connects the pins in array order and shows the direction: birth place then death place, source then mouth, the stops of a journey. Without `line` the pins stand alone.
- **The app frames the map** so that every pin is visible, with enough of the surrounding geography to recognise the region. A single pin shows a region about 40 degrees wide, far apart pins give a bigger view. You cannot and need not choose a zoom.
- **A map must not answer its own question.** For "Where did it happen?" put the pin into `answerMap`, so that the map appears after answering. Use `map` when the learner has to find out something else from the place ("Which sea lies here?").
- **Today's borders.** The map shows the borders of today's world. Use it to show *where*, not to ask about historical borders or empires.
- **One drawing per side.** An item has a `figure` or a `map`, and an `answerFigure` or an `answerMap`, not both on the same side. A figure with an `answerMap` is fine.
- **A pack with a map is `"version": 3`.** Apps that read an older version do not import it and ask the user to update. Keep `"version": 1` or `2` for a pack without maps.
- Write `alt`: one sentence that says what the map shows, without giving the answer away when it is a `map`.
- Reference coordinates to compare with: Paris 48.86, 2.35; New York 40.71, -74.01; Sydney -33.87, 151.21; Cape Town -33.92, 18.42; Tokyo 35.68, 139.65; Reykjavik 64.15, -21.94.

## 5. IDs

Every item needs an `id`, and the pack needs one. Learning progress is stored per item id, so ids are a promise to the learner.

- **Item ids**: lowercase slugs from the content: `capital-france`, `hola`, `ww1-armistice-year`. Characters `a-z 0-9 . _ -`, at most 64. They must be **unique within the pack**, and they must **never change** when you edit the pack later, unless the item's meaning changes.
- Do not use positions (`item-17`) unless you append new items only and never reorder or delete: inserting an item in the middle would otherwise shift all later ids.
- For vocabulary, use the headword (`el-agua`). If two items would get the same id, add a disambiguator (`bank-river`, `bank-money`).
- **Pack id**: globally unique and stable. A reverse domain you control (`com.example.spanish-basics`) or a UUID. If you have neither, use `pack.<topic-slug>.<6 random hex characters>`, for example `pack.roman-emperors.7f3a9c`. Never change it for the same pack. A new id is a new pack.
- To update a pack: keep the pack id, keep the item ids, add or change items, increase `revision` by one and update `updated`. The app keeps the learner's progress for items whose id is unchanged.

## 6. Language-learning packs

- Set `"language"` to the language being learned (`"es"`), and write prompts and explanations in the learner's language (usually the explanations and the English side are English).
- Typical card: `front` is the foreign term with its article (`el agua`), `back` is the meaning (`water`). Set `study.reversible: true` so the learner is asked in both directions.
- List the variants a learner might type in `alternatives` (for the meaning side) and `frontAlternatives` (for the foreign side, for example the noun without article: `agua`).
- Accents: set `study.ignoreDiacritics: true` for beginner packs so `adios` matches `adiós`. Leave it off when accents distinguish words you teach.
- Include gender, plural forms or register notes in `explanation`.
- Group by theme (greetings, food, travel), 10 to 20 words per section. 8 to 10 new items per session is a good pace (`study.newPerSession`).
- Use `cloze` for useful phrases, with the target word as the gap; use `choice` for false friends with crafted distractors.
- Do not invent words or translations. If a term has regional variants (Spain versus Latin America), say which one the card teaches, in an `explanation`.

## 7. Size and structure

- **Items per pack**: 20 to 500 is typical. More than a few hundred: split by topic into several packs. Hard limits: 5,000 items and 5 MB.
- **Sections**: use them from about 20 items on. Aim for 5 to 25 items per section, titles that say what is inside ("Numbers 1-10", "The Cold War"). Either assign every item to a section or none.
- **Order of items** is the order in which new items are introduced (unless `study.order` is `"random"`). Put prerequisites first and keep the items of a section together. Interview-style packs with independent questions often use `"order": "random"`.
- **`study`**: `newPerSession` 5 to 10 for hard material, up to 15 for easy vocabulary. Restrict `exercises.card` (for example `["choice", "type"]`) when flipping would be pointless.
- **Metadata**: `title` and `description` should say what is in the pack and for whom. Add `language`, `tags`, a single-emoji `icon` and a `#RRGGBB` `color` if you like. Fill in `author`, `license` (an SPDX id such as `CC-BY-4.0`) and `source` when you redistribute someone else's material, and respect that material's license.

## 8. Common mistakes

| Mistake | Why it is a problem | Fix |
|---|---|---|
| Indices instead of flags for choice (`"answer": 2`) | Format has no indices; off-by-one errors | `{ "text": "...", "correct": true }` on the option |
| No option with `"correct": true` | Item is invalid and skipped | Mark at least one option |
| `"correct": "true"` (a string) | Treated as false | Use the JSON boolean `true` |
| `"answer": "true"` on a `truefalse` item | Item is invalid | `"answer": true` |
| `cloze` without `{{ }}`, or `{{ }}` with nothing inside | Item is invalid | At least one gap with an answer |
| A single `{ }` or `[answer]` for gaps | Not a gap | Double curly braces: `{{answer}}` |
| Long, multi-part `back` or `answers` | Cannot be typed or graded | One short fact per item; details into `explanation` |
| Duplicate item ids | Later duplicates are skipped | Unique, slug-like ids |
| Ids with spaces, umlauts or slashes | Not allowed | `a-z 0-9 . _ -` only |
| Changing ids on every regeneration | Learner loses all progress | Derive ids from content and keep them |
| Answers only in one spelling | Learners are marked wrong | List variants in `answers`/`alternatives` |
| Putting the answer into the question or `hint` | No learning effect | Hint points the way, never gives the answer |
| A cloze whose gaps remove the subject | Nothing left to recall from | Keep the subject, blank the facts, add gap hints |
| Distractors that are silly, or all from a different category | Right answer is obvious | Plausible, same category |
| Correct option noticeably longer than the others | Gives it away | Similar length and style |
| Facts you are not sure of | The learner memorises errors | Leave out or mark for review |
| HTML tags, Markdown headings, bullet lists, links | Not supported, shown literally | Plain text; `**bold**`, `*italic*`, `` `code` `` only |
| Stray `*` or `` ` `` in text | May turn into styling | Escape with `\\*` in JSON, or rephrase |
| Markdown fences or explanations inside the file | Not valid JSON | Output the JSON only |
| Learner progress, scores or dates in items | Not part of the format | Content only |
| A figure with `<script>`, links, external images or fonts | The figure is dropped | Self-contained SVG, see [4.10](#410-figures) |
| A figure that only works on a white background | Unreadable in dark mode | `currentColor` and a transparent background |
| Latitude and longitude swapped, or west and south written as positive | The pin lands in the wrong place (or is dropped when `lat` is above 90) | `lat` first, north and east positive, see [4.11](#411-maps) |
| A `map` that shows the answer, or a `figure` together with a `map` | Gives the answer away; the map of that side is ignored | The labelled map goes into `answerMap`, see [4.11](#411-maps) |
| Everything as `choice` or everything as `card` | Dull and not matched to the material | Use the type that fits ([section 3](#3-choosing-item-types)) |
| `sections` declared, `section` ids misspelled | Items end up ungrouped | Section ids must match exactly |

## 9. JSON pitfalls

- Use **double quotes** for all keys and strings. No single quotes, no comments, **no trailing commas**.
- Escape a double quote inside a string as `\"`, a backslash as `\\`, a newline as `\n`. A literal tab or newline inside a string is invalid JSON.
- Use **straight** quotes `"` for JSON syntax. Typographic quotes (`“ ”`) are fine *inside* texts, but never as JSON delimiters.
- Booleans are `true`/`false` (no quotes), numbers are numbers (`"version": 1`).
- The file is UTF-8. Write real characters (`é`, `ü`, `ñ`, `日本語`, emoji), not HTML entities (`&eacute;`).
- Property names are case-sensitive (`keyPoints`, `caseSensitive`, `truefalse`).
- Do not wrap the JSON in prose. If you answer in a chat, put the JSON into **one** code block and say nothing inside it.

## 10. Checking your work

Self-check before delivering:

- [ ] Top level has `format` = `"jackdaw-pack"`, `version` = `1` (`2` if an item has a figure, `3` if an item has a map), `id`, `title`, `items` (non-empty).
- [ ] Every item has `id` (unique, `a-z 0-9 . _ -`) and a `type` from the seven.
- [ ] `card`: `front` and `back`. `choice`: `question` and 2 or more `options`, at least one `"correct": true`. `text`: `question` and non-empty `answers`. `cloze`: `text` with at least one `{{gap}}`. `truefalse`: `statement` and boolean `answer`. `order`: `prompt` and `sequence` of 2 or more distinct entries in the **correct** order. `open`: `question`.
- [ ] Every `section` value matches a declared section id.
- [ ] No HTML or Markdown beyond `**bold**`, `*italic*`, `` `code` ``; no unescaped stray `*` or backtick.
- [ ] Every fact is correct. Every typed answer lists its reasonable variants.
- [ ] The JSON parses (matching braces and brackets, commas between, none after the last element).

With a code runner, validate with [`validate.py`](validate.py) (needs `pip install jsonschema`, use a throwaway virtualenv):

```
python3 validate.py my-pack.jackdaw
```

It checks the [JSON Schema](jackdaw-pack.schema.json) and the rules a schema cannot (unique ids, section references, at least one correct option, gaps in cloze texts) and prints warnings for suspicious content. When something fails, fix it and run again; pasting the validator's output back to a chatbot is a quick way to repair a pack.

## 11. Notes for tool authors

- **Converting from other formats** (CSV, Anki, Quizlet, spreadsheets, Markdown notes): map a two-column list to `card` items; a question column plus several answer columns to `choice` or `text`; Anki "Cloze" notes (`{{c1::answer}}`) to `cloze` (one item per cloze number, written `{{answer}}`); question plus bullet answer to `open` with `keyPoints`. Convert HTML to the inline markup subset or strip it.
- **Ids must be stable between runs.** Prefer a stable key from the source (row key, note id). Otherwise derive a slug from the content (lowercase, transliterate to ASCII, replace everything outside `a-z 0-9` by `-`, trim, at most 40 characters) and append `-2`, `-3` for duplicates. If the slug is derived from text that may be edited, remember that editing the text then changes the id and resets the learner's progress. Never use random ids that change per export.
- **Updating**: keep the pack `id` and item ids; write the same content in the same order; increase `revision`; set `updated`.
- **Output**: UTF-8 without BOM, LF newlines, two-space indentation is conventional. Key order is irrelevant. Do not write `null` for absent properties, omit them. Write `.jackdaw` as the file extension.
- **Round-tripping**: put your own data into `x-` properties (`x-acme-note-id`). Readers ignore them, and the format will never use the prefix itself.
- **Do not store progress** in packs. Export content only.
- **Validate before you ship**, with the schema and [`validate.py`](validate.py), and keep to the size guidance (5,000 items, 5 MB).
- **Reading packs**: follow the reader rules in [`README.md`](README.md#11-what-a-reader-does-with-invalid-input): ignore unknown properties, skip items of unknown `type`, tolerate and report invalid items, reject unsupported `version`s. Verify your answer matching and markup parsing against [`test-vectors.json`](test-vectors.json).
- **Generating packs with a language model in a pipeline**: put the compact format description of the prompt below into the system prompt, ask for JSON only, parse it, run the validator, and feed the error messages back for one repair round. Treat all generated facts as unverified.

---

## 12. Copy-paste prompt for any chatbot

Paste the block below into any chatbot, replace the parts in `[square brackets]`, and send it. The block is the file [`prompt.txt`](prompt.txt) verbatim; the apps offer the same text under "Create with AI". The prompt contains the whole format, so the chatbot needs no other context. Save the answer (the JSON) as `something.jackdaw` and import it into Jackdaw.

Tips: paste your own notes or a text after the task line if you want the pack to be based on them; ask for fewer items first to check quality; if the app reports errors, paste them back into the chat and ask for a corrected file.

````text
You are creating a learning pack for the app Jackdaw. Produce ONE JSON document in the "Jackdaw Pack" format described below. Output only that JSON, in a single ```json code block, with no text before or after it.

TASK
Create a Jackdaw pack about: [TOPIC, or paste your notes / source text here]
Language of the content: [e.g. English. For a language course: the language being learned, plus the language the explanations are written in]
Audience / level: [e.g. complete beginners, exam in June, software engineers preparing for interviews]
Number of items: about [e.g. 40]
Preferred item types: [e.g. mostly cards and a few multiple-choice questions, or: choose what fits best]
Use only facts you are sure about. If you are unsure about something, leave it out.

FORMAT (Jackdaw Pack)
The file is one JSON object.
Top level:
- "format": "jackdaw-pack"                      (required, always this string)
- "version": 1                                  (required; the number 1, or 2 if any item has a "figure" or "answerFigure", or 3 if any item has a "map" or "answerMap")
- "id": string                                  (required; only A-Z a-z 0-9 . _ - ; use "pack." + topic slug + "." + 6 random hex characters, e.g. "pack.roman-emperors.7f3a9c")
- "title": string                               (required)
- optional: "description" (string), "language" (BCP-47 tag of the subject, e.g. "es", "en", "de-CH"), "author" (string), "license" (e.g. "CC-BY-4.0"), "tags" (string array), "icon" (ONE emoji), "color" ("#RRGGBB"), "revision": 1, "created" and "updated" ("YYYY-MM-DD")
- "sections": optional array of {"id", "title", "description"?} that groups items into chapters or topics (ids like "greetings"). Use sections when there are more than about 20 items.
- "study": optional object: "order": "sequential" or "random" (default sequential); "newPerSession": integer (default 10); "reversible": boolean (default false; for cards, also ask back to front); "ignoreDiacritics": boolean (default false; true = typed "adios" matches "adiós"); "exercises": {"card": any non-empty subset of ["flip","choice","type"]}
- "items": array (required, non-empty)

Every item has: "id" (required; unique within the file; lowercase slug of a-z 0-9 . _ - , max 64 characters, e.g. "capital-of-france"; never reuse an id), "type" (required, one of the types below). Optional on every item: "section" (an id from "sections"), "tags" (string array), "hint" (a nudge shown BEFORE answering; never the answer), "explanation" (shown AFTER answering: why it is so, an example, a memory aid).

The item types:
1. "card": two-sided flashcard, for pairs and vocabulary. Required "front", "back" (strings; keep "back" short, it can be typed). Optional: "reversible" (bool: also ask back to front), "alternatives" (array of other accepted typed answers for the back), "frontAlternatives" (accepted typed answers for the front when asked in reverse), "distractors" (array of explicit WRONG options for multiple choice; omit unless needed, the app draws wrong options from other cards), "exercises" (subset of ["flip","choice","type"]).
2. "choice": multiple choice, for recognition with plausible wrong options (3-5 options of similar length and style, no "all of the above"). Required "question" (string) and "options": array of 2-10 objects {"text": string, "correct": true (only on correct options), "explanation": string (optional)}. At least one option must have "correct": true. Several correct options make it multi-select (say "Select all that apply" in the question). Optional "shuffle" (bool, default true). Never use indices or letters to mark the answer.
3. "text": short typed answer, for one short fact. Required "question", "answers" (array of at least 1 accepted answer strings, first is canonical; list spelling variants, abbreviations, digits and words). Optional "caseSensitive" (bool, default false).
4. "cloze": fill in the gaps, for the key words of a sentence. Required "text" containing at least one gap written {{answer}}. Optional hint inside the gap: {{answer::hint}}. Alternatives inside a gap are separated with |: {{colour|color}}. Example: "The Berlin Wall fell in {{1989::year}}." Use 1-3 gaps per item.
5. "truefalse": true or false, only for common misconceptions. Required "statement" (string), "answer" (boolean true or false). Add an "explanation".
6. "order": put things in order, for sequences with one correct order. Required "prompt" (what to order and by which criterion) and "sequence": array of 2-20 DISTINCT strings in the CORRECT order (the app shuffles them). Do not put years or ranks that reveal the order into the entries.
7. "open": open question, self-graded, for explanations and interview questions. Required "question". Optional "answer" (compact model answer) and "keyPoints" (array of 3-7 short strings: the checklist a good answer covers).

FIGURES (drawings; add them wherever seeing it helps: a map, a diagram, a shape, a formula, a process, a timeline)
Any item may have "figure" (shown with the question) and "answerFigure" (shown after answering, replaces "figure"). Each is an object {"alt": "what the drawing shows", "svg": "<svg ...>...</svg>"}.
- "svg" is one complete, self-contained SVG document inside the JSON string. Use single quotes for all SVG attributes. The root needs xmlns='http://www.w3.org/2000/svg' and a viewBox, e.g. viewBox='0 0 240 160'. Keep it small and simple (a few hundred characters up to a few thousand).
- Allowed: shapes, paths, text, gradients, clip paths, filters, a <style> element, and animation with <animate>, <animateTransform>, <animateMotion>, <set> or CSS @keyframes. NOT allowed: <script>, <foreignObject>, <a>, <image>, event attributes (onclick, onload, ...), external fonts, and any href or url() that is not a '#id' inside the same SVG. Write a literal < in a label as &lt;.
- The figure is shown on a light or a dark background: use stroke='currentColor' / fill='currentColor' for lines and labels, plus colours that are readable on both. Font: font-family='sans-serif'.
- The figure must not show the answer of its own question; put the labelled version into "answerFigure". A pack with a figure must say "version": 2.

MAPS (places on a world map; for events at a location, the birth and death place of a person, the source and mouth of a river, a journey)
Any item may have "map" (shown with the question) and "answerMap" (shown after answering, replaces "map"). Each is an object {"alt": "what the map shows", "pins": [{"lat": 48.86, "lng": 2.35, "label": "Paris"}], "line": true}.
- "pins": 1 to 20 places, each with "lat" (latitude, -90 to 90, north is positive) and "lng" (longitude, -180 to 180, east is positive) as JSON numbers with 2 to 4 decimals, and an optional "label" (short name, at most about 30 characters). Only give coordinates you are sure about.
- "line": optional boolean. true connects the pins in array order with an arrow (birth to death, source to mouth, a route). Leave it out otherwise.
- The app draws the world map itself and shows every pin: draw nothing. The map shows today's borders, so do not ask questions that depend on historical borders.
- An item has either a "figure" or a "map" on the same side, not both. Put the labelled map into "answerMap" when it would give the answer away (not "map" for "Where did the Battle of Waterloo take place?"). A pack with a map must say "version": 3.

TEXT RULES
- Text is plain text. The only markup is **bold**, *italic*, `code` and \n for a new line. No HTML, no links, no Markdown headings or lists. Avoid stray * or ` characters.
- "alternatives", "frontAlternatives" and "answers" are literal text without markup.
- Typed answers are compared ignoring upper/lower case, extra spaces, trailing . , ; : ! ? and a leading ¿ or ¡. Accents, spelling variants, synonyms and number words are NOT matched automatically: list them as alternatives (or set study.ignoreDiacritics to true).
- JSON rules: double quotes only, no comments, no trailing commas, escape a double quote inside a string as \" and a backslash as \\. Write real UTF-8 characters, not HTML entities.

QUALITY RULES
- One fact per item (atomic); short prompts; short answers. Put background into "explanation", not into the answer.
- Choose the item type that fits each fact (the list of item types says what each one is for). Mix types sensibly, do not force variety. Put prerequisites first. Keep items of a section together.
- Give every item a short, meaningful, unique slug id. Do not use spaces, umlauts or slashes in ids.
- The question must not contain its own answer or its distinctive words (not "Which crisis over missiles in Cuba ...?" for "Cuban Missile Crisis"). Ask for it by description.
- A cloze must stay answerable: leave enough of the sentence to identify every gap, and never blank out the subject together with its facts (not "The {{Berlin Wall}} was built in {{1961}}").
- Names from other scripts or languages: list the common transliterations and local spellings as accepted answers (Gorbachev, Gorbatchev, Gorbatschow).
- Add "hint" only when it truly helps; add "explanation" where it teaches something.

EXAMPLE of the expected output (content is only an illustration):
```json pack
{
  "format": "jackdaw-pack",
  "version": 2,
  "id": "pack.solar-system.4f7a2c",
  "title": "The Solar System",
  "description": "Basic facts about the Sun and the planets.",
  "language": "en",
  "icon": "🪐",
  "sections": [{ "id": "basics", "title": "Basics" }],
  "study": { "newPerSession": 5 },
  "items": [
    { "id": "largest-planet", "type": "card", "section": "basics", "front": "Largest planet in the Solar System", "back": "Jupiter", "distractors": ["Saturn", "Neptune", "Earth"],
      "figure": { "alt": "The eight planets in a row, drawn to scale, the largest one filled", "svg": "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 230 56'><g fill='none' stroke='currentColor' stroke-width='2'><circle cx='12' cy='28' r='3'/><circle cx='26' cy='28' r='5'/><circle cx='44' cy='28' r='5'/><circle cx='60' cy='28' r='4'/><circle cx='148' cy='28' r='14'/><circle cx='184' cy='28' r='8'/><circle cx='212' cy='28' r='8'/></g><circle cx='96' cy='28' r='24' fill='#e8a33d'/></svg>" } },
    { "id": "red-planet", "type": "choice", "section": "basics", "question": "Which planet is called the Red Planet?",
      "options": [{ "text": "Mars", "correct": true }, { "text": "Venus" }, { "text": "Mercury", "explanation": "Mercury is grey and closest to the Sun." }] },
    { "id": "planet-count", "type": "text", "section": "basics", "question": "How many planets does the Solar System have?", "answers": ["8", "eight"] },
    { "id": "sun-mass", "type": "cloze", "section": "basics", "text": "The {{Sun::our star}} holds more than 99% of the mass of the Solar System." },
    { "id": "pluto-planet", "type": "truefalse", "section": "basics", "statement": "Pluto is classified as a planet.", "answer": false, "explanation": "Since 2006 Pluto is a dwarf planet." },
    { "id": "planet-order", "type": "order", "section": "basics", "prompt": "Order these planets by distance from the Sun, closest first.", "sequence": ["Mercury", "Venus", "Earth", "Mars"] },
    { "id": "why-seasons", "type": "open", "section": "basics", "question": "Why does Earth have seasons?", "answer": "Earth's axis is tilted by about 23.4 degrees. During the year each hemisphere leans towards or away from the Sun, which changes the daylight hours and the angle of the sunlight.",
      "keyPoints": ["Caused by the tilt of Earth's axis", "Not caused by the distance to the Sun", "Changes day length and the angle of sunlight"] }
  ]
}
```

Before you answer, check silently: valid JSON; every id unique; every choice has at least one "correct": true; every cloze has at least one {{gap}}; every "section" matches a declared section id; all facts are correct. Then output only the JSON code block.
````

### Variants of the prompt

- **Pack from your own notes:** replace the topic line by `Create a Jackdaw pack from the following notes. Use only information from the notes: [paste notes]`.
- **Language course:** `Create a Jackdaw pack to learn Italian vocabulary for travel (restaurant, hotel, transport), explained in English, about 60 words, reversible cards, ignoreDiacritics true.`
- **Exam preparation:** `Create a Jackdaw pack with exam questions on [subject, chapter]: mostly choice and text items, a few order and cloze items, with explanations.`
- **Interview preparation:** `Create a Jackdaw pack for [role] interviews: mostly open questions with keyPoints, a few cards for key terms.`
- **Update an existing pack:** paste the existing JSON and ask `Add 20 more items about [topic] in the same style. Keep every existing id and pack id unchanged, add new items at the end, increase revision by 1.`
