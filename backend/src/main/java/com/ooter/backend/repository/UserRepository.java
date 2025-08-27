package com.ooter.backend.repository;

import com.ooter.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPhone(String phone);
    Optional<User> findByReferralCode(String referralCode);
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailOrGoogleId(String email, String googleId);
    
    // New method for phone OR email login
    @Query("SELECT u FROM User u WHERE u.phone = :identifier OR u.email = :identifier")
    Optional<User> findByPhoneOrEmail(@Param("identifier") String identifier);
    
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.recentSearches WHERE u.id = :id")
    Optional<User> findByIdWithRecentSearches(Long id);
}
