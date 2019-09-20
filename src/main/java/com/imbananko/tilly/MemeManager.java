package com.imbananko.tilly;

import com.imbananko.tilly.dao.MemeDao;
import com.imbananko.tilly.dao.VoteDao;
import com.imbananko.tilly.model.MemeEntity;
import com.imbananko.tilly.model.Statistics;
import com.imbananko.tilly.model.VoteEntity;
import com.imbananko.tilly.utility.TelegramPredicates;
import io.vavr.collection.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import reactor.core.publisher.Mono;

import static com.imbananko.tilly.model.Statistics.zeroStatistics;
import static com.imbananko.tilly.model.VoteEntity.Value.*;
import static io.vavr.API.*;
import static io.vavr.Predicates.allOf;

@Component
@Slf4j
public class MemeManager extends TelegramLongPollingBot {

    private final MemeDao memeDao;
    private final VoteDao voteDao;

    @Value("${target.chat.id}")
    private long chatId;

    @Value("${bot.token}")
    private String token;

    @Value("${bot.username}")
    private String username;

    @Autowired
    public MemeManager(MemeDao memeDao, VoteDao voteDao) {
        this.memeDao = memeDao;
        this.voteDao = voteDao;
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public void onUpdateReceived(Update update) {
        Match(update)
                .of(
                        Case($(allOf(TelegramPredicates.isP2PChat(), TelegramPredicates.hasPhoto())), this::processMeme),
                        Case($(TelegramPredicates.hasVote()), this::processVote),
                        Case($(), Mono.empty())
                );
    }

    private Mono<?> processMeme(Update update) {
        Message message = update.getMessage();
        MemeEntity meme = MemeEntity.builder()
                .authorUsername(message.getChat().getUserName())
                .targetChatId(chatId)
                .fileId(message.getPhoto().get(0).getFileId())
                .build();

        return Mono.fromCallable(() -> execute(
                new SendPhoto()
                        .setChatId(chatId)
                        .setPhoto(meme.getFileId())
                        .setCaption("Sender: " + meme.getAuthorUsername())
                        .setReplyMarkup(createMarkup(zeroStatistics))
                )
        ).flatMap(mes -> memeDao.save(meme))
                .doOnSuccess(ignore -> log.info("Sent meme=" + meme))
                .doOnError(throwable -> log.error("Failed to send meme=" + meme + ". Exception=" + throwable.getMessage()));
    }

    private Mono<?> processVote(Update update) {
        final Message message = update.getCallbackQuery().getMessage();
        final Mono<MemeEntity> memeMono = memeDao.findById(message.getPhoto().get(0).getFileId());

        return memeMono.flatMap(meme -> {
            VoteEntity voteEntity =
                    VoteEntity.builder()
                            .chatId(chatId)
                            .fileId(meme.getFileId())
                            .username(update.getCallbackQuery().getFrom().getUserName())
                            .value(VoteEntity.Value.valueOf(update.getCallbackQuery().getData()))
                            .build();

            return voteDao.exists(voteEntity)
                    .flatMap(exists -> exists
                            ? voteDao.delete(voteEntity)
                            : voteDao.insertOrUpdate(voteEntity)
                    )
                    .flatMap(notUser -> voteDao.getStats(meme.getFileId(), meme.getTargetChatId()))
                    .flatMap(statistics ->
                            Mono.fromCallable(() -> execute(
                                    new EditMessageReplyMarkup()
                                            .setMessageId(message.getMessageId())
                                            .setChatId(message.getChatId())
                                            .setInlineMessageId(update.getCallbackQuery().getInlineMessageId())
                                            .setReplyMarkup(createMarkup(statistics))
                                    )
                            ).doOnSuccess(ignore -> log.info("Processed vote=" + voteEntity))
                                    .doOnError(throwable -> log.error("Failed to process vote=" + voteEntity + ". Exception=" + throwable.getMessage()))
                                    .flatMap(notUsed -> {
                                        if (VoteEntity.Value.valueOf(update.getCallbackQuery().getData()).equals(EXPLAIN) && statistics.explainCount == 3L) {
                                            final var replyText =
                                                    "@" + update.getCallbackQuery().getMessage().getCaption().replaceFirst("Sender: ", "")
                                                            + ", поясни за мем";

                                            return Mono.fromCallable(() -> execute(
                                                    new SendMessage()
                                                            .setChatId(message.getChatId())
                                                            .setReplyToMessageId(update.getCallbackQuery().getMessage().getMessageId())
                                                            .setText(replyText)))
                                                    .doOnSuccess(ignore -> log.info("Successful reply for explaining"))
                                                    .doOnError(throwable -> log.error("Failed to reply for explaining. Exception=" + throwable.getMessage()));
                                        } else {
                                            return Mono.empty();
                                        }
                                    })
                    );
        });
    }

    private static InlineKeyboardMarkup createMarkup(Statistics statistics) {
        return new InlineKeyboardMarkup()
                .setKeyboard(
                        List.of(
                                List.of(
                                        createVoteInlineKeyboardButton(UP, statistics.upCount),
                                        createVoteInlineKeyboardButton(EXPLAIN, statistics.explainCount),
                                        createVoteInlineKeyboardButton(DOWN, statistics.downCount)).asJava()).asJava());
    }

    private static InlineKeyboardButton createVoteInlineKeyboardButton(VoteEntity.Value voteValue, long voteCount) {
        return new InlineKeyboardButton()
                .setText(voteCount == 0L ? voteValue.getEmoji() : voteValue.getEmoji() + " " + voteCount)
                .setCallbackData(voteValue.name());
    }
}
