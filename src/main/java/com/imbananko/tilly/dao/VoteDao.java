package com.imbananko.tilly.dao;

import com.imbananko.tilly.model.Statistics;
import com.imbananko.tilly.model.VoteEntity;
import reactor.core.publisher.Mono;

public interface VoteDao {
    Mono<Boolean> exists(VoteEntity entity);

    Mono<Statistics> getStats(String fileId, long chatId);

    Mono<Integer> delete(VoteEntity entity);

    Mono<Integer> insertOrUpdate(VoteEntity entity);
}