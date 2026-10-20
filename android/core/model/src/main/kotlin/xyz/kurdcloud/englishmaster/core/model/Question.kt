package xyz.kurdcloud.englishmaster.core.model

/** A practice question as stored on the device. Answer keys are never downloaded. */
data class Question(
    val id: String,
    val level: CefrLevel,
    val skill: String,
    val prompt: String,
    val options: List<String>,
    val version: Int,
    /** False while a teacher has not reviewed the item. */
    val reviewed: Boolean,
    /** Reading text shown above the question, if any. */
    val passage: String? = null,
    val media: QuestionMedia? = null,
)

enum class MediaType(val apiValue: String) {
    IMAGE("image"), VIDEO("video");

    companion object {
        fun fromApi(value: String?): MediaType? = entries.firstOrNull { it.apiValue == value }
    }
}

/**
 * A picture or short video attached to a question. [url] is the public storage URL; [localPath]
 * is set when the file was saved with the downloaded level pack (pictures only), so it works offline.
 */
data class QuestionMedia(val type: MediaType, val url: String, val caption: String?, val localPath: String? = null)

/** Option letters used by the API: index 0 -> "A". */
fun optionLetter(index: Int): String {
    require(index in 0 until 6) { "Option index out of range: $index" }
    return ('A' + index).toString()
}

fun optionIndex(letter: String): Int = letter.single().uppercaseChar() - 'A'
