package com.imbananko.tilly.utility;

import org.springframework.stereotype.Service;

@Service
public class KEK {

    private final SqlQueries sqlQueries;


    public KEK(SqlQueries sqlQueries) {
        this.sqlQueries = sqlQueries;
    }
}
