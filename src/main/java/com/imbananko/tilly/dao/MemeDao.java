package com.imbananko.tilly.dao;

import com.imbananko.tilly.model.MemeEntity;
import reactor.core.publisher.Mono;

public interface MemeDao {
  Mono<Integer> save(MemeEntity meme);
}
