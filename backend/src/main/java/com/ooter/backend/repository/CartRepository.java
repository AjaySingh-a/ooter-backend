package com.ooter.backend.repository;

import com.ooter.backend.entity.CartItem;
import com.ooter.backend.entity.Hoarding;
import com.ooter.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CartRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByUser(User user);
    Optional<CartItem> findByUserAndHoarding(User user, Hoarding hoarding);
    boolean existsByUserAndHoarding(User user, Hoarding hoarding);
    // Add to CartRepository
    @Query("SELECT MAX(ci.updatedAt) FROM CartItem ci WHERE ci.user.id = :userId")
    Instant findMaxUpdatedAtByUser(@Param("userId") Long userId);

    void deleteByUserAndHoardingId(User user, Long hoardingId);
    

}
