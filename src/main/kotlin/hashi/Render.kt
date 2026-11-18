package hashi

/** The words of Hashi's answer, in the four languages of its users. */
object Text {
    val LOCALES = listOf("en", "fr", "ja", "zh-Hant")

    private val T: Map<String, Map<String, String>> = mapOf(
        "en" to mapOf(
            "by" to "by {a}", "written" to "written in {l}", "original" to "original",
            "editions" to "{n} editions", "edition" to "1 edition",
            "none" to "nothing in Open Library, Wikidata or the NDL",
            "tr" to "tr. {t}", "more" to "… {n} more (use --all)",
            "approx" to "≈ language read from the ISBN or the title, not stated by a catalogue",
            "unreachable" to "{s} could not be reached: this answer may be incomplete.",
            "nothing" to "Nothing found for “{q}”.",
            "sources" to "Sources: Open Library, Wikidata (CC0), 国立国会図書館書誌データをもとにHashiが作成.",
            "und" to "Language unknown", "others" to "Other languages (--all to list them): {l}",
        ),
        "fr" to mapOf(
            "by" to "de {a}", "written" to "écrit en {l}", "original" to "langue originale",
            "editions" to "{n} éditions", "edition" to "1 édition",
            "none" to "rien dans Open Library, Wikidata ni la NDL",
            "tr" to "trad. {t}", "more" to "… et {n} autres (option --all)",
            "approx" to "≈ langue déduite de l'ISBN ou du titre, pas indiquée par un catalogue",
            "unreachable" to "{s} n'a pas répondu : la réponse peut être incomplète.",
            "nothing" to "Rien trouvé pour « {q} ».",
            "sources" to "Sources : Open Library, Wikidata (CC0), 国立国会図書館書誌データをもとにHashiが作成.",
            "und" to "Langue inconnue", "others" to "Autres langues (--all pour les lister) : {l}",
        ),
        "ja" to mapOf(
            "by" to "{a}", "written" to "原語：{l}", "original" to "原語",
            "editions" to "{n}版", "edition" to "1版",
            "none" to "Open Library・Wikidata・国立国会図書館に見つかりません",
            "tr" to "{t} 訳", "more" to "…ほか{n}版（--all で全件）",
            "approx" to "≈ 目録に記載がなく、ISBNまたは書名から推定した言語",
            "unreachable" to "{s}に接続できませんでした。結果が不完全な可能性があります。",
            "nothing" to "「{q}」は見つかりませんでした。",
            "sources" to "出典：Open Library、Wikidata（CC0）、国立国会図書館書誌データをもとにHashiが作成。",
            "und" to "言語不明", "others" to "その他の言語（--all で一覧）：{l}",
        ),
        "zh-Hant" to mapOf(
            "by" to "{a} 著", "written" to "原文：{l}", "original" to "原文",
            "editions" to "{n} 種版本", "edition" to "1 種版本",
            "none" to "Open Library、Wikidata 與日本國立國會圖書館皆查無版本",
            "tr" to "{t} 譯", "more" to "…另有 {n} 種（加 --all 顯示全部）",
            "approx" to "≈ 書目未註明語言，由 ISBN 或書名推定",
            "unreachable" to "無法連線至 {s}，結果可能不完整。",
            "nothing" to "查無「{q}」。",
            "sources" to "資料來源：Open Library、Wikidata（CC0）、国立国会図書館書誌データをもとにHashiが作成。",
            "und" to "語言不明", "others" to "其他語言（加 --all 列出）：{l}",
        ),
    )

    fun locale(raw: String?): String = Lang.code(raw)?.takeIf { it in LOCALES } ?: if (Lang.code(raw)?.startsWith("zh") == true) "zh-Hant" else "en"

    fun t(locale: String, key: String, vararg args: Pair<String, Any>): String {
        var s = T[locale]?.get(key) ?: T.getValue("en").getValue(key)
        for ((k, v) in args) s = s.replace("{$k}", v.toString())
        return s
    }

    fun keys(locale: String): Set<String> = T.getValue(locale).keys
}

