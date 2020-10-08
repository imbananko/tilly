package com.chsdngm.tilly.model

enum class VoteValue(val emoji: String) {
  UP("\uD83D\uDC8E"),
  DOWN("\uD83D\uDCA9")
}

enum class PrivateVoteValue(val emoji: String) {
  APPROVE("\uD83D\uDC8E"),
  DECLINE("\uD83D\uDCA9")
}