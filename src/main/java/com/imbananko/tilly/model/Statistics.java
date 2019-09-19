package com.imbananko.tilly.model;

import io.vavr.collection.List;
import lombok.NoArgsConstructor;


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
    return stats.find(it -> it.value.equals(voteValue)).map(it -> it.count).getOrElse(0L);
  }

  public static Statistics zeroStatistics = new Statistics();

}