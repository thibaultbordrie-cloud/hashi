package hashi

import java.net.URLEncoder

/**
 * Open Library (openlibrary.org, Internet Archive): many editions, often with the translator
 * on the title page statement, sometimes without a language. Hashi asks for few fields.
 */
class OpenLibrary(private val fetch: Fetch, private val base: String = "https://openlibrary.org") {
    data class Hit(val key: String, val title: String, val authors: List<String>, val authorKeys: List<String>, val wikidata: List<String>, val editions: Int)

    private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)

    private val fields = "key,title,author_name,author_key,id_wikidata,edition_count"

    fun search(title: String, author: String?): List<Hit> {
        val url = "$base/search.json?title=${enc(title)}" + (author?.let { "&author=${enc(it)}" } ?: "") + "&fields=$fields&limit=5"
        return hits(fetch.get(url))
    }

    fun byIsbn(isbn13: String): List<Hit> = hits(fetch.get("$base/search.json?isbn=$isbn13&fields=$fields&limit=3"))

    @Suppress("UNCHECKED_CAST")
    private fun hits(body: String?): List<Hit> {
        val root = body?.let { Json.parse(it) } as? Map<String, Any?> ?: return emptyList()
        return root.list("docs").mapNotNull { d ->
            val m = d as? Map<String, Any?> ?: return@mapNotNull null
            val key = m.strOrNull("key")?.takeIf { WORK.matches(it) } ?: return@mapNotNull null
            Hit(
                key.removePrefix("/works/"), m.strOrNull("title") ?: "",
                m.list("author_name").filterIsInstance<String>(), m.list("author_key").filterIsInstance<String>(),
                m.list("id_wikidata").filterIsInstance<String>().filter { QID.matches(it) },
                (m["edition_count"] as? Number)?.toInt() ?: 0,
            )
        }
    }

    /** The Wikidata item of an Open Library author, when Open Library knows it. */
    @Suppress("UNCHECKED_CAST")
    fun authorWikidata(authorKey: String): String? {
        require(AUTHOR.matches(authorKey))
        val m = fetch.get("$base/authors/$authorKey.json")?.let { Json.parse(it) } as? Map<String, Any?> ?: return null
        return (m["remote_ids"] as? Map<String, Any?>)?.strOrNull("wikidata")?.takeIf { QID.matches(it) }
    }

    /** Every edition of one work (Open Library pages them by 100; Hashi reads up to 300). */
    @Suppress("UNCHECKED_CAST")
    fun editions(workId: String): List<Record> {
        require(WORK_ID.matches(workId))
        val out = ArrayList<Record>()
        var offset = 0
        while (offset < 300) {
            val root = fetch.get("$base/works/$workId/editions.json?limit=100" + if (offset > 0) "&offset=$offset" else "")
                ?.let { Json.parse(it) } as? Map<String, Any?> ?: break
            val entries = root.list("entries")
            entries.mapNotNullTo(out) { record(it as? Map<String, Any?> ?: return@mapNotNullTo null) }
            val size = (root["size"] as? Number)?.toInt() ?: 0
            offset += 100
            if (entries.size < 100 || offset >= size) break
        }
        return out
    }

    @Suppress("UNCHECKED_CAST")
    private fun record(e: Map<String, Any?>): Record? {
        val title = listOfNotNull(e.strOrNull("title"), e.strOrNull("subtitle")).joinToString(" : ").trim()
        if (title.isEmpty()) return null
        val isbn = (e.list("isbn_13") + e.list("isbn_10")).filterIsInstance<String>().firstNotNullOfOrNull { Isbn.normalize(it) }
        val lang = e.list("languages").firstNotNullOfOrNull { ((it as? Map<String, Any?>)?.strOrNull("key"))?.substringAfterLast('/') }
        val contributors = e.list("contributors").mapNotNull { c ->
            val m = c as? Map<String, Any?> ?: return@mapNotNull null
            m.strOrNull("name")?.takeIf { m.strOrNull("role")?.contains("transl", ignoreCase = true) == true }
        }
        val translators = Translators.merge(listOf(contributors, Translators.fromStatement(e.strOrNull("by_statement"))))
        return Record(
            Source.OPEN_LIBRARY, base + (e.strOrNull("key") ?: ""), title, isbn, Lang.code(lang),
            e.list("publishers").filterIsInstance<String>().firstOrNull(), year(e.strOrNull("publish_date")), translators,
        )
    }

    companion object {
        val WORK = Regex("^/works/OL\\d+W$")
        val WORK_ID = Regex("^OL\\d+W$")
        val AUTHOR = Regex("^OL\\d+A$")
        val QID = Regex("^Q\\d+$")

        /** The year in "1987", "March 1, 1999", "2003-02-14" or "[c1987]". */
        fun year(raw: String?): Int? = raw?.let { Regex("(1[5-9]\\d\\d|20\\d\\d)").find(it)?.value?.toInt() }
    }
}
