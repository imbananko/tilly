package com.imbananko.tilly.utility;

import io.vavr.Tuple2;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Helpers {
  public static String getMemeId(long chatId, Integer messageId) {
    return chatId + "_" + messageId;
  }

  public static Tuple2<Long, Integer> parseMemeId(String memeId) {
    try {
      final var parts = memeId.split("_");

      return new Tuple2<>(Long.parseLong(parts[0]), Integer.parseInt(parts[1]));
    } catch (Exception e) {
      return new Tuple2<>(0L, 0);
    }
  }
}
