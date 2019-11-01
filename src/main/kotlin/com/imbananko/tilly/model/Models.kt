package com.imbananko.tilly.model

data class MemeEntity(val chatId: Long,
                      val messageId: Int,
                      val senderId: Int,
                      val fileId: String)

data class VoteEntity(
        val chatId: Long,
        val messageId: Int,
        val voterId: Int,
        val voteValue: VoteValue
)

enum class VoteValue(val emoji: String) {
    UP("\uD83D\uDC8E"),
    EXPLAIN("\uD83E\uDD14"),
    DOWN("\uD83D\uDCA9")
}
