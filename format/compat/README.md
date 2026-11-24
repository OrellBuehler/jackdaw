# Frozen packs

These packs back the compatibility promise of the format ([section 14](../README.md#14-versioning-policy)): a pack that is valid under any version of the format stays valid and keeps importing completely in every later app.

- One or more packs per specification revision, named `<revision>-<what>.jackdaw`, using what that revision added.
- **Never edit a file here.** `manifest.json` records the SHA-256 and the item count of each pack; `validate.py` fails when a file changed, is missing, no longer passes the current schema, or produces a warning.
- The shared reader is tested to import each of them with every item and without warnings (`CompatibilityTest`).
- When a version or revision adds something to the format, add a new pack that uses it and a line in `manifest.json`. Existing lines stay as they are.
