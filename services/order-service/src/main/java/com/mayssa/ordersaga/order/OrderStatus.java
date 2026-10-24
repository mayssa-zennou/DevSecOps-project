package com.mayssa.ordersaga.order;

public enum OrderStatus {
    PENDING,      // order created, waiting for stock response
    CONFIRMED,    // stock confirmed the order
    REJECTED      // stock refused the order (compensation)
}
