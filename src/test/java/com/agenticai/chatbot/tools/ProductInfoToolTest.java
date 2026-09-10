package com.agenticai.chatbot.tools;

import com.agenticai.chatbot.model.ChatModels.ProductInfoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductInfoToolTest {

    private ProductInfoTool productInfoTool;

    @BeforeEach
    void setUp() {
        productInfoTool = new ProductInfoTool();
    }

    @Test
    void testGetProductInfo_Success_ExistingProduct() {
        // Tests case insensitivity and trim features
        String productId = "  prod-001  ";
        ProductInfoResponse response = productInfoTool.getProductInfo(productId);

        assertNotNull(response);
        assertEquals("PROD-001", response.productId());
        assertEquals("AcmePro Laptop 15\"", response.name());
        assertEquals(1299.99, response.price());
        assertTrue(response.inStock());
        assertEquals("Electronics", response.category());
    }

    @Test
    void testGetProductInfo_NullProductId() {
        ProductInfoResponse response = productInfoTool.getProductInfo(null);

        assertNotNull(response);
        assertEquals("Unknown Product", response.name());
        assertEquals(0.0, response.price());
        assertEquals("N/A", response.category());
    }

    @Test
    void testGetProductInfo_NotFound() {
        String productId = "PROD-999";
        ProductInfoResponse response = productInfoTool.getProductInfo(productId);

        assertNotNull(response);
        assertEquals(productId, response.productId());
        assertEquals("Unknown Product", response.name());
        assertEquals("No product found with ID: " + productId, response.description());
    }
}