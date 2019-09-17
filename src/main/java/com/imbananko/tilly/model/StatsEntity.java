package com.imbananko.tilly.model;

public class StatsEntity {
    VoteEntity.Value value;
    long count;

    public StatsEntity(VoteEntity.Value value, long count) {
        this.value = value;
        this.count  = count;
    }
}