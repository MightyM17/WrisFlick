package com.example.wristtype.presentation

object ArcDecoder {

    // 4 groups for 4 directions: UP, RIGHT, DOWN, LEFT
    private val groups4 = listOf(
        "ABCDEF",    // index 0 (UP)
        "GHIJKL",    // index 1 (RIGHT)
        "MNOPQR",    // index 2 (DOWN)
        "STUVWXYZ"   // index 3 (LEFT)
    )

    // --- 1) GROUP + TOKEN HELPERS ---

    // Used by ArcKeyboard to draw labels around the ring
    fun groups(): List<String> = groups4

    // Map hover angle (radians) -> arc index 0..3
    // 0° = UP, 90° = RIGHT, 180° = DOWN, 270° = LEFT
    fun arcIndexForAngle(rad: Float): Int {
        val deg = ((Math.toDegrees(rad.toDouble()) + 360.0) % 360.0).toFloat()
        return when {
            deg < 45f || deg >= 315f -> 0       // UP
            deg < 135f               -> 1       // RIGHT
            deg < 225f               -> 2       // DOWN
            else                     -> 3       // LEFT
        }
    }

    // Token is just a compact code: 'A', 'B', 'C', 'D'
    fun tokenForArcIndex(idx: Int): Char =
        ('A'.code + idx.coerceIn(0, 3)).toChar()

    // For UI / debugging if you want group text from index
    fun groupForArcIndex(idx: Int): String =
        groups4[idx.coerceIn(0, groups4.lastIndex)]

    // Turn a token char into its group
    private fun groupForToken(ch: Char): String {
        val i = (ch - 'A').coerceIn(0, groups4.lastIndex)
        return groups4[i]
    }

    // --- 2) BASIC EXPANSION (FALLBACK) ---

    // Extremely simple "decoder": for now, just take the FIRST letter
    // of each group for each token in the code.
    // "A"  -> "A"
    // "B"  -> "G"
    // "AB" -> "AG"
    fun expand(code: String): String {
        val sb = StringBuilder()
        for (c in code) {
            val grp = groupForToken(c)
            sb.append(grp.first())  // always first letter of the group
        }
        return sb.toString()
    }

    // --- 3) LIGHTWEIGHT DICTIONARY-BASED PREDICTIONS ---

    // Tiny example lexicon; put your most common words here.
    // You can safely grow this up to a few hundred / thousand without lag.
    private val lexicon: List<String> = listOf(
        // greetings / basics
        "hello", "hey", "hi", "how", "are", "you", "good", "morning", "night",
        "thanks", "thank", "please", "ok", "okay", "yes", "no",
        // filler words
        "this", "that", "there", "here", "what", "when", "where", "who",
        "why", "which",
        // common verbs
        "go", "going", "come", "coming", "want", "need", "like", "love",
        "know", "think", "see", "say", "get", "make", "do",
        // short common words
        "me", "my", "we", "our", "they", "them", "it", "is", "was",
        "on", "in", "at", "for", "to", "from", "with", "about"
        // add more as you like
    )

    // Precomputed map: code -> list of words sharing that exact key pattern.
    // Example: "hello" -> "BABBCD"
    //          codeToWords["BABBCD"] = ["hello"]
    private val codeToWords: Map<String, List<String>> by lazy {
        val map = mutableMapOf<String, MutableList<String>>()
        for (word in lexicon) {
            val code = encodeWordToCode(word)
            if (code.isNotEmpty()) {
                val lst = map.getOrPut(code) { mutableListOf() }
                lst.add(word)
            }
        }
        // Freeze lists to immutable for safety
        map.mapValues { it.value.toList() }
    }

    // Map a word ("hello") to its group-token string ("BABBCD")
    private fun encodeWordToCode(word: String): String {
        val sb = StringBuilder()
        for (ch in word.uppercase()) {
            if (ch !in 'A'..'Z') return ""  // skip words with digits / symbols for now
            val groupIndex = groups4.indexOfFirst { grp -> grp.contains(ch) }
            if (groupIndex == -1) return "" // letter not in any group; skip word
            val token = ('A'.code + groupIndex).toChar()
            sb.append(token)
        }
        return sb.toString()
    }

    // --- 4) CANDIDATE GENERATION ---

    // Returns:
    // 1) If we know words with this exact code: them (sorted by lexicon order)
    // 2) Otherwise: the simple expand(code) as a 1-element fallback.
    fun candidatesFor(code: String): List<String> {
        if (code.isEmpty()) return emptyList()

        // exact code match first
        val exact = codeToWords[code]
        if (!exact.isNullOrEmpty()) {
            return exact
        }

        // You could also do PREFIX search here if you want more “T9-like” behavior:
        // val prefixMatches = codeToWords
        //    .filterKeys { it.startsWith(code) }
        //    .flatMap { it.value }
        // if (prefixMatches.isNotEmpty()) return prefixMatches

        // fallback: raw expansion
        return listOf(expand(code))
    }
}
