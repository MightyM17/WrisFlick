object ArcDecoder {
    // 8 groups in T9 order (2..9)
    private val octGroups = listOf(
        "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ"
    )

    fun groups(): List<String> = octGroups

    // Angle → octant index (0..7). 0° = North. Each slice = 45°.
    fun arcIndexForAngle(rad: Float): Int {
        val deg = ((Math.toDegrees(rad.toDouble()) + 360.0) % 360.0).toFloat()
        return (((deg + 22.5f) / 45f).toInt()) % 8
    }

    // Token we store per slice: 'A'..'H'
    fun tokenForArcIndex(i: Int): String = ('A'.code + i.coerceIn(0,7)).toChar().toString()

    private fun groupForToken(ch: Char): String = octGroups[(ch - 'A').coerceIn(0,7)]

    // Fallback expansion (first letter of each chosen group)
    fun expand(code: String): String = code.map { groupForToken(it).first() }.joinToString("")

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
