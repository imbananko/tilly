package com.imbananko.tilly.utility;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sql")
@Getter
public class SqlQueries {
  private Map<String, String> queries;

  public void setQueries(java.util.Map<String, String> queries) {
      this.queries = HashMap.ofAll(queries);
  }
}
