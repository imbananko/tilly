package com.imbananko.tilly.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Builder(toBuilder = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoteEntity {
  private String fileId;
  private String username;
  private Long chatId;

  private Value value;

  public enum Value {
    UP("\uD83D\uDC8E"),
    EXPLAIN("\uD83E\uDD14"),
    DOWN("\uD83D\uDCA9");

    private String emoji;

    Value(String emoji) {
      this.emoji = emoji;
    }

    public String getEmoji() {
      return emoji;
    }
  }

  @NoArgsConstructor
  @AllArgsConstructor
  @Data
  @Builder
  public static class VoteKey implements Serializable {
    private String fileId;
    private String username;
    private Long chatId;
  }
}
