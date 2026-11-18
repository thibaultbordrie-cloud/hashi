package hashi

/** Where a fact comes from. Every edition Hashi shows says which catalogues know it. */
enum class Source(val label: String) {
    OPEN_LIBRARY("Open Library"),
    WIKIDATA("Wikidata"),
    NDL("NDL"),
}

/** Why Hashi files an edition under a language: said by a catalogue, read from the ISBN, or seen in the title. */
enum class Why { DECLARED, ISBN, SCRIPT, UNKNOWN }

/** One record of one edition, as one catalogue gives it. */
data class Record(
    val source: Source,
    val link: String,
    val title: String,
    val isbn13: String?,
    val language: String?,
    val publisher: String?,
    val year: Int?,
    val translators: List<String>,
    /** A note that gives the language of the text away, such as the NDL's "本文は中国語". */
    val textLanguage: String? = null,
)

/** An edition after merging the records of every catalogue that knows it. */
data class Edition(
    val isbn13: String?,
    val title: String,
    val language: String?,
    val why: Why,
    val publisher: String?,
    val year: Int?,
    val translators: List<String>,
    val sources: Set<Source>,
    val links: List<String>,
)

data class Author(val qid: String?, val names: Map<String, String>) {
    fun name(lang: String): String? = names[lang] ?: names[lang.substringBefore('-')]
}

/** The work every edition belongs to: its titles in each language, its authors, the language it was written in. */
data class Work(
    val qid: String?,
    val openLibrary: Set<String>,
    val titles: Map<String, String>,
    val authors: List<Author>,
    val original: String?,
) {
    fun title(lang: String): String? = titles[lang] ?: titles[lang.substringBefore('-')]
}

/** What Hashi found: the work, its editions by language, and what it could not check. */
data class Report(
    val query: String,
    val work: Work,
    val shelves: Map<String, List<Edition>>,
    val notes: List<String>,
) {
    val editions: List<Edition> get() = shelves.values.flatten()
}
