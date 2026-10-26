package com.mayssa.ordersaga.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockRepository extends JpaRepository<Stock, String> {

    /**
     * Atomic reservation: the check and the decrement happen in ONE statement.
     * Returns the number of rows updated: 1 = reserved, 0 = insufficient stock or unknown product.
     */
    @Modifying
    @Query("UPDATE Stock s SET s.available = s.available - :qty "
            + "WHERE s.productId = :productId AND s.available >= :qty")
    int reserve(@Param("productId") String productId, @Param("qty") int qty);
}