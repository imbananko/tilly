package com.imbananko.tilly;

import com.imbananko.tilly.utility.SqlQueries;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.telegram.telegrambots.ApiContextInitializer;

@SpringBootApplication
@EnableConfigurationProperties(SqlQueries.class)
public class TillyApplication {

  public static void main(String[] args) {
    ApiContextInitializer.init();
    SpringApplication.run(TillyApplication.class, args);
  }
}
