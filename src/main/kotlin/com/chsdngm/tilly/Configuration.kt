package com.chsdngm.tilly

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.rest_client.RestClientTransport
import com.chsdngm.tilly.config.MetadataProperties
import com.chsdngm.tilly.config.TelegramProperties
import com.chsdngm.tilly.handlers.AbstractHandler
import com.chsdngm.tilly.model.Timestampable
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.springframework.beans.factory.annotation.Value
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

    @Bean
    fun elasticsearchClient(@Value("\${elasticsearch.url}") elasticsearchUrl: String): ElasticsearchAsyncClient {
        val restClient = RestClient.builder(HttpHost(elasticsearchUrl, 9200)).build()
        val transport = RestClientTransport(restClient, JacksonJsonpMapper())

        return ElasticsearchAsyncClient(transport)
    }
}