package com.imbananko.tilly.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;

@Table(name = "vote")
@Entity
@Builder(toBuilder = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@IdClass(VoteEntity.VoteKey.class)
public class VoteEntity {
  @Id private String fileId;
  @Id private String username;
  @Id private Long chatId;

  @Enumerated(EnumType.STRING)
  private Value value;

  public enum Value {
    UP {
      @Override
      public String toString() {
        return "\uD83D\uDC8E";
      }
    },
    DOWN {
      @Override
      public String toString() {
        return "\uD83D\uDCA9";
      }
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
