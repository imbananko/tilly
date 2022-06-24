package com.chsdngm.tilly.model

enum class VoteValue(val emoji: String) {
    UP("\uD83D\uDC8E"),
    DOWN("\uD83D\uDCA9")
}

enum class PrivateVoteValue(val emoji: String) {
    APPROVE("\uD83D\uDC8E"),
    DECLINE("\uD83D\uDCA9")
}

enum class MemeStatus(val description: String) {
    MODERATION("мем на модерации."),
    LOCAL("так как мем локальный, на канал он отправлен не будет."),
    SCHEDULED("мем будет отправлен на канал."),
    PUBLISHED("мем отправлен на канал."),
    DECLINED("мем предан забвению.")
}

enum class WeightedModerationType(val weight: Int) {
    PRIVATE(20),
    DEFAULT(80)
}

enum class UserStatus {
    DEFAULT,
    BANNED
}