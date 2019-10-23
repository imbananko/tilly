package com.imbananko.tilly.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Builder(toBuilder = true)
@Getter
@ToString
public class MemeEntity {
  private String memeId;
  private Integer senderId;
  private String fileId;
  private String authorUsername;
  private Long targetChatId;
}
