package hashi

import hashi.Check.eq
import hashi.Check.ok
import hashi.Check.test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

/**
 * Six books, looked up against answers recorded from Open Library, Wikidata and the NDL
 * (src/test/fixtures). Each test checks editions a bookseller can verify on the book itself.
 */
fun fixtureTests() {
    val dir = File("src/test/fixtures")
    val reports = HashMap<String, Report>()
    fun find(q: String): Report = reports.getOrPut(q) { Hashi(Cache(dir, null)).find(q) ?: throw AssertionError("nothing found for $q") }
    fun Report.shelf(lang: String) = shelves[lang] ?: emptyList()
    fun Report.edition(isbn: String) = editions.firstOrNull { it.isbn13 == isbn } ?: throw AssertionError("$isbn not found for $query")

    test("Norwegian Wood from the ISBN of its French edition") {
        val r = find("9782020134736")
        eq("Q751348", r.work.qid)
        eq("ja", r.work.original)
        eq("ノルウェイの森", r.work.title("ja"))
        eq("La Ballade de l'impossible", r.work.title("fr"))
        eq("fr", r.edition("9782020134736").language)
        eq(listOf("Rose-Marie Makino-Fayolle"), r.edition("9782264047731").translators)
        eq("ja", r.edition("9784062035156").language, "Kodansha 1987, the first edition")
        eq("en", r.edition("9780375704024").language)
        eq(listOf("Jay Rubin"), r.edition("9780375704024").translators)
        eq("zh-Hant", r.edition("9789571340067").language, "Taipei edition, China Times")
        eq("zh-Hans", r.edition("9787532742929").language, "Shanghai Translation Publishing House")
        eq("zh-Hans", r.edition("9787532765546").language, "a mainland ISBN beats a Japanese title copied from the original")
        eq("ko", r.edition("9788937463105").language)
        ok(r.shelf("zh-Hans").all { it.isbn13 == null || it.isbn13.startsWith("9787") }, "only mainland ISBNs in simplified Chinese")
    }

    test("The Vegetarian from its Korean title") {
        val r = find("채식주의자")
        eq("Q24082770", r.work.qid)
        eq("ko", r.work.original)
        eq("ko", r.edition("9788936433598").language, "Changbi 2007")
        eq(listOf("Jeong Eun-Jin", "Jacques Batilliot"), r.edition("9782253067900").translators)
        ok("Deborah Smith" in r.edition("9781846275623").translators, "Portobello 2015")
        val ja = r.edition("9784904855027")
        eq("ja", ja.language)
        eq("クオン", ja.publisher)
        eq(setOf(Source.WIKIDATA, Source.NDL), ja.sources)
        eq(emptyList<Edition>(), r.shelf("zh-Hant"), "no Taiwan edition in the open catalogues: the gap shows")
        eq("素食主義者", r.work.title("zh-Hant"))
    }

    test("The Three-Body Problem from its Chinese title and author") {
        val r = find("三体 / 刘慈欣")
        eq("Q607112", r.work.qid)
        eq("zh-Hans", r.edition("9787536692930").language, "Chongqing 2008")
        eq("zh-Hant", r.edition("9789861206066").language, "Taipei, Mao Tou Ying 2011")
        eq(listOf("Ken Liu"), r.edition("9780765377067").translators)
        eq(listOf("Gwennaël Gaffric"), r.edition("9782330113551").translators)
        eq("ja", r.edition("9784152098702").language, "Hayakawa 2019, a kanji-only title on a Japanese ISBN")
        ok(r.editions.none { it.isbn13 == "9784150124755" }, "三体0 (Ball Lightning) is another book")
        eq(emptyList<Edition>(), r.shelf("ko"))
    }

    test("L'Étranger from its title and author") {
        val r = find("L'Étranger / Camus")
        eq("Q163297", r.work.qid)
        eq("fr", r.work.original)
        val shincho = r.edition("9784102114018")
        eq(listOf("窪田啓作"), shincho.translators)
        eq(setOf(Source.WIKIDATA, Source.NDL), shincho.sources)
        ok(r.shelf("ja").any { it.isbn13 == null && it.year == 1954 }, "the 1954 Shinchosha edition, before ISBNs, from the NDL")
        eq("zh-Hant", r.edition("9789579093668").language)
        ok(Hashi.printOnDemand(r.shelf("en").last()), "print-on-demand reprints come last")
        ok(!Hashi.printOnDemand(r.shelf("en").first()), "and not first")
    }

    test("Convenience Store Woman: three shelves with nothing say so") {
        val r = find("コンビニ人間")
        eq("Q27863945", r.work.qid)
        eq(listOf("Mathilde Tamae-Bouhon"), r.edition("9782072798559").translators)
        eq(listOf("Ginny Tapley Takemori"), r.edition("9780802128256").translators)
        eq("ja", r.edition("9784163906188").language)
        for (l in listOf("ko", "zh-Hant", "zh-Hans")) eq(0, r.shelf(l).size, l)
        val text = Render.text(r, "fr")
        ok(text.contains("한국어 · 편의점 인간 — rien dans Open Library, Wikidata ni la NDL"), "the Korean gap, with the Korean title to look for")
    }

    test("Kitchen: the author picks the right Kitchen") {
        val r = find("Kitchen / Yoshimoto")
        eq("Q2246971", r.work.qid)
        eq("ko", r.edition("9788937403170").language)
        eq("zh-Hant", r.edition("9789621712929").language)
        eq(listOf("李萍"), r.edition("9787532734917").translators)
        eq("ja", r.edition("9784828822525").language, "Fukutake 1988, the first edition")
        eq(listOf("Megan Backus"), r.edition("9780571167906").translators)
    }

    test("Text answer in the four languages, JSON for programs") {
        val r = find("L'Étranger / Camus")
        ok(Render.text(r, "en").contains("English · The Stranger"), "English")
        ok(Render.text(r, "ja").contains("日本語 · 異邦人"), "Japanese")
        ok(Render.text(r, "zh-Hant").contains("繁體中文 · 異鄉人"), "Traditional Chinese")
        ok(Render.text(r, "fr").contains("Français (langue originale)"), "French")
        ok(Render.text(r, "en").contains("Other languages"), "other languages summed up")
        ok(Render.text(r, "en", all = true).contains("Svenska · Främlingen"), "--all lists them")
        @Suppress("UNCHECKED_CAST")
        val j = Json.parse(Render.json(r)) as Map<String, Any?>
        eq("Q163297", j.obj("work")["wikidata"])
        val ja = j.obj("shelves").list("ja").map { it as Map<*, *> }
        ok(ja.any { it["isbn13"] == "9784102114018" && it["languageFrom"] == "declared" }, "JSON edition")
    }

    test("Command line, offline, from the recorded answers") {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val code = run(listOf("--offline", "--cache", dir.path, "--lang", "ja", "コンビニ人間"), PrintStream(out, true, "UTF-8"), PrintStream(err), ::defaultFetch)
        eq(0, code)
        ok(out.toString("UTF-8").contains("Français · La Fille de la supérette"), "answer printed")
        eq(3, run(listOf("--offline", "--cache", dir.path, "a book nobody recorded"), PrintStream(out), PrintStream(err), ::defaultFetch))
    }

    test("A catalogue that does not answer leaves the others working") {
        val cache = Cache(dir, null)
        val down = Fetch { url -> if (url.startsWith("https://query.wikidata.org")) throw FetchError("query.wikidata.org answered 503") else cache.get(url) }
        val r = Hashi(down).find("Kitchen / Yoshimoto") ?: throw AssertionError("Open Library alone should answer")
        eq(null, r.work.qid)
        ok(r.notes.any { it.startsWith("unreachable:Wikidata") }, "the gap is noted")
        ok(r.editions.any { it.isbn13 == "9780571342723" }, "Open Library editions are still there")
        eq("zh-Hant", r.edition("9789621712929").language, "and still on their shelves")
        ok(Render.text(r, "en").contains("Wikidata could not be reached"), "and said")
    }
}
