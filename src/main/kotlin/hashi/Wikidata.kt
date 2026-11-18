package hashi

import java.net.URLEncoder

/**
 * Wikidata (CC0): knows a work's title in many languages and links it to Open Library.
 * It lists fewer editions than Open Library, but each with its language and translators.
 */
class Wikidata(private val fetch: Fetch, private val endpoint: String = "https://query.wikidata.org/sparql") {
    /** Label languages Hashi reads: its shelves, their regional variants, and "mul" (one name for every language). */
    private val labelLangs = listOf("fr", "en", "ja", "ko", "zh", "zh-hant", "zh-tw", "zh-hk", "zh-hans", "zh-cn", "mul")
    private val inLangs = labelLangs.joinToString(",") { "\"$it\"" }

    data class Candidate(val qid: String, val rank: Int)

    data class Details(
        val qid: String,
        val labels: Map<String, String>,
        val sitelinks: Int,
        val original: String?,
        val authors: List<Author>,
        val openLibrary: Set<String>,
    )

    private fun rows(query: String): List<Map<String, String>> {
        val body = fetch.get("$endpoint?format=json&query=" + URLEncoder.encode(query.replace(Regex("\\s+"), " ").trim(), Charsets.UTF_8)) ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        val root = Json.parse(body) as? Map<String, Any?> ?: throw FetchError("Wikidata: unreadable answer")
        return root.obj("results").list("bindings").mapNotNull { b ->
            @Suppress("UNCHECKED_CAST")
            (b as? Map<String, Any?>)?.mapNotNull { (k, v) ->
                val cell = v as? Map<String, Any?> ?: return@mapNotNull null
                val value = cell.strOrNull("value") ?: return@mapNotNull null
                val lang = cell.strOrNull("xml:lang")
                (if (lang != null) "$k@$lang" else k) to value
            }?.toMap()
        }
    }

    private fun qid(uri: String?): String? = uri?.substringAfterLast('/')?.takeIf { OpenLibrary.QID.matches(it) }

    /** A string literal for SPARQL: quotes, backslashes and line breaks escaped, length capped. */
    private fun lit(s: String) = "\"" + s.take(200).replace("\\", "\\\\").replace("\"", "\\\"").replace(Regex("[\\r\\n\\t]"), " ") + "\""

    /**
     * Works whose label or alias matches the text (an edition found leads to its work).
     * The optimizer is told to keep the written order: search first, then the few items found.
     */
    fun search(text: String, lang: String): List<Candidate> {
        val wl = lang.substringBefore('-').lowercase().takeIf { it.length == 2 } ?: "en"
        return rows(
            """
            SELECT ?work (MIN(?rank) AS ?r) WHERE {
              hint:Query hint:optimizer "None" .
              SERVICE wikibase:mwapi { bd:serviceParam wikibase:endpoint "www.wikidata.org"; wikibase:api "EntitySearch";
                mwapi:search ${lit(text)}; mwapi:language "$wl"; mwapi:limit "12" .
                ?item wikibase:apiOutputItem mwapi:item . ?rank wikibase:apiOrdinal true . }
              OPTIONAL { ?item wdt:P629 ?w . }
              BIND(COALESCE(?w, ?item) AS ?work)
              ?work wdt:P50 [] .
            } GROUP BY ?work ORDER BY ?r LIMIT 5
            """,
        ).mapNotNull { r -> qid(r["work"])?.let { Candidate(it, r["r"]?.toIntOrNull() ?: 99) } }
    }

    /** Works linked to these Open Library works (directly, or through one of their editions). */
    fun byOpenLibrary(workIds: Collection<String>): List<String> {
        val ids = workIds.filter { OpenLibrary.WORK_ID.matches(it) }.take(10)
        if (ids.isEmpty()) return emptyList()
        return rows(
            """
            SELECT DISTINCT ?work WHERE {
              VALUES ?ol { ${ids.joinToString(" ") { "\"$it\"" }} }
              { ?work wdt:P648 ?ol . } UNION { ?ed wdt:P648 ?ol ; wdt:P629 ?work . }
              FILTER NOT EXISTS { ?work wdt:P629 [] }
            }
            """,
        ).mapNotNull { qid(it["work"]) }.distinct()
    }

    /** An item given by Open Library may be an edition: this returns its work, or the item itself. */
    fun workOf(qids: Collection<String>): List<String> {
        val ids = qids.filter { OpenLibrary.QID.matches(it) }.take(10)
        if (ids.isEmpty()) return emptyList()
        return rows(
            """
            SELECT DISTINCT ?work WHERE {
              hint:Query hint:optimizer "None" .
              VALUES ?item { ${ids.joinToString(" ") { "wd:$it" }} }
              OPTIONAL { ?item wdt:P629 ?w . }
              BIND(COALESCE(?w, ?item) AS ?work)
              ?work wdt:P50 [] .
            }
            """,
        ).mapNotNull { qid(it["work"]) }.distinct()
    }

    /** Works of one author, with their labels, to match a title against. */
    fun byAuthor(authorQid: String): Map<String, Set<String>> {
        require(OpenLibrary.QID.matches(authorQid))
        val out = LinkedHashMap<String, MutableSet<String>>()
        for (r in rows(
            """
            SELECT ?work ?label WHERE {
              ?work wdt:P50 wd:$authorQid .
              FILTER NOT EXISTS { ?work wdt:P629 [] }
              ?work rdfs:label ?label . FILTER(LANG(?label) IN ($inLangs))
            } LIMIT 3000
            """,
        )) {
            val q = qid(r["work"]) ?: continue
            r.entries.firstOrNull { it.key.startsWith("label@") }?.let { out.getOrPut(q) { LinkedHashSet() }.add(it.value) }
        }
        return out
    }

