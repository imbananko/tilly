package com.chsdngm.tilly.handlers

import com.chsdngm.tilly.model.CommandUpdate
import com.chsdngm.tilly.model.CommandUpdate.Command
import com.chsdngm.tilly.model.VoteValue
import com.chsdngm.tilly.repository.MemeRepository
import com.chsdngm.tilly.repository.UserRepository
import com.chsdngm.tilly.repository.VoteRepository
import com.chsdngm.tilly.utility.TillyConfig
import com.chsdngm.tilly.utility.TillyConfig.Companion.BOT_USERNAME
import com.chsdngm.tilly.utility.TillyConfig.Companion.api
import com.chsdngm.tilly.utility.minusDays
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Service
class CommandHandler(
    private val userRepository: UserRepository,
    private val memeRepository: MemeRepository,
    private val voteRepository: VoteRepository,
) :
    AbstractHandler<CommandUpdate> {
    private val log = LoggerFactory.getLogger(javaClass)

    var executor: ExecutorService = Executors.newFixedThreadPool(10)

    override fun handle(update: CommandUpdate): CompletableFuture<Void> = CompletableFuture.supplyAsync({
        if (update.value == Command.STATS) {
            sendStats(update)
        } else if (update.value == Command.HELP || update.value == Command.START) {
            sendInfoMessage(update)
        } else if (update.value == Command.CONFIG && update.chatId == TillyConfig.BETA_CHAT_ID) {
            changeConfig(update)
        } else {
            log.warn("unknown command from update=$update")
        }
    }, executor).thenAccept {
        log.info("processed command update=$update")
    }

    private fun sendStats(update: CommandUpdate) = runBlocking {

        val memesByUserAll = withContext(Dispatchers.IO) { memeRepository.findBySenderId(update.senderId.toInt()) }
        val votesByUserAll = withContext(Dispatchers.IO) { voteRepository.findAllByVoterId(update.senderId.toInt()) }

        if (memesByUserAll.isEmpty() && votesByUserAll.isEmpty()) {
            "Статистика недоступна. Отправляй и оценивай мемы!"
        } else {
            val memesByUserWeek = memesByUserAll.filter { it.created > Instant.now().minusDays(7) }
            val votesByUserWeek = votesByUserAll.filter { it.created > Instant.now().minusDays(7) }

            val likeDislikeByUserWeek = votesByUserWeek.groupingBy { it.value }.eachCount()
            val userMemesVotesWeek = memesByUserWeek.flatMap { it.votes }.groupingBy { it.value }.eachCount()

            val likeDislikeByUserAll = votesByUserAll.groupingBy { it.value }.eachCount()
            val userMemesVotesAll = memesByUserAll.flatMap { it.votes }.groupingBy { it.value }.eachCount()

            """
          <u><b>Статистика за неделю:</b></u>
          
          Мемов отправлено: <b>${memesByUserWeek.size}</b>
          Прошло модерацию: <b>${memesByUserWeek.filter { it.channelMessageId != null }.size}</b>
          Получено: <b>${VoteValue.UP.emoji} ${userMemesVotesWeek[VoteValue.UP] ?: 0} · ${userMemesVotesWeek[VoteValue.DOWN] ?: 0} ${VoteValue.DOWN.emoji}</b>
          
          Мемов оценено: <b>${votesByUserWeek.size}</b>
          Поставлено: <b>${VoteValue.UP.emoji} ${likeDislikeByUserWeek[VoteValue.UP] ?: 0} · ${likeDislikeByUserWeek[VoteValue.DOWN] ?: 0} ${VoteValue.DOWN.emoji}</b>
          
          Ранк за неделю: <b>#${withContext(Dispatchers.IO) { userRepository.findUserWeekRank(update.senderId.toLong()) } ?: "NaN"}</b>
          
          <u><b>Статистика за все время:</b></u>
          
          Мемов отправлено: <b>${memesByUserAll.size}</b>
          Прошло модерацию: <b>${memesByUserAll.filter { it.channelMessageId != null }.size}</b>
          Получено: <b>${VoteValue.UP.emoji} ${userMemesVotesAll[VoteValue.UP] ?: 0} · ${userMemesVotesAll[VoteValue.DOWN] ?: 0} ${VoteValue.DOWN.emoji}</b>
          
          Мемов оценено: <b>${votesByUserAll.size}</b>
          Поставлено: <b>${VoteValue.UP.emoji} ${likeDislikeByUserAll[VoteValue.UP] ?: 0} · ${likeDislikeByUserAll[VoteValue.DOWN] ?: 0} ${VoteValue.DOWN.emoji}</b>
          
          Ранк: <b>#${withContext(Dispatchers.IO) { userRepository.findUserRank(update.senderId.toLong()) } ?: "NaN"}</b>
          
          """.trimIndent()
        }
    }.let { statsMessageText ->
        SendMessage().apply {
            parseMode = ParseMode.HTML
            chatId = update.senderId
            text = statsMessageText
        }.let { api.execute(it) }
    }

    fun sendInfoMessage(update: CommandUpdate) {
        val infoText = """
      Привет, я $BOT_USERNAME. 
      
      Чат со мной - это место для твоих лучших мемов, которыми охота поделиться.
      Сейчас же отправляй мне самый крутой мем, и, если он пройдёт модерацию, то попадёт на канал <a href="https://t.me/chsdngm/">че с деньгами</a>. 
      Мем, набравший за неделю больше всех кристаллов, станет <b>мемом недели</b>, а его обладатель получит бесконечный респект и поздравление на канале.

      Термины и определения:
      
      Мем (англ. meme) — единица культурной информации, имеющей развлекательный характер (в нашем случае - картинка). 
      Модерация (от лат. moderor — умеряю, сдерживаю) — все мемы проходят предварительную оценку экспертов, а на канал попадут только лучшие. 
      
      За динамикой оценки также можно следить тут.
    """.trimIndent()


        SendMessage().apply {
            chatId = update.senderId
            parseMode = ParseMode.HTML
            text = infoText
        }.let { api.execute(it) }
    }

    fun changeConfig(update: CommandUpdate) {
        val message = when {
            update.text.contains("enable publishing") -> {
                TillyConfig.publishEnabled = true
                "Публикация мемов включена"
            }
            update.text.contains("disable publishing") -> {
                TillyConfig.publishEnabled = false
                "Публикация мемов выключена"
            }
            else ->
                """
          Не удается прочитать сообщение. Правильные команды выглядят так:
          /config enable publishing
          /config disable publishing
        """.trimIndent()
        }

        SendMessage().apply {
            chatId = TillyConfig.BETA_CHAT_ID
            parseMode = ParseMode.HTML
            replyToMessageId = update.messageId
            text = message
        }.let { api.execute(it) }
    }
}
