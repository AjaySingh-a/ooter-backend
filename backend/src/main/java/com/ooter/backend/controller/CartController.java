package com.ooter.backend.controller;

import com.ooter.backend.dto.CartItemResponse;
import com.ooter.backend.dto.AddToCartRequest;
import com.ooter.backend.entity.CartItem;
import com.ooter.backend.entity.Hoarding;
import com.ooter.backend.entity.User;
import com.ooter.backend.repository.CartRepository;
import com.ooter.backend.repository.HoardingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartRepository cartRepository;
    private final HoardingRepository hoardingRepository;

    private int calculateFullMonths(LocalDate startDate, LocalDate endDate) {
        int months = (endDate.getYear() - startDate.getYear()) * 12;
        months += endDate.getMonthValue() - startDate.getMonthValue();
        
        if (endDate.getDayOfMonth() >= startDate.getDayOfMonth()) {
            months += 1;
        }
        
        return Math.max(1, months);
    }

    @PostMapping("/add")
    public ResponseEntity<?> addToCart(@RequestBody AddToCartRequest request,
                                     @AuthenticationPrincipal User user) {
        try {
            Hoarding hoarding = hoardingRepository.findById(request.getHoardingId())
                .orElseThrow(() -> new RuntimeException("Hoarding not found"));

            if (cartRepository.existsByUserAndHoarding(user, hoarding)) {
                return ResponseEntity.badRequest().body("Already added to cart");
            }

            LocalDate startDate = request.getStartDate();
            LocalDate endDate = request.getEndDate();
            int months = calculateFullMonths(startDate, endDate);

            double discount = request.getDiscount() != null ? request.getDiscount() : 0;
            double printingCharges = request.getPrintingCharges() != null ? request.getPrintingCharges() : 0;
            double mountingCharges = request.getMountingCharges() != null ? request.getMountingCharges() : 0;
            
            double basePrice = hoarding.getPricePerMonth() * months;
            double subtotal = basePrice + printingCharges + mountingCharges;
            double commission = subtotal * 0.15;
            double grossTotal = subtotal + commission;
            double afterDiscount = grossTotal - discount;
            double gst = afterDiscount * 0.18;
            int finalPrice = (int) Math.round(afterDiscount + gst);

            CartItem cartItem = CartItem.builder()
                .user(user)
                .hoarding(hoarding)
                .addedAt(LocalDateTime.now())
                .startDate(startDate)
                .endDate(endDate)
                .totalMonths(months)
                .finalPrice(finalPrice)
                .discount(discount)
                .printingCharges(printingCharges)
                .mountingCharges(mountingCharges)
                .build();
            
            cartRepository.save(cartItem);
            return ResponseEntity.ok("Added to cart");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                Map.of(
                    "status", "error",
                    "message", "Error adding to cart",
                    "error", e.getMessage()
                )
            );
        }
    }

    @GetMapping
    public ResponseEntity<?> viewCart(
            @AuthenticationPrincipal User user,
            @RequestHeader(value = "If-Modified-Since", required = false) String ifModifiedSince) {
        
        if (user == null) {
            return ResponseEntity.status(401).body(
                Map.of(
                    "status", "error",
                    "message", "Unauthorized"
                )
            );
        }

        try {
            Instant lastUpdate = cartRepository.findMaxUpdatedAtByUser(user.getId());
            if (lastUpdate == null) {
                lastUpdate = Instant.now();
            }

            if (ifModifiedSince != null) {
                try {
                    Instant ifModifiedSinceInstant = Instant.parse(ifModifiedSince);
                    if (!lastUpdate.isAfter(ifModifiedSinceInstant)) {
                        return ResponseEntity.status(304).build();
                    }
                } catch (Exception e) {
                    log.warn("Invalid If-Modified-Since header", e);
                }
            }

            List<CartItemResponse> response = cartRepository.findByUser(user)
                .stream()
                .map(item -> {
                    Hoarding hoarding = item.getHoarding();
                    return CartItemResponse.builder()
                        .id(item.getId())
                        .hoardingId(hoarding.getId())
                        .city(hoarding.getCity())
                        .location(hoarding.getLocation())
                        .imageUrl(hoarding.getImageUrl())
                        .pricePerMonth(hoarding.getPricePerMonth())
                        .discount(item.getDiscount())
                        .printingCharges(item.getPrintingCharges())
                        .mountingCharges(item.getMountingCharges())
                        .startDate(item.getStartDate().toString())
                        .endDate(item.getEndDate().toString())
                        .totalMonths(item.getTotalMonths())
                        .finalPrice(item.getFinalPrice())
                        .build();
                })
                .collect(Collectors.toList());

            ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES));
            
            builder.lastModified(lastUpdate);

            return builder.body(response);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                Map.of(
                    "status", "error",
                    "message", "Failed to load cart",
                    "error", e.getMessage()
                )
            );
        }
    }

    @DeleteMapping("/remove/{hoardingId}")
    public ResponseEntity<?> removeFromCart(@PathVariable Long hoardingId,
                                          @AuthenticationPrincipal User user) {
        try {
            Hoarding hoarding = hoardingRepository.findById(hoardingId)
                .orElseThrow(() -> new RuntimeException("Hoarding not found"));
            
            Optional<CartItem> cartItem = cartRepository.findByUserAndHoarding(user, hoarding);
            if (cartItem.isPresent()) {
                cartRepository.delete(cartItem.get());
                return ResponseEntity.ok(
                    Map.of(
                        "status", "success",
                        "message", "Removed from cart"
                    )
                );
            }
            return ResponseEntity.status(404).body(
                Map.of(
                    "status", "error",
                    "message", "Item not found in cart"
                )
            );
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                Map.of(
                    "status", "error",
                    "message", "Error removing item",
                    "error", e.getMessage()
                )
            );
        }
    }

    private Instant parseHttpDate(String httpDate) {
        try {
            return Instant.parse(httpDate);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}