package com.imbananko.tilly;

import com.imbananko.tilly.model.MemeEntity;
import com.imbananko.tilly.model.VoteEntity;
import com.imbananko.tilly.repository.MemeRepository;
import com.imbananko.tilly.repository.VoteRepository;
import com.imbananko.tilly.utility.TelegramPredicates;
import io.vavr.collection.HashMap;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;
import java.util.Optional;

import static com.imbananko.tilly.model.VoteEntity.Value.*;
import static io.vavr.API.*;
import static io.vavr.Predicates.allOf;

@Slf4j
@Component
public class MemeManager extends TelegramLongPollingBot {

  private final MemeRepository memeRepository;
  private final VoteRepository voteRepository;

  @Value("${target.chat.id}")
  private long chatId;

  @Value("${bot.token}")
  private String token;

  @Value("${bot.username}")
  private String username;

  @Autowired
  public MemeManager(MemeRepository memeRepository, VoteRepository voteRepository) {
    this.memeRepository = memeRepository;
    this.voteRepository = voteRepository;
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
        Case($(), () -> null)
      );
  }

  private MemeEntity processMeme(Update update) {
    final var message = update.getMessage();
    final var meme =
      MemeEntity.builder()
        .authorUsername(message.getChat().getUserName())
        .targetChatId(chatId)
        .fileId(message.getPhoto().get(0).getFileId())
        .build();
    final var memeCaption =
      Optional.ofNullable(message.getCaption()).map(it -> it.trim() + "\n\n").orElse("") + "Sender: " + meme.getAuthorUsername();

    Try.of(() -> execute(
      new SendPhoto()
        .setChatId(chatId)
        .setPhoto(meme.getFileId())
        .setCaption(memeCaption)
        .setReplyMarkup(createMarkup(HashMap.empty()))
      )
    )
      .onSuccess(ignore -> log.info("Sent meme=" + meme))
      .onFailure(throwable -> log.error("Failed to send meme=" + meme + ". Exception=" + throwable.getMessage()));

    memeRepository.save(meme);
    return meme;
  }

  private VoteEntity processVote(Update update) {
    final var message = update.getCallbackQuery().getMessage();
    final var fileId = message.getPhoto().get(0).getFileId();
    final var targetChatId = message.getChatId();

    var voteEntity =
      VoteEntity.builder()
        .chatId(targetChatId)
        .fileId(fileId)
        .username(update.getCallbackQuery().getFrom().getUserName())
        .value(VoteEntity.Value.valueOf(update.getCallbackQuery().getData()))
        .build();

    if (voteRepository.exists(voteEntity)) {
      voteRepository.delete(voteEntity);
    } else {
      voteRepository.insertOrUpdate(voteEntity);
    }

    final var statistics = voteRepository.getStats(fileId, targetChatId);

    Try.of(() -> execute(
      new EditMessageReplyMarkup()
        .setMessageId(message.getMessageId())
        .setChatId(message.getChatId())
        .setInlineMessageId(update.getCallbackQuery().getInlineMessageId())
        .setReplyMarkup(createMarkup(statistics))
      )
    )
      .onSuccess(ignore -> log.info("Processed vote=" + voteEntity))
      .onFailure(throwable -> log.error("Failed to process vote=" + voteEntity + ". Exception=" + throwable.getMessage()));

    if (VoteEntity.Value.valueOf(update.getCallbackQuery().getData()).equals(EXPLAIN) && statistics.getOrElse(EXPLAIN, 0L) == 3L) {

      final var replyText =
        "@" + update.getCallbackQuery().getMessage().getCaption().replaceFirst("Sender: ", "")
        + ", поясни за мем";

      Try.of(() -> execute(
        new SendMessage()
          .setChatId(message.getChatId())
          .setReplyToMessageId(update.getCallbackQuery().getMessage().getMessageId())
          .setText(replyText)
        )
      )
        .onSuccess(ignore -> log.info("Successful reply for explaining"))
        .onFailure(throwable -> log.error("Failed to reply for explaining. Exception=" + throwable.getMessage()));
    }

    return voteEntity;
  }

  private static InlineKeyboardMarkup createMarkup(HashMap<VoteEntity.Value, Long> stats) {
    return new InlineKeyboardMarkup().setKeyboard(
      List.of(
        List.of(
          createVoteInlineKeyboardButton(UP, stats.getOrElse(UP, 0L)),
          createVoteInlineKeyboardButton(EXPLAIN, stats.getOrElse(EXPLAIN, 0L)),
          createVoteInlineKeyboardButton(DOWN, stats.getOrElse(DOWN, 0L)))
      )
    );
  }

  private static InlineKeyboardButton createVoteInlineKeyboardButton(VoteEntity.Value voteValue, long voteCount) {
    return new InlineKeyboardButton()
      .setText(voteCount == 0L ? voteValue.getEmoji() : voteValue.getEmoji() + " " + voteCount)
      .setCallbackData(voteValue.name());
  }
}
