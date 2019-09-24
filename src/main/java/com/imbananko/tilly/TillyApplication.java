package com.imbananko.tilly;

import com.imbananko.tilly.dao.DaoModule;
import com.imbananko.tilly.dao.MemeDaoImpl;
import com.imbananko.tilly.dao.VoteDaoImpl;
import com.typesafe.config.ConfigFactory;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.meta.TelegramBotsApi;

@Slf4j
public class TillyApplication {

  public static void main(String[] args) {
    ApiContextInitializer.init();

    final var config = ConfigFactory.load();
    final var daoModule = new DaoModule(config);
    final var memeDao = new MemeDaoImpl(daoModule);
    final var voteDao = new VoteDaoImpl(daoModule);
    final var memeManager = new MemeManager(memeDao, voteDao, config);

    TelegramBotsApi telegramBotsApi = new TelegramBotsApi();

    Try.of(() -> telegramBotsApi.registerBot(memeManager))
      .onSuccess(ignore -> log.info("Bot registered"))
      .onFailure(error -> log.error("Bot registration was failed because of", error));
  }
}
