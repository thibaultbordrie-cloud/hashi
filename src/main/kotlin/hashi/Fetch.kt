package hashi

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration

/** Reads a URL. Returns null when the server says the thing does not exist (404). */
fun interface Fetch {
    fun get(url: String): String?
}

class FetchError(msg: String) : Exception(msg)

/**
 * The network, politely: one request at a time per host and at least a second between two,
 * a User-Agent that says what Hashi is and where its code lives, no cookies, nothing about the user.
 */
class Net(
    private val userAgent: String = "Hashi/$VERSION (https://github.com/thibaultbordrie-cloud/hashi)",
    private val gapMillis: Long = 1000,
    private val maxBytes: Int = 4_000_000,
) : Fetch {
    private val client: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build()
    }
    private val last = HashMap<String, Long>()

    override fun get(url: String): String? {
        val uri = URI.create(url)
        require(uri.scheme == "https") { "Hashi only speaks HTTPS: $url" }
        val host = uri.host
        synchronized(this) {
            val wait = (last[host] ?: 0L) + gapMillis - System.currentTimeMillis()
            if (wait > 0) Thread.sleep(wait)
            last[host] = System.currentTimeMillis()
        }
        val req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
            .header("User-Agent", userAgent).header("Accept", "application/sparql-results+json, application/json, application/xml;q=0.9, */*;q=0.5")
            .GET().build()
        for (attempt in 1..2) {
            val res = try {
                client.send(req, HttpResponse.BodyHandlers.ofByteArray())
            } catch (e: java.io.IOException) {
                if (attempt == 2) throw FetchError("$host: ${e.message}")
                Thread.sleep(2000)
                continue
            }
            when (val code = res.statusCode()) {
                200 -> {
                    if (res.body().size > maxBytes) throw FetchError("$host: answer too large")
                    return String(res.body(), Charsets.UTF_8)
                }
                404 -> return null
                429, 500, 502, 503, 504 -> {
                    if (attempt == 2) throw FetchError("$host answered $code")
                    val after = res.headers().firstValue("Retry-After").orElse("").toLongOrNull()?.coerceIn(1, 30) ?: 3
                    Thread.sleep(after * 1000)
                }
                else -> throw FetchError("$host answered $code")
            }
        }
        throw FetchError("$host: no answer")
    }
}

/**
 * Keeps every answer in a folder, one file per URL (first line the URL, then the body), so a
 * second search for the same book costs the libraries nothing. Without [next] it works offline
 * and a missing answer is an error: that is how the tests replay recorded answers.
 */
class Cache(private val dir: File, private val next: Fetch?, private val maxAgeDays: Long = 30) : Fetch {
    class Missing(val url: String) : Exception("not recorded: $url")

    fun file(url: String): File = File(dir, name(url))

    override fun get(url: String): String? {
        val f = file(url)
        if (f.isFile) {
            val fresh = next == null || System.currentTimeMillis() - f.lastModified() < maxAgeDays * 86_400_000L
            val text = f.readText()
            val nl = text.indexOf('\n')
            if (fresh && nl > 0 && text.substring(0, nl) == url) {
                val body = text.substring(nl + 1)
                return if (body == NOT_FOUND) null else body
            }
        }
        val source = next ?: throw Missing(url)
        val body = source.get(url)
        dir.mkdirs()
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(url + "\n" + (body ?: NOT_FOUND))
        java.nio.file.Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        return body
    }

    companion object {
        const val NOT_FOUND = "#hashi:404"

        fun name(url: String): String {
            val d = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
            val host = runCatching { URI.create(url).host }.getOrNull()?.substringBefore('.')?.takeIf { it.all(Char::isLetter) } ?: "x"
            return host + "-" + d.take(8).joinToString("") { "%02x".format(it) } + ".txt"
        }
    }
}
