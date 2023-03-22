package com.chsdngm.tilly

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.rest_client.RestClientTransport
import com.chsdngm.tilly.config.TelegramConfig
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.telegram.telegrambots.bots.DefaultAbsSender
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import org.telegram.telegrambots.updatesreceivers.DefaultWebhook

@Configuration
class Configuration {
    @Bean
    fun api(): DefaultAbsSender {
        return object : DefaultAbsSender(DefaultBotOptions()) {
            override fun getBotToken(): String = TelegramConfig.BOT_TOKEN
        }
    }

    @Bean
    fun elasticsearchClient(config: TelegramConfig): ElasticsearchAsyncClient {
        val restClient = RestClient.builder(HttpHost(config.elasticsearchUrl, 9200)).build()
        val transport = RestClientTransport(restClient, JacksonJsonpMapper())

        return ElasticsearchAsyncClient(transport)
    }

    @Bean
    fun telegramBotsApi(): TelegramBotsApi =
            TelegramBotsApi(DefaultBotSession::class.java, DefaultWebhook().apply { setInternalUrl("https://127.0.0.1:8443") })
}