package com.agenticai.chatbot.tools;

import com.agenticai.chatbot.model.ChatModels.OrderTrackingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OrderTrackingToolTest {

    private OrderTrackingTool orderTrackingTool;

    @BeforeEach
    void setUp() {
        orderTrackingTool = new OrderTrackingTool();
    }

    @Test
    void testTrackOrder_Success_ExistingOrder() {
        // Tests case insensitivity and trim features
        String orderId = "  ord-1001  ";
        OrderTrackingResponse response = orderTrackingTool.trackOrder(orderId);

        assertNotNull(response);
        assertEquals("ORD-1001", response.orderId());
        assertEquals("Delivered", response.status());
        assertEquals("FedEx", response.carrier());
        assertEquals("FX-88912345", response.trackingNumber());
    }

    @Test
    void testTrackOrder_NullOrderId() {
        OrderTrackingResponse response = orderTrackingTool.trackOrder(null);

        assertNotNull(response);
        assertEquals("Not Found", response.status());
        assertEquals("N/A", response.carrier());
        assertEquals("Order ID not found. Please check and try again.", response.lastLocation());
    }

    @Test
    void testTrackOrder_NotFound() {
        String orderId = "ORD-UNKNOWN";
        OrderTrackingResponse response = orderTrackingTool.trackOrder(orderId);

        assertNotNull(response);
        assertEquals(orderId, response.orderId());
        assertEquals("Not Found", response.status());
        assertEquals("N/A", response.carrier());
        assertEquals("Order ID not found. Please check and try again.", response.lastLocation());
    }
}