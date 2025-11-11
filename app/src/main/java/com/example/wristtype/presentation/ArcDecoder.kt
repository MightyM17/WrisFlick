package com.example.wristtype.presentation

object ArcDecoder {
    // 6 large groups (tweak later to optimize)
    private val arcGroups = listOf(
        "ABCDE", "FGHIJ", "KLMN", "OPQR", "STUV", "WXYZ"
    )

    fun groups(): List<String> = arcGroups

    // Convert hover angle (radians; 0 = North, +clockwise) to arc index 0..5
    fun arcIndexForAngle(rad: Float): Int {
        val deg = ((Math.toDegrees(rad.toDouble()) + 360.0) % 360.0).toFloat()
        return (((deg + 30f) / 60f).toInt()) % 6
    }

    fun tokenForArcIndex(i: Int): String =
        ('A'.code + (i.coerceIn(0, 5))).toChar().toString()

    fun groupForArcIndex(i: Int): String = arcGroups[i.coerceIn(0, 5)]

    fun expand(code: String): String =
        code.map { ch ->
            val idx = (ch - 'A').coerceIn(0, 5)
            groupForArcIndex(idx).first()
        }.joinToString("")

    // TEMP tiny lexicon — replace with real dictionary later
    private val toyLexicon = listOf(
        "hello","help","held","you","your","yours",
        "watch","wear","wrist","text","type","typing","time",
        "smart","study","speed","space","south","north"
    )

    fun candidatesFor(code: String): List<String> {
        if (code.isEmpty()) return emptyList()
        return toyLexicon.filter { w ->
            if (w.length < code.length) return@filter false
            code.indices.all { i ->
                val idx = (code[i] - 'A').coerceIn(0, 5)
                w[i].uppercaseChar() in groupForArcIndex(idx)
            }
        }.take(8).ifEmpty { listOf(expand(code)) }
    }
}
