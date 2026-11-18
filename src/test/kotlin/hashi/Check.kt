package hashi

import kotlin.system.exitProcess

/** A small test runner, so the tests need nothing but the JDK and the Kotlin standard library. */
object Check {
    private var passed = 0
    private val failed = ArrayList<String>()

    fun test(name: String, body: () -> Unit) {
        try {
            body()
            passed++
        } catch (e: Throwable) {
            failed += "$name: ${e::class.simpleName}: ${e.message}"
        }
    }

    fun eq(expected: Any?, actual: Any?, what: String = "") {
        if (expected != actual) throw AssertionError("${if (what.isNotEmpty()) "$what: " else ""}expected <$expected> but was <$actual>")
    }

    fun ok(cond: Boolean, what: String) {
        if (!cond) throw AssertionError(what)
    }

    inline fun <reified T : Throwable> fails(body: () -> Unit): T {
        try {
            body()
        } catch (e: Throwable) {
            if (e is T) return e
            throw AssertionError("expected ${T::class.simpleName} but got ${e::class.simpleName}: ${e.message}")
        }
        throw AssertionError("expected ${T::class.simpleName}, nothing was thrown")
    }

    fun report(): Int {
        failed.forEach { System.err.println("FAIL $it") }
        println("${passed + failed.size} tests, ${failed.size} failed")
        return if (failed.isEmpty()) 0 else 1
    }
}

/** The books the tests look up, against answers recorded from the real catalogues. */
object Fixtures {
    val QUERIES = listOf(
        "9782020134736",
        "채식주의자",
        "三体 / 刘慈欣",
        "L'Étranger / Camus",
        "コンビニ人間",
        "Kitchen / Yoshimoto",
    )
}

fun main() {
    unitTests()
    fixtureTests()
    exitProcess(Check.report())
}

