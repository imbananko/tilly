package com.imbananko.tilly.model

data class MemeEntity(
    val chatId: Long,
    val messageId: Int,
    val senderId: Int,
    val fileId: String,
    val channelId: Long? = null,
    val channelMessageId: Int? = null) {

  fun isPublishedOnChannel(): Boolean = channelId != null && channelMessageId != null
}

data class VoteEntity(
    val chatId: Long,
    val messageId: Int,
    val voterId: Int,
    val voteValue: VoteValue
)

enum class VoteValue(val emoji: String) {
  UP("\uD83D\uDC8E"),
  DOWN("\uD83D\uDCA9")
}

class MemeStatsEntry(vararg val counts: Pair<VoteValue, Int>)
