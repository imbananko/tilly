package com.imbananko.tilly.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder(toBuilder = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemeEntity {
  private String fileId;
  private String authorUsername;
  private Long targetChatId;
}
