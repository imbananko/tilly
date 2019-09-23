package com.imbananko.tilly.utility;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@Data
@ConfigurationProperties(prefix = "sqlka")
public class SqlQueries {
    private String lol;
    private Map<String, String> queries;

}
