package com.ooter.backend.dto;

import com.ooter.backend.entity.Booking;
import lombok.Builder;
import lombok.Data;

import java.time.format.DateTimeFormatter;

@Data
@Builder
public class BookingResponse {
    private Long id;
    private String city;
    private String name;
    private String location;
    private String imageUrl;
    private String startDate;
    private String endDate;
    private String status;
    private Double totalPrice;
    private Double refundAmount;

    public static BookingResponse from(Booking booking) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM yyyy");

        return BookingResponse.builder()
                .id(booking.getId())
                .city(booking.getHoarding().getCity())
                .name(booking.getHoarding().getName())
                .location(booking.getHoarding().getLocation())
                .imageUrl(booking.getHoarding().getImageUrl())
                .startDate(booking.getStartDate().format(fmt))
                .endDate(booking.getEndDate().format(fmt))
                .status(booking.getStatus().name())
                .totalPrice(booking.getTotalPrice())
                .refundAmount(booking.getRefundAmount()) // nullable
                .build();
    }
}
