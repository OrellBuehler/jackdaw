#!/usr/bin/env python3
"""Validate Jackdaw packs: JSON Schema plus the semantic rules a schema cannot express.

Usage:
    python3 validate.py [--strict] [--no-docs] [--no-vectors] [--write-index] [FILE ...]

With no FILE it checks every examples/*.jackdaw, the packs and items embedded in
README.md / AUTHORING.md (code fences marked "json pack" and "json item"), the frozen
packs in compat/ and the reference implementation against test-vectors.json.

Requires the Python package "jsonschema" (4.x). The module also contains a small
reference implementation of the rules the specification defines (markup, cloze
gaps, answer normalisation); the Kotlin parser must behave identically on
test-vectors.json.
"""

import argparse
import json
import re
import sys
import unicodedata
from pathlib import Path

try:
    from jsonschema import Draft202012Validator
except ImportError:
    sys.exit("validate.py needs the 'jsonschema' package (pip install jsonschema), "
             "ideally inside a throwaway virtualenv.")

HERE = Path(__file__).resolve().parent
SCHEMA_PATH = HERE / "jackdaw-pack.schema.json"
VECTORS_PATH = HERE / "test-vectors.json"

MAX_ITEMS = 5000
MAX_BYTES = 5 * 1024 * 1024


# --------------------------------------------------------------------------
# Reference implementation of the rules defined in README.md
# --------------------------------------------------------------------------

ESCAPABLE = "\\*`{}|:"

# Whitespace for markup rules = the White_Space set of the specification (section "Answer matching")
_WS_CHARS = ("\t\n\v\f\r \u0085\u00a0\u1680" + "".join(chr(c) for c in range(0x2000, 0x200B))
             + "\u2028\u2029\u202f\u205f\u3000")
_S = re.escape(_WS_CHARS)
_CODE = re.compile(r"`([^`\n]+)`")
_BOLD = re.compile(r"\*\*([^%s](?:[^\n]*?[^%s])??)\*\*" % (_S, _S))
_ITALIC = re.compile(r"\*([^%s*](?:[^*\n]*[^%s*])?)\*" % (_S, _S))


def unescape(s):
    """Resolve backslash escapes (\\ \\* \\` \\{ \\} \\| \\:); other backslashes stay."""
    out = []
    i = 0
    while i < len(s):
        if s[i] == "\\" and i + 1 < len(s) and s[i + 1] in ESCAPABLE:
            out.append(s[i + 1])
            i += 2
        else:
            out.append(s[i])
            i += 1
    return "".join(out)


def parse_markup(s, warnings=None):
    """Rich text -> list of (style, text); style is '' (plain), 'b', 'i' or 'c'."""
    s = s.replace("\r\n", "\n").replace("\r", "\n")
    spans = []
    buf = []

    def flush():
        if buf:
            spans.append(("", "".join(buf)))
            buf.clear()

    i = 0
    while i < len(s):
        c = s[i]
        if c == "\\" and i + 1 < len(s) and s[i + 1] in ESCAPABLE:
            buf.append(s[i + 1])
            i += 2
            continue
        if c == "`":
            m = _CODE.match(s, i)
            if m:
                flush()
                spans.append(("c", m.group(1)))
                i = m.end()
                continue
        elif c == "*":
            m = _BOLD.match(s, i) if s.startswith("**", i) else None
            if m:
                flush()
                spans.append(("b", unescape(m.group(1))))
                i = m.end()
                continue
            m = _ITALIC.match(s, i)
            if m:
                flush()
                spans.append(("i", unescape(m.group(1))))
                i = m.end()
                continue
        if c in "*`" and warnings is not None:
            warnings.append("unmatched markup marker %r" % c)
        buf.append(c)
        i += 1
    flush()
    return spans


def plain_text(s):
    """Plain-text rendering of rich text (what the learner sees, without styling)."""
    return "".join(t for _, t in parse_markup(s))


def _split_unescaped(s, sep):
    """Split on the first/each unescaped occurrence of sep (escape pairs are skipped)."""
    parts = []
    cur = 0
    i = 0
    while i < len(s):
        if s[i] == "\\" and i + 1 < len(s) and s[i + 1] in ESCAPABLE:
            i += 2
        elif s.startswith(sep, i):
            parts.append(s[cur:i])
            i += len(sep)
            cur = i
        else:
            i += 1
    parts.append(s[cur:])
    return parts


