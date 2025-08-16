package com.ooter.backend.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
@EnableCaching
public class CacheConfig {

    // User related caches
    public static final String USER_CACHE = "users";
    public static final String USER_PROFILE_CACHE = "userProfile";
    public static final String USER_SEARCHES_CACHE = "userSearches";
    
    // Auth related caches
    public static final String BLACKLIST_CACHE = "blacklistedTokens";
    public static final String JWT_CLAIMS_CACHE = "jwtClaims";
    
    // Vendor specific caches
    public static final String VENDOR_DASHBOARD = "vendorDashboard";
    public static final String VENDOR_SALES = "vendorSales";
    public static final String VENDOR_LISTING_STATS = "vendorListingStats";
    public static final String VENDOR_LISTINGS = "vendorListings";
    public static final String IN_PROGRESS_BOOKINGS = "inProgressBookings";
    public static final String BOOKING_DETAILS = "bookingDetails";

    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        cacheManager.setCacheNames(Arrays.asList(
            USER_CACHE,
            BLACKLIST_CACHE,
            JWT_CLAIMS_CACHE,
            USER_PROFILE_CACHE,
            USER_SEARCHES_CACHE,
            VENDOR_DASHBOARD,
            VENDOR_SALES,
            VENDOR_LISTING_STATS,
            VENDOR_LISTINGS,
            IN_PROGRESS_BOOKINGS,
            BOOKING_DETAILS
        ));
        
        // Disallow null values in cache
        cacheManager.setAllowNullValues(false);
        
        return cacheManager;
    }
}