package com.yonwi.farming;

import java.util.Arrays;

/** One finished, timed run, as saved in the profile's "records" setting. */
final class RunRecord {
  String signature;
  String label;
  String date;
  String setup;
  long milliseconds;
  /** The time was struck off (paused, logged out mid-run…): the run still counts, its time doesn't. */
  boolean timeVoid;
  int patches;
  int completed;
  int herbs;
  int bark;
  /** Farming XP gained during the run (0 for runs from before it was counted). */
  long xp;
  /** GP at guide prices: produce minus seeds, saplings, payments and compost. Null if unpriced. */
  Long profit;

  /**
   * The run's type with its parts in a fixed order, so "tree + fruit" and "fruit + tree" are one
   * group.
   */
  String type() {
    if (label == null) return "other";
    String[] parts = label.split(" · ")[0].split(" \\+ ");
    Arrays.sort(parts);
    return String.join(" + ", parts);
  }

  /** The run in progress, as saved in the profile's "session" setting. */
  static final class Session {
    String signature;
    String label;
    String setup;
    long elapsed;
    int patches;
    /** Grimy herbs picked during the run. */
    int herbs;
    /** Celastrus bark chopped during the run. */
    int bark;
    /** Farming XP when the run started, or 0 if unknown. */
    long startXp;
  }
}
