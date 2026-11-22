package com.mayssa.ordersaga.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "outbox.relay.enabled=false"
})
@AutoConfigureMockMvc 
@Testcontainers 
class OrderApiIntegrationTest {

    private static final String ALICE = "11111111-1111-1111-1111-111111111111";
    private static final String BOB = "22222222-2222-2222-2222-222222222222";
    private static final String EMAIL = "alice@test.com";
    
    @Container 
    @ServiceConnection 
    static PostgreSQLContainer postgresql = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired 
    MockMvc mvc;

    @Autowired 
    OrderRepository repository;

    @BeforeEach
    void cleanOrders() {
        repository.deleteAll();
    }

    // arrange / act / assert tests
    @Test
    void placeOrder_returns201_andPersistsPendingOrder() throws Exception {
        
        // request
        MvcResult result = mvc.perform(post("/orders")
        .with(jwt().jwt(token -> token.subject(ALICE).claim("email", EMAIL))
                .authorities(new SimpleGrantedAuthority("SCOPE_order:write")))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"productId": "SKU-42", "quantity": 3}
        """))
        
        // response check
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.productId").value("SKU-42"))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
                
        // db check
        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        Order persisted = repository.findById(UUID.fromString(id)).orElseThrow();

        assertThat(persisted.getProductId()).isEqualTo("SKU-42");
        assertThat(persisted.getQuantity()).isEqualTo(3);
        assertThat(persisted.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test 
    void placeOrder_withZeroQuantity_returns400_andPersistsNothing() throws Exception {
        long before = repository.count();

        mvc.perform(post("/orders")
                        .with(jwt().jwt(token -> token.subject(ALICE).claim("email", EMAIL))
                                .authorities(new SimpleGrantedAuthority("SCOPE_order:write")))
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
                        .with(jwt().jwt(token -> token.subject(ALICE).claim("email", EMAIL))
                                .authorities(new SimpleGrantedAuthority("SCOPE_order:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId": "", "quantity": 1}
                                """))
                .andExpect(status().isBadRequest());
        
        assertThat(repository.count()).isEqualTo(before);
    }
    
    @Test 
    void getOrder_returnsExistingOrder() throws Exception {
        Order saved = repository.save(new Order("SKU-7", 2, ALICE, EMAIL));

        mvc.perform(get("/orders/" + saved.getId()).with(readToken(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.productId").value("SKU-7"))
                .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    void getOrder_ownedByAnotherCustomer_returns404() throws Exception {
        Order saved = repository.save(new Order("SKU-private", 1, ALICE, EMAIL));

        mvc.perform(get("/orders/" + saved.getId()).with(readToken(BOB)))
                .andExpect(status().isNotFound());
    }

    @Test 
    void getOrder_withUnknownId_returns404() throws Exception {
        mvc.perform(get("/orders/" + UUID.randomUUID()).with(readToken(ALICE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrder_withMalformedId_returns400() throws Exception {
        mvc.perform(get("/orders/Invalid_UUID_0").with(readToken(ALICE)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listOrders_includesSavedOrders() throws Exception {
        Order saved = repository.save(new Order("SKU-9", 1, ALICE, EMAIL));
        repository.save(new Order("SKU-private", 2, BOB, "bob@test.com"));

        mvc.perform(get("/orders").with(readToken(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(saved.getId().toString()));
    }

    @Test
    void placeOrder_withoutEmailClaim_returns403() throws Exception {
        long before = repository.count();

        mvc.perform(post("/orders")
                        .with(jwt().jwt(token -> token.subject(ALICE))
                                .authorities(new SimpleGrantedAuthority("SCOPE_order:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"SKU-42\",\"quantity\":1}"))
                .andExpect(status().isForbidden());

        assertThat(repository.count()).isEqualTo(before);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor readToken(String subject) {
        return jwt().jwt(token -> token.subject(subject))
                .authorities(new SimpleGrantedAuthority("SCOPE_order:read"));
    }
}
