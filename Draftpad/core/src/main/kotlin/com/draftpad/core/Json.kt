package com.draftpad.core

import java.util.Locale
import kotlin.math.abs

/** Tiny dependency-free JSON reader and writer (objects, arrays, strings, numbers, booleans, null). */
object Json {
    fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (ch in s) when (ch) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\t' -> sb.append("\\t")
            else -> if (ch < ' ') sb.append(String.format(Locale.ROOT, "\\u%04x", ch.code)) else sb.append(ch)
        }
        return sb.append('"').toString()
    }

    /** A coordinate or size rounded to a tenth, with no trailing ".0" and the same in every locale. */
    fun num(v: Float): String {
        if (!v.isFinite()) return "0"
        val tenths = Math.round(v * 10.0)
        val whole = abs(tenths) / 10
        val fraction = abs(tenths) % 10
        val sign = if (tenths < 0) "-" else ""
        return if (fraction == 0L) "$sign$whole" else "$sign$whole.$fraction"
    }

    /** Writes maps, iterables, float arrays, strings, numbers, booleans and null on one line. */
    fun write(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> sb.append(quote(v))
            is Boolean -> sb.append(v)
            is Float -> sb.append(num(v))
            is Double -> sb.append(num(v.toFloat()))
            is Number -> sb.append(v.toLong())
            is FloatArray -> {
                sb.append('[')
                v.forEachIndexed { i, f -> if (i > 0) sb.append(','); sb.append(num(f)) }
                sb.append(']')
            }
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, x) in v) {
                    if (!first) sb.append(',')
                    first = false
                    sb.append(quote(k.toString())).append(':')
                    write(sb, x)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (x in v) {
                    if (!first) sb.append(',')
                    first = false
                    write(sb, x)
                }
                sb.append(']')
            }
            else -> throw IllegalArgumentException("Can't write ${v::class.java.simpleName} as JSON")
        }
    }

    fun parse(text: String): Any? {
        val p = Parser(text)
        val v = p.value()
        p.ws()
        require(p.i == text.length) { "Trailing data at ${p.i}" }
        return v
    }

    private class Parser(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            ws()
            require(i < s.length) { "Unexpected end of JSON" }
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> { expect("true"); true }
                'f' -> { expect("false"); false }
                'n' -> { expect("null"); null }
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("Unexpected '$c' at $i")
            }
        }
        fun expect(w: String) { require(s.startsWith(w, i)) { "Expected $w at $i" }; i += w.length }
        fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++; ws()
            if (s[i] == '}') { i++; return m }
            while (true) {
                ws(); val k = str(); ws(); expect(":")
                m[k] = value(); ws()
                if (s[i] == ',') { i++; continue }
                expect("}"); return m
            }
        }
        fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++; ws()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l += value(); ws()
                if (s[i] == ',') { i++; continue }
                expect("]"); return l
            }
        }
        fun str(): String {
            expect("\"")
            val sb = StringBuilder()
            while (s[i] != '"') {
                val c = s[i++]
                if (c == '\\') {
                    when (val e = s[i++]) {
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                        'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                        'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> sb.append(e)
                    }
                } else sb.append(c)
            }
            i++
            return sb.toString()
        }
        fun num(): Number {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            val t = s.substring(start, i)
            return if (t.any { it in ".eE" }) t.toDouble() else t.toLong()
        }
    }
}
