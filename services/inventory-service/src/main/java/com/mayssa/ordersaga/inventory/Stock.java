package com.mayssa.ordersaga.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@Entity 
@Table(name="stock")
@Getter 
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor 
public class Stock {
    
    @Id 
    @Column(name = "product_id")
    private String productId;

    @Column(nullable = false) 
    private int available;

}
