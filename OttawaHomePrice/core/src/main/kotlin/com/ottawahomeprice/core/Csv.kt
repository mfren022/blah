package com.ottawahomeprice.core

/**
 * A small CSV reader for the data files the app ships. The first non-comment line is the header.
 * Fields may be quoted ("a, b" and "" for a quote). Blank lines and lines starting with # are skipped.
 */
class CsvTable(val name: String, val header: List<String>, val rows: List<CsvRow>) {

    companion object {
        fun parse(name: String, text: String): CsvTable {
            var header: List<String>? = null
            val rows = ArrayList<CsvRow>()
            text.lineSequence().forEachIndexed { index, raw ->
                val line = raw.trimEnd('\r')
                if (line.isBlank() || line.trimStart().startsWith("#")) return@forEachIndexed
                val fields = splitLine(line, name, index + 1)
                val columns = header
                if (columns == null) {
                    header = fields.map { it.trim() }
                } else {
                    require(fields.size == columns.size) {
                        "$name line ${index + 1}: expected ${columns.size} fields, found ${fields.size}"
                    }
                    rows += CsvRow(name, index + 1, columns.zip(fields).toMap())
                }
            }
            return CsvTable(name, header ?: error("$name has no header"), rows)
        }

        private fun splitLine(line: String, name: String, lineNumber: Int): List<String> {
            val fields = ArrayList<String>()
            val field = StringBuilder()
            var quoted = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' && (quoted || field.isBlank()) -> { quoted = !quoted; if (quoted) field.clear() }
                    !quoted && c == ',' -> { fields += field.toString(); field.clear() }
                    else -> field.append(c)
                }
                i++
            }
            require(!quoted) { "$name line $lineNumber: unterminated quote" }
            fields += field.toString()
            return fields
        }
    }
}

/** One record of a [CsvTable], with typed accessors that name the file and line on bad data. */
class CsvRow(private val table: String, val line: Int, private val values: Map<String, String>) {

    fun has(column: String): Boolean = !values[column].isNullOrBlank()

    fun str(column: String): String =
        values[column]?.trim() ?: throw IllegalArgumentException("$table line $line: no column '$column'")

    fun strOrNull(column: String): String? = values[column]?.trim()?.takeIf { it.isNotEmpty() }

    fun double(column: String): Double =
        str(column).toDoubleOrNull() ?: throw IllegalArgumentException("$table line $line: '$column' is not a number")

    fun doubleOrNull(column: String): Double? = strOrNull(column)?.let {
        it.toDoubleOrNull() ?: throw IllegalArgumentException("$table line $line: '$column' is not a number")
    }

    fun int(column: String): Int =
        str(column).toIntOrNull() ?: throw IllegalArgumentException("$table line $line: '$column' is not an integer")

    /** A list stored in one field, separated by |. */
    fun list(column: String): List<String> =
        strOrNull(column)?.split('|')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    override fun toString(): String = "$table:$line $values"
}