    /** Labels, fame (number of Wikipedia articles), original language, authors and Open Library links. */
    fun details(qids: Collection<String>): List<Details> {
        val ids = qids.filter { OpenLibrary.QID.matches(it) }.distinct().take(5)
        if (ids.isEmpty()) return emptyList()
        val rs = rows(
            """
            SELECT ?work ?label ?sitelinks ?orig ?author ?alabel ?ol WHERE {
              VALUES ?work { ${ids.joinToString(" ") { "wd:$it" }} }
              { ?work rdfs:label ?label . FILTER(LANG(?label) IN ($inLangs)) }
              UNION { ?work wikibase:sitelinks ?sitelinks . }
              UNION { ?work wdt:P407 ?o . ?o wdt:P424 ?orig . }
              UNION { ?work wdt:P50 ?author . OPTIONAL { ?author rdfs:label ?alabel . FILTER(LANG(?alabel) IN ($inLangs)) } }
              UNION { ?work wdt:P648 ?ol . }
            }
            """,
        )
        return ids.map { id ->
            val mine = rs.filter { qid(it["work"]) == id }
            val labels = LinkedHashMap<String, String>()
            mine.forEach { r -> r.entries.firstOrNull { it.key.startsWith("label@") }?.let { (k, v) -> labels.putIfAbsent(k.substringAfter('@'), v) } }
            val authors = LinkedHashMap<String, LinkedHashMap<String, String>>()
            mine.forEach { r ->
                val a = qid(r["author"]) ?: return@forEach
                val names = authors.getOrPut(a) { LinkedHashMap() }
                r.entries.firstOrNull { it.key.startsWith("alabel@") }?.let { (k, v) -> names.putIfAbsent(k.substringAfter('@'), v) }
            }
            Details(
                id, normalizeLabels(labels),
                mine.firstNotNullOfOrNull { it["sitelinks"]?.toIntOrNull() } ?: 0,
                mine.firstNotNullOfOrNull { it["orig"] }?.let { Lang.code(it) },
                authors.map { (q, n) -> Author(q, normalizeLabels(n)) },
                mine.mapNotNull { it["ol"]?.takeIf { o -> OpenLibrary.WORK_ID.matches(o) } }.toSet(),
            )
        }
    }

    /** Wikidata label languages to Hashi's: "zh-tw" and "zh-hant" both go to "zh-Hant"; "mul" fills the gaps. */
    private fun normalizeLabels(raw: Map<String, String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (want in listOf("zh-hant", "zh-tw", "zh-hk", "zh-hans", "zh-cn", "fr", "en", "ja", "ko", "zh")) {
            val v = raw[want] ?: continue
            out.putIfAbsent(Lang.code(want) ?: continue, v)
        }
        raw["zh"]?.let { zh -> Lang.chinese(zh)?.let { out.putIfAbsent(it, zh) } }
        raw["mul"]?.let { m -> for (l in listOf("fr", "en")) out.putIfAbsent(l, m) }
        return out
    }

    /** The editions Wikidata lists for a work. */
    fun editions(workQid: String): List<Record> {
        require(OpenLibrary.QID.matches(workQid))
        val rs = rows(
            """
            SELECT ?ed ?title ?edLabel ?lc ?isbn13 ?isbn10 ?pubLabel ?date ?trLabel WHERE {
              ?ed wdt:P629 wd:$workQid .
              OPTIONAL { ?ed wdt:P1476 ?title . }
              OPTIONAL { ?ed wdt:P407 ?l . ?l wdt:P424 ?lc . }
              OPTIONAL { ?ed wdt:P212 ?isbn13 . }
              OPTIONAL { ?ed wdt:P957 ?isbn10 . }
              OPTIONAL { ?ed wdt:P123 ?pub . }
              OPTIONAL { ?ed wdt:P577 ?date . }
              OPTIONAL { ?ed wdt:P655 ?tr . }
              SERVICE wikibase:label { bd:serviceParam wikibase:language "mul,en,fr,ja,ko,zh" . }
            } LIMIT 1000
            """,
        )
        return rs.groupBy { it["ed"] ?: "" }.filterKeys { qid(it) != null }.map { (ed, g) ->
            val title = g.firstNotNullOfOrNull { r -> r.entries.firstOrNull { it.key.startsWith("title") }?.value }
                ?: g.firstNotNullOfOrNull { r -> r.entries.firstOrNull { it.key.startsWith("edLabel") }?.value } ?: ""
            Record(
                Source.WIKIDATA, "https://www.wikidata.org/wiki/${qid(ed)}", title,
                g.firstNotNullOfOrNull { it["isbn13"]?.let(Isbn::normalize) } ?: g.firstNotNullOfOrNull { it["isbn10"]?.let(Isbn::normalize) },
                g.firstNotNullOfOrNull { Lang.code(it["lc"]) }
                    ?: g.firstNotNullOfOrNull { r -> r.keys.firstOrNull { it.startsWith("title@") }?.let { Lang.code(it.substringAfter('@')) } },
                g.firstNotNullOfOrNull { r -> r.entries.firstOrNull { it.key.startsWith("pubLabel") }?.value?.takeUnless { v -> OpenLibrary.QID.matches(v) } },
                g.firstNotNullOfOrNull { OpenLibrary.year(it["date"]) },
                Translators.merge(listOf(g.mapNotNull { r -> r.entries.firstOrNull { it.key.startsWith("trLabel") }?.value?.takeUnless { v -> OpenLibrary.QID.matches(v) } }.distinct())),
            )
        }.filter { it.title.isNotEmpty() && !OpenLibrary.QID.matches(it.title) }
    }
}
