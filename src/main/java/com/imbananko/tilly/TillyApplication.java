package com.imbananko.tilly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.telegram.telegrambots.ApiContextInitializer;

@SpringBootApplication
public class TillyApplication {

  public static void main(String[] args) {
    ApiContextInitializer.init();
    SpringApplication.run(TillyApplication.class, args);
  }
}
