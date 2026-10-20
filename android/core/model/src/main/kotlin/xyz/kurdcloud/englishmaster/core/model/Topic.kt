package xyz.kurdcloud.englishmaster.core.model

/**
 * Practice by theme across all downloaded levels. A question belongs to a topic when it carries
 * one of [tags] (set by the content batches), or for the reading/media topics when it has a
 * passage or a picture/video.
 */
enum class Topic(val apiValue: String, val tags: List<String>, val withPassage: Boolean = false, val withMedia: Boolean = false) {
    LIFE_IN_UK("life_in_uk", listOf("life_in_uk")),
    KURDISTAN("kurdistan", listOf("kurdistan")),
    STORIES("stories", listOf("story", "dialogue"), withPassage = true),
    WORLD("world", listOf("history", "world_knowledge", "culture", "nature")),
    PICTURES("pictures", listOf("picture", "video"), withMedia = true);

    companion object {
        /** The local topic query takes this many tag patterns (see QuestionDao.pickForTopic). */
        const val MAX_TAGS = 4
        fun fromApi(value: String?): Topic? = entries.firstOrNull { it.apiValue == value }
    }
}
