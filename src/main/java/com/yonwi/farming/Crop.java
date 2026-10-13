package com.yonwi.farming;

final class Crop {
  final String name, sapling, fruit, payment;
  final int level, paymentQty;
  final double plant, check, harvest;

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
