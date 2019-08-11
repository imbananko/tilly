package com.imbananko.tilly.repository;

import com.imbananko.tilly.model.VoteEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VoteRepository extends CrudRepository<VoteEntity, VoteEntity.VoteKey> {
  int countByFileIdAndChatIdAndValue(String fileId, long targetChatId, VoteEntity.Value value);

  boolean existsByFileIdAndChatIdAndValueAndUsername(
      String fileId, long chatId, VoteEntity.Value value, String username);

  default boolean exists(VoteEntity entity) {
    return existsByFileIdAndChatIdAndValueAndUsername(
        entity.getFileId(), entity.getChatId(), entity.getValue(), entity.getUsername());
  }
}
