package com.ooter.backend.dto;
import lombok.Data;
import java.time.LocalDate;

@Data
public class AddToCartRequest {
    private Long hoardingId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Double discount;
    private Double printingCharges;
    private Double mountingCharges;
}