package com.imbananko.tilly.model;

import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
public class Statistics {
    public long upCount;
    public long explainCount;
    public long downCount;

    public Statistics(List<StatsEntity> stats) {
        this.upCount = voteCount(stats, VoteEntity.Value.UP);
        this.explainCount = voteCount(stats, VoteEntity.Value.EXPLAIN);
        this.downCount = voteCount(stats, VoteEntity.Value.DOWN);
    }

    private static long voteCount(List<StatsEntity> stats, VoteEntity.Value voteValue) {
        return stats.stream()
                .filter(it -> it.value.equals(voteValue))
                .findAny().map(it -> it.count).orElse(0L);
    }

    public static Statistics zeroStatistics = new Statistics();
}