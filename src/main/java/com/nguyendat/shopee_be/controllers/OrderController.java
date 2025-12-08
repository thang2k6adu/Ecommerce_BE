package com.nguyendat.shopee_be.controllers;

import com.nguyendat.shopee_be.dto.OrderRequest;
import com.nguyendat.shopee_be.dto.OrderResponse;
import com.nguyendat.shopee_be.dto.OrderItemResponse;
import com.nguyendat.shopee_be.dto.UpdateStatusRequest;
import com.nguyendat.shopee_be.dto.UpdatePaymentStatusRequest;
import com.nguyendat.shopee_be.entities.Order;
import com.nguyendat.shopee_be.entities.OrderStatus;
import com.nguyendat.shopee_be.entities.PaymentStatus;
import com.nguyendat.shopee_be.services.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import com.nguyendat.shopee_be.auth.config.JWTTokenHelper;
import com.nguyendat.shopee_be.auth.repositories.UserDetailRepository;
import com.nguyendat.shopee_be.auth.entities.User;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.nguyendat.shopee_be.entities.OrderItem;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Manage customer orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private JWTTokenHelper jwtTokenHelper;

    @Autowired
    private UserDetailRepository userRepository;

    @GetMapping
    @Operation(summary = "Get all orders")
    public ResponseEntity<List<OrderResponse>> getAll() {
        List<OrderResponse> responses = orderService.findAll().stream().map(this::toOrderResponse).collect(Collectors.toList());
        return new ResponseEntity<>(responses, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by id")
    public ResponseEntity<OrderResponse> getById(@PathVariable UUID id) {
        Order order = orderService.findById(id);
        return new ResponseEntity<>(toOrderResponse(order), HttpStatus.OK);
    }

    @GetMapping("/me")
    @Operation(summary = "Get orders for current authenticated user (use access token)")
    public ResponseEntity<List<OrderResponse>> getCurrentUserOrders(HttpServletRequest request) {
        String token = jwtTokenHelper.getToken(request);
        if (token == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        String username = jwtTokenHelper.getUserNameFromToken(token);
        if (username == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        User user = userRepository.findByEmail(username);
        if (user == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

    List<Order> orders = orderService.findByUser(user);
    List<OrderResponse> responses = orders.stream().map(this::toOrderResponse).collect(Collectors.toList());
    return new ResponseEntity<>(responses, HttpStatus.OK);
    }

    @GetMapping("/unreviewed")
    @Operation(summary = "Get order items that haven't been reviewed by current user")
    public ResponseEntity<List<OrderItemResponse>> getUnreviewedOrderItems(HttpServletRequest request) {
        String token = jwtTokenHelper.getToken(request);
        if (token == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        String username = jwtTokenHelper.getUserNameFromToken(token);
        if (username == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        User user = userRepository.findByEmail(username);
        if (user == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        List<OrderItem> items = orderService.findUnreviewedOrderItemsByUser(user);
        List<OrderItemResponse> responses = items.stream().map(this::toOrderItemResponse).collect(Collectors.toList());
        return new ResponseEntity<>(responses, HttpStatus.OK);
    }

    // --- Mapping helpers -------------------------------------------------
    private OrderResponse toOrderResponse(Order order) {
        OrderResponse resp = OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .orderDate(order.getOrderDate())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus() != null ? order.getStatus().name() : null)
                .paymentMethod(order.getPaymentMethod())
                .shippingAddress(order.getShippingAddress())
                .notes(order.getNotes())
                .customerId(order.getCustomer() != null ? order.getCustomer().getId() : null)
                .orderItems(order.getOrderItems() != null ? order.getOrderItems().stream().map(this::toOrderItemResponse).collect(Collectors.toList()) : null)
                .customerName(getFullName(order.getCustomer()))
                .build();

        return resp;
    }

    private OrderItemResponse toOrderItemResponse(OrderItem item) {
        UUID productId = null;
        if (item.getProduct() != null) productId = item.getProduct().getId();
        UUID userId = null;
        if (item.getOrder() != null && item.getOrder().getCustomer() != null) {
            userId = item.getOrder().getCustomer().getId();
        }

        return OrderItemResponse.builder()
                .id(item.getId())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .totalPrice(item.getTotalPrice())
                .productId(productId)
                .productVariantId(item.getProductVariant() != null ? item.getProductVariant().getId() : null)
                .productName(item.getProduct() != null ? item.getProduct().getName() : null)
                .isReviewed(item.getIsReviewed() != null ? item.getIsReviewed() : false)
                .thumbnail(determineThumbnailUrl(item))
                .userId(userId)
                .build();
    }

    // choose product thumbnail: primary resource URL if present, otherwise first resource URL, otherwise null
    private String determineThumbnailUrl(OrderItem item) {
        if (item == null || item.getProduct() == null || item.getProduct().getResources() == null || item.getProduct().getResources().isEmpty()) {
            return null;
        }

        return item.getProduct().getResources().stream()
                .filter(r -> r.getIsPrimary() != null && r.getIsPrimary())
                .findFirst()
                .map(r -> r.getUrl())
                .orElse(item.getProduct().getResources().get(0).getUrl());
    }

    // Helper to build full name from User entity (handles nulls)
    private String getFullName(User user) {
        if (user == null) return null;
        String first = user.getFirstName() != null ? user.getFirstName() : "";
        String last = user.getLastName() != null ? user.getLastName() : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? null : full;
    }

    @PostMapping
    @Operation(summary = "Create order")
    public ResponseEntity<Order> create(@RequestBody OrderRequest request) {
        Order created = orderService.create(request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
}

    @PutMapping("/{id}")
    @Operation(summary = "Update order")
    public ResponseEntity<Order> update(@PathVariable UUID id, @RequestBody OrderRequest request) {
        Order updated = orderService.update(id, request);
        return new ResponseEntity<>(updated, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete order")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        orderService.deleteById(id);
        return ResponseEntity.ok().build();
    }
    
    @PatchMapping("/{id}/status")
    @Operation(summary = "Update order status")
    public ResponseEntity<Order> updateStatus(@PathVariable UUID id, @RequestBody UpdateStatusRequest request, @RequestParam String changedBy) {
        Order updated = orderService.updateStatus(id, OrderStatus.valueOf(request.getStatus()), changedBy);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/payment-status")
    @Operation(summary = "Update payment status")
    public ResponseEntity<Order> updatePaymentStatus(@PathVariable UUID id, @RequestBody UpdatePaymentStatusRequest request, @RequestParam String changedBy) {
        Order updated = orderService.updatePaymentStatus(id, PaymentStatus.valueOf(request.getStatus()), changedBy);
        return ResponseEntity.ok(updated);
    }


}
