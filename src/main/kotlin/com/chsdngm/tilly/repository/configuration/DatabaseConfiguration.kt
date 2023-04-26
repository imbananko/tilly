package com.chsdngm.tilly.repository.configuration

import com.chsdngm.tilly.model.dto.*
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@EnableConfigurationProperties(DatabaseProperties::class)
class DatabaseConfiguration {
    @Bean
    fun dataSource(dataSourceProperties: DatabaseProperties): HikariDataSource {
        return dataSourceProperties
            .initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .username(dataSourceProperties.username)
            .password(dataSourceProperties.password)
            .driverClassName(dataSourceProperties.driverClassName)
            .build()
    }

    @Bean
    @Profile("default")
    fun database(dataSource: HikariDataSource): Database {
        return Database.connect(dataSource)
    }

    @Bean
    @Profile("local")
    fun databaseLocal(dataSource: HikariDataSource): Database {
        return Database.connect(dataSource).apply {
            transaction {
                SchemaUtils.createMissingTablesAndColumns(
                    Images,
                    Memes,
                    MemesLogs,
                    Votes,
                    TelegramUsers,
                    DistributedModerationEvents
                )
            }
        }
    }
}
