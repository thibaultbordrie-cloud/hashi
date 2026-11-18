package hashi

import java.io.File

/**
 * Lists the answers the tests still need, and the recorded ones they no longer use.
 * Run from the repository root:
 *   java -cp build/hashi-tests.jar hashi.RecordKt
 * Each missing URL is then fetched once, its answer trimmed to the fields Hashi reads, and saved
 * in src/test/fixtures (first line the URL, then the body); see src/test/fixtures/README.md.
 */
fun main() {
    val dir = File("src/test/fixtures")
    val cache = Cache(dir, null)
    val missing = LinkedHashSet<String>()
    val used = HashSet<String>()
    val fetch = Fetch { url ->
        used += Cache.name(url)
        try {
            cache.get(url)
        } catch (e: Cache.Missing) {
            missing += url
            null
        }
    }
    for (q in Fixtures.QUERIES) Hashi(fetch).find(q)
    // The outage test: Kitchen with Wikidata down.
    Hashi { url -> if (url.startsWith("https://query.wikidata.org")) throw FetchError("down") else fetch.get(url) }.find("Kitchen / Yoshimoto")
    missing.forEach(::println)
    dir.listFiles { f -> f.name.endsWith(".txt") && f.name !in used }?.sortedBy { it.name }?.forEach { println("unused ${it.name}") }
}
