package com.chsdngm.tilly

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.rest_client.RestClientTransport
import com.chsdngm.tilly.config.TelegramConfig
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.telegram.telegrambots.bots.DefaultAbsSender
import org.telegram.telegrambots.bots.DefaultBotOptions

@Configuration
class Configuration {
    @Bean
    fun api(): DefaultAbsSender {
        return object : DefaultAbsSender(DefaultBotOptions()) {
            override fun getBotToken(): String = TelegramConfig.BOT_TOKEN
        }
    }

    @Bean
    fun elasticsearchClient(@Value("elasticsearch.url") elasticsearchUrl: String): ElasticsearchAsyncClient {
        val restClient = RestClient.builder(HttpHost(elasticsearchUrl, 9200)).build()
        val transport = RestClientTransport(restClient, JacksonJsonpMapper())

        return ElasticsearchAsyncClient(transport)
    }
}