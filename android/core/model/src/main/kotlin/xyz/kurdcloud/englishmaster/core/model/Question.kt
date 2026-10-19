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
)

/** Option letters used by the API: index 0 -> "A". */
fun optionLetter(index: Int): String {
    require(index in 0 until 6) { "Option index out of range: $index" }
    return ('A' + index).toString()
}

fun optionIndex(letter: String): Int = letter.single().uppercaseChar() - 'A'
