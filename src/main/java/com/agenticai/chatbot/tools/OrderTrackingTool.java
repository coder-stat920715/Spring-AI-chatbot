package com.agenticai.chatbot.tools;

import com.agenticai.chatbot.model.ChatModels.OrderTrackingResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Agentic tool: order tracking.
 *
 * <p>Claude calls this when the user provides an order ID or asks about
 * delivery status. In production, replace the mock store with a real
 * database or logistics API call.
 */
@Slf4j
@Component
public class OrderTrackingTool {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy");

    /** Simulated order database. */
    private static final Map<String, OrderTrackingResponse> ORDERS = Map.of(

            "ORD-1001", new OrderTrackingResponse(
                    "ORD-1001", "Delivered",
                    LocalDate.now().minusDays(2).format(DATE_FMT),
                    "FedEx", "FX-88912345",
                    "Delivered to front door"),

            "ORD-1002", new OrderTrackingResponse(
                    "ORD-1002", "In Transit",
                    LocalDate.now().plusDays(2).format(DATE_FMT),
                    "UPS", "1Z-99876543",
                    "Memphis, TN Distribution Center"),

            "ORD-1003", new OrderTrackingResponse(
                    "ORD-1003", "Processing",
                    LocalDate.now().plusDays(5).format(DATE_FMT),
                    "DHL", "DHL-56789012",
                    "AcmeCorp Warehouse"),

            "ORD-1004", new OrderTrackingResponse(
                    "ORD-1004", "Out for Delivery",
                    LocalDate.now().format(DATE_FMT),
                    "USPS", "9400111899223345678",
                    "Local Post Office"),

            "ORD-1005", new OrderTrackingResponse(
                    "ORD-1005", "Shipped",
                    LocalDate.now().plusDays(3).format(DATE_FMT),
                    "FedEx", "FX-11234567",
                    "Chicago, IL Sort Facility")
    );

    /**
     * Tracks the status of an order by its ID.
     *
     * @param orderId The order identifier, e.g. "ORD-1001".
     */
    @Tool(name = "track_order",
          description = "Track the current status and location of a customer order. "
                      + "Returns order status, estimated delivery date, carrier, "
                      + "tracking number, and last known location.")
    public OrderTrackingResponse trackOrder(
            @ToolParam(description = "The order ID to track, e.g. 'ORD-1001'")
            String orderId) {

        log.info("[TOOL CALL] track_order → orderId={}", orderId);

        String normalized = orderId == null ? "" : orderId.trim().toUpperCase();
        OrderTrackingResponse result = ORDERS.get(normalized);

        if (result == null) {
            // Return a "not found" response rather than throwing
            return new OrderTrackingResponse(
                    orderId,
                    "Not Found",
                    "N/A",
                    "N/A",
                    "N/A",
                    "Order ID not found. Please check and try again."
            );
        }

        return result;
    }
}