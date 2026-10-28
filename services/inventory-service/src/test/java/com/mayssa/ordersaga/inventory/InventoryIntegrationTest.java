package com.mayssa.ordersaga.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=50")
@AutoConfigureMockMvc
@Testcontainers
class InventoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgresql = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    StockRepository repository;

    @Autowired
    InventoryService service;

    @Test
    void getStock_returnsAvailableQuantity() throws Exception {
        repository.save(new Stock("SKU-A", 10));

        mvc.perform(get("/stock/SKU-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("SKU-A"))
                .andExpect(jsonPath("$.available").value(10));
    }

    @Test
    void getStock_unknownProduct_returns404() throws Exception {
        mvc.perform(get("/stock/Unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reserve_decrementsAvailableStock() throws Exception {
        repository.save(new Stock("SKU-B", 10));

        reserve("SKU-B", 6)
                .andExpect(status().isOk());

        assertThat(repository.findById("SKU-B").orElseThrow().getAvailable())
                .isEqualTo(4);
    }

    @Test
    void reserve_exactlyAvailableQuantity_succeedsAndEmptiesStock() throws Exception {
        repository.save(new Stock("SKU-E", 10));

        reserve("SKU-E", 10)
                .andExpect(status().isOk());

        assertThat(repository.findById("SKU-E").orElseThrow().getAvailable()).isZero();
    }

    @Test
    void reserve_withInsufficientStock_returns409_andLeavesStockUntouched() throws Exception {
        repository.save(new Stock("SKU-C", 10));

        reserve("SKU-C", 11)
                .andExpect(status().isConflict());

        assertThat(repository.findById("SKU-C").orElseThrow().getAvailable())
                .isEqualTo(10);
    }

    @Test
    void reserve_unknownProduct_returns404() throws Exception {
        reserve("Unknown", 4)
                .andExpect(status().isNotFound());
    }

    @Test
    void reserve_withInvalidQuantity_returns400() throws Exception {
        repository.save(new Stock("SKU-D", 10));

        reserve("SKU-D", 0)
                .andExpect(status().isBadRequest());

        assertThat(repository.findById("SKU-D").orElseThrow().getAvailable())
                .isEqualTo(10);
    }

    @Test
    void concurrentReservations_neverOversell() throws Exception {
        repository.save(new Stock("SKU-RACE", 10));
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            CountDownLatch startSignal = new CountDownLatch(1);

            List<Future<ReservationResult>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    startSignal.await();                 // everyone waits here...
                    return service.reserve("SKU-RACE", 1);
                }));
            }
            startSignal.countDown();                     // ...and is released at the same instant

            long reserved = 0;
            long rejected = 0;
            for (Future<ReservationResult> future : futures) {
                ReservationResult result = future.get(10, TimeUnit.SECONDS);
                if (result == ReservationResult.RESERVED) {
                    reserved++;
                } else if (result == ReservationResult.INSUFFICIENT_STOCK) {
                    rejected++;
                }
            }

            assertThat(reserved).isEqualTo(10);
            assertThat(rejected).isEqualTo(40);
            assertThat(repository.findById("SKU-RACE").orElseThrow().getAvailable()).isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    private ResultActions reserve(String productId, int quantity) throws Exception {
        return mvc.perform(post("/stock/" + productId + "/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\": " + quantity + "}"));
    }
}