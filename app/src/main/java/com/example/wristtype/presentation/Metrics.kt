package com.example.wristtype.presentation

import kotlin.math.min

object Metrics {

    fun normalize(s: String): String {
        // Lowercase, keep letters/spaces, collapse spaces
        val cleaned = buildString {
            for (ch in s.lowercase()) {
                when {
                    ch in 'a'..'z' -> append(ch)
                    ch == ' ' -> append(' ')
                    else -> { /* drop punctuation */ }
                }
            }
        }
        return cleaned.trim().replace(Regex("\\s+"), " ")
    }

    // Standard watch/text-entry WPM:
    // WPM = ((|T|-1)/S) * (60/5)
    fun wpm(transcribed: String, startMs: Long, endMs: Long): Double {
        val t = normalize(transcribed)
        val chars = t.length
        val seconds = ((endMs - startMs).coerceAtLeast(1L)) / 1000.0
        if (chars <= 1) return 0.0
        return ((chars - 1) / seconds) * (60.0 / 5.0)
    }

    // Levenshtein distance O(m*n) time, O(min(m,n)) memory
    fun levenshtein(aRaw: String, bRaw: String): Int {
        val a = normalize(aRaw)
        val b = normalize(bRaw)
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        // Ensure b is shorter for smaller memory
        val s1: String
        val s2: String
        if (a.length >= b.length) { s1 = a; s2 = b } else { s1 = b; s2 = a }

        val prev = IntArray(s2.length + 1) { it }
        val cur = IntArray(s2.length + 1)

        for (i in 1..s1.length) {
            cur[0] = i
            val c1 = s1[i - 1]
            for (j in 1..s2.length) {
                val c2 = s2[j - 1]
                val cost = if (c1 == c2) 0 else 1
                val del = prev[j] + 1
                val ins = cur[j - 1] + 1
                val sub = prev[j - 1] + cost
                cur[j] = minOf(del, ins, sub)
            }
            // swap
            for (j in prev.indices) prev[j] = cur[j]
        }
        return prev[s2.length]
    }

    fun cer(presented: String, transcribed: String): Double {
        val p = normalize(presented)
        val d = levenshtein(p, transcribed)
        val denom = p.length.coerceAtLeast(1)
        return d.toDouble() / denom.toDouble()
    }

    fun wordAccuracy(presented: String, transcribed: String): Double {
        val p = normalize(presented).split(" ").filter { it.isNotBlank() }
        val t = normalize(transcribed).split(" ").filter { it.isNotBlank() }
        if (p.isEmpty()) return 0.0
        val n = min(p.size, t.size)
        var correct = 0
        for (i in 0 until n) {
            if (p[i] == t[i]) correct++
        }
        return correct.toDouble() / p.size.toDouble()
    }
}
