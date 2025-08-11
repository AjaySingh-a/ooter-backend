package com.ooter.backend.controller;

import com.ooter.backend.entity.*;
import com.ooter.backend.repository.HoardingRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/hoardings")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class HoardingController {

    private final HoardingRepository hoardingRepository;
    private static final DateTimeFormatter HTTP_HEADER_DATE_FORMAT = DateTimeFormatter.RFC_1123_DATE_TIME;

    private Instant parseHttpDate(String httpDate) {
        if (httpDate == null) return null;
        try {
            return ZonedDateTime.parse(httpDate, HTTP_HEADER_DATE_FORMAT).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @PostMapping
    public ResponseEntity<?> createHoarding(@RequestBody Hoarding hoarding, @AuthenticationPrincipal User vendor) {
        if (vendor == null || vendor.getRole() != Role.VENDOR) {
            return ResponseEntity.status(403).body("Only vendors can create hoardings");
        }

        hoarding.setOwner(vendor);

        if (hoarding.getCategory() == null) {
            hoarding.setCategory(HoardingCategory.RECOMMENDED);
        }

        if (hoarding.getStatus() == null) {
            hoarding.setStatus(HoardingStatus.ACTIVE);
        }

        hoarding.setBooked(hoarding.getStatus() == HoardingStatus.BOOKED);
        hoarding.setPinCode(hoarding.getPinCode());

        Hoarding saved = hoardingRepository.save(hoarding);
        return ResponseEntity.ok(new HoardingResponse(saved));
    }

    @GetMapping
    public ResponseEntity<List<HoardingResponse>> getAllHoardings(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String city,
            @RequestHeader(value = "If-Modified-Since", required = false) String ifModifiedSince) {
        
        Instant lastUpdate = Optional.ofNullable(hoardingRepository.findMaxUpdatedAt())
                                   .orElse(Instant.now());
        
        if (ifModifiedSince != null) {
            Instant modifiedSince = parseHttpDate(ifModifiedSince);
            if (modifiedSince != null && !lastUpdate.isAfter(modifiedSince)) {
                return ResponseEntity.status(304).build();
            }
        }

        List<Hoarding> hoardings;

        if (category != null && city != null) {
            try {
                HoardingCategory cat = HoardingCategory.valueOf(category.toUpperCase());
                hoardings = hoardingRepository.findByCategoryAndCityIgnoreCase(cat, city);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        } else if (category != null) {
            try {
                HoardingCategory cat = HoardingCategory.valueOf(category.toUpperCase());
                hoardings = hoardingRepository.findByCategory(cat);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        } else if (city != null) {
            hoardings = hoardingRepository.findByCityIgnoreCase(city);
        } else {
            hoardings = hoardingRepository.findAll();
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .lastModified(lastUpdate)
                .body(hoardings.stream().map(HoardingResponse::new).toList());
    }

    @GetMapping("/search")
    public ResponseEntity<List<HoardingResponse>> searchByKeyword(
            @RequestParam String location,
            @RequestHeader(value = "If-Modified-Since", required = false) String ifModifiedSince) {
        
        Instant lastUpdate = Optional.ofNullable(hoardingRepository.findMaxUpdatedAt())
                                   .orElse(Instant.now());
        
        if (ifModifiedSince != null) {
            Instant modifiedSince = parseHttpDate(ifModifiedSince);
            if (modifiedSince != null && !lastUpdate.isAfter(modifiedSince)) {
                return ResponseEntity.status(304).build();
            }
        }

        List<Hoarding> results = hoardingRepository
                .findByLocationContainingIgnoreCaseOrCityContainingIgnoreCaseOrStateContainingIgnoreCase(
                        location, location, location);
        
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .lastModified(lastUpdate)
                .body(results.stream().map(HoardingResponse::new).toList());
    }

    @GetMapping("/nearby")
    public ResponseEntity<List<HoardingResponse>> getNearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "20") double radius,
            @RequestHeader(value = "If-Modified-Since", required = false) String ifModifiedSince) {
        
        Instant lastUpdate = Optional.ofNullable(hoardingRepository.findMaxUpdatedAt())
                                   .orElse(Instant.now());
        
        if (ifModifiedSince != null) {
            Instant modifiedSince = parseHttpDate(ifModifiedSince);
            if (modifiedSince != null && !lastUpdate.isAfter(modifiedSince)) {
                return ResponseEntity.status(304).build();
            }
        }

        List<Hoarding> results = hoardingRepository.findNearbyHoardings(lat, lng, radius);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.MINUTES))
                .lastModified(lastUpdate)
                .body(results.stream().map(HoardingResponse::new).toList());
    }

    @GetMapping("/vendor/{ownerId}")
    public ResponseEntity<List<HoardingResponse>> getVendorHoardings(
            @PathVariable Long ownerId,
            @RequestHeader(value = "If-Modified-Since", required = false) String ifModifiedSince) {
        
        Instant lastUpdate = Optional.ofNullable(hoardingRepository.findMaxUpdatedByOwner(ownerId))
                                   .orElse(Instant.now());
        
        if (ifModifiedSince != null) {
            Instant modifiedSince = parseHttpDate(ifModifiedSince);
            if (modifiedSince != null && !lastUpdate.isAfter(modifiedSince)) {
                return ResponseEntity.status(304).build();
            }
        }

        List<Hoarding> hoardings = hoardingRepository.findByOwnerId(ownerId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .lastModified(lastUpdate)
                .body(hoardings.stream().map(HoardingResponse::new).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<HoardingResponse> getById(
            @PathVariable Long id,
            @RequestHeader(value = "If-Modified-Since", required = false) String ifModifiedSince) {
        
        return hoardingRepository.findById(id)
                .map(hoarding -> {
                    Instant lastUpdate = Optional.ofNullable(hoarding.getUpdatedAt())
                                               .orElse(Instant.now());
                    
                    if (ifModifiedSince != null) {
                        Instant modifiedSince = parseHttpDate(ifModifiedSince);
                        if (modifiedSince != null && !lastUpdate.isAfter(modifiedSince)) {
                            return ResponseEntity.status(304).<HoardingResponse>build();
                        }
                    }
                    
                    return ResponseEntity.ok()
                            .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                            .lastModified(lastUpdate)
                            .body(new HoardingResponse(hoarding));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateHoarding(@PathVariable Long id, @RequestBody Hoarding updated, @AuthenticationPrincipal User vendor) {
        if (vendor == null || vendor.getRole() != Role.VENDOR) {
            return ResponseEntity.status(403).body("Only vendors can update hoardings");
        }

        return hoardingRepository.findById(id).map(existing -> {
            if (!existing.getOwner().getId().equals(vendor.getId())) {
                return ResponseEntity.status(403).body("You can only update your own listings");
            }

            existing.setName(updated.getName());
            existing.setLocation(updated.getLocation());
            existing.setCity(updated.getCity());
            existing.setState(updated.getState());
            existing.setDistrict(updated.getDistrict());
            existing.setLandmark(updated.getLandmark());
            existing.setCountry(updated.getCountry());
            existing.setPinCode(updated.getPinCode());

            existing.setLatitude(updated.getLatitude());
            existing.setLongitude(updated.getLongitude());

            existing.setSize(updated.getSize());
            existing.setSizeUnit(updated.getSizeUnit());
            existing.setMaterial(updated.getMaterial());
            existing.setSiteType(updated.getSiteType());
            existing.setScreenType(updated.getScreenType());
            existing.setScreenWidth(updated.getScreenWidth());
            existing.setScreenHeight(updated.getScreenHeight());
            existing.setScreenDepth(updated.getScreenDepth());

            existing.setPricePerMonth(updated.getPricePerMonth());
            existing.setDiscount(updated.getDiscount());
            existing.setPrintingCharges(updated.getPrintingCharges());
            existing.setMountingCharges(updated.getMountingCharges());
            existing.setGst(updated.getGst());

            existing.setDescription(updated.getDescription());
            existing.setImageUrl(updated.getImageUrl());
            existing.setSku(updated.getSku());
            existing.setHshCode(updated.getHshCode());

            existing.setEyeCatching(updated.isEyeCatching());
            existing.setMainHighway(updated.isMainHighway());
            existing.setVerifiedProperty(updated.isVerifiedProperty());

            existing.setStatus(updated.getStatus());
            existing.setBooked(updated.getStatus() == HoardingStatus.BOOKED);

            if (updated.getStatus() == HoardingStatus.BOOKED) {
                if (updated.getAvailableDate() == null) {
                    return ResponseEntity.badRequest().body("Available date is required when marking status as BOOKED");
                }
                existing.setAvailableDate(updated.getAvailableDate());
            } else {
                existing.setAvailableDate(null);
            }

            Hoarding saved = hoardingRepository.save(existing);
            return ResponseEntity.ok(new HoardingResponse(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @Data
    @AllArgsConstructor
    static class HoardingResponse {
        private Long id;
        private String name;
        private String location;
        private String city;
        private String size;
        private String imageUrl;
        private double price;
        private String description;
        private String category;

        private String material;
        private String siteType;
        private String country;
        private String state;
        private String district;
        private String landmark;
        private Double latitude;
        private Double longitude;

        private Double discount;
        private Integer printingCharges;
        private Integer mountingCharges;

        private String screenType;
        private String screenWidth;
        private String screenHeight;
        private String screenDepth;
        private String sizeUnit;

        private String sku;
        private String hshCode;
        private String gst;
        private String pinCode;

        private boolean verifiedProperty;
        private boolean eyeCatching;
        private boolean mainHighway;
        private boolean currentlyAvailable;

        private String status;
        private String availableDate;

        public HoardingResponse(Hoarding h) {
            this.id = h.getId();
            this.name = h.getName();
            this.location = h.getLocation();
            this.city = h.getCity();
            this.size = h.getSize();
            this.imageUrl = h.getImageUrl();
            this.price = h.getPricePerMonth();
            this.description = h.getDescription();
            this.category = h.getCategory() != null ? h.getCategory().name() : null;

            this.material = h.getMaterial();
            this.siteType = h.getSiteType();
            this.country = h.getCountry();
            this.state = h.getState();
            this.district = h.getDistrict();
            this.landmark = h.getLandmark();
            this.latitude = h.getLatitude();
            this.longitude = h.getLongitude();

            this.discount = h.getDiscount();
            this.printingCharges = h.getPrintingCharges();
            this.mountingCharges = h.getMountingCharges();

            this.screenType = h.getScreenType();
            this.screenWidth = h.getScreenWidth();
            this.screenHeight = h.getScreenHeight();
            this.screenDepth = h.getScreenDepth();
            this.sizeUnit = h.getSizeUnit();

            this.sku = h.getSku();
            this.hshCode = h.getHshCode();
            this.gst = h.getGst();
            this.pinCode = h.getPinCode();

            this.verifiedProperty = h.isVerifiedProperty();
            this.eyeCatching = h.isEyeCatching();
            this.mainHighway = h.isMainHighway();
            this.currentlyAvailable = !h.isBooked();

            this.status = h.getStatus() != null ? h.getStatus().name() : null;
            this.availableDate = h.getAvailableDate() != null ? h.getAvailableDate().toString() : null;
        }
    }
}