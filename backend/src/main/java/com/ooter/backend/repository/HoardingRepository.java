package com.ooter.backend.repository;

import com.ooter.backend.entity.Hoarding;
import com.ooter.backend.entity.HoardingCategory;
import com.ooter.backend.entity.HoardingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface HoardingRepository extends JpaRepository<Hoarding, Long> {

    List<Hoarding> findByLocationContainingIgnoreCase(String location);

    List<Hoarding> findByOwnerId(Long ownerId);

    int countByOwnerId(Long ownerId);

    List<Hoarding> findByCategory(HoardingCategory category);

    List<Hoarding> findByCityIgnoreCase(String city);

    List<Hoarding> findByCategoryAndCityIgnoreCase(HoardingCategory category, String city);

    List<Hoarding> findByLocationContainingIgnoreCaseOrCityContainingIgnoreCaseOrStateContainingIgnoreCase(
            String location, String city, String state);

    List<Hoarding> findByStatus(HoardingStatus status);

    @Query("SELECT COUNT(h) FROM Hoarding h WHERE h.owner.id = :vendorId AND h.status = :status")
    int countByVendorAndStatus(@Param("vendorId") Long vendorId, @Param("status") HoardingStatus status);

    Page<Hoarding> findByOwnerIdAndStatus(Long ownerId, HoardingStatus status, Pageable pageable);

    List<Hoarding> findByOwnerIdAndStatus(Long vendorId, HoardingStatus status);

    @Query(value = """
        SELECT * FROM hoarding
        WHERE latitude IS NOT NULL AND longitude IS NOT NULL AND
        6371 * acos(
            cos(radians(:lat)) * cos(radians(latitude)) *
            cos(radians(longitude) - radians(:lng)) +
            sin(radians(:lat)) * sin(radians(latitude))
        ) <= :radius
        """, nativeQuery = true)
    List<Hoarding> findNearbyHoardings(double lat, double lng, double radius);

    @Query("SELECT h FROM Hoarding h WHERE h.owner.id = :vendorId AND (h.status = 'ACTIVE' OR h.status = 'AVAILABLE')")
    List<Hoarding> findActiveAndAvailableByVendor(@Param("vendorId") Long vendorId);

    @Query("""
        SELECT h FROM Hoarding h
        WHERE h.owner.id = :vendorId
        AND h.status = com.ooter.backend.entity.HoardingStatus.ACTIVE
        AND NOT EXISTS (
            SELECT b FROM Booking b
            WHERE b.hoarding.id = h.id
            AND b.status = com.ooter.backend.entity.BookingStatus.CONFIRMED
            AND b.endDate >= CURRENT_DATE
        )
    """)
    Page<Hoarding> findAvailableByVendor(@Param("vendorId") Long vendorId, Pageable pageable);

    @Query("""
        SELECT h FROM Hoarding h
        WHERE h.owner.id = :vendorId
        AND (
            h.status = com.ooter.backend.entity.HoardingStatus.BOOKED
            OR EXISTS (
                SELECT b FROM Booking b
                WHERE b.hoarding.id = h.id
                AND b.status = com.ooter.backend.entity.BookingStatus.CONFIRMED
                AND b.endDate >= CURRENT_DATE
            )
        )
    """)
    Page<Hoarding> findBookedByVendor(@Param("vendorId") Long vendorId, Pageable pageable);

    @Query("SELECT h FROM Hoarding h WHERE h.owner.id = :vendorId AND h.status = 'NON_ACTIVE'")
    Page<Hoarding> findNonActiveByVendor(@Param("vendorId") Long vendorId, Pageable pageable);

    @Query("""
        SELECT h FROM Hoarding h
        WHERE h.owner.id = :vendorId
        AND h.status IN (
            com.ooter.backend.entity.HoardingStatus.ACTIVE,
            com.ooter.backend.entity.HoardingStatus.BOOKED
        )
    """)
    List<Hoarding> findAllActiveTabHoardings(@Param("vendorId") Long vendorId);
    @Query("SELECT MAX(h.updatedAt) FROM Hoarding h")
    Instant findMaxUpdatedAt();

    @Query("SELECT MAX(h.updatedAt) FROM Hoarding h WHERE h.owner.id = :ownerId")
    Instant findMaxUpdatedByOwner(@Param("ownerId") Long ownerId);
}