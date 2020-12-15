package com.chsdngm.tilly.model

enum class VoteValue(val emoji: String) {
  UP("\uD83D\uDC8E"),
  DOWN("\uD83D\uDCA9")
}

enum class PrivateVoteValue(val emoji: String) {
  APPROVE("\uD83D\uDC8E"),
  DECLINE("\uD83D\uDCA9")
}

enum class MemeStatus(val value: String) {
  MODERATION("MODERATION") {
    override val description: String = "мем на модерации."
  },
  LOCAL("LOCAL") {
    override val description: String = "так как мем локальный, на канал он отправлен не будет."
  },
  SCHEDULED("SCHEDULED") {
    override val description: String = "мем будет отправлен на канал."
  },
  PUBLISHED("PUBLISHED") {
    override val description: String = "мем отправлен на канал."
  };

  abstract val description: String

  override fun toString(): String = value

  fun canBeScheduled(): Boolean = this == MODERATION
}