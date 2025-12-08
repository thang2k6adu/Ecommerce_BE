package com.nguyendat.shopee_be.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemResponse {
    private UUID id;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private UUID productId;
    private UUID productVariantId;
    // Name of the product for this order item (convenience for clients)
    private String productName;
    // Indicate whether the order item has been reviewed by the customer
    private Boolean isReviewed;
    // Product thumbnail URL (primary image) to show product image in unreviewed items
    private String thumbnail;
}
