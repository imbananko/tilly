package com.imbananko.tilly.model;

import io.vavr.Tuple2;
import io.vavr.collection.List;
import lombok.NoArgsConstructor;


@NoArgsConstructor
public class Statistics {

  public long upCount;
  public long explainCount;
  public long downCount;

  public Statistics(List<Tuple2<VoteEntity.Value, Long>> rawStats) {
    this.upCount = voteCount(rawStats, VoteEntity.Value.UP);
    this.explainCount = voteCount(rawStats, VoteEntity.Value.EXPLAIN);
    this.downCount = voteCount(rawStats, VoteEntity.Value.DOWN);
  }

  private static long voteCount(List<Tuple2<VoteEntity.Value, Long>> rawStats, VoteEntity.Value voteValue) {
    return rawStats.find(it -> it._1.equals(voteValue)).map(it -> it._2).getOrElse(0L);
  }

  public static Statistics zeroStatistics = new Statistics();

}