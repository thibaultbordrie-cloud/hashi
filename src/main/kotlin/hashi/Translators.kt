package hashi

/**
 * Finds translators' names in the statements catalogues copy from title pages:
 * "roman traduit du japonais par Rose-Marie Makino-Fayolle", "translated by Deborah Smith",
 * "窪田啓作 訳", "賴明珠 譯", "김난주 옮김", "Lin Shaohua yi".
 */
object Translators {
    private val BEFORE = listOf(
        Regex("""(?i)\btradu(?:it|ite|its|ites)\b[^;]*?\bpar\s+(.+)"""),
        Regex("""(?i)\btraduction\s+(?:de|d')\s*(?:l'\S+\s+)?(?:par\s+)?(.+)"""),
        Regex("""(?i)\btrad\.\s*(?:de\s+l'\S+\s+)?(?:par\s+)?(.+)"""),
        Regex("""(?i)\btranslated\b[^;]*?\bby\s+(.+)"""),
        Regex("""(?i)\b(?:traduzione|traducci[oó]n|tradu[cç][aã]o|trandu[cç][aã]o|[üu]bersetzung|[üu]bersetzt)\b[^;]*?\b(?:di|de|von|by|par)\s+(.+)"""),
        Regex("""(?i)\btrans(?:lation)?\.?\s+by\s+(.+)"""),
        Regex("""(?:訳者|翻訳者|譯者|译者|번역자?)\s*[:：]\s*(.+)"""),
        Regex("""^\s*(?:訳|譯|译)\s*[:：]\s*(.+)"""),
    )
    private val AFTER = Regex("""^(.+?)\s*(?:翻訳|翻譯|翻译|訳注|訳・解説|訳|譯|译|옮김|번역|역)\s*$""")
    private val PINYIN_YI = Regex("""^(.+?)\s+yi$""")
    private val SPLIT = Regex("""\s*(?:,|、|，|;|；|\bet\b|\band\b|&|/)\s*""")

    /** Translators named in one statement, in their order, without duplicates. */
    fun fromStatement(statement: String?): List<String> {
        val s = statement?.trim() ?: return emptyList()
        if (s.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        for (part in s.split(Regex("""\s*[;；]\s*|\s+/\s+|\s*\]\s*"""))) {
            val p = part.trim().trim('[', ']', '.').trim()
            if (p.isEmpty()) continue
            val m = BEFORE.firstNotNullOfOrNull { it.find(p) }
            if (m != null) {
                names(m.groupValues[1]).forEach(out::add)
                continue
            }
            for (piece in p.split(Regex("""\s*[,，、]\s*"""))) {
                (AFTER.find(piece.trim()) ?: PINYIN_YI.find(piece.trim()))?.let { names(it.groupValues[1]).forEach(out::add) }
            }
        }
        return out.toList()
    }

    private fun names(raw: String): List<String> =
        raw.split(SPLIT).map { clean(it) }.filter { n -> n.length in 2..80 && n.any { it.isLetter() } }

    private fun clean(n: String): String =
        n.trim().trimEnd('.', ',', ':', ']', ')').trimStart('[', '(').replace(Regex("""\s+"""), " ").trim()

    /** "Makino-Fayolle, Rose-Marie" and "Rose-Marie Makino-Fayolle" are the same person; so are "賴明珠" and "頼明珠". */
    fun key(name: String): String {
        val n = name.lowercase().replace('頼', '賴').replace(Regex("""[\s.,'’・·\-]"""), "")
        return n.toCharArray().sorted().joinToString("")
    }

    fun merge(lists: List<List<String>>): List<String> {
        val seen = LinkedHashMap<String, String>()
        for (l in lists) for (n in l) seen.putIfAbsent(key(n), n)
        return seen.values.toList()
    }
}
