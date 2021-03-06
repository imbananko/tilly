package com.chsdngm.tilly.repository

import com.chsdngm.tilly.model.PrivateModerator
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface PrivateModeratorRepository : CrudRepository<PrivateModerator, Int> {
  @Query(value = """
    select user_id
    from private_moderator
    where assigned > (NOW() - interval '1 DAY');
  """, nativeQuery = true)
  fun findCurrentModeratorsIds(): List<Int>

  @Transactional
  @Modifying
  @Query(value = """
    insert into private_moderator (user_id, assigned)
    values (:id, now())
    on conflict on constraint private_moderator_pkey do update set assigned = now()
  """, nativeQuery = true)
  fun addPrivateModerator(id: Int)
}