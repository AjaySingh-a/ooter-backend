package com.ooter.backend.dto;

import com.ooter.backend.entity.Booking;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingProgressResponse {
    private String orderId;
    private String siteName;
    private String siteType;
    private String imageUrl;
    private String sku;
    private double latitude;
    private double longitude;

    private boolean mediaDownloaded;
    private boolean printingStarted;
    private boolean mountingStarted;
    private boolean siteLive;

    private LocalDate bookingDate;            // ✅ Site is booked
    private LocalDate mediaDownloadDate;      // ✅ Media downloaded
    private LocalDate printingStartDate;      // ✅ Printing started
    private LocalDate mountingStartDate;      // ✅ Mounting started
    private LocalDate siteLiveDate;           // ✅ Site is live

    private LocalDate bookedTill;

    private boolean paidToVendor;
    private double settlementAmount;
    private String transactionId;
    private LocalDate paymentDate;

    private String bookingProgressStatus;

    public static BookingProgressResponse from(Booking booking) {
        String progressStatus;

        if (!booking.isMediaDownloaded()) {
            progressStatus = "Pending for Media";
        } else if (!booking.isPrintingStarted()) {
            progressStatus = "Pending for Printing";
        } else if (!booking.isMountingStarted()) {
            progressStatus = "Pending for Mounting";
        } else if (!booking.isSiteLive()) {
            progressStatus = "Pending to Go Live";
        } else {
            progressStatus = "Live";
        }

        return BookingProgressResponse.builder()
                .orderId(booking.getOrderId())
                .siteName(booking.getHoarding().getName())
                .siteType(booking.getHoarding().getSiteType())
                .imageUrl(booking.getHoarding().getImageUrl())
                .sku(booking.getHoarding().getSku())
                .latitude(booking.getHoarding().getLatitude())
                .longitude(booking.getHoarding().getLongitude())

                .mediaDownloaded(booking.isMediaDownloaded())
                .printingStarted(booking.isPrintingStarted())
                .mountingStarted(booking.isMountingStarted())
                .siteLive(booking.isSiteLive())

                .bookingDate(booking.getBookingDate())
                .mediaDownloadDate(booking.getMediaDownloadDate())
                .printingStartDate(booking.getPrintingStartDate())
                .mountingStartDate(booking.getMountingStartDate())
                .siteLiveDate(booking.getSiteLiveDate())

                .bookedTill(booking.getEndDate())

                .paidToVendor(booking.isPaidToVendor())
                .settlementAmount(booking.getSettlementAmount())
                .transactionId(booking.getTransactionId())
                .paymentDate(booking.getPaymentDate())

                .bookingProgressStatus(progressStatus)
                .build();
    }
}
