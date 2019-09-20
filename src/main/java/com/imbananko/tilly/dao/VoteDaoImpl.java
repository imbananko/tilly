package com.imbananko.tilly.dao;

import com.imbananko.tilly.model.Statistics;
import com.imbananko.tilly.model.VoteEntity;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class VoteDaoImpl implements VoteDao {

    private final Mono<Connection> connectionMono;

    @Autowired
    public VoteDaoImpl(DaoModule daoModule) {
        this.connectionMono = daoModule.connectionMono;
    }

    @Override
    public Mono<Boolean> exists(VoteEntity entity) {
        return connectionMono.flatMapMany(connection -> connection
                .createStatement("SELECT EXISTS(SELECT 1 FROM vote WHERE file_id=$1 AND chat_id=$2 AND value=$3 AND username=$4)")
                .bind("$1", entity.getFileId())
                .bind("$2", entity.getChatId())
                .bind("$3", entity.getValue().name())
                .bind("$4", entity.getUsername())
                .execute())
                .flatMap(res -> res.map((row, rowMetadata) -> row.get("exists", Boolean.class)))
                .reduce((fst, snd) -> fst && snd);
    }

    @Override
    public Mono<Statistics> getStats(String fileId, long chatId) {
        return connectionMono.flatMapMany(connection -> connection
                .createStatement("SELECT v.value as val, COUNT(v.value) as count FROM vote v WHERE v.fileId=$1 and v.chatId=$2 GROUP BY v.value")
                .bind("$1", fileId)
                .bind("$2", chatId)
                .execute())
                .flatMap(res -> res.map((row, rowMetadata) -> {
                    VoteEntity.Value value = VoteEntity.Value.valueOf(row.get("val", String.class));
                    Long count = row.get("count", Long.class);

                    return new Tuple2<>(value, count);
                })).<List<Tuple2<VoteEntity.Value, Long>>>reduceWith(List::empty, List::append)
                .map(Statistics::new);
    }

    @Override
    public Mono<Integer> delete(VoteEntity entity) {
        return connectionMono.flatMapMany(connection -> connection
                .createStatement("DELETE FROM vote WHERE file_id=$1 AND chat_id=$2 AND username=$3")
                .bind("$1", entity.getFileId())
                .bind("$2", entity.getChatId())
                .bind("$3", entity.getUsername())
                .execute())
                .flatMap(Result::getRowsUpdated)
                .reduce(Integer::sum);
    }

    @Override
    public Mono<Integer> insertOrUpdate(VoteEntity entity) {
        return connectionMono.flatMapMany(connection -> connection
                .createStatement(
                        "INSERT INTO vote(file_id, chat_id, username, value) VALUES ($1, $2, $3, $4) " +
                                "ON CONFLICT (chat_id, file_id, username) DO UPDATE SET value=$4"
                )
                .bind("$1", entity.getFileId())
                .bind("$2", entity.getChatId())
                .bind("$3", entity.getUsername())
                .bind("$4", entity.getValue().name())
                .execute())
                .flatMap(Result::getRowsUpdated)
                .reduce(Integer::sum);
    }
}
