package com.imbananko.tilly.dao;

import com.imbananko.tilly.model.MemeEntity;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import reactor.core.publisher.Mono;

public class MemeDaoImpl implements MemeDao {
  private final Mono<Connection> connectionMono;

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
}
