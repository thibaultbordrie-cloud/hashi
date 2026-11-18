package hashi

/**
 * From one ISBN or one title, finds the work and every edition the open catalogues know,
 * then files each edition under its language. Nothing is guessed silently: each edition says
 * which catalogues list it and why it sits on its shelf.
 */
class Hashi(fetch: Fetch) {
    private val ol = OpenLibrary(fetch)
    private val wd = Wikidata(fetch)
    private val ndl = Ndl(fetch)

    data class Query(val text: String, val isbn: String?, val title: String?, val author: String?) {
        companion object {
            /** An ISBN, "a title", or "a title / an author". */
            fun parse(raw: String): Query {
                val text = raw.trim().replace(Regex("\\s+"), " ").take(300)
                Isbn.normalize(text)?.let { return Query(text, it, null, null) }
                val parts = text.split(Regex("\\s+/\\s+|\\s*／\\s*"), limit = 2)
                return Query(text, null, parts[0].trim().takeIf { it.isNotEmpty() }, parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() })
            }
        }
    }

    fun find(raw: String): Report? {
        val q = Query.parse(raw)
        val notes = ArrayList<String>()
        fun <T> tryOr(what: Source, empty: T, body: () -> T): T = try {
            body()
        } catch (e: FetchError) {
            notes += "unreachable:${what.label}:${e.message}"
            empty
        } catch (e: Json.JsonError) {
            notes += "unreadable:${what.label}"
            empty
        }

        // 1. Candidates in Open Library and Wikidata.
        val olHits = when {
            q.isbn != null -> tryOr(Source.OPEN_LIBRARY, emptyList()) { ol.byIsbn(q.isbn) }
            else -> tryOr(Source.OPEN_LIBRARY, emptyList()) { ol.search(q.title!!, q.author) }
        }
        val title = q.title ?: olHits.firstOrNull()?.title
        val candidates = LinkedHashMap<String, Int>()
        tryOr(Source.WIKIDATA, Unit) {
            wd.workOf(olHits.flatMap { it.wikidata }).forEach { candidates.putIfAbsent(it, 0) }
            wd.byOpenLibrary(olHits.map { it.key }).forEach { candidates.putIfAbsent(it, 0) }
            if (q.title != null) {
                val langs = listOfNotNull(Lang.fromScript(q.title)?.substringBefore('-'), "en").distinct()
                for (l in langs) wd.search(q.title, l).forEach { candidates.putIfAbsent(it.qid, it.rank) }
            }
            if (candidates.isEmpty() && title != null) {
                // Last resort: the author's works on Wikidata, matched by title.
                val authorQid = olHits.firstOrNull()?.authorKeys?.firstOrNull()?.let { tryOr(Source.OPEN_LIBRARY, null) { ol.authorWikidata(it) } }
                if (authorQid != null) {
                    wd.byAuthor(authorQid).filter { (_, labels) -> labels.any { same(it, title) } }.keys.forEach { candidates.putIfAbsent(it, 5) }
                }
            }
        }

        // 2. The best Wikidata work: the author asked for, the title, the Open Library link, fame.
        val details = tryOr(Source.WIKIDATA, emptyList()) { wd.details(candidates.keys.take(5)) }
        val olKeys = olHits.map { it.key }.toSet()
        val best = details.maxByOrNull { d ->
            var s = 0
            if (q.author != null) s += if (d.authors.any { a -> a.names.values.any { nameMatches(it, q.author) } }) 100 else -100
            if (title != null) s += when {
                d.labels.values.any { same(it, title) } -> 50
                d.labels.values.any { norm(it).contains(norm(title)) || norm(title).contains(norm(it)) } -> 15
                else -> 0
            }
            if (d.openLibrary.any { it in olKeys }) s += 40
            s += 10 - (candidates[d.qid] ?: 10).coerceAtMost(10)
            s += d.sitelinks.coerceAtMost(100) / 10
            s
        }?.takeIf { d -> q.author == null || d.authors.any { a -> a.names.values.any { nameMatches(it, q.author) } } }

        if (best == null && olHits.isEmpty()) return null

        val authorNames: List<String> = best?.authors?.flatMap { it.names.values }?.distinct()
            ?: olHits.firstOrNull()?.authors ?: emptyList()
        val authorKeys = olHits.firstOrNull()?.authorKeys?.toSet() ?: emptySet()

        // 3. Open Library works of the same book: linked from Wikidata, or the same title by the same author.
        fun sameAuthor(h: OpenLibrary.Hit) = h.authorKeys.any { it in authorKeys } ||
            h.authors.any { n -> authorNames.any { Translators.key(it) == Translators.key(n) || nameMatches(n, it) } }
        val works = LinkedHashSet<String>()
        best?.openLibrary?.let(works::addAll)
        if (q.isbn != null) olHits.firstOrNull()?.let { works += it.key }
        olHits.filter { h -> h.key in works || (sameAuthor(h) && (best == null || best.labels.values.any { same(it, h.title) } || (title != null && same(h.title, title)))) }
            .forEach { works += it.key }
        val titles = best?.labels ?: emptyMap()
        val searched = HashSet<String>().apply { title?.let { add(norm(it)) } }
        for (t in titles.values) {
            if (!searched.add(norm(t))) continue
            tryOr(Source.OPEN_LIBRARY, emptyList()) { ol.search(t, null) }
                .filter { h -> sameAuthor(h) && same(h.title, t) }.forEach { works += it.key }
        }

        // 4. Every catalogue's records.
        val records = ArrayList<Record>()
        for (w in works.take(8)) records += tryOr(Source.OPEN_LIBRARY, emptyList()) { ol.editions(w) }
        best?.let { b -> records += tryOr(Source.WIKIDATA, emptyList()) { wd.editions(b.qid) } }
        val jaTitle = titles["ja"] ?: title?.takeIf { Lang.fromScript(it) == "ja" }
        if (jaTitle != null) {
            val jaAuthor = best?.authors?.firstNotNullOfOrNull { it.names["ja"] }?.let { familyName(it) }
            records += tryOr(Source.NDL, emptyList()) { ndl.search(jaTitle, jaAuthor) }.filter { Ndl.sameWork(it.title, jaTitle) }
        }

        val editions = merge(records)
        // Without Wikidata's word, the language of the oldest edition a catalogue dates and declares.
        val original = best?.original ?: editions.filter { it.why == Why.DECLARED && it.year != null && it.language != null }
            .minByOrNull { it.year!! }?.language?.substringBefore('-')
        val work = Work(
            best?.qid, works, titles.ifEmpty { mapOf("und" to (title ?: q.text)) },
            best?.authors ?: olHits.firstOrNull()?.authors?.map { Author(null, mapOf("und" to it)) } ?: emptyList(),
            original,
        )
        return Report(q.text, work, shelve(editions), notes)
    }

    companion object {
        /** Lower case, no spaces, no punctuation, full-width folded: "La Ballade de l'impossible" ≈ "la ballade de l’impossible". */
        fun norm(s: String): String = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC).lowercase()
            .substringBefore(" : ").replace(Regex("[\\p{P}\\p{S}\\s]"), "")

        fun same(a: String, b: String) = norm(a).isNotEmpty() && norm(a) == norm(b)

        /** "camus" matches "Albert Camus"; "村上春樹" matches "村上 春樹"; "Murakami Haruki" matches "Haruki Murakami". */
        fun nameMatches(name: String, wanted: String): Boolean {
            val n = norm(name)
            val w = norm(wanted)
            if (w.isEmpty() || n.isEmpty()) return false
            if (n.contains(w) || w.contains(n)) return true
            val words = wanted.lowercase().split(Regex("[\\s,]+")).filter { it.length > 1 }
            return words.isNotEmpty() && words.all { norm(name).contains(norm(it)) }
        }

        /** The part of a name the NDL indexes reliably: "アルベール・カミュ" → "カミュ"; "村上春樹" stays whole. */
        fun familyName(ja: String): String = ja.split('・', '･', '=', '＝').last().trim()

        private val PRIORITY = listOf(Source.NDL, Source.WIKIDATA, Source.OPEN_LIBRARY)

        /** One edition per ISBN; records without one are kept apart unless title, publisher and year agree. */
        fun merge(records: List<Record>): List<Edition> {
            val groups = LinkedHashMap<String, MutableList<Record>>()
            for (r in records) {
                val key = r.isbn13 ?: "t:" + norm(r.title) + "|" + norm(r.publisher ?: "") + "|" + (r.year ?: "")
                groups.getOrPut(key) { ArrayList() } += r
            }
            return groups.values.map { g ->
                val sorted = g.sortedBy { PRIORITY.indexOf(it.source) }
                val (lang, why) = language(sorted)
                val title = (sorted.firstOrNull { lang != null && scriptFits(it.title, lang) }?.title ?: sorted.first().title)
                    .trim().trimEnd('.', '/', ':', ';', ',').trim().ifEmpty { sorted.first().title }
                Edition(
                    g.first().isbn13, title, lang, why,
                    sorted.firstNotNullOfOrNull { it.publisher?.takeIf(String::isNotBlank) },
                    sorted.firstNotNullOfOrNull { it.year },
                    Translators.merge(sorted.map { it.translators }),
                    sorted.map { it.source }.toSortedSet(),
                    sorted.map { it.link }.filter { it.isNotEmpty() }.distinct(),
                )
            }
        }

        /** A title written in the script of its language: Japanese editions show ノルウェイの森, not "Noruwei no mori". */
        fun scriptFits(title: String, lang: String): Boolean = when (lang) {
            "ja" -> title.any { Lang.isKana(it) || Lang.isHan(it) }
            "ko" -> title.any(Lang::isHangul)
            "zh-Hant", "zh-Hans", "zh" -> title.any(Lang::isHan)
            else -> title.none { Lang.isHan(it) || Lang.isKana(it) || Lang.isHangul(it) }
        }

        /**
         * The language of an edition. A catalogue's word comes first (a note on the text itself
         * before a coded field); Chinese is then split by the ISBN's area (Taiwan, Hong Kong and
         * Macau print in traditional characters, mainland China in simplified ones) or by the title.
         * Without a word from any catalogue, the title's script and the ISBN's area decide.
         */
        fun language(records: List<Record>): Pair<String?, Why> {
            val area = records.firstNotNullOfOrNull { r -> r.isbn13?.let { Isbn.area(it) } }
            val declared = records.firstNotNullOfOrNull { it.textLanguage }
                ?: records.mapNotNull { it.language }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            fun zh(): String = area?.language?.takeIf { it.startsWith("zh-") }
                ?: records.firstNotNullOfOrNull { r -> Lang.chinese(r.title) } ?: "zh"
            if (declared != null) return (if (declared == "zh") zh() else declared) to Why.DECLARED
            val script = records.firstNotNullOfOrNull { Lang.fromScript(it.title) }
            val cjk = { l: String? -> l != null && (l == "ja" || l == "ko" || l.startsWith("zh")) }
            return when {
                script == null && area?.language != null -> area.language to Why.ISBN
                script == null -> null to Why.UNKNOWN
                // Japan, Korea, Taiwan, Hong Kong and mainland China publish in their own language: the ISBN beats a
                // title that a catalogue may have copied from the original.
                cjk(script) && cjk(area?.language) -> area!!.language to Why.ISBN
                // A Chinese title on a French ISBN: one of the two is wrong, and Hashi cannot tell which.
                area?.language != null && area.language != "en" && !cjk(area.language) -> null to Why.UNKNOWN
                script == "zh" -> zh() to Why.SCRIPT
                else -> script to Why.SCRIPT
            }
        }

        /**
         * Print-on-demand reprints (ISBNs from 979-8, "Independently Published", CreateSpace) are often unauthorised
         * copies of a translation: Hashi lists them last.
         */
        fun printOnDemand(e: Edition): Boolean = e.isbn13?.startsWith("9798") == true ||
            e.publisher?.let { Regex("(?i)independently published|createspace").containsMatchIn(it) } == true

        /**
         * Shelves in a fixed order (French, English, Japanese, Korean, then both Chinese scripts), then the other
         * languages. On a shelf, editions known to more catalogues come first, then the newest; print-on-demand last.
         */
        fun shelve(editions: List<Edition>): Map<String, List<Edition>> {
            val byLang = editions.groupBy { it.language ?: "und" }
            val order = Lang.SHELVES + byLang.keys.filter { it !in Lang.SHELVES && it != "und" }
                .sortedWith(compareByDescending<String> { byLang[it]!!.size }.thenBy { it }) + listOf("und")
            return order.associateWith { l ->
                (byLang[l] ?: emptyList()).sortedWith(
                    compareBy<Edition>({ printOnDemand(it) }, { it.isbn13 == null }, { -it.sources.size }, { -(it.year ?: 0) }, { it.isbn13 ?: "" }, { it.title }),
                )
            }.filter { (l, e) -> l in Lang.SHELVES || e.isNotEmpty() }
        }
    }
}
