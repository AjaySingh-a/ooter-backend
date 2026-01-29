package com.ooter.backend.repository;

import com.ooter.backend.entity.Booking;
import com.ooter.backend.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserId(Long userId);

    List<Booking> findByHoardingId(Long hoardingId);

    List<Booking> findByUserIdAndStatusIn(Long userId, List<BookingStatus> statuses);

    List<Booking> findByUserIdAndStatus(Long userId, BookingStatus status);

    // ✅ Count vendor’s confirmed bookings
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.vendor.id = :vendorId AND b.status = :status")
    int countByVendorIdAndStatus(@Param("vendorId") Long vendorId, @Param("status") BookingStatus status);

    // ✅ Sum of paidAmount for CONFIRMED bookings only
    @Query("SELECT COALESCE(SUM(b.paidAmount), 0) FROM Booking b WHERE b.vendor.id = :vendorId AND b.status = 'CONFIRMED'")
    double sumPaidAmountByVendorId(@Param("vendorId") Long vendorId);

    // ✅ Sum of previous payments (assume status is COMPLETED)
    @Query("SELECT COALESCE(SUM(b.paidAmount), 0) FROM Booking b WHERE b.vendor.id = :vendorId AND b.status = 'COMPLETED'")
    double sumPreviousPaymentsByVendorId(@Param("vendorId") Long vendorId);

    // ✅ Sum of upcoming payments (assume status is UPCOMING)
    @Query("SELECT COALESCE(SUM(b.paidAmount), 0) FROM Booking b WHERE b.vendor.id = :vendorId AND b.status = 'UPCOMING'")
    double sumUpcomingPaymentsByVendorId(@Param("vendorId") Long vendorId);

    // ✅ New orders: Pending bookings
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.vendor.id = :vendorId AND b.status = 'PENDING'")
    int countNewOrdersForVendor(@Param("vendorId") Long vendorId);

    // ✅ Pending RTM: Status = PRINTING (example)
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.vendor.id = :vendorId AND b.status = 'PRINTING'")
    int countPendingRTMForVendor(@Param("vendorId") Long vendorId);

    // ✅ Latest booking end date for a hoarding
    @Query("""
        SELECT MAX(b.endDate) FROM Booking b
        WHERE b.hoarding.id = :hoardingId AND b.status = com.ooter.backend.entity.BookingStatus.CONFIRMED
    """)
    LocalDate findLatestBookingEndDateForHoarding(@Param("hoardingId") Long hoardingId);

    // ✅ Count active/future confirmed bookings for a hoarding
    @Query("""
        SELECT COUNT(b) FROM Booking b 
        WHERE b.hoarding.id = :hoardingId 
        AND b.status = com.ooter.backend.entity.BookingStatus.CONFIRMED 
        AND b.endDate >= CURRENT_DATE
    """)
    long countActiveOrFutureBookings(@Param("hoardingId") Long hoardingId);
    @Query("""
    SELECT COUNT(b) > 0 FROM Booking b
    WHERE b.hoarding.id = :hoardingId
    AND b.status = com.ooter.backend.entity.BookingStatus.CONFIRMED
    AND b.endDate >= CURRENT_DATE
    """)
    boolean existsActiveBookingForHoarding(@Param("hoardingId") Long hoardingId);
    // ✅ All vendor bookings (no filter on siteLive — booking stays in list after Site is Live)
    @Query("""
        SELECT b FROM Booking b 
        WHERE b.vendor.id = :vendorId 
        ORDER BY b.id DESC
    """)
    List<Booking> findInProgressBookingsByVendor(@Param("vendorId") Long vendorId);
    List<Booking> findByHoardingIdAndStatusIn(Long hoardingId, List<BookingStatus> statuses);
    // Add to BookingRepository
    @Query("SELECT MAX(b.updatedAt) FROM Booking b WHERE b.user.id = :userId")
    Instant findMaxUpdatedAtByUser(@Param("userId") Long userId);

    Optional<Booking> findByOrderId(String orderId);
    @Query("SELECT MAX(b.updatedAt) FROM Booking b WHERE b.hoarding.owner.id = :vendorId")
    Optional<Instant> findMaxUpdatedAtByVendor(@Param("vendorId") Long vendorId);

    @Query("SELECT SUM(b.totalAmount) FROM Booking b " +
           "WHERE b.vendor.id = :vendorId " +
           "AND b.createdAt BETWEEN :start AND :end " +
           "AND b.status = 'CONFIRMED'")
    Double sumSalesByVendorAndDateRange(
            @Param("vendorId") Long vendorId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT b FROM Booking b " +
           "WHERE b.vendor.id = :vendorId " +
           "AND b.createdAt BETWEEN :start AND :end " +
           "AND b.status = 'CONFIRMED' " +
           "ORDER BY b.createdAt DESC")
    List<Booking> findByVendorIdAndDateRange(
            @Param("vendorId") Long vendorId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);


    


}
