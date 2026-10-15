package com.yonwi.farming;

/** A farming patch: its ID, display name and run type (herb, tree, fruit…). */
final class Patch {
  final String id;
  final String name;
  final String kind;

  Patch(String id, String name, String kind) {
    this.id = id;
    this.name = name;
    this.kind = kind;
  }
}
