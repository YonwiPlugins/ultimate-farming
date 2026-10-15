package com.yonwi.farming;

/** A crop: its level, what's planted, its XP, its produce and the gardener's payment. */
final class Crop {
  final String name;
  final String sapling;
  final String fruit;
  final String payment;
  final int level;
  final int paymentQty;
  final double plant;
  final double check;
  final double harvest;

  Crop(
      String name,
      int level,
      String sapling,
      double plant,
      double check,
      double harvest,
      String fruit,
      String payment,
      int qty) {
    this.name = name;
    this.level = level;
    this.sapling = sapling;
    this.plant = plant;
    this.check = check;
    this.harvest = harvest;
    this.fruit = fruit;
    this.payment = payment;
    this.paymentQty = qty;
  }

  @Override
  public String toString() {
    return name;
  }
}
