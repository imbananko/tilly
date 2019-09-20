package com.imbananko.tilly.dao;

import com.imbananko.tilly.model.MemeEntity;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import io.vavr.collection.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class MemeDaoImpl implements MemeDao {
    private final Mono<Connection> connectionMono;

    @Autowired
    public MemeDaoImpl(DaoModule daoModule) {
        this.connectionMono = daoModule.connectionMono;
    }

    @Override
    public Mono<Integer> save(MemeEntity meme) {
        return connectionMono.flatMapMany(connection -> connection
                .createStatement("INSERT INTO meme (file_id, author_username, target_chat_id) VALUES ($1, $2, $3)")
                .bind("$1", meme.getFileId())
                .bind("$2", meme.getAuthorUsername())
                .bind("$3", meme.getTargetChatId())
                .execute())
                .flatMap(Result::getRowsUpdated)
                .reduce(Integer::sum);
    }

    @Override
    public Mono<MemeEntity> findById(String fileId) {
        return connectionMono.flatMapMany(connection -> connection
                .createStatement("SELECT author_username, target_chat_id FROM meme WHERE file_id= $1")
                .bind("$1", fileId)
                .execute())
                .flatMap(res -> res.map((row, rowMetadata) -> {
                    String authorUsername = row.get("title", String.class);
                    Long targetChatId = row.get("target_chat_id", Long.class);

                    return new MemeEntity(fileId, authorUsername, targetChatId);
                })).<List<MemeEntity>>reduceWith(List::empty, List::append)
                .map(it -> it.headOption().getOrElseThrow(IndexOutOfBoundsException::new));
    }
}
