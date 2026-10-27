package com.mayssa.ordersaga.inventory;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final StockRepository repository;

    public InventoryService(StockRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ReservationResult reserve(String productId, int quantity) {
        if (repository.reserve(productId, quantity) == 1) {
            return ReservationResult.RESERVED;
        }
        // 0 rows updated: either the product doesn't exist or stock is too low
        return repository.existsById(productId)
                ? ReservationResult.INSUFFICIENT_STOCK
                : ReservationResult.UNKNOWN_PRODUCT;
    }

    public Optional<Stock> getStock(String productId) {
        return repository.findById(productId);
    }
}