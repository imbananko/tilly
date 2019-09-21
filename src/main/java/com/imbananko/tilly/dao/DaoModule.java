package com.imbananko.tilly.dao;

import com.typesafe.config.Config;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

@NoArgsConstructor
public class DaoModule {
    Mono<Connection> connectionMono;

    public DaoModule(Config config) {
        final ConnectionFactory connectionFactory = ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(DRIVER, "postgresql")
                .option(HOST, config.getString("database.host"))
                .option(PORT, config.getInt("database.port"))  // optional, defaults to
                .option(USER, config.getString("database.user"))
                .option(PASSWORD, config.getString("database.password"))
                .option(DATABASE, config.getString("database.name"))  // optional
                .build());

        connectionMono = Mono.from(connectionFactory.create());

    }
}
