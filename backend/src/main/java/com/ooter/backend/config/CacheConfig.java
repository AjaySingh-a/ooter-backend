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

    public static final String USER_CACHE = "users";
    public static final String BLACKLIST_CACHE = "blacklistedTokens";
    public static final String JWT_CLAIMS_CACHE = "jwtClaims";
    public static final String USER_PROFILE_CACHE = "userProfile";
    public static final String USER_SEARCHES_CACHE = "userSearches";

    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        cacheManager.setCacheNames(Arrays.asList(
            USER_CACHE,
            BLACKLIST_CACHE,
            JWT_CLAIMS_CACHE,
            USER_PROFILE_CACHE,
            USER_SEARCHES_CACHE
        ));
        cacheManager.setAllowNullValues(false);
        return cacheManager;
    }
}