def parse_gap(raw):
    """Parse the inside of {{...}} -> {'answers': [...], 'hint': str|None}, or None if invalid."""
    pieces = _split_unescaped(raw, "::")
    answer_raw = pieces[0]
    hint_raw = "::".join(pieces[1:]) if len(pieces) > 1 else ""
    answers = [unescape(a).strip() for a in _split_unescaped(answer_raw, "|")]
    answers = [a for a in answers if a]
    if not answers:
        return None
    hint = unescape(hint_raw).strip() or None
    return {"answers": answers, "hint": hint}


def parse_cloze(text):
    """Cloze text -> list of ('t', raw_rich_text) / ('g', gap) segments."""
    segs = []
    buf = []
    n = len(text)
    i = 0

    def flush():
        if buf:
            segs.append(("t", "".join(buf)))
            buf.clear()

    while i < n:
        c = text[i]
        if c == "\\" and i + 1 < n and text[i + 1] in ESCAPABLE:
            buf.append(text[i:i + 2])
            i += 2
            continue
        if text.startswith("{{", i):
            j = i + 2
            end = None
            while j < n:
                if text[j] == "\n":
                    break
                if text[j] == "\\" and j + 1 < n and text[j + 1] in ESCAPABLE:
                    j += 2
                    continue
                if text.startswith("}}", j):
                    end = j
                    break
                j += 1
            if end is not None:
                gap = parse_gap(text[i + 2:end])
                if gap is not None:
                    flush()
                    segs.append(("g", gap))
                    i = end + 2
                    continue
        buf.append(c)
        i += 1
    flush()
    return segs


_QUOTES = {
    "\u2018": "'", "\u2019": "'", "\u201b": "'", "\u02bc": "'", "\u2032": "'",
    "\u201c": '"', "\u201d": '"', "\u201e": '"', "\u201f": '"', "\u2033": '"',
    "\u2010": "-", "\u2011": "-", "\u2012": "-", "\u2013": "-", "\u2212": "-",
}
_QUOTE_TABLE = str.maketrans(_QUOTES)
_WS = re.compile("[" + re.escape(_WS_CHARS) + "]+")
_LEADING = "\u00bf\u00a1" + _WS_CHARS  # inverted ? and !
_TRAILING = ".,;:!?\u2026\u3002\u3001\uff0e\uff0c\uff1b\uff1a\uff01\uff1f" + _WS_CHARS


def normalize(s, case_sensitive=False, ignore_diacritics=False):
    """Normal form used to compare typed answers with accepted answers."""
    s = s.translate(_QUOTE_TABLE)
    if ignore_diacritics:
        s = "".join(ch for ch in unicodedata.normalize("NFD", s) if not "\u0300" <= ch <= "\u036f")
    if not case_sensitive:
        s = s.lower()
    s = unicodedata.normalize("NFC", s)
    s = _WS.sub(" ", s).strip(" ")
    t = s.lstrip(_LEADING).rstrip(_TRAILING)
    return t if t else s


def matches(typed, expected, case_sensitive=False, ignore_diacritics=False):
    return normalize(typed, case_sensitive, ignore_diacritics) == normalize(expected, case_sensitive, ignore_diacritics)


# --------------------------------------------------------------------------
# Validation
# --------------------------------------------------------------------------

# Figures (README.md section 8.4). The checks are lexical on purpose: every reader can apply
# them without an XML parser and gets the same result.

