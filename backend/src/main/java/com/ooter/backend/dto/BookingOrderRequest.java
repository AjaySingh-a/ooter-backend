package com.ooter.backend.dto;

import lombok.Data;

@Data
public class BookingOrderRequest {
    private Long hoardingId;
    private String startDate;
    private String endDate;
    private Double totalPrice;
    private Double printingCharges;
    private Double mountingCharges;
    private Double discount;
    private Double gst;
}
