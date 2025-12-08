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
    private String productName;
    private Boolean isReviewed;
    private String thumbnail;
    private java.util.UUID userId;
}
    