package com.imbananko.tilly.repository;

import com.imbananko.tilly.model.StatsEntity;
import com.imbananko.tilly.model.VoteEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface VoteRepository extends CrudRepository<VoteEntity, VoteEntity.VoteKey> {
  boolean existsByFileIdAndChatIdAndValueAndUsername(
      String fileId, long chatId, VoteEntity.Value value, String username);

  default boolean exists(VoteEntity entity) {
    return existsByFileIdAndChatIdAndValueAndUsername(
        entity.getFileId(), entity.getChatId(), entity.getValue(), entity.getUsername());
  }

  @Query(
      "SELECT new com.imbananko.tilly.model.StatsEntity(v.value, COUNT(v.value)) "
          + "FROM VoteEntity v "
          + "WHERE v.fileId=?1 and v.chatId=?2 "
          + "GROUP BY v.value")
  List<StatsEntity> getStats(String fileId, long chatId);

  @Modifying
  @Query(value = "DELETE FROM vote WHERE file_id=:fileId AND chat_id=:chatId AND username=:username", nativeQuery = true)
  void delete(@Param("fileId") String fileId, @Param("chatId") long chatId, @Param("username") String username);

  @Override
  default void delete(VoteEntity entity) {
    delete(entity.getFileId(), entity.getChatId(), entity.getUsername());
  }

  @Transactional
  @Modifying
  @Query(value = "INSERT INTO vote(file_id, chat_id, username, value) VALUES (:fileId, :chatId, :username, :val) " +
                 "ON CONFLICT (chat_id, file_id, username) DO UPDATE SET value=:val", nativeQuery = true)
  void insertOrUpdate(@Param("fileId") String fileId,
                      @Param("chatId") long chatId,
                      @Param("username") String username,
                      @Param("val") String val);

  default void insertOrUpdate(VoteEntity entity) {
      insertOrUpdate(entity.getFileId(), entity.getChatId(), entity.getUsername(), entity.getValue().name());
  }
}
