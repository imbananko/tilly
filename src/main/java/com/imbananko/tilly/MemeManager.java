package com.imbananko.tilly;

import com.imbananko.tilly.model.MemeEntity;
import com.imbananko.tilly.model.Statistics;
import com.imbananko.tilly.model.VoteEntity;
import com.imbananko.tilly.repository.MemeRepository;
import com.imbananko.tilly.repository.VoteRepository;
import com.imbananko.tilly.utility.TelegramPredicates;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Try;
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

import java.util.concurrent.CompletableFuture;

import static com.imbananko.tilly.model.Statistics.emptyStatistics;
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
  private String botUsername;

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
    return botUsername;
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
    return CompletableFuture.runAsync(() -> {
      var message = update.getMessage();
      var meme =
        MemeEntity.builder()
          .authorUsername(message.getChat().getUserName())
          .targetChatId(chatId)
          .fileId(message.getPhoto().get(0).getFileId())
          .build();

      Try.of(() -> execute(
        new SendPhoto()
          .setChatId(chatId)
          .setPhoto(meme.getFileId())
          .setCaption("Sender: " + meme.getAuthorUsername())
          .setReplyMarkup(createMarkup(emptyStatistics))
        )
      )
        .onSuccess(ignore -> log.info("Sent meme=" + meme))
        .onFailure(throwable -> log.error("Failed to send meme=" + meme + ". Exception=" + throwable.getMessage()));

      memeRepository.save(meme);
    });
  }

  private CompletableFuture<?> processVote(Update update) {
    final var message = update.getCallbackQuery().getMessage();
    final var fileId = message.getPhoto().get(0).getFileId();
    final var targetChatId = message.getChatId();
    final var voteValue = VoteEntity.Value.valueOf(update.getCallbackQuery().getData().split(" ")[0]);
    final var voterUsername = update.getCallbackQuery().getFrom().getUserName();

    var voteEntity =
      VoteEntity.builder()
        .chatId(targetChatId)
        .fileId(fileId)
        .username(voterUsername)
        .value(voteValue)
        .build();

    final var currentStats = getStatisticsFromMarkup(message);
    final var newStats = calculateNewStatistics(currentStats, voterUsername, voteValue);

    Try.of(() -> execute(
      new EditMessageReplyMarkup()
        .setMessageId(message.getMessageId())
        .setChatId(message.getChatId())
        .setInlineMessageId(update.getCallbackQuery().getInlineMessageId())
        .setReplyMarkup(createMarkup(newStats))
      )
    )
      .onSuccess(ignore -> log.info("Processed vote=" + voteEntity))
      .onFailure(throwable -> log.error("Failed to process vote=" + voteEntity + ". Exception=" + throwable.getMessage()));

    return CompletableFuture.runAsync(() -> {
      final var voteWasExisted =
        (currentStats.upVoters.contains(voterUsername) && voteValue.equals(UP))
          || (currentStats.explainVoters.contains(voterUsername) && voteValue.equals(EXPLAIN))
          || (currentStats.downVoters.contains(voterUsername) && voteValue.equals(DOWN));

      if (voteWasExisted) {
        voteRepository.delete(voteEntity);
      } else {
        voteRepository.insertOrUpdate(voteEntity);
      }

      if (voteValue.equals(EXPLAIN) && newStats.explainVoters.length() == 3) {
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
    });
  }

  private static InlineKeyboardMarkup createMarkup(Statistics stats) {
    return new InlineKeyboardMarkup().setKeyboard(
      java.util.List.of(
        java.util.List.of(
          createVoteInlineKeyboardButton(UP, stats.upVoters),
          createVoteInlineKeyboardButton(EXPLAIN, stats.explainVoters),
          createVoteInlineKeyboardButton(DOWN, stats.downVoters)
        )
      )
    );
  }

  private static InlineKeyboardButton createVoteInlineKeyboardButton(VoteEntity.Value voteValue, Set<String> voters) {
    var voteCount = voters.length();
    var votersString = voters.mkString("[", ",", "]");

    return new InlineKeyboardButton()
      .setText(voteCount == 0 ? voteValue.getEmoji() : voteValue.getEmoji() + " " + voteCount)
      .setCallbackData(voteValue.name() + " " + votersString);
  }

  private static Statistics getStatisticsFromMarkup(Message message) {
    final var buttons = message.getReplyMarkup().getKeyboard().get(0);
    final var votersList = List.ofAll(buttons).map(it -> extractVoters(it.getCallbackData()));

    return new Statistics(votersList.get(0), votersList.get(1), votersList.get(2));
  }

  private static Set<String> extractVoters(String data) {
    final var votersString = data.split(" ")[1].replaceAll("\\[|\\]", "");

    return votersString.isEmpty() ? HashSet.empty() : Set(votersString.split(","));
  }

  private static Statistics calculateNewStatistics(Statistics oldStats, String voteUsername, VoteEntity.Value voteValue) {
    var newUpStats = oldStats.upVoters.contains(voteUsername)
      ? oldStats.upVoters.remove(voteUsername)
      : UP.equals(voteValue)
        ? oldStats.upVoters.add(voteUsername)
        : oldStats.upVoters;

    var newExplainStats = oldStats.explainVoters.contains(voteUsername)
      ? oldStats.explainVoters.remove(voteUsername)
      : EXPLAIN.equals(voteValue)
        ? oldStats.explainVoters.add(voteUsername)
        : oldStats.explainVoters;

    var newDownStats = oldStats.downVoters.contains(voteUsername)
      ? oldStats.downVoters.remove(voteUsername)
      : DOWN.equals(voteValue)
        ? oldStats.downVoters.add(voteUsername)
        : oldStats.downVoters;

    return new Statistics(newUpStats, newExplainStats, newDownStats);
  }
}