FIGURE_MAX_LENGTH = 200000
FIGURE_ELEMENTS = frozenset("""
svg g defs symbol use title desc path rect circle ellipse line polyline polygon text tspan textPath
linearGradient radialGradient stop pattern clipPath mask marker style animate animateTransform
animateMotion mpath set filter feBlend feColorMatrix feComponentTransfer feComposite feConvolveMatrix
feDiffuseLighting feDisplacementMap feDistantLight feDropShadow feFlood feFuncA feFuncB feFuncG feFuncR
feGaussianBlur feMerge feMergeNode feMorphology feOffset fePointLight feSpecularLighting feSpotLight
feTile feTurbulence
""".split())
_FIG_SPACE = " \t\n\r\f"
_FIG_TAG = re.compile(r"<(?!!--|!\[CDATA\[)(/?)([A-Za-z0-9:_.-]*)")
_FIG_VIEWBOX = re.compile(r"[ \t\n\r\f]viewBox[ \t\n\r\f]*=[ \t\n\r\f]*(?:\"([^\"]*)\"|'([^']*)')")
_FIG_HANDLER = re.compile(r"[ \t\n\r\f\"'/]on[a-z]+[ \t\n\r\f]*=")
_FIG_HREF = re.compile(r"href[ \t\n\r\f]*=[ \t\n\r\f]*")
_FIG_HREF_LOCAL = re.compile(r"[\"'][ \t\n\r\f]*#")
_FIG_URL = re.compile(r"url\([ \t\n\r\f]*")
_FIG_URL_LOCAL = re.compile(r"[\"']?[ \t\n\r\f]*#")
_FIG_NUMBER = re.compile(r"[0-9+\-.eE]+")


def check_figure(text):
    """Returns (problem, aspect_ratio): problem is None for a figure a reader accepts."""
    if len(text.encode("utf-16-le")) // 2 > FIGURE_MAX_LENGTH:
        return "is longer than %d characters" % FIGURE_MAX_LENGTH, None
    svg = text.strip(_FIG_SPACE)
    if svg.startswith("<?xml"):
        end = svg.find("?>")
        if end < 0:
            return "has an unterminated XML declaration", None
        svg = svg[end + 2:].strip(_FIG_SPACE)
    lower = svg.lower()
    for bad in ("<?", "<!doctype", "<!entity", "javascript:", "@import"):
        if bad in lower:
            return "must not contain %r" % bad, None
    root = None
    for m in _FIG_TAG.finditer(svg):
        if m.group(2) not in FIGURE_ELEMENTS:
            return "element <%s> is not allowed (write a literal '<' as &lt;)" % m.group(2), None
        if root is None:
            close = svg.find(">", m.end())
            if m.group(1) or m.group(2) != "svg" or close < 0:
                return "must start with an <svg> element", None
            root = svg[m.end():close]
    if root is None:
        return "must start with an <svg> element", None
    box = _FIG_VIEWBOX.search(root)
    if not box:
        return "the <svg> element needs a viewBox", None
    parts = [p for p in re.split(r"[ \t\n\r\f,]", box.group(1) if box.group(1) is not None else box.group(2)) if p]
    try:
        if len(parts) != 4 or not all(_FIG_NUMBER.fullmatch(p) for p in parts):
            raise ValueError
        numbers = [float(p) for p in parts]
    except ValueError:
        return "viewBox must be four numbers", None
    if any(n != n or n in (float("inf"), float("-inf")) for n in numbers) or numbers[2] <= 0 or numbers[3] <= 0:
        return "viewBox needs a positive width and height", None
    if _FIG_HANDLER.search(lower):
        return "event handler attributes (on...) are not allowed", None
    if any(not _FIG_HREF_LOCAL.match(lower, m.end()) for m in _FIG_HREF.finditer(lower)):
        return "href may only point to an element of the figure (#id)", None
    if any(not _FIG_URL_LOCAL.match(lower, m.end()) for m in _FIG_URL.finditer(lower)):
        return "url() may only point to an element of the figure (#id)", None
    return None, numbers[2] / numbers[3]


def figure_is_well_formed(text):
    import xml.etree.ElementTree as ET
    try:
        ET.fromstring(text.strip(_FIG_SPACE).encode("utf-8"))
        return True
    except ET.ParseError:
        return False


class Report:
    def __init__(self, name):
        self.name = name
        self.errors = []
        self.warnings = []

    def error(self, where, msg):
        self.errors.append("%s: %s" % (where, msg))

    def warn(self, where, msg):
        self.warnings.append("%s: %s" % (where, msg))


def _no_duplicate_keys(pairs):
    seen = {}
    for k, v in pairs:
        if k in seen:
            raise ValueError("duplicate key %r in a JSON object" % k)
        seen[k] = v
    return seen


