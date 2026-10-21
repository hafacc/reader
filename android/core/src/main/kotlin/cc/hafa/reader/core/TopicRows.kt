package cc.hafa.reader.core

/** The most rows of topics shown before the topics scroll sideways. */
const val TOPIC_ROWS = 3

/** Where one item goes in [wrappedRows] or [packedRows]: its [row], counted from zero, and its [left] edge. */
data class RowPosition(val row: Int, val left: Int)

/** Where items of these widths go when laid left to right and wrapped at [available], with [gap] between neighbors. */
fun wrappedRows(widths: List<Int>, available: Int, gap: Int): List<RowPosition> {
    val positions = ArrayList<RowPosition>(widths.size)
    var row = -1
    var used = 0
    for (width in widths) {
        if (row < 0 || used + gap + width > available) {
            row += 1
            positions.add(RowPosition(row, 0))
            used = width
        } else {
            positions.add(RowPosition(row, used + gap))
            used += gap + width
        }
    }
    return positions
}

/**
 * Where items of these widths go in [rows] rows that grow sideways without limit: each in turn goes at the end of the
 * row that is shortest so far (the upper one on a tie), [gap] after the item before it.
 */
fun packedRows(widths: List<Int>, rows: Int, gap: Int): List<RowPosition> {
    // Where each row ends, counting a gap before its first item.
    val ends = IntArray(rows) { -gap }
    return widths.map { width ->
        val row = ends.indices.minBy { candidate -> ends[candidate] }
        val left = ends[row] + gap
        ends[row] = left + width
        RowPosition(row, left)
    }
}

/** The number of rows [positions] fill. */
fun rowCount(positions: List<RowPosition>): Int = positions.maxOfOrNull { position -> position.row + 1 } ?: 0
