package hashi

/**
 * Languages as Hashi files them: BCP 47 codes, with Chinese split by script, because a
 * bookshop in Taipei sells 繁體 editions and one in Shanghai sells 简体 ones.
 */
object Lang {
    /** The shelves Hashi always shows, in this order, even when they are empty. */
    val SHELVES = listOf("fr", "en", "ja", "ko", "zh-Hant", "zh-Hans")

    /** Each language's name in itself. */
    val NATIVE = mapOf(
        "fr" to "Français", "en" to "English", "ja" to "日本語", "ko" to "한국어",
        "zh-Hant" to "繁體中文", "zh-Hans" to "简体中文", "zh" to "中文",
        "de" to "Deutsch", "es" to "Español", "it" to "Italiano", "pt" to "Português",
        "ru" to "Русский", "nl" to "Nederlands", "cs" to "Čeština", "pl" to "Polski",
        "sv" to "Svenska", "da" to "Dansk", "no" to "Norsk", "fi" to "Suomi",
        "et" to "Eesti", "hu" to "Magyar", "tr" to "Türkçe", "ar" to "العربية",
        "he" to "עברית", "vi" to "Tiếng Việt", "th" to "ไทย", "id" to "Bahasa Indonesia",
        "gl" to "Galego", "ca" to "Català", "el" to "Ελληνικά", "ro" to "Română", "uk" to "Українська",
    )

    /** MARC / ISO 639-2 codes, as Open Library and the NDL write them. */
    private val THREE = mapOf(
        "fre" to "fr", "fra" to "fr", "eng" to "en", "jpn" to "ja", "kor" to "ko",
        "chi" to "zh", "zho" to "zh", "ger" to "de", "deu" to "de", "spa" to "es",
        "ita" to "it", "por" to "pt", "rus" to "ru", "dut" to "nl", "nld" to "nl",
        "cze" to "cs", "ces" to "cs", "pol" to "pl", "swe" to "sv", "dan" to "da",
        "nor" to "no", "nob" to "no", "fin" to "fi", "est" to "et", "hun" to "hu",
        "tur" to "tr", "ara" to "ar", "heb" to "he", "vie" to "vi", "tha" to "th",
        "ind" to "id", "glg" to "gl", "cat" to "ca", "gre" to "el", "ell" to "el",
        "rum" to "ro", "ron" to "ro", "ukr" to "uk",
    )

    /** "fre" → "fr", "zh-tw" → "zh-Hant", "FR" → "fr"; null when the code means nothing to Hashi. */
    fun code(raw: String?): String? {
        val s = raw?.trim()?.lowercase() ?: return null
        if (s.isEmpty()) return null
        return when (s) {
            "zh-hant", "zh-tw", "zh-hk", "zh-mo" -> "zh-Hant"
            "zh-hans", "zh-cn", "zh-sg", "zh-my" -> "zh-Hans"
            "nb", "nn" -> "no"
            else -> THREE[s] ?: s.takeIf { it.length == 2 && it.all { c -> c in 'a'..'z' } }
        }
    }

    /** Japanese names of languages as the NDL writes them in notes ("本文は中国語"). */
    private val JA_NAMES = mapOf(
        "日本語" to "ja", "中国語" to "zh", "英語" to "en", "フランス語" to "fr", "仏語" to "fr",
        "韓国語" to "ko", "朝鮮語" to "ko", "ハングル" to "ko", "ドイツ語" to "de", "スペイン語" to "es",
    )

    fun fromJapaneseName(name: String): String? = JA_NAMES[name.trim()]

    fun isKana(c: Char) = c in '぀'..'ヿ' || c in 'ㇰ'..'ㇿ' || c in 'ｦ'..'ﾝ'
    fun isHangul(c: Char) = c in '가'..'힣' || c in 'ᄀ'..'ᇿ' || c in '㄰'..'㆏'
    fun isHan(c: Char) = Character.UnicodeScript.of(c.code) == Character.UnicodeScript.HAN

    /**
     * The language a title's script gives away: kana means Japanese, hangul Korean, Chinese
     * characters alone Chinese (in one script or the other when it shows). Latin script says nothing.
     */
    fun fromScript(text: String): String? = when {
        text.any(::isHangul) -> "ko"
        text.any(::isKana) -> "ja"
        text.any(::isHan) -> chinese(text) ?: "zh"
        else -> null
    }

    /** Characters that exist in one Chinese script only: (traditional, simplified). */
    private const val PAIRS =
        "們们國国說说這这來来會会對对時时個个學学樣样過过為为與与東东長长開开書书愛爱體体" +
        "麗丽夢梦風风島岛無无問问間间聽听見见頭头門门車车馬马魚鱼鳥鸟龍龙語语讀读記记" +
        "紅红綠绿藍蓝黃黄滅灭劍剑戰战亂乱邊边遠远陽阳陰阴歲岁壽寿傳传殺杀雙双飛飞" +
        "憶忆鄉乡戀恋歡欢淚泪燈灯塵尘雜杂瑪玛麼么嗎吗終终結结鐘钟"

    private val TRAD: Set<Char> = PAIRS.filterIndexed { i, _ -> i % 2 == 0 }.toSet()
    private val SIMP: Set<Char> = PAIRS.filterIndexed { i, _ -> i % 2 == 1 }.toSet()

    /** "zh-Hant" or "zh-Hans" when the text uses a character of only one script, else null. */
    fun chinese(text: String): String? {
        val t = text.count { it in TRAD }
        val s = text.count { it in SIMP }
        return when {
            t > s -> "zh-Hant"
            s > t -> "zh-Hans"
            else -> null
        }
    }

    fun name(code: String?): String = code?.let { NATIVE[it] ?: it } ?: "?"
}