def load_pack(text):
    if text.startswith("\ufeff"):
        text = text[1:]
    return json.loads(text, object_pairs_hook=_no_duplicate_keys)


def _path(err):
    return "/" + "/".join(str(p) for p in err.absolute_path)


def _schema_errors(validator, pack, type_index):
    """Yield (path, message), replacing oneOf noise on items by the errors of the item's own type."""
    for err in validator.iter_errors(pack):
        if err.validator == "oneOf" and isinstance(err.instance, dict):
            branch = type_index.get(err.instance.get("type")) if isinstance(err.instance.get("type"), str) else None
            if branch is None:
                continue  # unknown or missing 'type': reported separately by the enum/required checks
            sub = [e for e in err.context if e.schema_path and e.schema_path[0] == branch]
            for e in sub:
                full = list(err.absolute_path) + list(e.relative_path)
                yield "/" + "/".join(str(p) for p in full), e.message
            if sub:
                continue
        yield _path(err), err.message


def check_semantics(pack, rep, size_bytes):
    items = pack.get("items") if isinstance(pack.get("items"), list) else []
    sections = pack.get("sections") if isinstance(pack.get("sections"), list) else []
    study = pack.get("study") if isinstance(pack.get("study"), dict) else {}

    if len(items) > MAX_ITEMS:
        rep.warn("/items", "%d items exceed the recommended maximum of %d" % (len(items), MAX_ITEMS))
    if size_bytes > MAX_BYTES:
        rep.warn("/", "file is %.1f MB, recommended maximum is 5 MB" % (size_bytes / 1048576))

    section_ids = set()
    for i, sec in enumerate(sections):
        sid = sec.get("id") if isinstance(sec, dict) else None
        if sid in section_ids:
            rep.error("/sections/%d" % i, "duplicate section id %r" % sid)
        section_ids.add(sid)

    if pack.get("created") and pack.get("updated") and str(pack["updated"])[:10] < str(pack["created"])[:10]:
        rep.warn("/updated", "'updated' is earlier than 'created'")

    icon = pack.get("icon")
    if isinstance(icon, str) and re.search(r"[A-Za-z0-9]", icon) and not re.fullmatch(r"[0-9#*]\ufe0f?\u20e3", icon):
        rep.warn("/icon", "icon should be a single emoji, got %r" % icon)

    seen_ids = {}
    used_sections = set()
    cards = [it for it in items if isinstance(it, dict) and it.get("type") == "card"]
    for i, it in enumerate(items):
        if not isinstance(it, dict):
            continue
        where = "/items/%d" % i
        iid = it.get("id")
        if isinstance(iid, str):
            where = "/items/%d (%s)" % (i, iid)
            if iid in seen_ids:
                rep.error(where, "duplicate item id (first used by item #%d)" % seen_ids[iid])
            else:
                seen_ids[iid] = i
        sec = it.get("section")
        if sec is not None:
            used_sections.add(sec)
            if sec not in section_ids:
                rep.error(where, "section %r is not declared in 'sections'" % sec)
        elif section_ids:
            rep.warn(where, "item has no 'section' although the pack declares sections")

        for field in ("hint", "explanation", "front", "back", "question", "statement", "prompt", "answer"):
            v = it.get(field)
            if isinstance(v, str):
                w = []
                parse_markup(v, w)
                for msg in w:
                    rep.warn("%s.%s" % (where, field), msg + " (escape it as \\* or \\` if literal)")

        for field in ("figure", "answerFigure"):
            fig = it.get(field)
            if isinstance(fig, dict) and isinstance(fig.get("svg"), str):
                problem, _ = check_figure(fig["svg"])
                if problem:
                    rep.error("%s.%s" % (where, field), "svg " + problem)
                elif not figure_is_well_formed(fig["svg"]):
                    rep.error("%s.%s" % (where, field), "svg is not well-formed XML")

        t = it.get("type")
        if t == "card":
            for field in ("back", "front"):
                if isinstance(it.get(field), str) and not normalize(plain_text(it[field])):
                    rep.error(where, "%s has no typeable text" % field)
            allowed = it.get("exercises") or (study.get("exercises") or {}).get("card")
            if allowed and set(allowed) == {"choice"} and len(cards) < 4 and not it.get("distractors"):
                rep.warn(where, "only 'choice' is allowed but the pack has fewer than 4 cards and no explicit distractors")
        elif t == "choice":
            opts = it.get("options")
            if isinstance(opts, list):
                correct = [o for o in opts if isinstance(o, dict) and o.get("correct") is True]
                if not correct:
                    rep.error(where, "no option has \"correct\": true")
                if opts and len(correct) == len(opts):
                    rep.warn(where, "all options are correct")
                texts = [normalize(plain_text(o["text"])) for o in opts if isinstance(o, dict) and isinstance(o.get("text"), str)]
                if len(set(texts)) != len(texts):
                    rep.warn(where, "two options have the same text")
                for o in opts:
                    if isinstance(o, dict) and isinstance(o.get("text"), str):
                        w = []
                        parse_markup(o["text"], w)
                        for msg in w:
                            rep.warn(where + ".options", msg)
        elif t == "text":
            ans = it.get("answers")
            if isinstance(ans, list):
                for a in ans:
                    if isinstance(a, str) and not normalize(a):
                        rep.error(where, "answer %r is empty after normalisation" % a)
        elif t == "cloze":
            text = it.get("text")
            if isinstance(text, str):
                segs = parse_cloze(text)
                gaps = [s for k, s in segs if k == "g"]
                if not gaps:
                    rep.error(where, "cloze text contains no valid {{gap}}")
                if len(gaps) > 6:
                    rep.warn(where, "%d gaps in one item; consider splitting it" % len(gaps))
                for g in gaps:
                    for a in g["answers"]:
                        if not normalize(a):
                            rep.error(where, "gap answer %r is empty after normalisation" % a)
                if re.search(r"\{\{\s*\}\}|\{\{\s*::", text):
                    rep.error(where, "empty gap")
                literal = "".join(s for k, s in segs if k == "t")
                if "{{" in re.sub(r"\\.", "", literal) or "}}" in re.sub(r"\\.", "", literal):
                    rep.warn(where, "stray '{{' or '}}' outside a valid gap (escape with \\{ and \\} if literal)")
                for k, s in segs:
                    if k == "t":
                        w = []
                        parse_markup(s, w)
                        for msg in w:
                            rep.warn(where + ".text", msg)
        elif t == "order":
            seq = it.get("sequence")
            if isinstance(seq, list):
                norm = [normalize(plain_text(s)) for s in seq if isinstance(s, str)]
                if len(set(norm)) != len(norm):
                    rep.error(where, "sequence entries must be distinct")
        elif t == "open":
            if not (it.get("answer") or it.get("keyPoints") or it.get("explanation")):
                rep.warn(where, "open item has no answer, keyPoints or explanation to reveal")

    for sid in section_ids:
        if sid not in used_sections:
            rep.warn("/sections", "section %r has no items" % sid)


