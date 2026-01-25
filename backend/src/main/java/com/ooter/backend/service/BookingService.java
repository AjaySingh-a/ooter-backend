package com.ooter.backend.service;

import com.ooter.backend.dto.BookingOrderRequest;
import com.ooter.backend.dto.UploadedFileRequest;
import com.ooter.backend.entity.*;
import com.ooter.backend.exception.BookingException;
import com.ooter.backend.exception.NotFoundException;
import com.ooter.backend.repository.BookingRepository;
import com.ooter.backend.repository.HoardingRepository;
import com.ooter.backend.repository.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.cache.annotation.CacheEvict;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final HoardingRepository hoardingRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final FileStorageService fileStorageService;
    private final RazorpayService razorpayService;

    private static final int MAX_UPLOAD_FILES = 3;

    @Transactional
    public Booking createConfirmedBookingAfterPayment(BookingOrderRequest request, String transactionId, String razorpayOrderId, String razorpaySignature, User user) {
        try {
            // ✅ Razorpay signature verification
            boolean isValid = razorpayService.verifyPayment(razorpayOrderId, transactionId, razorpaySignature);
            if (!isValid) {
                log.warn("Invalid payment signature for orderId={}, paymentId={}", razorpayOrderId, transactionId);
                throw new BookingException("Payment verification failed. Booking not created.");
            }

            Hoarding hoarding = hoardingRepository.findById(request.getHoardingId())
                    .orElseThrow(() -> new NotFoundException("Hoarding not found with ID: " + request.getHoardingId()));

            Booking booking = new Booking();
            booking.setUser(user);
            booking.setVendor(hoarding.getOwner());
            booking.setHoarding(hoarding);
            booking.setStartDate(LocalDate.parse(request.getStartDate()));
            booking.setEndDate(LocalDate.parse(request.getEndDate()));
            booking.setTotalPrice(request.getTotalPrice());
            booking.setPrintingCharges(request.getPrintingCharges());
            booking.setMountingCharges(request.getMountingCharges());
            booking.setDiscount(request.getDiscount());
            booking.setGst(request.getGst());
            booking.setOrderId(razorpayOrderId);
            booking.setTransactionId(transactionId);
            booking.setBookingDate(LocalDate.now());
            booking.setPaymentDate(LocalDate.now());
            // Calculate total paid amount: totalPrice + printingCharges + mountingCharges + gst - discount
            double totalPaidAmount = request.getTotalPrice() + 
                                    request.getPrintingCharges() + 
                                    request.getMountingCharges() + 
                                    request.getGst() - 
                                    request.getDiscount();
            booking.setPaidAmount(totalPaidAmount);
            // Calculate settlement amount for vendor: base amount (totalPrice + printingCharges + mountingCharges)
            // This is what the vendor receives (before commission, GST, and discount)
            double settlementAmount = request.getTotalPrice() + 
                                     request.getPrintingCharges() + 
                                     request.getMountingCharges();
            booking.setSettlementAmount(settlementAmount);
            booking.setStatus(BookingStatus.CONFIRMED);

            Booking saved = bookingRepository.save(booking);
            updateHoardingStatus(hoarding, HoardingStatus.BOOKED);

            log.info("✅ Booking confirmed after verified payment. Booking ID: {}, Order ID: {}", saved.getId(), razorpayOrderId);
            return saved;

        } catch (Exception e) {
            log.error("❌ Error while confirming booking after payment", e);
            throw new BookingException("Failed to confirm booking after payment");
        }
    }


    @Transactional
    public Booking createBooking(Booking booking) {
        try {
            Booking saved = bookingRepository.save(booking);

            if (booking.getStatus() == BookingStatus.CONFIRMED) {
                updateHoardingStatus(saved.getHoarding(), HoardingStatus.BOOKED);
            }

            log.info("Created booking with ID: {}", saved.getId());
            return saved;
        } catch (Exception e) {
            log.error("Failed to create booking", e);
            throw new BookingException("Failed to create booking");
        }
    }

    @Transactional
    public void updateBookingOrderId(Long bookingId, String orderId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found with ID: " + bookingId));

        booking.setOrderId(orderId);
        bookingRepository.save(booking);
        log.info("Updated order ID {} for booking {}", orderId, bookingId);
    }

    @Transactional
    public Booking confirmBookingPayment(Long bookingId, String paymentId) {
        try {
            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new NotFoundException("Booking not found with ID: " + bookingId));

            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setTransactionId(paymentId);

            updateHoardingStatus(booking.getHoarding(), HoardingStatus.BOOKED);

            Booking confirmedBooking = bookingRepository.save(booking);
            log.info("Confirmed booking {} with payment ID {}", bookingId, paymentId);
            return confirmedBooking;
        } catch (Exception e) {
            log.error("Failed to confirm booking payment for booking ID: {}", bookingId, e);
            throw new BookingException("Failed to confirm booking payment");
        }
    }

    @CacheEvict(value = {"vendorListingStats", "vendorDashboard", "vendorListings"}, allEntries = true)
    private void updateHoardingStatus(Hoarding hoarding, HoardingStatus status) {
        if (hoarding.getStatus() == HoardingStatus.ACTIVE || hoarding.getStatus() == HoardingStatus.AVAILABLE) {
            hoarding.setStatus(status);
            hoardingRepository.save(hoarding);
            log.info("Updated hoarding {} status to {}", hoarding.getId(), status);
        }
    }

    public List<Booking> getBookingsByUserId(Long userId) {
        return bookingRepository.findByUserId(userId);
    }

    public List<Booking> getBookingsByHoardingId(Long hoardingId) {
        return bookingRepository.findByHoardingId(hoardingId);
    }

    public List<Booking> getBookingsByStatusList(Long userId, List<BookingStatus> statuses) {
        return bookingRepository.findByUserIdAndStatusIn(userId, statuses);
    }

    public List<Booking> getBookingsByStatus(Long userId, BookingStatus status) {
        return bookingRepository.findByUserIdAndStatus(userId, status);
    }

    public List<Booking> getInProgressBookingsByVendor(Long vendorId) {
        return bookingRepository.findInProgressBookingsByVendor(vendorId);
    }

    public Optional<Booking> getById(Long id) {
        return bookingRepository.findById(id);
    }

    @Transactional
    public Booking saveBooking(Booking booking) {
        try {
            return bookingRepository.save(booking);
        } catch (Exception e) {
            log.error("Failed to save booking with ID: {}", booking.getId(), e);
            throw new BookingException("Failed to save booking");
        }
    }

    @Transactional
    public List<UploadedFile> uploadBookingFiles(Long bookingId, List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Files list cannot be empty");
        }

        if (files.size() > MAX_UPLOAD_FILES) {
            throw new BookingException("Maximum " + MAX_UPLOAD_FILES + " files can be uploaded");
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found with ID: " + bookingId));

        List<UploadedFile> uploadedFiles = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }

            String url = fileStorageService.saveFile(file);
            UploadedFile uploadedFile = UploadedFile.builder()
                    .booking(booking)
                    .name(file.getOriginalFilename())
                    .url(url)
                    .build();

            uploadedFileRepository.save(uploadedFile);
            uploadedFiles.add(uploadedFile);
        }

        return uploadedFiles;
    }

    @Transactional
    public List<UploadedFile> saveUploadedFileUrls(Long bookingId, List<UploadedFileRequest> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Files list cannot be empty");
        }

        if (files.size() > MAX_UPLOAD_FILES) {
            throw new BookingException("Maximum " + MAX_UPLOAD_FILES + " files can be uploaded");
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found with ID: " + bookingId));

        List<UploadedFile> existing = uploadedFileRepository.findByBookingId(bookingId);
        if (existing.size() + files.size() > MAX_UPLOAD_FILES) {
            throw new BookingException("Maximum " + MAX_UPLOAD_FILES + " files can be uploaded");
        }

        List<UploadedFile> saved = new ArrayList<>();
        for (UploadedFileRequest file : files) {
            if (file == null) continue;
            String name = file.getName();
            String url = file.getUrl();
            if (url == null || url.trim().isEmpty()) {
                continue;
            }

            UploadedFile uploadedFile = UploadedFile.builder()
                    .booking(booking)
                    .name(name)
                    .url(url)
                    .build();

            uploadedFileRepository.save(uploadedFile);
            saved.add(uploadedFile);
        }

        return saved;
    }
}
