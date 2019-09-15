package com.imbananko.tilly.utility;

import com.imbananko.tilly.model.VoteEntity;
import lombok.experimental.UtilityClass;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@UtilityClass
public class TelegramPredicates {
  private final Set<String> voteValues =
      Arrays.stream(VoteEntity.Value.values()).map(Enum::toString).collect(Collectors.toSet());

  public Predicate<Update> hasPhoto() {
    return update -> update.hasMessage() && update.getMessage().hasPhoto();
  }

  public Predicate<Update> hasVote() {
    return update ->
        update.hasCallbackQuery() && voteValues.contains(update.getCallbackQuery().getData());
  }
}
