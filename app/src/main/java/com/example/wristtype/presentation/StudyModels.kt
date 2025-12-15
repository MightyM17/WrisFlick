package com.example.wristtype.presentation

enum class StudyScreen {
    CONSENT, INSTRUCTIONS, CALIBRATION, TYPING, SURVEY, SUMMARY
}

data class TrialMetrics(
    val phrase: String,
    val transcribed: String,
    val wpm: Double,
    val cer: Double,
    val wordAccuracy: Double,
    val selections: Int,
    val deletes: Int,
    val durationMs: Long
)

data class SurveyAnswers(
    val comfort: Int,
    val fatigue: Int,
    val control: Int,
    val frustration: Int
)
