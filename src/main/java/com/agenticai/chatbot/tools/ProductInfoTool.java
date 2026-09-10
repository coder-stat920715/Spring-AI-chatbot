package com.agenticai.chatbot.tools;

import com.agenticai.chatbot.model.ChatModels.ProductInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Agentic tool: product catalog lookup.
 *
 * <p>Claude calls this when the user asks about product details, pricing,
 * or availability. In production, replace the in-memory map with a call
 * to your product database or external catalog API.
 */
@Slf4j
@Component
public class ProductInfoTool {

    /** Simulated product catalog. */
    private static final Map<String, ProductInfoResponse> CATALOG = Map.of(

            "PROD-001", new ProductInfoResponse(
                    "PROD-001", "AcmePro Laptop 15\"", 1299.99, true,
                    "15-inch laptop with Intel Core i7, 16GB RAM, 512GB SSD, and 4K display.",
                    "Electronics"),

            "PROD-002", new ProductInfoResponse(
                    "PROD-002", "AcmePhone X12", 799.00, true,
                    "Flagship smartphone with 6.7\" AMOLED display, 50MP camera, and 5G.",
                    "Mobile"),

            "PROD-003", new ProductInfoResponse(
                    "PROD-003", "AcmeBuds Pro", 149.99, false,
                    "True wireless earbuds with active noise cancellation and 30-hour battery.",
                    "Audio"),

            "PROD-004", new ProductInfoResponse(
                    "PROD-004", "AcmeWatch Series 5", 349.00, true,
                    "Smartwatch with health monitoring, GPS, and 7-day battery life.",
                    "Wearables"),

            "PROD-005", new ProductInfoResponse(
                    "PROD-005", "AcmeTab 11", 499.99, true,
                    "11-inch tablet with 2K display, 8GB RAM, stylus support, and 12-hour battery.",
                    "Tablets")
    );

    /**
     * Retrieves product information by product ID.
     *
     * @param productId The product identifier, e.g. "PROD-001".
     */
    @Tool(name = "get_product_info",
          description = "Retrieve detailed product information including name, price, "
                      + "availability, description, and category for a given product ID.")
    public ProductInfoResponse getProductInfo(
            @ToolParam(description = "The product ID, e.g. 'PROD-001'")
            String productId) {

        log.info("[TOOL CALL] get_product_info → productId={}", productId);

        String normalized = productId == null ? "" : productId.trim().toUpperCase();
        ProductInfoResponse product = CATALOG.get(normalized);

        if (product == null) {
            return new ProductInfoResponse(
                    productId,
                    "Unknown Product",
                    0.0, false,
                    "No product found with ID: " + productId,
                    "N/A"
            );
        }

        return product;
    }
}