package com.ottawahomeprice.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTest {
    @Test
    fun readsHeaderRowsQuotesAndComments() {
        val text = listOf(
            "# a comment",
            "name,aliases,value",
            "",
            "\"Smith, John\",\"a|b\",1.5",
            "Plain,,2",
            "  # indented comment",
            "\"Say \"\"hi\"\"\",x,3",
        ).joinToString("\n")
        val table = CsvTable.parse("t.csv", text)
        assertEquals(listOf("name", "aliases", "value"), table.header)
        assertEquals(3, table.rows.size)
        assertEquals("Smith, John", table.rows[0].str("name"))
        assertEquals(listOf("a", "b"), table.rows[0].list("aliases"))
        assertEquals(1.5, table.rows[0].double("value"), 0.0)
        assertTrue(table.rows[1].list("aliases").isEmpty())
        assertFalse(table.rows[1].has("aliases"))
        assertEquals("Say \"hi\"", table.rows[2].str("name"))
    }

    @Test
    fun badDataNamesTheFileAndLine() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            CsvTable.parse("t.csv", "a,b\n1,2\n3").rows
        }
        assertTrue(error.message!!, error.message!!.contains("t.csv line 3"))
        val table = CsvTable.parse("t.csv", "a\nx")
        val notNumber = assertThrows(IllegalArgumentException::class.java) { table.rows[0].double("a") }
        assertTrue(notNumber.message!!.contains("line 2"))
    }

    @Test
    fun handlesWindowsLineEndings() {
        val table = CsvTable.parse("t.csv", "a,b\r\n1,2\r\n")
        assertEquals("2", table.rows[0].str("b"))
    }
}
