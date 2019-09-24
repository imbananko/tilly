package com.imbananko.tilly.utility;

import com.imbananko.tilly.model.VoteEntity;
import io.vavr.control.Try;
import lombok.experimental.UtilityClass;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.function.Predicate;

@UtilityClass
public class TelegramPredicates {
  public Predicate<Update> hasPhoto() {
    return update -> update.hasMessage() && update.getMessage().hasPhoto();
  }

  public Predicate<Update> isP2PChat() {
    return update -> update.hasMessage() && update.getMessage().getChat().isUserChat();
  }

  public Predicate<Update> hasVote() {
    return update -> {
      var voteParts = update.getCallbackQuery().getData().split(" ");

      return update.hasCallbackQuery()
        && voteParts.length > 1
        && Try.of(() -> VoteEntity.Value.valueOf(voteParts[0])).isSuccess();
    };
  }
}