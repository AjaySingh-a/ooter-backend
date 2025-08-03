package com.ooter.backend.dto;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CartItemResponse {
    private Long id;           // CartItem ID
    private Long hoardingId;   // Hoarding ID
    private String city;
    private String location;
    private String imageUrl;
    private double pricePerMonth;
    private double discount;
    private double printingCharges;
    private double mountingCharges;

    private Integer totalMonths;
    private Integer finalPrice;


    private String startDate; // Optional (for booking page if needed later)
    private String endDate;   // Optional
}
