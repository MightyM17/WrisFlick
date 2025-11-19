object ArcDecoder {
    // 8 groups in T9 order (2..9)
    private val groups4 = listOf(
        "ABCDEF",   // UP
        "GHIJKL",   // RIGHT
        "MNOPQR",   // DOWN
        "STUVWXYZ"  // LEFT
    )

    fun groups(): List<String> = groups4

    fun arcIndexForAngle(rad: Float): Int {
        val deg = ((Math.toDegrees(rad.toDouble()) + 360.0) % 360.0).toFloat()
        // 0° (UP), 90° (RIGHT), 180° (DOWN), 270° (LEFT)
        return when {
            deg < 45f || deg >= 315f -> 0        // UP
            deg < 135f               -> 1        // RIGHT
            deg < 225f               -> 2        // DOWN
            else                     -> 3        // LEFT
        }
    }

    fun tokenForArcIndex(i: Int): String = ('A'.code + i.coerceIn(0, 3)).toChar().toString()

    private fun groupForToken(ch: Char): String = groups4[(ch - 'A').coerceIn(0, 3)]

    fun expand(code: String): String =
        code.map { groupForToken(it).first() }.joinToString("")

    // Tiny lexicon placeholder
    private val toyLexicon = listOf(
        "you","your","yours","hello","help","watch","wear","wrist","text","type",
        "time","study","speed","smart","north","south","test","tune","quick","java","kotlin"
    )

    fun candidatesFor(code: String): List<String> {
        if (code.isEmpty()) return emptyList()
        val ok = toyLexicon.filter { w ->
            if (w.length < code.length) return@filter false
            code.indices.all { i ->
                val g = groupForToken(code[i])
                w[i].uppercaseChar() in g
            }
        }
        return ok.take(8).ifEmpty { listOf(expand(code)) }
    }
}
