package com.ooter.backend.dto;

import com.ooter.backend.entity.Hoarding;
import com.ooter.backend.entity.HoardingStatus;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ActiveListingResponse {
    private Long id;
    private String name;
    private String imageUrl;
    private String city;
    private String state;
    private String district;
    private double pricePerMonth;
    private HoardingStatus status;
    private String sku;
    private LocalDate bookedTill;

    public static ActiveListingResponse from(Hoarding hoarding, LocalDate bookedTill) {
        return ActiveListingResponse.builder()
                .id(hoarding.getId())
                .name(hoarding.getName())
                .imageUrl(hoarding.getImageUrl())
                .city(hoarding.getCity())
                .state(hoarding.getState())
                .district(hoarding.getDistrict())
                .pricePerMonth(hoarding.getPricePerMonth())
                .status(hoarding.getStatus())
                .sku(hoarding.getSku())
                .bookedTill(bookedTill)
                .build();
    }
}
