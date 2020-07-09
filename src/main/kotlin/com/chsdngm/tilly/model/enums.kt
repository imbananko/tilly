package com.chsdngm.tilly.model

enum class VoteValue(val emoji: String) {
  UP("\uD83D\uDC8E"),
  DOWN("\uD83D\uDCA9")
}

enum class VoteSourceType {
  CHAT,
  CHANNEL,
  PRIVATE_CHAT
}

enum class PrivateVoteValue {
  APPROVE,
  DECLINE
}