def validate_text(name, text, validator, type_index):
    rep = Report(name)
    size = len(text.encode("utf-8"))
    try:
        pack = load_pack(text)
    except ValueError as e:
        rep.error("/", "not valid JSON: %s" % e)
        return rep, None
    if not isinstance(pack, dict):
        rep.error("/", "top level must be a JSON object")
        return rep, None
    for path, msg in sorted(set(_schema_errors(validator, pack, type_index))):
        rep.error(path, msg)
    check_semantics(pack, rep, size)
    return rep, pack


def extract_pack_blocks(md_path):
    """Return [(label, json_text)] for the code fences of a Markdown file whose info string is
    exactly 'json pack' (a complete pack) or 'json item' (a single item, wrapped into a pack)."""
    blocks = []
    lines = md_path.read_text(encoding="utf-8").splitlines()
    i = 0
    while i < len(lines):
        m = re.match(r"^(\s*)(`{3,})json (pack|item)\s*$", lines[i])
        if m:
            fence = m.group(2)
            start = i + 1
            j = start
            while j < len(lines) and not re.match(r"^\s*%s\s*$" % fence, lines[j]):
                j += 1
            body = "\n".join(lines[start:j])
            if m.group(3) == "item":
                body = ('{"format": "jackdaw-pack", "version": 1, "id": "snippet", "title": "snippet", "items": [%s]}' % body)
            blocks.append(("%s line %d (%s)" % (md_path.name, start, m.group(3)), body))
            i = j
        i += 1
    return blocks


