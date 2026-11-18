package hashi

/**
 * ISBN-10 and ISBN-13 (ISO 2108). Hashi keys every edition by its ISBN-13 without hyphens.
 * It also reads the registration group (978-4, 978-957, 979-11…), which says where the book
 * was registered: that is how Hashi tells a Taiwan edition from a mainland one.
 */
object Isbn {
    /** Keeps digits (full-width ones become ASCII) and a final X; drops hyphens and spaces. */
    fun clean(raw: String): String {
        val sb = StringBuilder()
        for (c in raw.trim()) {
            when {
                c in '0'..'9' -> sb.append(c)
                c in '０'..'９' -> sb.append('0' + (c - '０'))
                c == 'x' || c == 'X' || c == 'Ｘ' || c == 'ｘ' -> sb.append('X')
                c == '-' || c == ' ' || c == '‐' || c == '－' || c == '　' -> {}
                else -> return ""
            }
        }
        return sb.toString()
    }

    fun check13(first12: String): Char {
        require(first12.length == 12 && first12.all { it.isDigit() })
        val sum = first12.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
        return '0' + (10 - sum % 10) % 10
    }

    fun check10(first9: String): Char {
        require(first9.length == 9 && first9.all { it.isDigit() })
        val sum = first9.mapIndexed { i, c -> (c - '0') * (10 - i) }.sum()
        val r = (11 - sum % 11) % 11
        return if (r == 10) 'X' else '0' + r
    }

    fun isValid13(s: String) =
        s.length == 13 && s.all { it.isDigit() } && (s.startsWith("978") || s.startsWith("979")) && check13(s.take(12)) == s[12]

    fun isValid10(s: String) =
        s.length == 10 && s.take(9).all { it.isDigit() } && (s[9].isDigit() || s[9] == 'X') && check10(s.take(9)) == s[9]

    /** The ISBN-13 for any valid input, or null. */
    fun normalize(raw: String): String? {
        val s = clean(raw)
        return when {
            isValid13(s) -> s
            isValid10(s) -> to13(s)
            else -> null
        }
    }

    fun to13(isbn10: String): String {
        val body = "978" + isbn10.take(9)
        return body + check13(body)
    }

    /**
     * The prefix and registration group, as "978-4" or "979-11". For 978 the group's length
     * follows from its first digits (ISO 2108): 0–5 and 7 one digit, 600–649 three (65: two),
     * 80–94 two, 950–989 three,
     * 9900–9989 four, 99900–99999 five. For 979, groups 10–13 have two digits and 8 has one.
     */
    fun group(isbn13: String): String? {
        if (!isValid13(isbn13)) return null
        val p = isbn13.take(3)
        val r = isbn13.substring(3)
        val len = if (p == "978") {
            val two = r.take(2).toInt()
            val three = r.take(3).toInt()
            val four = r.take(4).toInt()
            when {
                r[0] in '0'..'5' || r[0] == '7' -> 1
                two == 65 -> 2
                r[0] == '6' -> 3
                two in 80..94 -> 2
                three in 950..989 -> 3
                four in 9900..9989 -> 4
                else -> 5
            }
        } else {
            when {
                r[0] == '8' -> 1
                r.take(2).toInt() in 10..13 -> 2
                else -> return null
            }
        }
        return "$p-${r.take(len)}"
    }

    /** Where a registration group sits, and the language it usually means. */
    data class Area(val code: String, val language: String?)

    private val AREAS = mapOf(
        "978-0" to Area("EN", "en"), "978-1" to Area("EN", "en"),
        "978-2" to Area("FR", "fr"), "979-10" to Area("FR", "fr"),
        "978-3" to Area("DE", "de"), "978-4" to Area("JP", "ja"),
        "978-5" to Area("RU", "ru"), "978-7" to Area("CN", "zh-Hans"),
        "978-84" to Area("ES", "es"), "978-85" to Area("BR", "pt"), "978-65" to Area("BR", "pt"),
        "978-88" to Area("IT", "it"), "979-12" to Area("IT", "it"),
        "978-89" to Area("KR", "ko"), "979-11" to Area("KR", "ko"),
        "978-957" to Area("TW", "zh-Hant"), "978-986" to Area("TW", "zh-Hant"), "978-626" to Area("TW", "zh-Hant"),
        "978-962" to Area("HK", "zh-Hant"), "978-988" to Area("HK", "zh-Hant"),
        "978-99937" to Area("MO", "zh-Hant"), "978-99965" to Area("MO", "zh-Hant"),
        "979-8" to Area("US", "en"),
    )

    /** The area of a group Hashi knows, or null: then the ISBN says nothing about the language. */
    fun area(isbn13: String): Area? = group(isbn13)?.let { AREAS[it] }
}
