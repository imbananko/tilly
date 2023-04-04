package com.chsdngm.tilly

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.rest_client.RestClientTransport
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class Configuration {
    @Bean
    fun elasticsearchClient(@Value("\${elasticsearch.url}") elasticsearchUrl: String): ElasticsearchAsyncClient {
        val restClient = RestClient.builder(HttpHost(elasticsearchUrl, 9200)).build()
        val transport = RestClientTransport(restClient, JacksonJsonpMapper())

        return ElasticsearchAsyncClient(transport)
    }
}