package com.imbananko.tilly.utility;

import com.imbananko.tilly.model.VoteEntity;
import lombok.experimental.UtilityClass;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.function.Predicate;

@UtilityClass
public class TelegramPredicates {
  public Predicate<Update> hasPhoto() {
    return update -> update.hasMessage() && update.getMessage().hasPhoto();
  }

  public Predicate<Update> hasVote() {
    return update ->
        update.hasCallbackQuery()
            && (VoteEntity.Value.UP.name().equalsIgnoreCase(update.getCallbackQuery().getData())
                || VoteEntity.Value.DOWN
                    .name()
                    .equalsIgnoreCase(update.getCallbackQuery().getData()));
  }
}
