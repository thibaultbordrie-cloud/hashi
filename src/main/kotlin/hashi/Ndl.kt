package hashi

import java.net.URLEncoder
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * The National Diet Library of Japan (NDL Search OpenSearch), restricted to the NDL's own
 * catalogue (iss-ndl-opac), whose data anyone may reuse. It knows every Japanese edition,
 * and many Chinese and Korean ones.
 */
class Ndl(private val fetch: Fetch, private val base: String = "https://ndlsearch.ndl.go.jp") {
    fun search(title: String, creator: String?): List<Record> {
        val url = "$base/api/opensearch?dpid=iss-ndl-opac&title=${enc(title)}" +
            (creator?.let { "&creator=${enc(it)}" } ?: "") + "&cnt=100"
        return fetch.get(url)?.let { parse(it) } ?: emptyList()
    }

    private fun enc(s: String) = URLEncoder.encode(s.take(200), Charsets.UTF_8)

    companion object {
        private const val DC = "http://purl.org/dc/elements/1.1/"
        private const val XSI = "http://www.w3.org/2001/XMLSchema-instance"

        /** Reads an OpenSearch RSS answer. Books only: films and recordings of the same title are left out. */
        fun parse(xml: String): List<Record> {
            val builder = secureFactory().newDocumentBuilder()
            builder.setErrorHandler(object : org.xml.sax.ErrorHandler {
                override fun warning(e: org.xml.sax.SAXParseException) {}
                override fun error(e: org.xml.sax.SAXParseException) = throw e
                override fun fatalError(e: org.xml.sax.SAXParseException) = throw e
            })
            val doc = builder.parse(xml.byteInputStream(Charsets.UTF_8))
            val items = doc.getElementsByTagName("item")
            val out = ArrayList<Record>()
            for (i in 0 until items.length) {
                val item = items.item(i) as Element
                val categories = children(item, null, "category").map { it.textContent.trim() }
                if (categories.isNotEmpty() && "図書" !in categories) continue
                val full = children(item, DC, "title").firstOrNull()?.textContent?.trim() ?: continue
                // Foreign books carry their statement of responsibility in the title: "Tokyo blues / Haruki Murakami ; traduzione di …".
                val title = full.substringBefore(" / ").trim()
                val inTitle = full.substringAfter(" / ", "")
                val ids = children(item, DC, "identifier")
                val isbn = ids.filter { it.getAttributeNS(XSI, "type").let { t -> t == "dcndl:ISBN" || t == "dcndl:ISBN13" } }
                    .firstNotNullOfOrNull { Isbn.normalize(it.textContent) }
                val descriptions = children(item, DC, "description").map { it.textContent.trim() }
                val statement = children(item, null, "author").firstOrNull()?.textContent ?: ""
                val translators = Translators.merge(listOf(Translators.fromStatement(statement), Translators.fromStatement(inTitle)) + descriptions.map { Translators.fromStatement(it) })
                val textLang = descriptions.firstNotNullOfOrNull { d -> Regex("本文は(.+?)語").find(d)?.let { Lang.fromJapaneseName(it.groupValues[1] + "語") } }
                // The NDL notes the language of the text only when it is not Japanese; a record in Japanese script
                // with no such note, and no foreign ISBN, is a Japanese edition.
                val area = isbn?.let { Isbn.area(it) }
                val lang = children(item, DC, "language").firstNotNullOfOrNull { Lang.code(it.textContent) }
                    ?: if (textLang == null && (area == null || area.code == "JP") && title.any { Lang.isKana(it) || Lang.isHan(it) }) "ja" else null
                val year = (children(item, DC, "date") + children(item, "http://purl.org/dc/terms/", "issued"))
                    .firstNotNullOfOrNull { OpenLibrary.year(it.textContent) }
                out += Record(
                    Source.NDL, children(item, null, "link").firstOrNull()?.textContent?.trim() ?: "",
                    title, isbn, lang, children(item, DC, "publisher").firstOrNull()?.textContent?.trim(), year, translators, textLang,
                )
            }
            return out
        }

        /**
         * The NDL searches titles by substring: "三体" also finds "三体0 : 球状閃電" and "三体II 黒暗森林", which are other
         * books. A record whose title contains the Japanese title is kept only if its main title is that title, give or
         * take a volume (上, 下, 第2巻). Records found through another field (a translation's original title) stay.
         */
        fun sameWork(recordTitle: String, jaTitle: String): Boolean {
            val want = Hashi.norm(jaTitle)
            val got = Hashi.norm(recordTitle)
            if (want.isEmpty() || !got.contains(want)) return true
            val main = Hashi.norm(recordTitle.substringBefore(" : ").substringBefore(" = "))
            return main == want || main.removePrefix(want).matches(Regex("(上|中|下|上巻|中巻|下巻|第?[0-9０-９一二三]+巻)"))
        }

        private fun children(e: Element, ns: String?, name: String): List<Element> {
            val out = ArrayList<Element>()
            var n = e.firstChild
            while (n != null) {
                if (n is Element && n.localName == name && (ns == null || n.namespaceURI == ns)) out += n
                n = n.nextSibling
            }
            return out
        }

        /** No DTD, no external entity, no XInclude: an answer cannot make Hashi read a file or call a server. */
        fun secureFactory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
    }
}
