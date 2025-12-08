package com.nguyendat.shopee_be.services;

import com.nguyendat.shopee_be.auth.repositories.UserDetailRepository;
import com.nguyendat.shopee_be.config.NotificationSocketHandler;
import com.nguyendat.shopee_be.dto.OrderRequest;
import com.nguyendat.shopee_be.entities.*;
import com.nguyendat.shopee_be.exceptions.ResourceNotFoundEx;
import com.nguyendat.shopee_be.repositories.*;
import com.nguyendat.shopee_be.auth.entities.User;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class OrderServiceImpl implements OrderService {

    @Value("${VNPAY_URL:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String vnpUrl;

    @Value("${VNPAY_TMN_CODE:}")
    private String vnpTmnCode;

    @Value("${VNPAY_HASH_SECRET:}")
    private String vnpHashSecret;

    @Value("${VNPAY_RETURN_URL_BASE:http://localhost:5173}")
    private String vnpReturnUrlBase;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserDetailRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private NotificationSocketHandler notificationSocketHandler;

    @Autowired
    private DashboardService dashboardService; 

    @Autowired
    private ObjectMapper objectMapper;

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    @Override
    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    @Override
    public Order findById(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundEx("Order not found with id: " + id));
    }

    @Override
    public Order create(OrderRequest request) {
        log.info("🔥🔥🔥 ============ CREATE METHOD CALLED ============");
        log.info("🔥🔥🔥 Customer ID: " + request.getCustomerId());
        User customer = userRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundEx("User not found with id: " + request.getCustomerId()));

        Order order = new Order();
        order.setOrderNumber(request.getOrderNumber());
        order.setOrderDate(new Date());
        order.setTotalAmount(request.getTotalAmount());
        order.setStatus(OrderStatus.valueOf(request.getStatus()));
        order.setPaymentMethod(request.getPaymentMethod());
        order.setShippingAddress(request.getShippingAddress());
        order.setNotes(request.getNotes());
        order.setCustomer(customer);

        Order savedOrder = orderRepository.save(order);

    // Validate stock and prepare OrderItem entities
    List<OrderItem> orderItems = request.getOrderItems().stream().map(itemReq -> {
        OrderItem item = new OrderItem();
        item.setOrder(savedOrder);
        item.setQuantity(itemReq.getQuantity());
        item.setUnitPrice(itemReq.getUnitPrice());
        item.setTotalPrice(itemReq.getTotalPrice());

        Product product = productRepository.findById(itemReq.getProductId())
            .orElseThrow(() -> new ResourceNotFoundEx("Product not found with id: " + itemReq.getProductId()));

        ProductVariant variant = productVariantRepository.findById(itemReq.getProductVariantId())
            .orElseThrow(() -> new ResourceNotFoundEx("Product variant not found with id: " + itemReq.getProductVariantId()));

        // Check stock availability
        int requested = itemReq.getQuantity();
        Integer available = variant.getStockQuantity() != null ? variant.getStockQuantity() : 0;
        if (available < requested) {
        throw new IllegalArgumentException("Insufficient stock for variant " + variant.getId() + ": requested=" + requested + ", available=" + available);
        }

        // Deduct stock and persist the variant
        variant.setStockQuantity(available - requested);
        productVariantRepository.save(variant);

        item.setProduct(product);
        item.setProductVariant(variant);

        return orderItemRepository.save(item);
    }).collect(Collectors.toList());

        savedOrder.setOrderItems(orderItems);
        log.info("🔥 INFO: Order created with ID: " + savedOrder.getId());

        Order finalOrder = orderRepository.save(savedOrder);
        log.info("🔥 DEBUG: Order created with ID: " + finalOrder.getId());
        log.info("🔥 DEBUG: Total amount: " + finalOrder.getTotalAmount());

        // GỬI DASHBOARD EVENT: NEW_ORDER
        try {
            // Lấy tên khách hàng: firstName + lastName, hoặc email nếu null
            String customerName = buildCustomerName(customer);
            
            dashboardService.pushNewOrderEvent(
                finalOrder.getId().toString(),
                customerName,
                finalOrder.getTotalAmount().doubleValue()
            );
        } catch (Exception e) {
            System.err.println("❌ Failed to push dashboard NEW_ORDER event: " + e.getMessage());
        }

        return finalOrder;
    }

    @Override
    public void deleteById(UUID id) {
        orderRepository.deleteById(id);
    }

    @Override
    public Order update(UUID id, OrderRequest request) {
        throw new UnsupportedOperationException("Unimplemented method 'update'");
    }

    @Override
    public String createVnpayUrl(UUID orderId, BigDecimal amount) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundEx("Order not found"));

        String vnp_ReturnUrl = vnpReturnUrlBase + "/vnpay-done/" + orderId;

        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", "2.1.0");
        vnpParams.put("vnp_Command", "pay");
        vnpParams.put("vnp_TmnCode", vnpTmnCode);
        vnpParams.put("vnp_Amount", amount.multiply(BigDecimal.valueOf(100)).toBigInteger().toString());
        vnpParams.put("vnp_CreateDate", new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", orderId.toString());
        vnpParams.put("vnp_OrderInfo", "Thanh toan don hang " + orderId);
        vnpParams.put("vnp_OrderType", "other");
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", vnp_ReturnUrl);
        vnpParams.put("vnp_IpAddr", "127.0.0.1");

        try {
            String query = vnpParams.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

            Mac hmacSHA512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKeySpec = new SecretKeySpec(vnpHashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmacSHA512.init(secretKeySpec);
            byte[] hashBytes = hmacSHA512.doFinal(query.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexHash = new StringBuilder();
            for (byte b : hashBytes) {
                hexHash.append(String.format("%02x", b & 0xff));
            }
            String secureHash = hexHash.toString();

            return vnpUrl + "?" + query + "&vnp_SecureHash=" + secureHash;

        } catch (Exception e) {
            throw new RuntimeException("Error generating VNPAY secure hash", e);
        }
    }

    @Override
    public boolean processVnpayReturned(Map<String,String> params) {
        String orderId = params.get("vnp_TxnRef");
        String rspCode = params.get("vnp_ResponseCode");
        Optional<Order> orderOpt = orderRepository.findById(UUID.fromString(orderId));
        
        if(orderOpt.isPresent()) {
            Order order = orderOpt.get();
            PaymentStatus oldPaymentStatus = order.getPaymentStatus();
            
            if ("00".equals(rspCode)) {
                order.setPaymentStatus(PaymentStatus.PAID); 
            } else {
                order.setPaymentStatus(PaymentStatus.FAILED);
            }
            orderRepository.save(order);

            // GỬI DASHBOARD EVENT: PAYMENT STATUS CHANGED
            try {
                if ("00".equals(rspCode) && order.getStatus() == OrderStatus.PAID) {
                    // Nếu thanh toán thành công và đơn đã PAID → update revenue
                    dashboardService.pushRevenueUpdate();
                }
            } catch (Exception e) {
                System.err.println("❌ Failed to push dashboard PAYMENT event: " + e.getMessage());
            }

            return "00".equals(rspCode);
        }
        return false;
    }

    @Override
    public Order updateStatus(UUID orderId, OrderStatus newStatus, String changedBy) {
        // 1️⃣ Lấy đơn hàng
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundEx("Order not found"));

        OrderStatus oldStatus = order.getStatus();

        // 2️⃣ Kiểm tra chuyển trạng thái hợp lệ
        if (!isValidTransition(oldStatus, newStatus)) {
            throw new IllegalArgumentException(
                    "Không thể chuyển từ trạng thái " + oldStatus + " sang " + newStatus
            );
        }

        // 3️⃣ Nếu hủy hoặc hoàn trả → trả kho
        if (newStatus == OrderStatus.CANCELED || newStatus == OrderStatus.REFUND) {
            for (OrderItem item : order.getOrderItems()) {
                ProductVariant variant = item.getProductVariant();
                variant.setStockQuantity(variant.getStockQuantity() + item.getQuantity());
                productVariantRepository.save(variant);
            }
        }

        // 4️⃣ Lưu lịch sử trạng thái
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedBy(changedBy);
        statusHistoryRepository.save(history);

        // 5️⃣ Cập nhật trạng thái đơn hàng
        order.setStatus(newStatus);
        order.setUpdatedAt(new Date());
        Order savedOrder = orderRepository.save(order);

        // 6️⃣  Gửi thông báo NOTIFICATION qua NotificationSocketHandler
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "ORDER_STATUS");
            payload.put("orderId", order.getId().toString());
            payload.put("oldStatus", oldStatus.name());
            payload.put("newStatus", newStatus.name());
            payload.put("changedBy", changedBy);
            payload.put("timestamp", System.currentTimeMillis());

            notificationSocketHandler.broadcastNotification(payload);
        } catch (Exception e) {
            System.err.println("❌ Failed to broadcast WebSocket notification: " + e.getMessage());
        }

        // 7️⃣  GỬI DASHBOARD EVENT: ORDER_STATUS_CHANGED
        try {
            dashboardService.pushOrderStatusChanged(
                orderId.toString(),
                oldStatus.name(),
                newStatus.name()
            );

            // Nếu đơn chuyển sang PAID (hoàn thành) → update revenue
            if (newStatus == OrderStatus.PAID) {
                dashboardService.pushRevenueUpdate();
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to push dashboard ORDER_STATUS_CHANGED event: " + e.getMessage());
        }

        return savedOrder;
    }

    @Override
    public Order updatePaymentStatus(UUID orderId, PaymentStatus newStatus, String changedBy) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundEx("Order not found"));

        PaymentStatus oldPaymentStatus = order.getPaymentStatus();
        order.setPaymentStatus(newStatus);
        order.setUpdatedAt(new Date());
        Order saved = orderRepository.save(order);

        //  Gửi NOTIFICATION WebSocket
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "PAYMENT_STATUS");
            payload.put("orderId", order.getId().toString());
            payload.put("oldPaymentStatus", oldPaymentStatus != null ? oldPaymentStatus.name() : null);
            payload.put("newPaymentStatus", newStatus.name());
            payload.put("changedBy", changedBy);
            payload.put("timestamp", System.currentTimeMillis());

            notificationSocketHandler.broadcastNotification(payload);
        } catch (Exception e) {
            System.err.println("❌ Failed to broadcast payment status WebSocket notification: " + e.getMessage());
        }

        //  GỬI DASHBOARD EVENT nếu thanh toán thành công
        try {
            if (newStatus == PaymentStatus.PAID && order.getStatus() == OrderStatus.PAID) {
                // Chỉ update revenue khi cả payment status VÀ order status đều PAID
                dashboardService.pushRevenueUpdate();
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to push dashboard PAYMENT event: " + e.getMessage());
        }

        return saved;
    }

    @Override
    public List<Order> findByUser(com.nguyendat.shopee_be.auth.entities.User user) {
        return orderRepository.findByCustomer(user);
    }

    @Override
    public List<OrderItem> findUnreviewedOrderItemsByUser(com.nguyendat.shopee_be.auth.entities.User user) {
        if (user == null || user.getId() == null) return java.util.Collections.emptyList();
        return orderItemRepository.findUnreviewedByCustomerId(user.getId());
    }

    private boolean isValidTransition(OrderStatus current, OrderStatus next) {
        return switch (current) {
            case PENDING -> List.of(OrderStatus.SHIPPING, OrderStatus.CANCELED).contains(next);
            case SHIPPING -> List.of(OrderStatus.WAIT_DELIVER, OrderStatus.CANCELED).contains(next);
            case WAIT_DELIVER -> List.of(OrderStatus.PAID, OrderStatus.REFUND).contains(next);
            case PAID -> next == OrderStatus.REFUND;
            case CANCELED, REFUND -> false;
        };
    }

    private String buildCustomerName(User customer) {
        if (customer.getFirstName() != null && customer.getLastName() != null) {
            return customer.getFirstName() + " " + customer.getLastName();
        } else if (customer.getFirstName() != null) {
            return customer.getFirstName();
        } else if (customer.getLastName() != null) {
            return customer.getLastName();
        } else {
            return customer.getEmail();
        }
    }
}