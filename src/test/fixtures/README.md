# Recorded catalogue answers

The tests look up six books without the network. Each file here is one answer from a catalogue: the first line is the URL Hashi asked for, the rest is the body. The file name is the host and the first 16 hex digits of the URL's SHA-256, as `Cache` names it.

The answers were recorded in November 2026 and trimmed to the fields Hashi reads:

- Open Library editions keep `key`, `title`, `subtitle`, the first two ISBNs, `languages`, the first publisher, `publish_date`, `by_statement` and translators among `contributors`.
- NDL items keep the title, link, statement of responsibility, category, publisher, dates, ISBNs, and notes about translators or the language of the text.
- Search answers and Wikidata answers are kept as they came.

To add a book, append it to `Fixtures.QUERIES`, build, and run `java -cp build/hashi-tests.jar hashi.RecordKt` from the repository root. It prints the URLs still missing and the recorded files no longer used. Fetch each missing URL once, trim it the same way, and save it under its name. Repeat until nothing is missing: each answer can lead to new questions.

Sources: Wikidata (CC0), Open Library, and 国立国会図書館サーチ API（国立国会図書館書誌データ、2026年11月取得、Hashi のテスト用に項目を削減）.
