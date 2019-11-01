package com.imbananko.tilly.repository

import com.imbananko.tilly.utility.SqlQueries
import com.imbananko.tilly.utility.getFromConfOrFail
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.telegram.telegrambots.meta.api.objects.User
import java.util.*

@Repository
class UserRepository(private val template: NamedParameterJdbcTemplate, private val queries: SqlQueries) {
    private val userCache: WeakHashMap<Int, User> = WeakHashMap()

    fun saveIfNotExists(user: User): Unit {
        userCache.computeIfAbsent(user.id) {
            template.update(queries.getFromConfOrFail("insertUserIfNotExists"),
                MapSqlParameterSource("userId", user.id)
                    .addValue("username", user.userName)
                    .addValue("firstName", user.firstName)
                    .addValue("lastName", user.lastName))
            user
        }
    }
}