# --------------------------------------------------------------------------
# Test vectors
# --------------------------------------------------------------------------

def run_vectors(path):
    errors = []
    data = json.loads(path.read_text(encoding="utf-8"))
    for n, v in enumerate(data.get("normalize", [])):
        got = normalize(v["input"], v.get("caseSensitive", False), v.get("ignoreDiacritics", False))
        if got != v["expected"]:
            errors.append("normalize[%d] %r: expected %r, got %r" % (n, v["input"], v["expected"], got))
    for n, v in enumerate(data.get("match", [])):
        got = matches(v["typed"], v["expected"], v.get("caseSensitive", False), v.get("ignoreDiacritics", False))
        if got != v["result"]:
            errors.append("match[%d] typed=%r expected=%r: wanted %r, got %r" % (n, v["typed"], v["expected"], v["result"], got))
    for n, v in enumerate(data.get("markup", [])):
        got = [list(s) for s in parse_markup(v["input"])]
        if got != v["spans"]:
            errors.append("markup[%d] %r: expected %r, got %r" % (n, v["input"], v["spans"], got))
        if "plain" in v and plain_text(v["input"]) != v["plain"]:
            errors.append("markup[%d] %r: plain expected %r, got %r" % (n, v["input"], v["plain"], plain_text(v["input"])))
    for n, v in enumerate(data.get("cloze", [])):
        got = [[k, s] for k, s in parse_cloze(v["input"])]
        if got != v["segments"]:
            errors.append("cloze[%d] %r: expected %r, got %r" % (n, v["input"], v["segments"], got))
    for n, v in enumerate(data.get("figure", [])):
        problem, ratio = check_figure(v["svg"])
        if (problem is None) != v["valid"]:
            errors.append("figure[%d] %r: expected valid=%r, got %r" % (n, v["svg"], v["valid"], problem))
        elif v["valid"] and abs(ratio - v["aspectRatio"]) > 1e-9:
            errors.append("figure[%d] %r: expected aspect ratio %r, got %r" % (n, v["svg"], v["aspectRatio"], ratio))
    return errors, sum(len(data.get(k, [])) for k in ("normalize", "match", "markup", "cloze", "figure"))


# --------------------------------------------------------------------------

def build_index(paths):
    """The catalogue of the example packs that the apps download (examples/index.json)."""
    packs = []
    for p in paths:
        pack = json.loads(p.read_text(encoding="utf-8-sig"))
        entry = {"id": pack["id"], "file": p.name, "title": pack["title"]}
        for key in ("description", "language", "icon", "color"):
            if key in pack:
                entry[key] = pack[key]
        entry["items"] = len(pack["items"])
        entry["revision"] = pack.get("revision", 0)
        packs.append(entry)
    index = {"format": "jackdaw-catalog", "version": 1, "packs": packs}
    return json.dumps(index, ensure_ascii=False, indent=2) + "\n"


def check_compat(validator, type_index):
    """The frozen packs of earlier revisions (compat/) must stay untouched and valid."""
    import hashlib
    folder = HERE / "compat"
    problems = []
    manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))["packs"]
    listed = {entry["file"] for entry in manifest}
    for p in sorted(folder.glob("*.jackdaw")):
        if p.name not in listed:
            problems.append("%s is not listed in compat/manifest.json" % p.name)
    for entry in manifest:
        p = folder / entry["file"]
        if not p.exists():
            problems.append("%s is missing" % entry["file"])
            continue
        data = p.read_bytes()
        if hashlib.sha256(data).hexdigest() != entry["sha256"]:
            problems.append("%s was changed: frozen packs are never edited" % entry["file"])
            continue
        rep, pack = validate_text(entry["file"], data.decode("utf-8-sig"), validator, type_index)
        for m in rep.errors + rep.warnings:
            problems.append("%s is no longer valid: %s" % (entry["file"], m))
        if pack and len(pack.get("items", [])) != entry["items"]:
            problems.append("%s: expected %d items" % (entry["file"], entry["items"]))
    return problems


