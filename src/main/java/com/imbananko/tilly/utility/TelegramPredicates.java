package com.imbananko.tilly.utility;

import com.imbananko.tilly.model.VoteEntity;
import lombok.experimental.UtilityClass;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Set;
import java.util.function.Predicate;

@UtilityClass
public class TelegramPredicates {
  private final Set<VoteEntity.Value> voteValues = Set.of(VoteEntity.Value.values());

  public Predicate<Update> hasPhoto() {
    return update -> update.hasMessage() && update.getMessage().hasPhoto();
  }

  public Predicate<Update> isP2PChat() {
    return update -> update.hasMessage() && update.getMessage().getChat().isUserChat();
  }

  public Predicate<Update> hasVote() {
    return update ->
        update.hasCallbackQuery()
            && voteValues.contains(VoteEntity.Value.valueOf(update.getCallbackQuery().getData()));
  }
}
