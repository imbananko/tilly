package com.imbananko.tilly.model;

import io.vavr.collection.HashSet;
import io.vavr.collection.Set;

public class Statistics {
  public Set<String> upVoters;
  public Set<String> explainVoters;
  public Set<String> downVoters;

  public Statistics(Set<String> upVoters, Set<String> explainVoters, Set<String> downVoters) {
    this.upVoters = upVoters;
    this.explainVoters = explainVoters;
    this.downVoters = downVoters;
  }

  public static Statistics emptyStatistics = new Statistics(HashSet.empty(), HashSet.empty(), HashSet.empty());
}