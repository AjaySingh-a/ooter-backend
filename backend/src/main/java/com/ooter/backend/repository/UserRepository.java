package com.ooter.backend.repository;

import com.ooter.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPhone(String phone);
    Optional<User>findByReferralCode(String referralCode);
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailOrGoogleId(String email, String googleId);

}
