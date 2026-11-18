package hashi

import java.io.File
import java.io.PrintStream
import kotlin.system.exitProcess

const val VERSION = "0.1.0"

private const val HELP = """Hashi 橋 $VERSION: every translation of a book, from the open catalogues.

  hashi 9782020134736                  an ISBN (10 or 13 digits, hyphens allowed)
  hashi "ノルウェイの森"                 a title, in any language
  hashi "L'Étranger / Camus"           a title and its author

  --lang en|fr|ja|zh-Hant   language of the answer (default: the system's)
  --all                     every edition, not the first six of each language
  --json                    the answer as JSON, for another program
  --offline                 answer from the cache only, without the network
  --cache DIR               where answers are kept (default: ~/.cache/hashi)
  --no-cache                keep nothing on disk

Sources: Open Library, Wikidata, the National Diet Library of Japan (NDL Search).
Only the ISBN or the title you type is sent to them."""

/** Runs the command line; returns the exit code (0 found, 1 not found, 2 usage error, 3 network). */
fun run(args: List<String>, out: PrintStream, err: PrintStream, fetchFor: (cache: File?, offline: Boolean) -> Fetch = ::defaultFetch): Int {
    var locale = Text.locale(System.getProperty("user.language", "en").let { l -> if (l == "zh") "zh-Hant" else l })
    var json = false
    var all = false
    var offline = false
    var cache: File? = File(System.getProperty("user.home"), ".cache/hashi")
    val words = ArrayList<String>()
    var i = 0
    while (i < args.size) {
        when (val a = args[i]) {
            "-h", "--help" -> { out.println(HELP.trimIndent()); return 0 }
            "--version" -> { out.println("hashi $VERSION"); return 0 }
            "--json" -> json = true
            "--all" -> all = true
            "--offline" -> offline = true
            "--no-cache" -> cache = null
            "--lang" -> locale = Text.locale(args.getOrNull(++i) ?: run { err.println("--lang needs a value"); return 2 })
            "--cache" -> cache = File(args.getOrNull(++i) ?: run { err.println("--cache needs a folder"); return 2 })
            else -> if (a.startsWith("--")) { err.println("unknown option $a (see --help)"); return 2 } else words += a
        }
        i++
    }
    val query = words.joinToString(" ").trim()
    if (query.isEmpty()) { err.println(HELP.trimIndent()); return 2 }
    if (offline && cache == null) { err.println("--offline needs the cache"); return 2 }
    val report = try {
        Hashi(fetchFor(cache, offline)).find(query)
    } catch (e: Cache.Missing) {
        err.println("offline, and not in the cache: ${e.url}")
        return 3
    } catch (e: FetchError) {
        err.println(e.message)
        return 3
    }
    if (report == null) {
        if (json) out.println("null") else out.println(Text.t(locale, "nothing", "q" to query))
        return 1
    }
    out.print(if (json) Render.json(report) + "\n" else Render.text(report, locale, all))
    return 0
}

fun defaultFetch(cache: File?, offline: Boolean): Fetch = when {
    cache == null -> Net()
    offline -> Cache(cache, null)
    else -> Cache(cache, Net())
}

fun main(args: Array<String>) {
    val out = PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.out), true, Charsets.UTF_8)
    exitProcess(run(args.toList(), out, System.err))
}
