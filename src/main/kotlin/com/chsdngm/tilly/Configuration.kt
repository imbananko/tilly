package com.chsdngm.tilly

import com.chsdngm.tilly.config.MetadataProperties
import com.chsdngm.tilly.config.TelegramProperties
import com.chsdngm.tilly.handlers.AbstractHandler
import com.chsdngm.tilly.model.Timestampable
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(TelegramProperties::class, MetadataProperties::class)
class Configuration {
    @Bean
    @Suppress("UNCHECKED_CAST")
    fun updateHandlers(updateHandlers: List<AbstractHandler<out Timestampable>>): List<AbstractHandler<Timestampable>> {
        return updateHandlers as List<AbstractHandler<Timestampable>>
    }
}