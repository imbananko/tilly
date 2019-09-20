package com.imbananko.tilly.dao;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

@Component
@NoArgsConstructor
public class DaoModule {
    private final ConnectionFactory connectionFactory = ConnectionFactories.get(ConnectionFactoryOptions.builder()
            .option(DRIVER, "postgresql")
            .option(HOST, "...")
            .option(PORT, 5432)  // optional, defaults to
            .option(USER, "...")
            .option(PASSWORD, "...")
            .option(DATABASE, "...")  // optional
            .build());

    final Mono<Connection> connectionMono = Mono.from(connectionFactory.create());
}
