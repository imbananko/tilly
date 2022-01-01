package com.chsdngm.tilly.model

enum class VoteValue(val emoji: String) {
  UP("\uD83D\uDC8E"),
  DOWN("\uD83D\uDCA9")
}

enum class PrivateVoteValue(val emoji: String) {
  APPROVE("\uD83D\uDC8E"),
  DECLINE("\uD83D\uDCA9")
}

enum class MemeStatus {
  MODERATION {
    override val description: String = "мем на модерации."
  },
  LOCAL {
    override val description: String = "так как мем локальный, на канал он отправлен не будет."
  },
  SCHEDULED {
    override val description: String = "мем будет отправлен на канал."
  },
  PUBLISHED {
    override val description: String = "мем отправлен на канал."
  },
  DECLINED {
    override val description: String = "мем предан забвению."
  };

  abstract val description: String

  fun canBeScheduled(): Boolean = this == MODERATION
}

enum class UserStatus {
  DEFAULT,
  BANNED
}