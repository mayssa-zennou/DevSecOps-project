package com.mayssa.ordersaga.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasItem;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest 
@AutoConfigureMockMvc 
@Testcontainers 
class OrderApiIntegrationTest {
    
    @Container 
    @ServiceConnection 
    static PostgreSQLContainer postgresql = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired 
    MockMvc mvc;

    @Autowired 
    OrderRepository repository;

    // arrange / act / assert tests
    @Test 
    void placeOrder_returns201_andPersistsPendingOrder() throws Exception {
        
        // request
        mvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId": "SKU-42", "quantity": 3}
                                """))

        // response check
                .andExpect(status().isCreated())  // code 201
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.productId").value("SKU-42"))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.status").value("PENDING"));

        // db check
        assertThat(repository.findAll())
                .anyMatch(o -> o.getProductId().equals("SKU-42")
                        && o.getStatus() == OrderStatus.PENDING);
    }

    @Test 
    void placeOrder_withZeroQuantity_returns400_andPersistsNothing() throws Exception {
        long before = repository.count();

        mvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId": "SKU-42", "quantity": 0}
                                """))
                .andExpect(status().isBadRequest());  // code 400
        
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test 
    void placeOrder_withBlankProductId_returns400() throws Exception {
        long before = repository.count();
        
        mvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId": "", "quantity": 1}
                                """))
                .andExpect(status().isBadRequest());
        
        assertThat(repository.count()).isEqualTo(before);
    }
    
    @Test 
    void getOrder_returnsExistingOrder() throws Exception {
        Order saved = repository.save(new Order("SKU-7", 2));

        mvc.perform(get("/orders/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.productId").value(saved.getProductId()))
                .andExpect(jsonPath("$.quantity").value(saved.getQuantity()));
    }

    @Test 
    void getOrder_withUnknownId_returns404() throws Exception {
        mvc.perform(get("/orders/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrder_withMalformedId_returns400() throws Exception {
        mvc.perform(get("/orders/Invalid_UUID_0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listOrders_includesSavedOrders() throws Exception {
        Order saved = repository.save(new Order("SKU-9", 1));

        mvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(saved.getId().toString())));
    }
}
