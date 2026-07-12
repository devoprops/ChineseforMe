package com.example.chineseforme.domain.model

/**
 * Study entry modes. All share current text + sentence selection;
 * only the activity after picking a sentence differs.
 */
enum class StudyMode(val routeKey: String, val label: String) {
    Standard("standard", "Standard"),
    Memorize("memorize", "Memorize"),
    Stroke("stroke", "Stroke practice");

    companion object {
        fun fromRoute(key: String): StudyMode =
            entries.find { it.routeKey == key } ?: Standard
    }
}
