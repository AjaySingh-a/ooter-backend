package com.ooter.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.LastModifiedDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hoarding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String location;
    private String size;
    private String city;
    private String pinCode; // ➕ Add this field

    private double pricePerMonth;
    private String imageUrl;
    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private HoardingStatus status;

    
    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

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

    @Enumerated(EnumType.STRING)
    private HoardingCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "recentSearches", "password"})
    private User owner;
    private String name;
    private boolean isBooked;



    // 🔰 New fields matching frontend

    private String screenType;
    private String screenWidth;
    private String screenHeight;
    private String screenDepth;
    private String sizeUnit;

    private String sku;
    private String hshCode;
    private String gst;

    private boolean verifiedProperty;
    private boolean eyeCatching;
    private boolean mainHighway;
    private boolean currentlyAvailable;

    private LocalDate availableDate;
}