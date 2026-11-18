# Security

Please report a vulnerability privately through **GitHub's private vulnerability reporting** (Security tab → Report a vulnerability), not in a public issue.

## What Hashi does, and how

- **What leaves the computer.** Only the ISBN, title or author typed on the command line, sent over HTTPS to Open Library, Wikidata and the National Diet Library of Japan. Nothing else: no account, no identifier, no telemetry. `--offline` sends nothing.
- **Politeness.** One request at a time per catalogue, at least one second apart, a retry after a 429 or 5xx that respects `Retry-After`, and a User-Agent that names Hashi.
- **Answers are data.** Answers over 4 MB are refused. JSON is read by a small parser with a depth limit. XML is parsed with DTDs, external entities and XInclude disabled, so an answer cannot make Hashi read a local file or call another server. SPARQL strings are escaped and capped at 200 characters, and identifiers are checked against their pattern (`Q123`, `OL123W`, `OL123A`) before they go into a query or a URL.
- **The cache.** Answers are kept in `~/.cache/hashi`, one file per URL, named by its SHA-256 and written atomically. The cache holds only public catalogue data, but it shows which books were looked up. Delete the folder, or use `--no-cache`, if that matters.

## Out of scope

- The accuracy of the catalogues themselves. Hashi shows which catalogue each edition comes from.
