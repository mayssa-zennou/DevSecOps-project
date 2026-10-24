package com.mayssa.ordersaga.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OrderTest {
    
    @Test
    void newOrder_startsPending_withGeneratedIdAndTimestamp() {
        Order order = new Order("P-42", 2);

        assertThat(order.getId()).isNotNull();
        assertThat(order.getProductId()).isEqualTo("P-42");
        assertThat(order.getQuantity()).isEqualTo(2);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getCreatedAt()).isNotNull();
    }

    @Test
    void confirm_movesPendingOrderToConfirmed() {
        Order order = new Order("P-42", 2);
        order.confirm();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void reject_movesPendingOrderToRejected() {
        Order order = new Order("P-42", 2);
        order.reject();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
    }

    @Test 
    void confirmedOrder_cannotBeRejected() {
        Order order = new Order("P-42", 2);
        order.confirm();
        assertThatThrownBy(order::reject)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already CONFIRMED");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test 
    void rejectedOrder_cannotBeConfirmed() {
        Order order = new Order("P-42", 2);
        order.reject();
        assertThatThrownBy(order::confirm)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already REJECTED");
            
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
    }
}
