package com.imbananko.tilly.repository;

import com.imbananko.tilly.model.MemeEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MemeRepository {
    private final NamedParameterJdbcTemplate template;

    public MemeRepository(NamedParameterJdbcTemplate template) {
        this.template = template;
    }

    public void save(MemeEntity memeEntity) {
        String query = "insert into meme (file_id, author_username, target_chat_id) values (:fileId, :username, :chatId)";
        template.update(query, new MapSqlParameterSource("fileId", memeEntity.getFileId())
                .addValue("username", memeEntity.getAuthorUsername())
                .addValue("chatId", memeEntity.getTargetChatId()));
    }
}
