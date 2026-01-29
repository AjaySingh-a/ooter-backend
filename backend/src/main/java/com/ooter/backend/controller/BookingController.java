package com.ooter.backend.controller;

import java.time.temporal.ChronoUnit;
import com.ooter.backend.dto.*;
import com.ooter.backend.entity.*;
import com.ooter.backend.exception.BookingException;
import com.ooter.backend.repository.*;
import com.ooter.backend.service.*;
import com.razorpay.RazorpayException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BookingController {

    private final BookingService bookingService;
    private final RazorpayService razorpayService;
    private final HoardingRepository hoardingRepository;
    private final UserRepository userRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final BookingRepository bookingRepository;
    private final FileStorageService fileStorageService;

    @GetMapping(value = "/verification-status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getVerificationStatus(@AuthenticationPrincipal User user) {
        try {
            if (user == null) {
                log.warn("Unauthorized access to verification status");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(
                            "status", "error",
                            "message", "Unauthorized access",
                            "code", 401
                        ));
            }
            
            log.info("Fetching verification status for user: {}", user.getEmail());
            
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                        "status", "success",
                        "verified", user.isVerified(),
                        "timestamp", System.currentTimeMillis(),
                        "code", 200
                    ));
                    
        } catch (Exception e) {
            log.error("Error in verification-status endpoint", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                        "status", "error",
                        "message", "Internal server error",
                        "error", e.getMessage(),
                        "code", 500
                    ));
        }
    }

    @PostMapping(value = "/upload-verification", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadVerification(
            @RequestParam("gstCertificate") MultipartFile gstFile,
            @RequestParam(value = "cinCertificate", required = false) MultipartFile cinFile,
            @AuthenticationPrincipal User user) throws IOException {
        
        try {
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(
                            "status", "error",
                            "message", "Unauthorized"
                        ));
            }

            String gstFilePath = fileStorageService.saveFile(gstFile);
            if (cinFile != null) {
                String cinFilePath = fileStorageService.saveFile(cinFile);
            }

            user.setVerified(true);
            userRepository.save(user);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                        "status", "success",
                        "message", "Verification submitted successfully",
                        "verified", true,
                        "timestamp", System.currentTimeMillis()
                    ));

        } catch (Exception e) {
            log.error("Verification upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                        "status", "error",
                        "message", "Verification upload failed",
                        "error", e.getMessage()
                    ));
        }
    }

    @PostMapping("/create-with-payment")
    public ResponseEntity<?> createBookingWithPayment(
            @Valid @RequestBody Map<String, Object> bookingRequest,
            @AuthenticationPrincipal User user) {
        
        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        try {
            Long hoardingId = Long.parseLong(bookingRequest.get("hoardingId").toString());
            LocalDate startDate = LocalDate.parse(bookingRequest.get("startDate").toString());
            LocalDate endDate = LocalDate.parse(bookingRequest.get("endDate").toString());
            double amount = Double.parseDouble(bookingRequest.get("amount").toString());

            Hoarding hoarding = hoardingRepository.findById(hoardingId)
                .orElseThrow(() -> new RuntimeException("Hoarding not found"));

            long months = ChronoUnit.MONTHS.between(startDate, endDate);
            if (months <= 0) months = 1;
            double total = hoarding.getPricePerMonth() * months;

            Map<String, String> notes = new HashMap<>();
            notes.put("hoardingId", hoardingId.toString());
            notes.put("userId", user.getId().toString());
            
            String orderId = razorpayService.createOrder(
                (int) amount,
                "INR",
                "Booking for hoarding " + hoarding.getId(),
                notes
            );

            Booking booking = new Booking();
            booking.setUser(user);
            booking.setHoarding(hoarding);
            booking.setStartDate(startDate);
            booking.setEndDate(endDate);
            booking.setTotalPrice(total);
            booking.setStatus(BookingStatus.PENDING);
            booking.setBookingDate(LocalDate.now());
            booking.setOrderId(orderId);
            
            Booking saved = bookingService.createBooking(booking);
            applyReferralBonus(user);

            Map<String, String> response = new HashMap<>();
            response.put("orderId", orderId);
            response.put("bookingId", saved.getId().toString());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error creating booking with payment", e);
            if (e.getCause() instanceof RazorpayException) {
                return ResponseEntity.status(500).body("Payment gateway error: " + e.getCause().getMessage());
            }
            return ResponseEntity.badRequest().body("Error creating booking: " + e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> createBooking(
            @Valid @RequestBody Booking booking,
            @AuthenticationPrincipal User user) {

        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        if (booking.getHoarding() == null || booking.getHoarding().getId() == null) {
            return ResponseEntity.badRequest().body("Hoarding ID is required");
        }

        Hoarding hoarding = hoardingRepository.findById(booking.getHoarding().getId()).orElse(null);
        if (hoarding == null) {
            return ResponseEntity.badRequest().body("Hoarding not found");
        }

        long months = ChronoUnit.MONTHS.between(booking.getStartDate(), booking.getEndDate());
        if (months <= 0) months = 1;
        double total = hoarding.getPricePerMonth() * months;

        booking.setUser(user);
        booking.setHoarding(hoarding);
        booking.setTotalPrice(total);
        booking.setStatus(BookingStatus.PENDING);
        booking.setBookingDate(LocalDate.now());

        Booking saved = bookingService.createBooking(booking);
        applyReferralBonus(user);

        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{bookingId}/upload")
    public ResponseEntity<?> uploadPhoto(
            @PathVariable Long bookingId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User user) throws IOException {
        if (user == null) return ResponseEntity.status(401).body("Unauthorized");

        Booking booking = bookingService.getById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        List<UploadedFile> existing = uploadedFileRepository.findByBookingId(bookingId);
        if (existing.size() >= 3) {
            return ResponseEntity.badRequest().body("Maximum 3 photos can be uploaded.");
        }

        List<UploadedFile> uploaded = bookingService.uploadBookingFiles(bookingId, List.of(file));
        return ResponseEntity.ok(uploaded);
    }

    @PostMapping("/{bookingId}/files/save")
    public ResponseEntity<?> uploadMultipleFiles(
            @PathVariable Long bookingId,
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal User user) throws IOException {
        try {
            if (user == null) return ResponseEntity.status(401).body("Unauthorized");

            bookingService.getById(bookingId)
                    .orElseThrow(() -> new RuntimeException("Booking not found"));

            List<UploadedFile> existing = uploadedFileRepository.findByBookingId(bookingId);
            if (existing.size() + files.size() > 3) {
                return ResponseEntity.badRequest().body("Maximum 3 files can be uploaded.");
            }

            List<UploadedFile> uploaded = bookingService.uploadBookingFiles(bookingId, files);
            return ResponseEntity.ok(uploaded);
        } catch (IOException e) {
            log.error("File upload failed for bookingId={}", bookingId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "status", "error",
                            "message", "File upload failed on server. Please try again.",
                            "code", 500
                    ));
        }
    }

    @GetMapping("/{bookingId}/uploads")
    public ResponseEntity<?> getUploadedFiles(
            @PathVariable Long bookingId,
            @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body("Unauthorized");

        List<UploadedFile> files = uploadedFileRepository.findByBookingId(bookingId);
        return ResponseEntity.ok(files);
    }

    /**
     * Save already-uploaded (Cloudinary) URLs for a booking.
     * Frontend uploads file(s) to Cloudinary and sends {name,url} here.
     */
    @PostMapping("/{bookingId}/uploads")
    public ResponseEntity<?> saveUploadedFiles(
            @PathVariable Long bookingId,
            @RequestBody List<UploadedFileRequest> files,
            @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body("Unauthorized");

        try {
            List<UploadedFile> saved = bookingService.saveUploadedFileUrls(bookingId, files);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (BookingException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to save uploaded file URLs for bookingId={}", bookingId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to save uploaded files");
        }
    }

    @GetMapping("/{bookingId}/execution-proof")
    public ResponseEntity<?> getExecutionProof(
            @PathVariable Long bookingId,
            @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body("Unauthorized");

        try {
            Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
            if (bookingOpt.isEmpty()) return ResponseEntity.status(404).body("Booking not found");
            Booking booking = bookingOpt.get();

            // Allow booking owner (customer) OR booking vendor
            boolean isCustomer = booking.getUser() != null && booking.getUser().getId() != null
                    && booking.getUser().getId().equals(user.getId());
            boolean isVendor = booking.getHoarding() != null
                    && booking.getHoarding().getOwner() != null
                    && booking.getHoarding().getOwner().getId() != null
                    && booking.getHoarding().getOwner().getId().equals(user.getId());

            if (!isCustomer && !isVendor) {
                return ResponseEntity.status(403).body("Access denied");
            }

            return ResponseEntity.ok(bookingService.getExecutionProofFiles(bookingId));
        } catch (Exception e) {
            log.error("Failed to get execution proof for bookingId={}", bookingId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to fetch execution proof");
        }
    }

    @PostMapping("/{bookingId}/execution-proof")
    public ResponseEntity<?> saveExecutionProof(
            @PathVariable Long bookingId,
            @RequestBody List<UploadedFileRequest> files,
            @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body("Unauthorized");

        try {
            Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
            if (bookingOpt.isEmpty()) return ResponseEntity.status(404).body("Booking not found");
            Booking booking = bookingOpt.get();

            // Only vendor who owns this booking can upload proof
            boolean isVendor = user.getRole() == Role.VENDOR
                    && booking.getHoarding() != null
                    && booking.getHoarding().getOwner() != null
                    && booking.getHoarding().getOwner().getId() != null
                    && booking.getHoarding().getOwner().getId().equals(user.getId());

            if (!isVendor) {
                return ResponseEntity.status(403).body("Access denied");
            }

            return ResponseEntity.ok(bookingService.saveExecutionProofUrls(bookingId, files));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (BookingException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to save execution proof for bookingId={}", bookingId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to save execution proof");
        }
    }

    @DeleteMapping("/uploads/{fileId}")
    public ResponseEntity<?> deleteUploadedFile(
            @PathVariable Long fileId,
            @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body("Unauthorized");

        Optional<UploadedFile> fileOpt = uploadedFileRepository.findById(fileId);
        if (fileOpt.isEmpty()) return ResponseEntity.notFound().build();

        uploadedFileRepository.deleteById(fileId);
        return ResponseEntity.ok("File deleted");
    }

    @GetMapping("/me")
    public ResponseEntity<?> myBookings(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body("Unauthorized");
        return ResponseEntity.ok(bookingService.getBookingsByUserId(user.getId()));
    }

    @GetMapping("/hoarding/{hoardingId}")
    public ResponseEntity<?> bookingsForHoarding(@PathVariable Long hoardingId) {
        return ResponseEntity.ok(bookingService.getBookingsByHoardingId(hoardingId));
    }

    @GetMapping("/hoarding/{hoardingId}/booked-dates")
    public ResponseEntity<?> getBookedDatesForHoarding(@PathVariable Long hoardingId) {
        try {
            List<Booking> bookings = bookingRepository.findByHoardingIdAndStatusIn(
                hoardingId,
                List.of(BookingStatus.CONFIRMED, BookingStatus.PENDING)
            );

            if (bookings == null || bookings.isEmpty()) {
                log.info("No booked dates found for hoardingId: {}", hoardingId);
                return ResponseEntity.ok(List.of());
            }

            List<Map<String, String>> bookedDateRanges = bookings.stream()
                .map(b -> Map.of(
                    "startDate", b.getStartDate().toString(),
                    "endDate", b.getEndDate().toString()
                ))
                .toList();

            log.info("Returning booked dates for hoardingId {}: {}", hoardingId, bookedDateRanges);
            return ResponseEntity.ok(bookedDateRanges);

        } catch (Exception e) {
            log.error("Error fetching booked dates for hoardingId: {}", hoardingId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "status", "error",
                        "message", "Failed to fetch booked dates",
                        "error", e.getMessage(),
                        "code", 500
                    ));
        }
    }

    @GetMapping("/orders")
    public ResponseEntity<?> getActiveBookings(
            @AuthenticationPrincipal User user,
            @RequestHeader(value = "If-Modified-Since", required = false) String ifModifiedSince) {
        
        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        try {
            Instant lastUpdate = bookingRepository.findMaxUpdatedAtByUser(user.getId());
            if (lastUpdate == null) {
                lastUpdate = Instant.now();
            }

            if (ifModifiedSince != null) {
                try {
                    Instant ifModifiedSinceInstant = Instant.parse(ifModifiedSince);
                    if (!lastUpdate.isAfter(ifModifiedSinceInstant)) {
                        return ResponseEntity.status(304).build();
                    }
                } catch (Exception e) {
                    log.warn("Invalid If-Modified-Since header", e);
                }
            }

            List<Booking> active = bookingService.getBookingsByStatusList(
                user.getId(),
                List.of(BookingStatus.CONFIRMED, BookingStatus.PENDING)
            );

            ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES));
            
            builder.lastModified(lastUpdate);

            return builder.body(active.stream().map(BookingResponse::from).toList());

        } catch (Exception e) {
            log.error("Error fetching active bookings", e);
            return ResponseEntity.internalServerError().body(
                Map.of(
                    "status", "error",
                    "message", "Failed to fetch bookings",
                    "error", e.getMessage()
                )
            );
        }
    }

    @GetMapping("/cancelled")
    public ResponseEntity<?> getCancelledBookings(
            @AuthenticationPrincipal User user,
            @RequestHeader(value = "If-Modified-Since", required = false) String ifModifiedSince) {
        
        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        try {
            Instant lastUpdate = bookingRepository.findMaxUpdatedAtByUser(user.getId());
            if (lastUpdate == null) {
                lastUpdate = Instant.now();
            }

            if (ifModifiedSince != null) {
                try {
                    Instant ifModifiedSinceInstant = Instant.parse(ifModifiedSince);
                    if (!lastUpdate.isAfter(ifModifiedSinceInstant)) {
                        return ResponseEntity.status(304).build();
                    }
                } catch (Exception e) {
                    log.warn("Invalid If-Modified-Since header", e);
                }
            }

            List<Booking> cancelled = bookingService.getBookingsByStatusList(
                user.getId(),
                List.of(BookingStatus.CANCELLED)
            );

            ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(24, TimeUnit.HOURS));
            
            builder.lastModified(lastUpdate);

            return builder.body(cancelled.stream().map(BookingResponse::from).toList());

        } catch (Exception e) {
            log.error("Error fetching cancelled bookings", e);
            return ResponseEntity.internalServerError().body(
                Map.of(
                    "status", "error",
                    "message", "Failed to fetch bookings",
                    "error", e.getMessage()
                )
            );
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getBookingDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body("Unauthorized");

        return bookingService.getById(id)
                .map(BookingDetailResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(null));
    }

    @PutMapping("/{bookingId}/cancel")
    public ResponseEntity<?> cancelBooking(@PathVariable Long bookingId) {
        Optional<Booking> optionalBooking = bookingRepository.findById(bookingId);
        if (!optionalBooking.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Booking not found");
        }

        Booking booking = optionalBooking.get();

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Booking already cancelled");
        }

        LocalDateTime bookingTime = booking.getCreatedAt();
        LocalDateTime now = LocalDateTime.now();

        Duration duration = Duration.between(bookingTime, now);
        if (duration.toHours() > 24) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Booking cannot be cancelled after 24 hours");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        return ResponseEntity.ok("Booking cancelled successfully");
    }

    private void applyReferralBonus(User user) {
        if (!user.isReferralUsed() && user.getReferredBy() != null) {
            Optional<User> referrerOpt = userRepository.findByReferralCode(user.getReferredBy());
            if (referrerOpt.isPresent()) {
                User referrer = referrerOpt.get();
                referrer.setReferralCoins(referrer.getReferralCoins() + 100);
                user.setReferralCoins(user.getReferralCoins() + 100);
                user.setReferralUsed(true);
                userRepository.save(referrer);
                userRepository.save(user);
            }
        }
    }

    private Instant parseHttpDate(String httpDate) {
        try {
            return Instant.parse(httpDate);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}