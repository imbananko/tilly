package com.chsdngm.tilly.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "metadata")
data class MetadataProperties(
    val moderationThreshold: String,
    val commitSha: String = "local"
)