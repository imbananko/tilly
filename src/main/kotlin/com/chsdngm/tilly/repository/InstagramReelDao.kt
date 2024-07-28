package com.chsdngm.tilly.repository

import com.chsdngm.tilly.allColumns
import com.chsdngm.tilly.config.MetadataProperties
import com.chsdngm.tilly.execAndMap
import com.chsdngm.tilly.indexedColumns
import com.chsdngm.tilly.model.InstagramReelStatus
import com.chsdngm.tilly.model.dto.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Repository
import java.sql.SQLException

@Repository
class InstagramReelDao(
    val database: Database,
    val metadata: MetadataProperties
) {
    fun insert(instagramReel: InstagramReel) = transaction {
        InstagramReels.insert { instagramReel.toInsertStatement(it) }.resultedValues?.first()?.toInstagramReel()
            ?: throw SQLException("Error saving reel")
    }

    fun update(instagramReel: InstagramReel) = transaction {
        InstagramReels.update({ InstagramReels.id eq instagramReel.id }) { instagramReel.toUpdateStatement(it) }
    }

    fun findReelByChannelMessageId(channelMessageId: Int): Pair<InstagramReel, List<Vote>>? = transaction {
        InstagramReels.join(Votes, JoinType.LEFT,
            onColumn = InstagramReels.id,
            otherColumn = Votes.memeId
        )
            .select(InstagramReels.channelMessageId eq channelMessageId)
            .toReelWithVotes()
    }

    fun findReelByModerationChatIdAndModerationChatMessageId(
        moderationChatId: Long,
        moderationChatMessageId: Int,
    ): Pair<InstagramReel, List<Vote>>? = transaction {
        InstagramReels.join(
            Votes, JoinType.LEFT,
            onColumn = InstagramReels.id,
            otherColumn = Votes.memeId
        )
            .select((InstagramReels.moderationChatId eq moderationChatId) and (InstagramReels.moderationChatMessageId eq moderationChatMessageId))
            .toReelWithVotes()
    }

    suspend fun findAllByStatusOrderByCreated(reelStatus: InstagramReelStatus): Map<InstagramReel, List<Vote>> =
        newSuspendedTransaction {
            InstagramReels.join(
                Votes,
                JoinType.LEFT,
                onColumn = InstagramReels.id,
                otherColumn = Votes.memeId
            )
                .select(InstagramReels.status eq reelStatus)
                .toInstagramReelsWithVotes()
        }

    suspend fun findTopRatedReelForLastWeek(): InstagramReel? = newSuspendedTransaction {
        val sql = """
                select ${InstagramReels.allColumns} 
                from instagram_reel    
                    left join vote v on id = v.meme_id where channel_message_id is not null    
                    and meme.published > current_timestamp - interval '7 days' 
                group by id 
                order by count(value) filter (where value = 'UP') - count(value) filter (where value = 'DOWN') desc 
                limit 1;
                """.trimIndent()

        sql.execAndMap { rs -> ResultRow.create(rs, Memes.indexedColumns) }.toInstagramReel()
    }

    private fun Iterable<ResultRow>.toReelWithVotes(): Pair<InstagramReel, List<Vote>>? {
        val iterator = this.iterator()

        if (!iterator.hasNext()) {
            return null
        }

        val first = iterator.next()
        val reel = first.toInstagramReel() ?: return null
        val votes = mutableListOf<Vote>()
        first.toVote()?.let { votes.add(it) }

        while (iterator.hasNext()) {
            val vote = iterator.next().toVote()
            if (vote == null || vote.memeId != reel.id) {
                continue
            }

            votes.add(vote)
        }

        return reel to votes
    }

    private fun ResultRow.toInstagramReel(): InstagramReel? {
        if (this.getOrNull(InstagramReels.id) == null) {
            return null
        }

        return InstagramReel(
            id = this[InstagramReels.id].value,
            url = this[InstagramReels.url],
            moderationChatId = this[InstagramReels.moderationChatId],
            moderationChatMessageId = this[InstagramReels.moderationChatMessageId],
            created = this[InstagramReels.created],
            channelMessageId = this[InstagramReels.channelMessageId],
            fileId = this[InstagramReels.fileId],
            privateReplyMessageId = this[InstagramReels.privateReplyMessageId],
            senderId = this[InstagramReels.senderId],
            status = this[InstagramReels.status],
            published = this[InstagramReels.published]
        )
    }

    fun scheduleReels(): List<InstagramReel> = transaction {
        val sql = """
                     update instagram_reel 
                     set status='SCHEDULED'
                     where id in (
                           select instagram_reel.id
                           from instagram_reel
                                    left join vote on instagram_reel.id = vote.meme_id
                           where instagram_reel.status = 'MODERATION'
                             and instagram_reel.channel_message_id is null
                             and instagram_reel.created > now() - interval '7 days'
                           group by instagram_reel.id
                           having count(vote) filter ( where vote.value = 'UP' ) -
                                  count(vote) filter ( where vote.value = 'DOWN') >= ${metadata.moderationThreshold}
                     ) returning ${InstagramReels.allColumns};
                  """.trimIndent()

        sql.execAndMap({ rs -> ResultRow.create(rs, InstagramReels.indexedColumns) }, StatementType.SELECT).toInstagramReels()
    }
}