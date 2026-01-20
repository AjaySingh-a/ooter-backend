package com.ooter.backend.controller;

import com.ooter.backend.dto.BookingOrderRequest;
import com.ooter.backend.entity.Booking;
import com.ooter.backend.entity.User;
import com.ooter.backend.exception.PaymentException;
import com.ooter.backend.service.BookingService;
import com.ooter.backend.service.RazorpayService;
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

    private final RazorpayService razorpayService;
    private final BookingService bookingService;

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal User user) {

        try {
            // Validate user
            Objects.requireNonNull(user, "User authentication required");

            // Validate amount
            if (request.getTotalAmount() <= 0) {
                throw new IllegalArgumentException("Amount must be greater than zero");
            }

            // Calculate and validate total amount
            double totalAmount = calculateTotalAmount(request);
            if (totalAmount < 1.0) { // Minimum ₹1
                throw new IllegalArgumentException("Total amount must be at least ₹1");
            }

            int amountInPaise = (int) (totalAmount * 100);

            // Create Razorpay order with additional metadata
            Map<String, String> notes = new HashMap<>();
            notes.put("hoardingId", request.getHoardingId().toString());
            notes.put("userId", user.getId().toString());

            String orderId = razorpayService.createOrder(
                    amountInPaise,
                    "INR",
                    "booking_user_" + user.getId(),
                    notes
            );

            log.info("Order created successfully for user {}: {}", user.getId(), orderId);
            
            return ResponseEntity.ok(new CreateOrderResponse(
                    orderId,
                    amountInPaise,
                    "INR",
                    razorpayService.getKeyId()
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

            // Verify payment signature
            boolean isValid = razorpayService.verifyPayment(
                    request.getRazorpayOrderId(),
                    request.getRazorpayPaymentId(),
                    request.getRazorpaySignature()
            );

            if (!isValid) {
                log.warn("Invalid payment signature for order {}", request.getRazorpayOrderId());
                return ResponseEntity.badRequest().body("Invalid payment signature");
            }

            // Convert and validate booking request
            BookingOrderRequest bookingRequest = convertToBookingRequest(request);
            validateBookingRequest(bookingRequest);

            // Create confirmed booking
            Booking booking = bookingService.createConfirmedBookingAfterPayment(
                    bookingRequest,
                    request.getRazorpayPaymentId(),
                    request.getRazorpayOrderId(),
                    request.getRazorpaySignature(),
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
        private String razorpayOrderId;
        private String razorpayPaymentId;
        private String razorpaySignature;
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
        private final int amount;
        private final String currency;
        private final String keyId;
    }

    @Data
    @RequiredArgsConstructor
    public static class PaymentVerificationResponse {
        private final Long bookingId;
        private final String message;
    }
}