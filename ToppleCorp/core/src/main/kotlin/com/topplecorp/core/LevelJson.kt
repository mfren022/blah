package com.topplecorp.core

/**
 * Level (de)serialization. Format is intentionally simple and human-editable so that
 * the level editor and external tools can produce it:
 *
 * {
 *   "version": 1, "city": 0, "index": 0, "name": "1-1", "width": 5, "height": 4, "seed": 0,
 *   "hint": "...",
 *   "turns": {"three": 3, "two": 4, "max": 6},
 *   "tools": {"HAMMER": -1, "DRILL": 2},
 *   "pieces": [{"id": 1, "material": "WOOD", "cells": [[0,0],[1,0]]}]
 * }
 */
object LevelJson {
    const val VERSION = 1

    fun encode(level: LevelDef): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"version\": $VERSION,\n")
        sb.append("  \"city\": ${level.city},\n")
        sb.append("  \"index\": ${level.index},\n")
        sb.append("  \"name\": ${Json.quote(level.name)},\n")
        sb.append("  \"width\": ${level.width},\n")
        sb.append("  \"height\": ${level.height},\n")
        sb.append("  \"seed\": ${level.seed},\n")
        if (level.hint != null) sb.append("  \"hint\": ${Json.quote(level.hint)},\n")
        sb.append("  \"turns\": {\"three\": ${level.turns.three}, \"two\": ${level.turns.two}, \"max\": ${level.turns.max}},\n")
        sb.append("  \"tools\": {")
        sb.append(level.tools.entries.joinToString(", ") { "\"${it.key.name}\": ${it.value}" })
        sb.append("},\n")
        sb.append("  \"pieces\": [\n")
        sb.append(level.pieces.joinToString(",\n") { p ->
            val cells = p.cells.joinToString(",") { "[${it.x},${it.y}]" }
            "    {\"id\": ${p.id}, \"material\": \"${p.material.name}\", \"cells\": [$cells]}"
        })
        sb.append("\n  ]\n}\n")
        return sb.toString()
    }

    @Suppress("UNCHECKED_CAST")
    fun decode(text: String): LevelDef {
        val root = Json.parse(text) as Map<String, Any?>
        fun int(m: Map<String, Any?>, k: String, def: Int = 0) = (m[k] as? Number)?.toInt() ?: def
        val turns = root["turns"] as Map<String, Any?>
        val tools = (root["tools"] as Map<String, Any?>).entries.associate { (k, v) ->
            ToolType.parse(k) to (v as Number).toInt()
        }
        val pieces = (root["pieces"] as List<Any?>).map { raw ->
            val pm = raw as Map<String, Any?>
            PieceDef(
                id = int(pm, "id"),
                material = Material.parse(pm["material"] as String),
                cells = (pm["cells"] as List<Any?>).map { c ->
                    val xy = c as List<Any?>
                    Cell((xy[0] as Number).toInt(), (xy[1] as Number).toInt())
                },
            )
        }
        return LevelDef(
            city = int(root, "city"),
            index = int(root, "index"),
            name = root["name"] as? String ?: "",
            width = int(root, "width"),
            height = int(root, "height"),
            pieces = pieces,
            tools = tools,
            turns = TurnLimits(int(turns, "three"), int(turns, "two"), int(turns, "max")),
            hint = root["hint"] as? String,
            seed = (root["seed"] as? Number)?.toLong() ?: 0L,
        )
    }
}

/** Tiny dependency-free JSON reader (objects, arrays, strings, numbers, booleans, null). */
object Json {
    fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (ch in s) when (ch) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\t' -> sb.append("\\t")
            else -> if (ch < ' ') sb.append(String.format("\\u%04x", ch.code)) else sb.append(ch)
        }
        return sb.append('"').toString()
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
                else -> if (c == '-' || c.isDigit()) num() else error("Unexpected '$c' at $i")
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
