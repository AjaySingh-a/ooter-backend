package com.ooter.backend.repository;

import com.ooter.backend.entity.CartItem;
import com.ooter.backend.entity.Hoarding;
import com.ooter.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByUser(User user);
    Optional<CartItem> findByUserAndHoarding(User user, Hoarding hoarding);
    boolean existsByUserAndHoarding(User user, Hoarding hoarding);

    void deleteByUserAndHoardingId(User user, Long hoardingId);

}
