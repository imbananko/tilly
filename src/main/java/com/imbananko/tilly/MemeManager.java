package com.imbananko.tilly;

import com.imbananko.tilly.model.MemeEntity;
import com.imbananko.tilly.model.VoteEntity;
import com.imbananko.tilly.repository.MemeRepository;
import com.imbananko.tilly.repository.UserRepository;
import com.imbananko.tilly.repository.VoteRepository;
import com.imbananko.tilly.utility.TelegramPredicates;
import com.imbananko.tilly.utility.Helpers;
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
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import static com.imbananko.tilly.model.VoteEntity.Value.*;
import static io.vavr.API.*;
import static io.vavr.Predicates.allOf;

@Slf4j
@Component
public class MemeManager extends TelegramLongPollingBot {

  private final MemeRepository memeRepository;
  private final VoteRepository voteRepository;
  private final UserRepository userRepository;

  @Value("${target.chat.id}")
  private long chatId;

  @Value("${bot.token}")
  private String token;

  @Value("${bot.username}")
  private String username;

  @Autowired
  public MemeManager(MemeRepository memeRepository, VoteRepository voteRepository, UserRepository userRepository) {
    this.memeRepository = memeRepository;
    this.voteRepository = voteRepository;
    this.userRepository = userRepository;
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

  private CompletableFuture<?> processMeme(Update update) {
    final var message = update.getMessage();
    final var fileId = message.getPhoto().get(0).getFileId();
    final var authorUsername = message.getFrom().getUserName();
    final var memeCaption =
      Optional.ofNullable(message.getCaption()).map(it -> it.trim() + "\n\n").orElse("")
        + "Sender: "
        + Optional.ofNullable(authorUsername).orElse("tg://user?id=" + message.getFrom().getId());

    Try.of(() -> execute(
      new SendPhoto()
        .setChatId(chatId)
        .setPhoto(fileId)
        .setCaption(memeCaption)
        .setReplyMarkup(createMarkup(HashMap.empty(), false))
      )
    )
      .onSuccess(sentMemeMessage -> {
        final var meme = MemeEntity.builder()
          .memeId(Helpers.getMemeId(sentMemeMessage.getChatId(), sentMemeMessage.getMessageId()))
          .senderId(message.getFrom().getId())
          .authorUsername(authorUsername)
          .targetChatId(chatId)
          .fileId(message.getPhoto().get(0).getFileId())
          .build();
        log.info("Sent meme=" + meme);
        memeRepository.save(meme);
      })
      .onFailure(throwable -> log.error("Failed to send meme from message=" + message + ". Exception=" + throwable.getMessage()));

    return saveUser(message.getFrom());
  }

  private CompletableFuture<?> processVote(Update update) {
    final var message = update.getCallbackQuery().getMessage();
    final var targetChatId = message.getChatId();
    final var memeId = Helpers.getMemeId(targetChatId, message.getMessageId());
    final var fileId = message.getPhoto().get(0).getFileId();
    final var vote = VoteEntity.Value.valueOf(update.getCallbackQuery().getData().split(" ")[0]);
    final var voteSender = update.getCallbackQuery().getFrom();

    final var wasExplained = message
      .getReplyMarkup()
      .getKeyboard().get(0).get(1)
      .getCallbackData()
      .contains("EXPLAINED");

    var voteEntity =
      VoteEntity.builder()
        .memeId(memeId)
        .voterId(voteSender.getId())
        .chatId(targetChatId)
        .fileId(fileId)
        .username(voteSender.getUserName())
        .value(vote)
        .build();

    if (voteRepository.isSenderAndVoterSame(memeId, voteSender.getId())) return CompletableFuture.completedFuture(null);

    if (voteRepository.exists(voteEntity)) {
      voteRepository.delete(voteEntity);
    } else {
      voteRepository.insertOrUpdate(voteEntity);
    }

    final var statistics = voteRepository.getStats(memeId);
    final var shouldMarkExplained = vote.equals(EXPLAIN) && !wasExplained && statistics.getOrElse(EXPLAIN, 0L) == 3L;

    Try.of(() -> execute(
      new EditMessageReplyMarkup()
        .setMessageId(message.getMessageId())
        .setChatId(message.getChatId())
        .setInlineMessageId(update.getCallbackQuery().getInlineMessageId())
        .setReplyMarkup(createMarkup(statistics, wasExplained || shouldMarkExplained))
      )
    )
      .onSuccess(ignore -> log.info("Processed vote=" + voteEntity))
      .onFailure(throwable -> log.error("Failed to process vote=" + voteEntity + ". Exception=" + throwable.getMessage()));

    if (shouldMarkExplained) {

      final var replyText =
        update.getCallbackQuery().getMessage().getCaption().replaceFirst("Sender: ", "@")
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

    return saveUser(update.getCallbackQuery().getFrom());
  }

  private static InlineKeyboardMarkup createMarkup(HashMap<VoteEntity.Value, Long> stats, boolean markExplained) {
    BiFunction<VoteEntity.Value, Long, InlineKeyboardButton> createVoteInlineKeyboardButton = (voteValue, voteCount) -> {
      final var callbackData = voteValue.equals(EXPLAIN) && markExplained
              ? voteValue.name() + " EXPLAINED"
              : voteValue.name();

      return new InlineKeyboardButton()
              .setText(voteCount == 0L ? voteValue.getEmoji() : voteValue.getEmoji() + " " + voteCount)
              .setCallbackData(callbackData);
    };

    return new InlineKeyboardMarkup().setKeyboard(
      List.of(
        List.of(
          createVoteInlineKeyboardButton.apply(UP, stats.getOrElse(UP, 0L)),
          createVoteInlineKeyboardButton.apply(EXPLAIN, stats.getOrElse(EXPLAIN, 0L)),
          createVoteInlineKeyboardButton.apply(DOWN, stats.getOrElse(DOWN, 0L))
        )
      )
    );
  }

  private CompletableFuture saveUser(User user) {
    return CompletableFuture.runAsync(() -> {
      if (!user.getBot()) {
        userRepository.saveIfNotExists(user);
      }
    }).handle((res, throwable) -> {
      if (throwable != null) {
        log.error("Failed to save user " + user + ". Exception=" + throwable);
      }

      return null;
    });
  }
}
