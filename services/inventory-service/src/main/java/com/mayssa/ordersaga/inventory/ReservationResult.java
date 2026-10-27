package com.mayssa.ordersaga.inventory;

public enum ReservationResult {
    RESERVED,            // stock decremented
    INSUFFICIENT_STOCK,  // product exists, but not enough units
    UNKNOWN_PRODUCT      // no such product
}