def main():
    ap = argparse.ArgumentParser(description="Validate Jackdaw packs against the schema and the semantic rules.")
    ap.add_argument("files", nargs="*", help="pack files (default: examples/*.jackdaw)")
    ap.add_argument("--strict", action="store_true", help="treat warnings as errors")
    ap.add_argument("--write-index", action="store_true", help="rewrite examples/index.json from the example packs")
    ap.add_argument("--no-docs", action="store_true", help="skip the packs embedded in README.md and AUTHORING.md")
    ap.add_argument("--no-vectors", action="store_true", help="skip the test vectors")
    args = ap.parse_args()

    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema)
    type_index = {}
    for idx, branch in enumerate(schema["$defs"]["Item"]["oneOf"]):
        name = branch["$ref"].rsplit("/", 1)[-1]
        type_index[schema["$defs"][name]["properties"]["type"]["const"]] = idx

    targets = []
    paths = [Path(f) for f in args.files] if args.files else sorted((HERE / "examples").glob("*.jackdaw"))
    for p in paths:
        targets.append((str(p), p.read_text(encoding="utf-8-sig"), None))
    if not args.files and not args.no_docs:
        for doc in ("README.md", "AUTHORING.md"):
            p = HERE / doc
            if p.exists():
                targets.extend((label, text, doc) for label, text in extract_pack_blocks(p))

    failed = False
    doc_stats = {}
    for name, text, doc in targets:
        rep, pack = validate_text(name, text, validator, type_index)
        bad = bool(rep.errors) or (args.strict and bool(rep.warnings))
        failed = failed or bad
        count = len(pack["items"]) if pack and isinstance(pack.get("items"), list) else 0
        if doc and not bad and not rep.warnings:
            stat = doc_stats.setdefault(doc, [0, 0])
            stat[0 if name.endswith("(pack)") else 1] += 1
            continue
        print("%s  %s  (%d items)" % ("FAIL" if bad else "ok  ", name, count))
        for m in rep.errors:
            print("      error: %s" % m)
        for m in rep.warnings:
            print("      warning: %s" % m)
    for doc, (packs, items) in doc_stats.items():
        print("ok    %s  (%d complete packs, %d item snippets)" % (doc, packs, items))

    if not args.files and not args.no_docs:
        tour = HERE / "examples" / "format-tour.jackdaw"
        readme = HERE / "README.md"
        if tour.exists() and readme.exists():
            expected = load_pack(tour.read_text(encoding="utf-8"))
            found = any(load_pack(t) == expected for label, t in extract_pack_blocks(readme) if label.endswith("(pack)"))
            print("%s  README.md contains examples/format-tour.jackdaw verbatim" % ("ok  " if found else "FAIL"))
            failed = failed or not found

    if not args.files:
        index_path = HERE / "examples" / "index.json"
        index_text = build_index(sorted((HERE / "examples").glob("*.jackdaw")))
        if args.write_index:
            index_path.write_text(index_text, encoding="utf-8")
        current = index_path.exists() and index_path.read_text(encoding="utf-8") == index_text
        print("%s  examples/index.json lists the example packs (update it with --write-index)" % ("ok  " if current else "FAIL"))
        failed = failed or not current

    if not args.files and not args.no_docs:
        prompt = HERE / "prompt.txt"
        authoring = HERE / "AUTHORING.md"
        if prompt.exists() and authoring.exists():
            found = prompt.read_text(encoding="utf-8").strip() in authoring.read_text(encoding="utf-8")
            print("%s  AUTHORING.md contains prompt.txt verbatim" % ("ok  " if found else "FAIL"))
            failed = failed or not found

    if not args.files:
        problems = check_compat(validator, type_index)
        print("%s  compat/  (frozen packs are unchanged and still valid)" % ("FAIL" if problems else "ok  "))
        for e in problems:
            print("      error: %s" % e)
        failed = failed or bool(problems)

    if not args.no_vectors and VECTORS_PATH.exists() and not args.files:
        errs, total = run_vectors(VECTORS_PATH)
        print("%s  test-vectors.json  (%d vectors)" % ("FAIL" if errs else "ok  ", total))
        for e in errs:
            print("      error: %s" % e)
        failed = failed or bool(errs)

    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
