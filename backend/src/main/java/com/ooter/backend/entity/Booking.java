package com.ooter.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import com.ooter.backend.entity.UploadedFile;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.springframework.cglib.core.Local;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hoarding_id")
    private Hoarding hoarding;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private User vendor;

    private LocalDate startDate;
    private LocalDate endDate;

    private double totalPrice;

    @Column(name = "paid_amount")
    private Double paidAmount;
     
    @Column(name = "total_amount")
    private Double totalAmount;
    
    @CreationTimestamp
    @Column(name = "created_at",nullable = false, updatable = false)
    private LocalDateTime createdAt;


    @Enumerated(EnumType.STRING)
    private BookingStatus status = BookingStatus.PENDING;

    private Double refundAmount;

    // ✅ NEW FIELDS BELOW:

    private boolean mediaDownloaded = false;
    private LocalDate mediaDownloadDate;

    private boolean printingStarted = false;
    private LocalDate printingStartDate;

    private boolean mountingStarted = false;
    private LocalDate mountingStartDate;

    private boolean siteLive = false;
    
    private LocalDate bookingDate;
    private Double printingCharges;
    private Double mountingCharges;
    private Double discount;
    private Double gst;


    private boolean paidToVendor = false;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL,orphanRemoval = true)
    private List<UploadedFile> uploadedFiles;

    private String transactionId;
    private LocalDate paymentDate;
    private double settlementAmount;

    private LocalDate siteLiveDate;

    @Column(name = "order_id",unique = true, nullable = false)
    private String orderId; // Used to track booking externally
}
