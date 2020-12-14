package com.chsdngm.tilly.repository

import com.chsdngm.tilly.model.PublishMemeTask
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface PublishMemeRepository : CrudRepository<PublishMemeTask, Int> {
    @Query(nativeQuery = true, value = """
    select meme_id from publish_meme_task order by got_in_queue_time limit 1
    """)
    fun findMemeToPublish(): PublishMemeTask?
}