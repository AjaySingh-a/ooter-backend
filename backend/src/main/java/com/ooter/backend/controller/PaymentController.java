package com.ooter.backend.controller;

import com.ooter.backend.dto.BookingOrderRequest;
import com.ooter.backend.entity.Booking;
import com.ooter.backend.entity.User;
import com.ooter.backend.exception.PaymentException;
import com.ooter.backend.service.BookingService;
import com.ooter.backend.service.CashfreeService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Objects;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final CashfreeService cashfreeService;
    private final BookingService bookingService;

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal User user) {

        try {
            // Validate user
            Objects.requireNonNull(user, "User authentication required");

            // Calculate total amount
            double totalAmount = calculateTotalAmount(request);
            
            // Cashfree minimum is ₹1
            if (totalAmount <= 0) {
                log.warn("Calculated amount is {} (zero or negative), setting to minimum ₹1", totalAmount);
                totalAmount = 1.0;
            }
            if (totalAmount < 1.0) {
                totalAmount = 1.0;
            }

            Map<String, String> orderTags = new HashMap<>();
            orderTags.put("hoardingId", request.getHoardingId().toString());
            orderTags.put("userId", user.getId().toString());

            // Return URL not required when using SDK promise; use placeholder for API requirement
            String returnUrl = "https://ooter.app/payment/return";

            CashfreeService.CreateOrderResult result = cashfreeService.createOrder(
                    totalAmount,
                    "INR",
                    "user_" + user.getId(),
                    user.getPhone() != null ? user.getPhone() : null,
                    user.getEmail() != null ? user.getEmail() : null,
                    returnUrl,
                    orderTags
            );

            log.info("Cashfree order created for user {}: {}", user.getId(), result.getOrderId());
            
            return ResponseEntity.ok(new CreateOrderResponse(
                    result.getOrderId(),
                    result.getPaymentSessionId(),
                    result.getOrderAmount(),
                    result.getOrderCurrency()
            ));

        } catch (IllegalArgumentException e) {
            log.warn("Validation error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (PaymentException e) {
            log.error("Payment processing error: {}", e.getMessage());
            return ResponseEntity.status(502).body(e.getMessage());
        } catch (Exception e) {
            log.error("Order creation failed", e);
            return ResponseEntity.internalServerError().body("Failed to create payment order");
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(
            @Valid @RequestBody PaymentVerificationRequest request,
            @AuthenticationPrincipal User user) {

        try {
            // Validate user
            Objects.requireNonNull(user, "User authentication required");

            // Verify payment with Cashfree (GET order status)
            String orderId = request.getOrderId();
            boolean isValid = cashfreeService.verifyPayment(orderId);

            if (!isValid) {
                log.warn("Cashfree order not PAID: {}", orderId);
                return ResponseEntity.badRequest().body("Payment not verified or order not completed");
            }

            // Convert and validate booking request
            BookingOrderRequest bookingRequest = convertToBookingRequest(request);
            validateBookingRequest(bookingRequest);

            String transactionId = request.getPaymentId() != null && !request.getPaymentId().isEmpty()
                    ? request.getPaymentId() : orderId;

            // Create confirmed booking
            Booking booking = bookingService.createConfirmedBookingAfterPayment(
                    bookingRequest,
                    transactionId,
                    orderId,
                    user
            );

            log.info("Payment verified and booking created: {}", booking.getId());
            
            return ResponseEntity.ok(new PaymentVerificationResponse(
                    booking.getId(),
                    "Payment verified and booking confirmed"
            ));

        } catch (IllegalArgumentException e) {
            log.warn("Validation error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Payment verification failed", e);
            return ResponseEntity.internalServerError().body("Payment verification failed");
        }
    }

    private double calculateTotalAmount(CreateOrderRequest request) {
        // Calculate subtotal (base price + printing + mounting)
        double subtotal = request.getTotalPrice() + 
                         request.getPrintingCharges() + 
                         request.getMountingCharges();
        
        // Add 15% commission (Ooter platform commission)
        double commission = subtotal * 0.15;
        double grossTotal = subtotal + commission;
        
        // Apply discount
        double afterDiscount = grossTotal - request.getDiscount();
        
        // Calculate GST (18%) on amount after discount
        double gst = afterDiscount * 0.18;
        
        // Final total
        return afterDiscount + gst;
    }

    private void validateBookingRequest(BookingOrderRequest request) {
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new IllegalArgumentException("Start and end dates are required");
        }
        if (request.getHoardingId() == null) {
            throw new IllegalArgumentException("Hoarding ID is required");
        }
    }

    private BookingOrderRequest convertToBookingRequest(PaymentVerificationRequest request) {
        // Recalculate GST to ensure consistency with create-order calculation
        double subtotal = request.getTotalPrice() + 
                         request.getPrintingCharges() + 
                         request.getMountingCharges();
        double commission = subtotal * 0.15;
        double grossTotal = subtotal + commission;
        double afterDiscount = grossTotal - request.getDiscount();
        double gst = afterDiscount * 0.18;
        
        BookingOrderRequest bookingRequest = new BookingOrderRequest();
        bookingRequest.setHoardingId(request.getHoardingId());
        bookingRequest.setStartDate(request.getStartDate());
        bookingRequest.setEndDate(request.getEndDate());
        bookingRequest.setTotalPrice((double) request.getTotalPrice());
        bookingRequest.setPrintingCharges((double) request.getPrintingCharges());
        bookingRequest.setMountingCharges((double) request.getMountingCharges());
        bookingRequest.setDiscount((double) request.getDiscount());
        bookingRequest.setGst(gst); // Use recalculated GST
        return bookingRequest;
    }

    // DTOs
    @Data
    public static class CreateOrderRequest {
        private Long hoardingId;
        private String startDate;
        private String endDate;
        private int totalPrice;
        private int printingCharges;
        private int mountingCharges;
        private int discount;
        private int gst;
        
        public double getTotalAmount() {
            return totalPrice + printingCharges + mountingCharges + gst - discount;
        }
    }

    @Data
    public static class PaymentVerificationRequest {
        private String orderId;
        private String paymentId;
        private Long hoardingId;
        private String startDate;
        private String endDate;
        private int totalPrice;
        private int printingCharges;
        private int mountingCharges;
        private int discount;
        private int gst;
    }

    @Data
    @RequiredArgsConstructor
    public static class CreateOrderResponse {
        private final String orderId;
        private final String paymentSessionId;
        private final double amount;
        private final String currency;
    }

    @Data
    @RequiredArgsConstructor
    public static class PaymentVerificationResponse {
        private final Long bookingId;
        private final String message;
    }
}