/** Prints a report for a person (text) or for a program (JSON). */
object Render {
    fun text(r: Report, locale: String, all: Boolean = false, perShelf: Int = 6): String {
        val sb = StringBuilder()
        val w = r.work
        val main = w.title(locale) ?: w.title("en") ?: w.titles.values.firstOrNull() ?: r.query
        val originalTitle = w.original?.let { w.title(it) }?.takeIf { it != main }
        sb.append("橋 ").append(main).append(originalTitle?.let { " · $it" } ?: "").append('\n')
        val facts = ArrayList<String>()
        w.authors.mapNotNull { a -> a.name(locale) ?: a.name("en") ?: a.names.values.firstOrNull() }.takeIf { it.isNotEmpty() }
            ?.let { facts += Text.t(locale, "by", "a" to it.joinToString(", ")) }
        w.original?.let { facts += Text.t(locale, "written", "l" to Lang.name(it)) }
        w.qid?.let { facts += "Wikidata $it" }
        if (w.openLibrary.isNotEmpty()) facts += "Open Library " + w.openLibrary.take(3).joinToString(" ")
        if (facts.isNotEmpty()) sb.append("  ").append(facts.joinToString(" · ")).append('\n')

        var approx = false
        val others = ArrayList<String>()
        for ((lang, eds) in r.shelves) {
            if (!all && lang !in Lang.SHELVES) {
                others += (if (lang == "und") Text.t(locale, "und") else Lang.name(lang)) + " " + eds.size
                continue
            }
            sb.append('\n')
            val name = if (lang == "und") Text.t(locale, "und") else Lang.name(lang)
            val shelfTitle = w.title(lang) ?: eds.groupingBy { it.title }.eachCount().maxByOrNull { it.value }?.key
            sb.append(name)
            if (w.original != null && lang.substringBefore('-') == w.original) sb.append(" (").append(Text.t(locale, "original")).append(')')
            if (shelfTitle != null && lang != "und") sb.append(" · ").append(shelfTitle)
            if (eds.isEmpty()) {
                sb.append(" — ").append(Text.t(locale, "none")).append('\n')
                continue
            }
            sb.append(" · ").append(if (eds.size == 1) Text.t(locale, "edition") else Text.t(locale, "editions", "n" to eds.size)).append('\n')
            val shown = if (all) eds else eds.take(perShelf)
            for (e in shown) {
                val mark = if (e.why == Why.DECLARED) " " else "≈"
                if (e.why != Why.DECLARED) approx = true
                val parts = ArrayList<String>()
                parts += e.isbn13 ?: "—".padEnd(13)
                if (shelfTitle == null || !Hashi.same(e.title, shelfTitle)) parts += if (e.title.length > 70) e.title.take(69) + "…" else e.title
                listOfNotNull(e.publisher, e.year?.toString()).takeIf { it.isNotEmpty() }?.let { parts += it.joinToString(" ") }
                if (e.translators.isNotEmpty()) parts += Text.t(locale, "tr", "t" to e.translators.joinToString(", "))
                parts += "[" + e.sources.joinToString(", ") { it.label } + "]"
                sb.append(' ').append(mark).append(' ').append(parts.joinToString("  ")).append('\n')
            }
            if (shown.size < eds.size) sb.append("    ").append(Text.t(locale, "more", "n" to eds.size - shown.size)).append('\n')
        }
        if (others.isNotEmpty()) sb.append('\n').append(Text.t(locale, "others", "l" to others.joinToString(" · "))).append('\n')
        sb.append('\n')
        for (n in r.notes) if (n.startsWith("unreachable:") || n.startsWith("unreadable:")) {
            sb.append(Text.t(locale, "unreachable", "s" to n.split(':')[1])).append('\n')
        }
        if (approx) sb.append(Text.t(locale, "approx")).append('\n')
        sb.append(Text.t(locale, "sources")).append('\n')
        return sb.toString()
    }

    fun json(r: Report): String = Json.write(
        linkedMapOf(
            "query" to r.query,
            "work" to linkedMapOf(
                "wikidata" to r.work.qid,
                "openLibrary" to r.work.openLibrary.toList(),
                "titles" to r.work.titles,
                "authors" to r.work.authors.map { linkedMapOf("wikidata" to it.qid, "names" to it.names) },
                "original" to r.work.original,
            ),
            "shelves" to r.shelves.mapValues { (_, eds) ->
                eds.map { e ->
                    linkedMapOf(
                        "isbn13" to e.isbn13, "title" to e.title, "language" to e.language, "languageFrom" to e.why.name.lowercase(),
                        "publisher" to e.publisher, "year" to e.year, "translators" to e.translators,
                        "sources" to e.sources.map { it.label }, "links" to e.links,
                    )
                }
            },
            "notes" to r.notes,
        ),
    )
}
