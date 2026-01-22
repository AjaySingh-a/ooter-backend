package com.ooter.backend.dto;

import com.ooter.backend.entity.Booking;
import com.ooter.backend.entity.Hoarding;
import com.ooter.backend.entity.User;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BookingDetailResponse {
    private String orderId;
    private String secondaryId;
    private String siteName;
    private String siteType;
    private String sku;
    private String imageUrl;
    private double pricePerMonth;
    private double totalPrice;
    private String location;
    private String size;
    private String latitude;
    private String longitude;
    private String bookedFrom;
    private String bookedTill;
    private String buyerName;
    private String buyerPhone;

    private String transactionId;
    private double amountPaid;
    private String paymentDate;

    private boolean mediaDownloaded;
    private boolean printingStarted;
    private boolean mountingStarted;
    private boolean siteLive;

    private String bookingDate;
    private String mediaDownloadDate;
    private String printingStartDate;
    private String mountingStartDate;
    private String siteLiveDate;

    private int uploadedPhotos;

    private String bookingStatus;

    public static BookingDetailResponse from(Booking b) {
        Hoarding h = b.getHoarding();
        User u = b.getUser();

        return BookingDetailResponse.builder()
                .orderId(b.getOrderId())
                .secondaryId("ODOP" + (b.getId() + 100))
                .siteName(h.getName())
                .siteType(h.getSiteType())
                .sku(h.getSku())
                .imageUrl(h.getImageUrl())
                .pricePerMonth(h.getPricePerMonth())
                .totalPrice(b.getTotalPrice())
                .location(h.getLocation())
                .size(h.getSize())
                .latitude(String.valueOf(h.getLatitude()))
                .longitude(String.valueOf(h.getLongitude()))
                .bookedFrom(String.valueOf(b.getStartDate()))
                .bookedTill(String.valueOf(b.getEndDate()))
                .buyerName(u.getName())
                .buyerPhone(u.getPhone())
                .transactionId(b.getTransactionId())
                .amountPaid(b.getPaidAmount() != null ? b.getPaidAmount() : 0.0)
                .paymentDate(b.getPaymentDate() != null ? b.getPaymentDate().toString() : null)
                .bookingDate(b.getBookingDate() != null ? b.getBookingDate().toString() : null)
                .mediaDownloaded(b.isMediaDownloaded())
                .printingStarted(b.isPrintingStarted())
                .mountingStarted(b.isMountingStarted())
                .siteLive(b.isSiteLive())
                .mediaDownloadDate(b.getMediaDownloadDate() != null ? b.getMediaDownloadDate().toString() : null)
                .printingStartDate(b.getPrintingStartDate() != null ? b.getPrintingStartDate().toString() : null)
                .mountingStartDate(b.getMountingStartDate() != null ? b.getMountingStartDate().toString() : null)
                .siteLiveDate(b.getSiteLiveDate() != null ? b.getSiteLiveDate().toString() : null)
                .uploadedPhotos(b.getUploadedFiles() != null ? b.getUploadedFiles().size() : 0)
                .bookingStatus(b.getStatus().name())
                .build();
    }
}
