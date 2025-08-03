package com.ooter.backend.controller;

import com.ooter.backend.dto.*;
import com.ooter.backend.entity.*;
import com.ooter.backend.repository.BookingRepository;
import com.ooter.backend.repository.HoardingRepository;
import com.ooter.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/vendors")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class VendorController {

    private final UserRepository userRepository;
    private final HoardingRepository hoardingRepository;
    private final BookingRepository bookingRepository;

    // ✅ Added new verification document upload endpoint
    @PostMapping("/upload-verification")
    public ResponseEntity<?> uploadVerificationDocs(
            @RequestParam("gstCertificate") MultipartFile gstCertificate,
            @RequestParam(value = "cinCertificate", required = false) MultipartFile cinCertificate,
            @AuthenticationPrincipal User vendor
    ) {
        if (vendor == null || vendor.getRole() != Role.VENDOR) {
            return ResponseEntity.status(403).body("Only vendors can upload verification documents");
        }

        if (gstCertificate.isEmpty()) {
            return ResponseEntity.badRequest().body("GST certificate is required");
        }

        try {
            // Create upload folder if not exists
            String uploadDir = "uploads/vendor-certificates/";
            File folder = new File(uploadDir);
            if (!folder.exists()) folder.mkdirs();

            // Save GST
            String gstFileName = "GST_" + vendor.getId() + "_" + gstCertificate.getOriginalFilename();
            File gstFile = new File(uploadDir + gstFileName);
            gstCertificate.transferTo(gstFile);

            // Save CIN (optional)
            if (cinCertificate != null && !cinCertificate.isEmpty()) {
                String cinFileName = "CIN_" + vendor.getId() + "_" + cinCertificate.getOriginalFilename();
                File cinFile = new File(uploadDir + cinFileName);
                cinCertificate.transferTo(cinFile);
            }

            // Mark verified
            vendor.setVerified(true);
            userRepository.save(vendor);

            return ResponseEntity.ok("Verification submitted successfully");
        } catch (IOException e) {
            return ResponseEntity.status(500).body("File upload failed: " + e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<MessageResponse> registerAsVendor(
            @AuthenticationPrincipal User user,
            @RequestBody VendorRegistrationRequest request) {
        if (user == null) return ResponseEntity.status(401).body(new MessageResponse("User not authenticated"));

        Optional<User> optionalUser = userRepository.findById(user.getId());
        if (optionalUser.isEmpty()) return ResponseEntity.badRequest().body(new MessageResponse("User not found"));

        User existingUser = optionalUser.get();
        if (existingUser.getRole() != Role.USER)
            return ResponseEntity.status(403).body(new MessageResponse("Only regular users can become vendors"));

        existingUser.setCompanyName(request.getCompanyName());
        existingUser.setEmail(request.getEmail());
        existingUser.setDesignation(request.getDesignation());
        existingUser.setMobile(request.getMobile());
        existingUser.setGstin(request.getGstin());
        existingUser.setPan(request.getPan());
        existingUser.setCin(request.getCin());
        existingUser.setAddress(request.getAddress());
        existingUser.setRole(Role.VENDOR);

        userRepository.save(existingUser);
        return ResponseEntity.ok(new MessageResponse("Vendor registration successful"));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> getVendorDashboard(@AuthenticationPrincipal User user) {
        if (user == null || user.getRole() != Role.VENDOR)
            return ResponseEntity.status(403).body(new MessageResponse("Access denied"));

        Long vendorId = user.getId();

        VendorDashboardResponse response = new VendorDashboardResponse(
                hoardingRepository.countByOwnerId(vendorId),
                bookingRepository.countByVendorIdAndStatus(vendorId, BookingStatus.CONFIRMED),
                bookingRepository.countByVendorIdAndStatus(vendorId, BookingStatus.CONFIRMED),
                bookingRepository.sumPaidAmountByVendorId(vendorId),
                bookingRepository.countByVendorIdAndStatus(vendorId, BookingStatus.CONFIRMED),
                bookingRepository.countNewOrdersForVendor(vendorId),
                bookingRepository.countPendingRTMForVendor(vendorId),
                bookingRepository.sumPreviousPaymentsByVendorId(vendorId),
                bookingRepository.sumUpcomingPaymentsByVendorId(vendorId),
                bookingRepository.countByVendorIdAndStatus(vendorId, BookingStatus.CONFIRMED),
                hoardingRepository.countByOwnerId(vendorId) - bookingRepository.countByVendorIdAndStatus(vendorId, BookingStatus.CONFIRMED),
                user.isVerified(),
                user.isOnHold()
        );

        return ResponseEntity.ok(response);
    }
    @GetMapping("/sales-overview")
    public ResponseEntity<?> getSalesOverview(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        if (user == null || user.getRole() != Role.VENDOR) {
            return ResponseEntity.status(403).body(new MessageResponse("Access denied"));
        }

        // Default to current month if dates not provided
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();

        // Calculate total sales for the period
        Double totalSales = bookingRepository.sumSalesByVendorAndDateRange(
                user.getId(), 
                start.atStartOfDay(), 
                end.plusDays(1).atStartOfDay()
        );
        
        if (totalSales == null) {
            totalSales = 0.0;
        }

        // Get orders for the period
        List<Booking> bookings = bookingRepository.findByVendorIdAndDateRange(
                user.getId(),
                start.atStartOfDay(),
                end.plusDays(1).atStartOfDay()
        );

        // Convert to DTO
        List<SalesOverviewDTO.OrderDTO> orderDTOs = bookings.stream()
                .map(b -> {
                    SalesOverviewDTO.OrderDTO dto = new SalesOverviewDTO.OrderDTO();
                    dto.setOrderId(b.getOrderId());
                    dto.setDate(b.getCreatedAt().toLocalDate().toString());
                    dto.setAmount(b.getTotalAmount());
                    return dto;
                })
                .toList();

        // Create response
        SalesOverviewDTO response = new SalesOverviewDTO();
        response.setTotalSales(totalSales);
        response.setPeriod(start.toString() + " to " + end.toString());
        response.setDaysShowing((int) start.datesUntil(end.plusDays(1)).count());
        response.setOrders(orderDTOs);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/listing-dashboard")
    public ResponseEntity<?> getVendorListingStats(@AuthenticationPrincipal User user) {
        if (user == null || user.getRole() != Role.VENDOR)
            return ResponseEntity.status(403).body(new MessageResponse("Access denied"));

        Long vendorId = user.getId();

        int availableCount = hoardingRepository.findActiveAndAvailableByVendor(vendorId).stream()
                .filter(h -> !bookingRepository.existsActiveBookingForHoarding(h.getId()))
                .toList()
                .size();

        int activeCount = hoardingRepository.findAllActiveTabHoardings(vendorId).size();
        int bookedCount = hoardingRepository.findBookedByVendor(vendorId, Pageable.unpaged()).getContent().size();
        int nonActiveCount = hoardingRepository.findNonActiveByVendor(vendorId, Pageable.unpaged()).getContent().size();

        ListingDashboardStatsDTO stats = new ListingDashboardStatsDTO(
                activeCount,
                nonActiveCount,
                bookedCount,
                availableCount,
                0, 0, 0, 0, 0, 0
        );

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/bookings/in-progress")
    public ResponseEntity<?> getInProgressBookings(@AuthenticationPrincipal User user) {
        if (user == null || user.getRole() != Role.VENDOR)
            return ResponseEntity.status(403).body(new MessageResponse("Access denied"));

        List<Booking> inProgressBookings = bookingRepository.findInProgressBookingsByVendor(user.getId());

        List<BookingProgressResponse> response = inProgressBookings.stream()
                .map(BookingProgressResponse::from)
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/bookings/{orderId}")
    public ResponseEntity<?> getBookingDetail(@AuthenticationPrincipal User user, @PathVariable String orderId) {
        if (user == null || user.getRole() != Role.VENDOR)
            return ResponseEntity.status(403).body(new MessageResponse("Access denied"));

        Optional<Booking> optional = bookingRepository.findByOrderId(orderId);
        if (optional.isEmpty()) return ResponseEntity.status(404).body(new MessageResponse("Booking not found"));

        Booking booking = optional.get();

        if (!booking.getHoarding().getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Access denied"));
        }

        BookingProgressResponse response = BookingProgressResponse.from(booking);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/bookings/{orderId}/media")
    public ResponseEntity<?> markMediaDownloaded(@AuthenticationPrincipal User user, @PathVariable String orderId) {
        return updateBookingStep(user, orderId, "media");
    }

    @PostMapping("/bookings/{orderId}/printing")
    public ResponseEntity<?> markPrintingStarted(@AuthenticationPrincipal User user, @PathVariable String orderId) {
        return updateBookingStep(user, orderId, "printing");
    }

    @PostMapping("/bookings/{orderId}/mounting")
    public ResponseEntity<?> markMountingStarted(@AuthenticationPrincipal User user, @PathVariable String orderId) {
        return updateBookingStep(user, orderId, "mounting");
    }

    @PostMapping("/bookings/{orderId}/live")
    public ResponseEntity<?> markSiteLive(@AuthenticationPrincipal User user, @PathVariable String orderId) {
        return updateBookingStep(user, orderId, "live");
    }

    private ResponseEntity<?> updateBookingStep(User user, String orderId, String step) {
        if (user == null || user.getRole() != Role.VENDOR)
            return ResponseEntity.status(403).body(new MessageResponse("Access denied"));

        Optional<Booking> optional = bookingRepository.findByOrderId(orderId);
        if (optional.isEmpty()) return ResponseEntity.status(404).body(new MessageResponse("Booking not found"));

        Booking booking = optional.get();

        if (!booking.getHoarding().getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Access denied"));
        }

        switch (step) {
            case "media" -> {
                booking.setMediaDownloaded(true);
                booking.setMediaDownloadDate(LocalDate.now());
            }
            case "printing" -> {
                booking.setPrintingStarted(true);
                booking.setPrintingStartDate(LocalDate.now());
            }
            case "mounting" -> {
                booking.setMountingStarted(true);
                booking.setMountingStartDate(LocalDate.now());
            }
            case "live" -> {
                booking.setSiteLive(true);
                booking.setSiteLiveDate(LocalDate.now());
            }
            default -> {
                return ResponseEntity.badRequest().body(new MessageResponse("Invalid step"));
            }
        }

        bookingRepository.save(booking);
        return ResponseEntity.ok(BookingProgressResponse.from(booking));
    }

    @GetMapping("/booked-listings")
    public ResponseEntity<?> getBookedListings(@AuthenticationPrincipal User user,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(defaultValue = "id,desc") String[] sort) {
        if (user == null || user.getRole() != Role.VENDOR)
            return ResponseEntity.status(403).body(new MessageResponse("Access denied"));

        Pageable pageable = PageRequest.of(page, size, getSortFrom(sort));
        return getListingsByStatus(user, HoardingStatus.BOOKED, pageable);
    }

    @GetMapping("/non-active-listings")
    public ResponseEntity<?> getNonActiveListings(@AuthenticationPrincipal User user,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @RequestParam(defaultValue = "id,desc") String[] sort) {
        if (user == null || user.getRole() != Role.VENDOR)
            return ResponseEntity.status(403).body(new MessageResponse("Access denied"));

        Pageable pageable = PageRequest.of(page, size, getSortFrom(sort));
        return getListingsByStatus(user, HoardingStatus.NON_ACTIVE, pageable);
    }

    @GetMapping("/active-listings")
    public ResponseEntity<?> getActiveListings(@AuthenticationPrincipal User user) {
        if (user == null || user.getRole() != Role.VENDOR)
            return ResponseEntity.status(403).body(new MessageResponse("Access denied"));

        List<Hoarding> hoardings = hoardingRepository.findAllActiveTabHoardings(user.getId());

        List<ActiveListingResponse> filtered = hoardings.stream()
                .map(h -> {
                    LocalDate bookedTill = bookingRepository.findLatestBookingEndDateForHoarding(h.getId());
                    return ActiveListingResponse.from(h, bookedTill);
                })
                .toList();

        return ResponseEntity.ok(filtered);
    }

    @GetMapping("/available-listings")
    public ResponseEntity<?> getAvailableListings(@AuthenticationPrincipal User user,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              @RequestParam(defaultValue = "id,desc") String[] sort) {
        if (user == null || user.getRole() != Role.VENDOR)
            return ResponseEntity.status(403).body(new MessageResponse("Access denied"));

        Pageable pageable = PageRequest.of(page, size, getSortFrom(sort));
        return getListingsByStatus(user, HoardingStatus.AVAILABLE, pageable);
    }

    private Sort getSortFrom(String[] sort) {
        String sortField = sort[0];
        Sort.Direction sortDirection = sort.length > 1 && sort[1].equalsIgnoreCase("asc") ?
                Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(sortDirection, sortField);
    }

    private ResponseEntity<?> getListingsByStatus(User user, HoardingStatus status, Pageable pageable) {
        Page<Hoarding> hoardingsPage;

        switch (status) {
            case ACTIVE -> hoardingsPage = hoardingRepository.findByOwnerIdAndStatus(user.getId(), status, pageable);
            case BOOKED -> hoardingsPage = hoardingRepository.findBookedByVendor(user.getId(), pageable);
            case AVAILABLE -> hoardingsPage = hoardingRepository.findAvailableByVendor(user.getId(), pageable);
            case NON_ACTIVE -> hoardingsPage = hoardingRepository.findNonActiveByVendor(user.getId(), pageable);
            default -> hoardingsPage = Page.empty();
        }

        List<ActiveListingResponse> responseList = hoardingsPage.getContent().stream()
                .map(h -> {
                    LocalDate bookedTill = bookingRepository.findLatestBookingEndDateForHoarding(h.getId());
                    return ActiveListingResponse.from(h, bookedTill);
                })
                .toList();

        return ResponseEntity.ok(new PageImpl<>(responseList, pageable, hoardingsPage.getTotalElements()));
    }
}