package com.yonwi.farming;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The debug setting's output: "[UF]" lines in RuneLite's client.log, and the last few detected
 * events for the debug box.
 */
final class DebugLog {
  private static final Logger LOG = LoggerFactory.getLogger(FarmingPlugin.class);
  private static final int EVENTS_KEPT = 4;
  private static final int EVENT_LENGTH = 40;

  @Inject private FarmingConfig config;

  private final Deque<String> events = new ArrayDeque<>();
  /** What each overlay highlighted last, so the log gets a line only when it changes. */
  private final Map<String, String> lastHighlights = new HashMap<>();
  /** Lines already logged by {@link #logOnce}. */
  private final Set<String> seen = new HashSet<>();
  /** The inventory overlay's last decision, shown in the debug box. */
  private volatile String overlayHint = "";

  /** With the debug setting on: a line in RuneLite's client.log, prefixed "[UF]". */
  void log(String text) {
    if (config.debug()) LOG.info("[UF] {}", text);
  }

  /** Logs a line the first time it comes up, and never again. */
  void logOnce(String line) {
    if (seen.add(line)) log(line);
  }

  /** Remembers the last few detected events for the debug box. */
  void event(String text) {
    String line = text.length() > EVENT_LENGTH ? text.substring(0, EVENT_LENGTH) + "…" : text;
    if (line.equals(events.peekFirst())) return;
    log("event: " + text);
    events.addFirst(line);
    while (events.size() > EVENTS_KEPT) events.removeLast();
  }

  Iterable<String> events() {
    return events;
  }

  /** Logs what an overlay highlights, and in which colour, whenever that changes. */
  void noteHighlights(String overlay, String what) {
    if (!config.debug() || what.equals(lastHighlights.get(overlay))) return;
    lastHighlights.put(overlay, what);
    log("highlights (" + overlay + "): " + (what.isEmpty() ? "none" : what));
  }

  void setOverlayHint(String hint) {
    overlayHint = hint;
  }

  String overlayHint() {
    return overlayHint;
  }
}
