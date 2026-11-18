package hashi

import hashi.Check.eq
import hashi.Check.fails
import hashi.Check.ok
import hashi.Check.test
import java.io.File
import java.nio.file.Files

fun unitTests() {
    test("ISBN: ten and thirteen digits, hyphens, full-width digits") {
        eq("9782020134736", Isbn.normalize("2-02-013473-X"))
        eq("9782020134736", Isbn.normalize("978-2-02-013473-6"))
        eq("9784062035163", Isbn.normalize("９７８４０６２０３５１６３"))
        eq(null, Isbn.normalize("9782020134737"), "bad check digit")
        eq(null, Isbn.normalize("978202013473"), "too short")
        eq(null, Isbn.normalize("abc"))
    }

    test("ISBN: registration groups of every length") {
        eq("978-4", Isbn.group("9784062035163"))
        eq("978-2", Isbn.group("9782020134736"))
        eq("978-89", Isbn.group("9788936433598"))
        eq("978-957", Isbn.group("9789571340067"))
        eq("978-626", Isbn.group(Isbn.to13("626353036")))
        eq("978-65", Isbn.group(Isbn.to13("655535039")))
        eq("979-11", Isbn.group("979118943352".let { it + Isbn.check13(it) }))
        eq("979-10", Isbn.group("9791030700012".let { it.take(12) + Isbn.check13(it.take(12)) }))
        eq("978-99937", Isbn.group(Isbn.to13("999371234")))
    }

    test("ISBN: the area tells Taiwan from mainland China, and Korea from Japan") {
        eq("zh-Hant", Isbn.area("9789571340067")?.language)
        eq("TW", Isbn.area("9789571340067")?.code)
        eq("zh-Hans", Isbn.area("9787532742929")?.language)
        eq("HK", Isbn.area(Isbn.to13("962170895"))?.code)
        eq("ko", Isbn.area("9788936433598")?.language)
        eq("ja", Isbn.area("9784062035163")?.language)
        eq("ES", Isbn.area(Isbn.to13("841234567"))?.code)
        eq(null, Isbn.area(Isbn.to13("909123456")), "a group Hashi does not place")
    }

    test("Languages: catalogue codes and scripts") {
        eq("fr", Lang.code("fre"))
        eq("ja", Lang.code("jpn"))
        eq("zh-Hant", Lang.code("zh-TW"))
        eq("zh-Hans", Lang.code("zh-hans"))
        eq(null, Lang.code("xyz1"))
        eq("ja", Lang.fromScript("コンビニ人間"))
        eq("ko", Lang.fromScript("채식주의자"))
        eq("zh-Hant", Lang.fromScript("便利店人間"))
        eq("zh-Hans", Lang.fromScript("三体：地球往事"))
        eq("zh", Lang.fromScript("挪威的森林"), "no telltale character: Chinese, script unknown")
        eq(null, Lang.fromScript("La Végétarienne"))
        eq("zh", Lang.fromJapaneseName("中国語"))
    }

    test("Translators: French, English, Japanese, Chinese, Korean and pinyin statements") {
        eq(listOf("Rose-Marie Makino-Fayolle"), Translators.fromStatement("Haruki Murakami ; roman traduit du japonais par Rose-Marie Makino-Fayolle"))
        eq(listOf("Jeong Eun-jin", "Jacques Batilliot"), Translators.fromStatement("Han Kang ; traduit du coréen par Jeong Eun-jin et Jacques Batilliot"))
        eq(listOf("Deborah Smith"), Translators.fromStatement("Han Kang ; translated from the Korean by Deborah Smith."))
        eq(listOf("窪田啓作"), Translators.fromStatement("カミユ 著,窪田啓作 訳"))
        eq(listOf("柳沢文昭"), Translators.fromStatement("アルベール・カミュ 作,柳沢文昭 訳注"))
        eq(listOf("賴明珠"), Translators.fromStatement("村上春樹 著 ; 賴明珠 譯"))
        eq(listOf("頼明珠"), Translators.fromStatement("訳:頼明珠"))
        eq(listOf("김난주"), Translators.fromStatement("무라카미 하루키 지음 ; 김난주 옮김"))
        eq(listOf("Lin Shaohua"), Translators.fromStatement("Cunshang Chunshu zhu ; Lin Shaohua yi"))
        eq(emptyList<String>(), Translators.fromStatement("Murakami Haruki"))
        eq(emptyList<String>(), Translators.fromStatement(null))
    }

    test("Translators: the same person written two ways counts once") {
        eq(listOf("賴明珠"), Translators.merge(listOf(listOf("賴明珠"), listOf("頼明珠"))))
        eq(listOf("Rose-Marie Makino-Fayolle"), Translators.merge(listOf(listOf("Rose-Marie Makino-Fayolle"), listOf("Makino-Fayolle, Rose-Marie"))))
    }

    test("Query: ISBN, title, title and author") {
        eq("9782020134736", Hashi.Query.parse(" 2-02-013473-X ").isbn)
        val q = Hashi.Query.parse("L'Étranger / Camus")
        eq("L'Étranger", q.title)
        eq("Camus", q.author)
        eq("三体", Hashi.Query.parse("三体／刘慈欣").title)
        eq(null, Hashi.Query.parse("Kitchen").author)
    }

    test("Names: order, spaces and case do not matter") {
        ok(Hashi.nameMatches("Albert Camus", "camus"), "family name")
        ok(Hashi.nameMatches("村上 春樹", "村上春樹"), "space in a Japanese name")
        ok(Hashi.nameMatches("Haruki Murakami", "Murakami Haruki"), "order")
        ok(!Hashi.nameMatches("Banana Yoshimoto", "Murakami"), "another author")
        eq("カミュ", Hashi.familyName("アルベール・カミュ"))
        eq("村上春樹", Hashi.familyName("村上春樹"))
        ok(Hashi.same("La Ballade de l'impossible", "la ballade de l’impossible"), "apostrophes and case")
        ok(Hashi.same("ノルウェイの森 : 上", "ノルウェイの森"), "volume after a colon")
    }

    fun rec(src: Source, title: String, isbn: String?, lang: String?, year: Int? = null, tr: List<String> = emptyList(), note: String? = null) =
        Record(src, "", title, isbn, lang, null, year, tr, note)

    test("Language: a catalogue's word first, Chinese split by the ISBN's area") {
        eq("zh-Hant" to Why.DECLARED, Hashi.language(listOf(rec(Source.OPEN_LIBRARY, "Norwegian Wood", "9789571340067", "zh"))))
        eq("zh-Hans" to Why.DECLARED, Hashi.language(listOf(rec(Source.OPEN_LIBRARY, "挪威的森林", "9787532742929", "zh"))))
        eq("zh" to Why.DECLARED, Hashi.language(listOf(rec(Source.OPEN_LIBRARY, "挪威的森林", null, "zh"))))
        eq("zh-Hant" to Why.DECLARED, Hashi.language(listOf(rec(Source.NDL, "■威的森林", "9789571323008", "ja", note = "zh"))), "the note on the text beats the coded field")
        eq("fr" to Why.DECLARED, Hashi.language(listOf(rec(Source.OPEN_LIBRARY, "La Ballade", "9782020134736", "fr"))))
    }

    test("Language: without a catalogue's word, the ISBN and the title decide") {
        eq("ja" to Why.ISBN, Hashi.language(listOf(rec(Source.OPEN_LIBRARY, "Noruwei no mori", "9784062035163", null))))
        eq("ja" to Why.ISBN, Hashi.language(listOf(rec(Source.OPEN_LIBRARY, "三体", Isbn.to13("415209868"), null))), "kanji-only title on a Japanese ISBN")
        eq("zh-Hant" to Why.ISBN, Hashi.language(listOf(rec(Source.OPEN_LIBRARY, "挪威的森林", "9789571340067", null))))
        eq("ko" to Why.SCRIPT, Hashi.language(listOf(rec(Source.OPEN_LIBRARY, "채식주의자", null, null))))
        eq(null to Why.UNKNOWN, Hashi.language(listOf(rec(Source.OPEN_LIBRARY, "Kitchen", null, null))))
    }

    test("Merge: one edition per ISBN, sources and translators united, the native title kept") {
        val eds = Hashi.merge(
            listOf(
                rec(Source.OPEN_LIBRARY, "Nuowei de sen lin", "9789571340067", "zh", 2003, listOf("Lai Mingzhu")),
                rec(Source.NDL, "挪威的森林", "9789571340067", null, 2003, listOf("頼明珠"), "zh"),
                rec(Source.OPEN_LIBRARY, "Kitchen", null, "en", 1993),
                rec(Source.WIKIDATA, "Kitchen", null, "en", 1993),
            ),
        )
        eq(2, eds.size)
        val tw = eds.first { it.isbn13 == "9789571340067" }
        eq("挪威的森林", tw.title)
        eq("zh-Hant", tw.language)
        eq(setOf(Source.NDL, Source.OPEN_LIBRARY), tw.sources)
        eq(listOf("頼明珠", "Lai Mingzhu"), tw.translators)
    }

    test("Shelves: always the six, in order; other languages after; empty ones only for the six") {
        val eds = Hashi.merge(
            listOf(
                rec(Source.OPEN_LIBRARY, "Naokos Lächeln", Isbn.to13("383218013"), "de", 2001),
                rec(Source.OPEN_LIBRARY, "La Ballade", "9782020134736", "fr", 1999),
            ),
        )
        eq(Lang.SHELVES + "de", Hashi.shelve(eds).keys.toList())
    }

    test("NDL: books only, ISBN, translator, language note; no DTD allowed") {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<rss xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="2.0"><channel>
<item><title>異邦人</title><link>https://ndlsearch.ndl.go.jp/books/R100000002-I1</link><author>カミュ 著,窪田啓作 訳</author><category>図書</category>
<dc:title>異邦人</dc:title><dc:publisher>新潮社</dc:publisher><dc:date xsi:type="dcterms:W3CDTF">1954</dc:date>
<dc:identifier xsi:type="dcndl:ISBN">978-4-10-211401-0</dc:identifier></item>
<item><title>異邦人</title><link>https://x/2</link><category>映像資料</category><dc:title>異邦人</dc:title></item>
<item><title>■威的森林</title><link>https://x/3</link><category>図書</category><dc:title>■威的森林</dc:title>
<dc:identifier xsi:type="dcndl:ISBN13">957-13-2300-4</dc:identifier><dc:description>訳:頼明珠</dc:description><dc:description>本文は中国語</dc:description></item>
</channel></rss>"""
        val rs = Ndl.parse(xml)
        eq(2, rs.size)
        eq(listOf("窪田啓作"), rs[0].translators)
        eq(1954, rs[0].year)
        eq("新潮社", rs[0].publisher)
        eq(Isbn.normalize("978-4-10-211401-0"), rs[0].isbn13)
        eq("zh", rs[1].textLanguage)
        eq(listOf("頼明珠"), rs[1].translators)
        eq("9789571323008", rs[1].isbn13)
        fails<Exception> { Ndl.parse("""<?xml version="1.0"?><!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]><rss><channel><item><title>&x;</title></item></channel></rss>""") }
    }

    test("Cache: replays answers, records 404s, refuses what it has not seen when offline") {
        val dir = Files.createTempDirectory("hashi-cache").toFile().also { it.deleteOnExit() }
        var calls = 0
        val net = Fetch { url -> calls++; if (url.endsWith("/none")) null else "body of $url\nline 2" }
        val online = Cache(dir, net)
        eq("body of https://a.example/x\nline 2", online.get("https://a.example/x"))
        eq("body of https://a.example/x\nline 2", online.get("https://a.example/x"))
        eq(null, online.get("https://a.example/none"))
        eq(null, online.get("https://a.example/none"))
        eq(2, calls)
        val offline = Cache(dir, null)
        eq("body of https://a.example/x\nline 2", offline.get("https://a.example/x"))
        fails<Cache.Missing> { offline.get("https://a.example/y") }
        ok(Cache.name("https://query.wikidata.org/sparql?q").startsWith("query-"), "file names say the host")
        ok(File(dir, Cache.name("https://a.example/x")).readText().startsWith("https://a.example/x\n"), "first line is the URL")
    }

    test("Net: only HTTPS") {
        fails<IllegalArgumentException> { Net().get("http://openlibrary.org/") }
    }

    test("Text: every locale has every key, with the same placeholders") {
        val en = Text.keys("en")
        for (l in Text.LOCALES) {
            eq(en, Text.keys(l), "keys of $l")
            for (k in en) {
                val ph = { s: String -> Regex("\\{(\\w+)}").findAll(s).map { it.value }.toSet() }
                eq(ph(Text.t("en", k)), ph(Text.t(l, k)), "placeholders of $k in $l")
            }
        }
        eq("zh-Hant", Text.locale("zh-TW"))
        eq("ja", Text.locale("ja"))
        eq("en", Text.locale("de"))
    }

    test("Command line: help, version, bad options, nothing to search") {
        val out = java.io.ByteArrayOutputStream()
        val err = java.io.ByteArrayOutputStream()
        val noNet = { _: File?, _: Boolean -> Fetch { throw FetchError("no network in tests") } }
        eq(0, run(listOf("--version"), java.io.PrintStream(out), java.io.PrintStream(err), noNet))
        ok(out.toString().contains(VERSION), "version printed")
        eq(2, run(listOf("--bogus"), java.io.PrintStream(out), java.io.PrintStream(err), noNet))
        eq(2, run(emptyList(), java.io.PrintStream(out), java.io.PrintStream(err), noNet))
        eq(2, run(listOf("--lang"), java.io.PrintStream(out), java.io.PrintStream(err), noNet))
    }
}
