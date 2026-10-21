package xyz.kurdcloud.englishmaster.core.model

/**
 * CEFR-referenced level. [apiValue] matches the `public.cefr_level` enum in the database.
 * [LIFE_IN_UK] is not an English level: it holds the Life in the UK test course, which is
 * downloaded and practised like a level but has no level exam or certificate.
 */
enum class CefrLevel(val apiValue: String, val label: String) {
    PRE_A1("PRE_A1", "Pre-A1"),
    A1("A1", "A1"),
    A2("A2", "A2"),
    B1("B1", "B1"),
    B2("B2", "B2"),
    C1("C1", "C1"),
    C2("C2", "C2"),
    LIFE_IN_UK("LIFE_IN_UK", "Life in the UK");

    companion object {
        /** Levels that currently have practice content. */
        val practiceLevels: List<CefrLevel> = listOf(A1, A2, B1, B2, C1, C2)

        /** Question sets staff can write for: the English levels and the Life in the UK course. */
        val authoringLevels: List<CefrLevel> = practiceLevels + LIFE_IN_UK

        fun fromApi(value: String): CefrLevel =
            entries.firstOrNull { it.apiValue == value } ?: error("Unknown CEFR level: $value")
    }
}
