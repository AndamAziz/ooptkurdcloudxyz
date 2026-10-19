package xyz.kurdcloud.englishmaster.core.model

/** CEFR-referenced level. [apiValue] matches the `public.cefr_level` enum in the database. */
enum class CefrLevel(val apiValue: String, val label: String) {
    PRE_A1("PRE_A1", "Pre-A1"),
    A1("A1", "A1"),
    A2("A2", "A2"),
    B1("B1", "B1"),
    B2("B2", "B2"),
    C1("C1", "C1"),
    C2("C2", "C2");

    companion object {
        /** Levels that currently have practice content. */
        val practiceLevels: List<CefrLevel> = listOf(A1, A2, B1, B2, C1, C2)

        fun fromApi(value: String): CefrLevel =
            entries.firstOrNull { it.apiValue == value } ?: error("Unknown CEFR level: $value")
    }
}
