package app.olauncher.data.store

import kotlin.math.abs

/**
 * Checks that a captured screenshot frame and the node tree read at the same moment
 * describe the same picture. The Circle-to-Search overlay (10.5) may only map node
 * boxes onto the frame when this verifies; a mismatch means the frame and the nodes
 * drifted apart (different app, rotation, density change) and the boxes would land
 * in the wrong place.
 *
 * Context-free on purpose: the frame is plain width/height data and the anchors are
 * [ScreenBox]es, so this stays JVM-testable (see ScreenMappingTest and the
 * capture/tagesschau_mismatch.xml fixture).
 */
object ScreenMapping {

    /** Captured frame size in pixels. */
    data class Frame(val width: Int, val height: Int)

    sealed interface Result

    /** The frame matches the node tree; word boxes may be scaled by these factors. */
    data class Verified(val scaleX: Float, val scaleY: Float) : Result

    /** The frame does not match; [reason] says why (for logging). */
    data class Mismatch(val reason: Reason) : Result

    enum class Reason {
        /** Frame and node tree have different aspect ratios. */
        ASPECT,

        /** The implied frame→screen scale deviates from 1.0 by more than [TOLERANCE]. */
        SCALE,

        /** No anchor box falls inside the frame. */
        NO_ANCHOR,
    }

    /** Deviation from 1.0 (scale) or from the screen aspect that still counts as a match. */
    private const val TOLERANCE = 0.02f

    /**
     * [frame] is the captured bitmap size, [screen] the node tree's root bounds and
     * [anchors] a few word boxes from [ScreenWords.build] (e.g. the first and last
     * word box). Checks aspect, then scale, then anchors, so the reasons stay
     * distinguishable.
     */
    fun verify(frame: Frame, screen: ScreenBox, anchors: List<ScreenBox>): Result {
        val frameAspect = aspect(frame.width, frame.height) ?: return Mismatch(Reason.ASPECT)
        val screenAspect = aspect(screen.width, screen.height) ?: return Mismatch(Reason.ASPECT)
        if (abs(frameAspect - screenAspect) > TOLERANCE * screenAspect) return Mismatch(Reason.ASPECT)
        val scaleX = screen.width.toFloat() / frame.width
        val scaleY = screen.height.toFloat() / frame.height
        if (abs(scaleX - 1f) > TOLERANCE || abs(scaleY - 1f) > TOLERANCE) return Mismatch(Reason.SCALE)
        if (anchors.none { it.isInside(frame) }) return Mismatch(Reason.NO_ANCHOR)
        return Verified(scaleX, scaleY)
    }

    private fun aspect(width: Int, height: Int): Float? =
        if (width <= 0 || height <= 0) null else width.toFloat() / height

    private fun ScreenBox.isInside(frame: Frame): Boolean =
        left >= 0 && top >= 0 && right <= frame.width && bottom <= frame.height
}

/**
 * The line handoff format for the node snapshot the service passes to
 * [app.olauncher.helper.WordCaptureActivity] next to the frame path: one node per
 * line, seven tab-separated fields — id, parentId, bounds ("l,t,r,b" or empty),
 * charBoxes ("l,t,r,b;l,t,r,b" or empty), className, contentDescription, text.
 *
 * The three text fields are backslash-escaped (backslash, tab, newline, carriage
 * return); a field that is exactly the escape sequence `\0` means null. Since every
 * backslash of real text is escaped, the marker is unambiguous. No platform or
 * third-party serialization — plain Kotlin, JVM-testable.
 */
object ScreenNodesCodec {

    fun encode(nodes: List<ScreenNode>): String = nodes.joinToString("\n") { node ->
        listOf(
            node.id.toString(),
            node.parentId.toString(),
            node.bounds?.let { box -> "${box.left},${box.top},${box.right},${box.bottom}" } ?: "",
            node.charBoxes?.joinToString(";") { box -> "${box.left},${box.top},${box.right},${box.bottom}" } ?: "",
            escape(node.className),
            escape(node.contentDescription),
            escape(node.text),
        ).joinToString("\t")
    }

    /**
     * Decodes a whole snapshot. Returns null when any line is malformed — never a
     * partial snapshot, a lost node would silently misplace every overlay box.
     */
    fun decode(encoded: String): List<ScreenNode>? = try {
        parse(encoded)
    } catch (_: Exception) {
        null
    }

    private fun parse(encoded: String): List<ScreenNode> {
        if (encoded.isEmpty()) return emptyList()
        return encoded.split('\n').map { line ->
            val fields = line.split('\t')
            require(fields.size == 7) { "expected 7 fields, got ${fields.size}" }
            ScreenNode(
                id = fields[0].toInt(),
                parentId = fields[1].toInt(),
                bounds = optionalBox(fields[2]),
                charBoxes = optionalBoxes(fields[3]),
                className = unescape(fields[4]),
                contentDescription = unescape(fields[5]),
                text = unescape(fields[6]),
            )
        }
    }

    /** "l,t,r,b" — anything else throws and fails the whole decode. */
    private fun parseBox(field: String): ScreenBox {
        val parts = field.split(',')
        require(parts.size == 4) { "malformed box: $field" }
        return ScreenBox(parts[0].toInt(), parts[1].toInt(), parts[2].toInt(), parts[3].toInt())
    }

    /** "" means null bounds; anything else must be exactly "l,t,r,b". */
    private fun optionalBox(field: String): ScreenBox? =
        if (field.isEmpty()) null else parseBox(field)

    private fun optionalBoxes(field: String): List<ScreenBox>? =
        if (field.isEmpty()) null else field.split(';').map { parseBox(it) }

    private fun escape(value: String?): String {
        if (value == null) return "\\0"
        val out = StringBuilder(value.length)
        for (c in value) when (c) {
            '\\' -> out.append("\\\\")
            '\t' -> out.append("\\t")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            else -> out.append(c)
        }
        return out.toString()
    }

    private fun unescape(field: String): String? {
        if (field == "\\0") return null
        val out = StringBuilder(field.length)
        var i = 0
        while (i < field.length) {
            val c = field[i]
            if (c == '\\' && i + 1 < field.length) {
                when (val next = field[i + 1]) {
                    't' -> out.append('\t')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    '\\' -> out.append('\\')
                    // "\0" mid-field cannot come from encode(); keep it visible.
                    else -> out.append(next)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
