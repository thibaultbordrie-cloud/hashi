# Hashi 橋

[![ci](https://github.com/thibaultbordrie-cloud/hashi/actions/workflows/ci.yml/badge.svg)](https://github.com/thibaultbordrie-cloud/hashi/actions/workflows/ci.yml)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![Dependencies](https://img.shields.io/badge/dependencies-0-2f6f6a)
![Languages](https://img.shields.io/badge/FR%20·%20EN%20·%20JA%20·%20KO%20·%20繁體%20·%20简体-lightgrey)
![License: MIT](https://img.shields.io/badge/License-MIT-lightgrey)

**Give Hashi one book, by ISBN or title, in any language. It finds the work and every edition the open catalogues know, in French, English, Japanese, Korean, and both Chinese scripts. Each edition comes with its ISBN, publisher, year and translator.**

*Hashi* (橋) means bridge.

[日本語](#日本語での概要) · [繁體中文](#中文簡介繁體) · [Français](#en-bref-français)

## Why a bookshop needs it

An international bookshop sells the same novel in several languages, and its customers ask:

- "Do you have *Norwegian Wood* in Japanese?"
- "Is there a Taiwan edition of *L'Étranger*?"
- "Who translated the French *Convenience Store Woman*?"

Answering means searching four or five catalogues, each in its own language and script, and then telling a Taipei edition (traditional characters) from a Shanghai one (simplified). Hashi does that in one command. It also shows the gaps: no Korean edition of a book in any open catalogue is worth knowing too.

```
$ hashi "Kitchen / Yoshimoto"
橋 Kitchen · キッチン
  by Banana Yoshimoto · written in 日本語 · Wikidata Q2246971 · Open Library OL2637599W OL929989W OL11677568W

Français · Kitchen · 2 editions
   9782070731442  Gallimard 1994  tr. Dominique Palmé, Kyōko Satō  [Open Library, NDL]
   9782070394869  Gallimard 1996  [Open Library]

日本語 (original) · キッチン · 7 editions
   9784041800089  角川書店 1998  [Open Library, NDL]
   9784828822525  福武書店 1988  [Open Library, NDL]
   …

한국어 · 키친 · 1 edition
   9788937403170  Minumsa 1999  [Open Library]

繁體中文 · 廚房 · 1 edition
   9789621712929  Kitchen  Bo Yi 1994  [Open Library]

简体中文 · 厨房 · 1 edition
 ≈ 9787532734917  上海译文出版社 2004  tr. 李萍  [NDL]

Other languages (--all to list them): Español 5 · Deutsch 2 · Português 2 · Русский 2 · …
```

(English editions are cut from this excerpt.)

## How it works

1. **The work.** Hashi searches [Wikidata](https://www.wikidata.org) and [Open Library](https://openlibrary.org) for the title, or Open Library for the ISBN. When there are several candidates, the author you typed decides first, then the exact title, then the link between the two catalogues, then how many Wikipedias have an article on the work. Wikidata then gives the work's title in each language: 素食主義者 in Taiwan, 素食主义者 in mainland China, 菜食主義者 in Japan.
2. **The editions.** Hashi reads the editions of the work in Open Library, then searches Open Library again under each translated title. A translation is often filed there as a separate work. It adds the editions Wikidata lists and the books in the catalogue of the [National Diet Library of Japan](https://ndlsearch.ndl.go.jp) (NDL). The NDL holds almost every Japanese edition and many Chinese and Korean ones.
3. **One edition per ISBN.** Records from several catalogues are merged. The edition keeps every source, every translator (賴明珠 and 頼明珠 count once), and a title in its own script: ノルウェイの森, not *Noruwei no mori*.
4. **The shelf.** A catalogue's own word on the language comes first. Chinese is then split by the ISBN's registration group: 978-957, 978-986 and 978-626 are Taiwan, 978-962 and 978-988 Hong Kong, 978-7 mainland China. Without a word from any catalogue, the ISBN's area and the title's script decide, and the edition is marked **≈**. A Chinese title on a French ISBN is filed under *language unknown*. One of the two must be wrong, and Hashi does not guess which.
5. **The order.** Editions known to more catalogues come first, then the newest. Print-on-demand reprints come last (979-8 ISBNs, "Independently Published"). They are often unauthorised copies of a translation.

The [tests](src/test/kotlin/hashi/FixtureTests.kt) check six books against answers a bookseller can verify on the book: *Norwegian Wood* found from its French ISBN, *The Vegetarian* from its Korean title, *The Three-Body Problem* from its Chinese title, *L'Étranger*, *Convenience Store Woman* and *Kitchen*. They run offline, on catalogue answers recorded in [src/test/fixtures](src/test/fixtures/README.md).

## Use

Java 21 or later. Download `hashi-<version>.jar` from the [releases](https://github.com/thibaultbordrie-cloud/hashi/releases) and check it against its `.sha256`.

```
java -jar hashi.jar 9782020134736            # an ISBN-10 or ISBN-13
java -jar hashi.jar "ノルウェイの森"           # a title in any language
java -jar hashi.jar "L'Étranger / Camus"     # a title and its author
java -jar hashi.jar --lang ja "채식주의자"     # the answer in Japanese (en, fr, ja, zh-Hant)
java -jar hashi.jar --json 9782020134736     # JSON, for another program
java -jar hashi.jar --all "Kitchen"          # every edition and every language
```

Answers are kept for 30 days in `~/.cache/hashi`, so a second search for the same book does not query the catalogues again. Use `--offline` to answer from that cache only, and `--no-cache` to keep nothing on disk.

On Windows, type titles in a UTF-8 console (`chcp 65001`). Other code pages can garble CJK characters before they reach Java.

## Privacy and politeness

- Only what you type (an ISBN, a title, an author) is sent, and only to the three catalogues. Hashi has no account, no tracking and no server of its own.
- One request at a time per catalogue, at least a second apart. The User-Agent names Hashi and links to this repository. Answers are cached.
- XML answers are parsed with DTDs and external entities disabled. A catalogue answer cannot make Hashi read a file or call another server.

## Limits

- Hashi knows only what the open catalogues know. Recent translations, small publishers and Korean editions are the most often missing. The National Library of Korea's catalogue needs an API key, so Hashi does not query it yet.
- Catalogues make mistakes: an edition filed under the wrong work, or a wrong language. Hashi never hides a source. Every edition says which catalogues list it, so a bookseller can check.
- Multi-volume editions (上・下) appear as one edition per volume, as they have one ISBN each.

## Build and test

Only the Kotlin compiler and a JDK, no build tool and no library:

```
KOTLINC=/path/to/kotlinc ./build.sh     # build/hashi.jar, then 26 tests
```

## Data and credits

- **Wikidata**: CC0.
- **Open Library** (Internet Archive): open bibliographic data.
- **National Diet Library of Japan**: 出典：国立国会図書館サーチ API（国立国会図書館書誌データ）。Hashi edits it: it merges, sorts and files the records by language. Hashi asks only for the NDL's own catalogue (`dpid=iss-ndl-opac`), whose data anyone may reuse. A commercial or hosted service built on Hashi must apply to the NDL first.
- The test fixtures were recorded in November 2026 and trimmed to the fields Hashi reads.

Code under the [MIT License](LICENSE).

---

## 日本語での概要

**Hashi（橋）** は、ISBN または書名（どの言語でも可）から作品を特定し、Wikidata・Open Library・国立国会図書館サーチにある版を、フランス語・英語・日本語・韓国語・中国語（繁体字／簡体字）ごとに一覧にするツールです。ISBN、出版社、刊行年、訳者を表示します。

- 中国語は ISBN の登録グループで分けます（978-957／986／626 は台湾、978-962／988 は香港、978-7 は中国本土）。
- どの目録にも言語の記載がない版は、ISBN と書名の文字から推定し「≈」を付けます。
- 送信するのは入力した ISBN・書名・著者名だけです。回答は 30 日間ローカルに保存します。
- 国立国会図書館の書誌データは、同館作成分（iss-ndl-opac）のみを利用し、加工（統合・言語別分類）して表示しています。

## 中文簡介（繁體）

**Hashi（橋）** 依 ISBN 或書名（任何語言）找出作品，並整理 Wikidata、Open Library 與日本國立國會圖書館所收錄的各語言版本：法文、英文、日文、韓文、繁體與簡體中文，附 ISBN、出版社、年份與譯者。

- 繁簡之分依 ISBN 的地區碼：978-957／986／626 為臺灣，978-962／988 為香港，978-7 為中國大陸。
- 書目未註明語言的版本，由 ISBN 與書名推定，並標上「≈」。
- 只會送出您輸入的 ISBN、書名或作者；查詢結果在本機保留 30 天。

## En bref (français)

**Hashi (橋, « pont »)** part d'un ISBN ou d'un titre, dans n'importe quelle langue. Il retrouve l'œuvre et toutes les éditions connues de Wikidata, d'Open Library et de la Bibliothèque nationale de la Diète du Japon : en français, anglais, japonais, coréen, chinois traditionnel et simplifié. Chaque édition a son ISBN, son éditeur, son année et son traducteur.

- Le chinois est réparti selon le groupe d'enregistrement de l'ISBN : 978-957, 978-986 et 978-626 pour Taïwan, 978-962 et 978-988 pour Hong Kong, 978-7 pour la Chine continentale.
- Une édition dont aucun catalogue n'indique la langue est classée d'après l'ISBN et l'écriture du titre, et marquée « ≈ ».
- Seuls l'ISBN, le titre ou l'auteur saisis sont envoyés aux catalogues. Les réponses sont gardées 30 jours sur l'ordinateur.
- Les rayons vides comptent aussi : aucune édition coréenne connue, c'est une information pour une librairie.
