package com.chsdngm.tilly.repository.configuration

import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(DatabaseProperties::class)
class ExposedConfiguration {
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
    fun database(dataSource: HikariDataSource): Database {
        return Database.connect(dataSource)
    }
}