package com.chsdngm.tilly

import com.chsdngm.tilly.utility.TillyConfig
import org.elasticsearch.client.RestHighLevelClient
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.elasticsearch.client.ClientConfiguration
import org.springframework.data.elasticsearch.client.RestClients
import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage

@SpringBootApplication
class TillyApplication

fun main(args: Array<String>) {
    SpringApplication.run(TillyApplication::class.java, *args)
    SendMessage().apply {
        chatId = TillyConfig.BETA_CHAT_ID
        text = "${TillyConfig.BOT_USERNAME} started with sha: ${TillyConfig.COMMIT_SHA}"
        parseMode = ParseMode.HTML
    }.let { method -> TillyConfig.api.execute(method) }
}

@Configuration
class ElasticsearchClientConfig(val tillyConfig: TillyConfig) : AbstractElasticsearchConfiguration() {
    @Bean
    override fun elasticsearchClient(): RestHighLevelClient {
        val clientConfiguration = ClientConfiguration.builder()
            .connectedTo(TillyConfig.ELASTICSEARCH_URL)
            .withConnectTimeout(2000)
            .withSocketTimeout(2000)
            .build()

        return RestClients.create(clientConfiguration).rest()
    }
}
