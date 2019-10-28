package com.imbananko.tilly.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Builder(toBuilder = true)
@Getter
@ToString
public class MemeEntity {
  private long chatId;
  private int messageId;
  private int senderId;
  private String fileId;
}
