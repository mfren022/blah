package com.draftpad.core

import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element as XmlElement

class SvgTest {
    private val ink = Colors.parse("#1F1F1F")
    private val blue = Colors.parse("#1E88E5")

    private fun parse(svg: String): Document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(svg.byteInputStream())

    private fun Document.all(tag: String): List<XmlElement> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as XmlElement }
    }

    @Test
    fun writesEveryElementRelativeToTheFrame() {
        val draft = Draft(
            id = "x",
            title = "Login <screen> & co",
            notes = "Two fields, one button",
            elements = listOf(
                Stroke(floatArrayOf(110f, 120f, 130f, 140f, 150f, 120f), ink, 4f),
                Stroke(floatArrayOf(200f, 200f), blue, 6f),
                Shape(ShapeKind.LINE, 100f, 100f, 300f, 100f, ink, 2f),
                Shape(ShapeKind.ARROW, 100f, 300f, 200f, 300f, blue, 4f),
                Shape(ShapeKind.RECT, 300f, 300f, 120f, 160f, ink, 4f),
                Shape(ShapeKind.ELLIPSE, 100f, 100f, 200f, 150f, 0x801E88E5.toInt(), 4f),
                Label("Email\nPassword", 120f, 200f, 20f, ink),
            ),
            background = Background("background.jpg", 100f, 100f, 360f, 640f),
        )
        val frame = Box(100f, 100f, 500f, 800f)
        val svg = Svg.write(draft, frame, "background.jpg")
        val doc = parse(svg)
        val root = doc.documentElement
        assertEquals("svg", root.localName)
        assertEquals("http://www.w3.org/2000/svg", root.namespaceURI)
        assertEquals("0 0 400 700", root.getAttribute("viewBox"))
        assertEquals("Login <screen> & co", doc.all("title").single().textContent)
        assertEquals("Two fields, one button", doc.all("desc").single().textContent)

        val image = doc.all("image").single()
        assertEquals("background.jpg", image.getAttribute("href"))
        assertEquals("0", image.getAttribute("x"))

        val path = doc.all("path").single()
        assertEquals("M10 20 Q30 40 40 30 L50 20", path.getAttribute("d"))
        assertEquals("#1F1F1F", path.getAttribute("stroke"))
        assertEquals("4", path.getAttribute("stroke-width"))

        val dot = doc.all("circle").single()
        assertEquals("100", dot.getAttribute("cx"))
        assertEquals("3", dot.getAttribute("r"))
        assertEquals("#1E88E5", dot.getAttribute("fill"))

        assertEquals(2, doc.all("line").size)
        assertEquals("200", doc.all("line")[0].getAttribute("x2"))
        assertEquals("arrow", (doc.all("polygon").single().parentNode as XmlElement).getAttribute("data-kind"))

        val rect = doc.all("rect").last()
        assertEquals(listOf("20", "60", "180", "140"), listOf("x", "y", "width", "height").map(rect::getAttribute))

        val ellipse = doc.all("ellipse").single()
        assertEquals(listOf("50", "25", "50", "25"), listOf("cx", "cy", "rx", "ry").map(ellipse::getAttribute))
        assertEquals("0.5", ellipse.getAttribute("stroke-opacity"))

        val spans = doc.all("tspan")
        assertEquals(listOf("Email", "Password"), spans.map { it.textContent })
        assertEquals(listOf("100", "125"), spans.map { it.getAttribute("y") })
        assertEquals("20", spans[0].getAttribute("x"))
    }

    @Test
    fun leavesOutTheBackgroundWithoutAnImage() {
        val draft = Draft(id = "x", background = Background("background.jpg", 0f, 0f, 10f, 10f))
        val doc = parse(Svg.write(draft, Box(0f, 0f, 10f, 10f)))
        assertTrue(doc.all("image").isEmpty())
        assertTrue(doc.all("title").isEmpty())
    }

    @Test
    fun escapesTextAndDropsInvalidCharacters() {
        assertEquals("a &amp; b &lt;c&gt; &quot;d&quot; &apos;e&apos;", Svg.escape("a & b <c> \"d\" 'e'"))
        assertEquals("ok", Svg.escape("o\u0000k\u0007"))
        val doc = parse(Svg.write(Draft(id = "x", elements = listOf(Label("<b>&\u0002", 0f, 20f, 20f, ink))), Box(0f, 0f, 50f, 50f)))
        assertEquals("<b>&", doc.all("tspan").single().textContent)
        assertFalse(doc.all("text").single().hasAttribute("stroke-width"))
    }
}
