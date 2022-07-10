package com.chsdngm.tilly.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class Metadata {
    companion object {
        @JvmField
        var MODERATION_THRESHOLD = 0L

        @JvmField
        var COMMIT_SHA = ""
    }

    @Value("\${metadata.moderation.threshold}")
    fun setModerationThreshold(moderationThreshold: Long) {
        MODERATION_THRESHOLD = moderationThreshold
    }

    @Value("\${metadata.commit.sha}")
    fun setCommitSha(commitSha: String) {
        COMMIT_SHA = commitSha
    }
}