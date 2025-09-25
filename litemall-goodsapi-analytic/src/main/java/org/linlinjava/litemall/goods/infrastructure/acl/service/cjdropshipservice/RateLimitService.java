package org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitService.class);

    private final AtomicInteger dailyRequestCount = new AtomicInteger(0);
    private long lastResetTime = System.currentTimeMillis();
    //private static final long DAY_IN_MILLIS = 24 * 60 * 60 * 1000; // every day
    //private static final long HOURLY_IN_MILLIS = 24 * 60 * 1000; // every 24 hours
    private static final long HOURLY_IN_MILLIS = 5 * 60 * 1000;// every 5 minutes

    // Track requests per category to avoid duplicate counting
    private final Map<String, Long> categoryLastRequestTime = new ConcurrentHashMap<>();
    private final Set<String> processedCategoriesToday = ConcurrentHashMap.newKeySet();





    /**
     * Check if we can make a request for a specific category
     * Returns true if under daily limit AND category hasn't been processed today
     */
    public boolean canMakeRequest(String categoryId) {
        resetCounterIfNeeded();

        // If we've already processed this category today, don't make another request
        if (processedCategoriesToday.contains(categoryId)) {
            logger.debug("Category {} already processed today", categoryId);
            return false;
        }

        // Check daily global limit
        //if (dailyRequestCount.get() >= 1000) {
        if (dailyRequestCount.get() >= 500) {
            logger.warn("Daily API limit (1000) reached. Cannot make request for category: {}", categoryId);
            return false;
        }

        return true;
    }
    /**
     * Record that a request was made for a specific category
     */
    public void recordRequest(String categoryId) {
        resetCounterIfNeeded();

        // Only increment counter if this is a new category for today
        if (!processedCategoriesToday.contains(categoryId)) {
            int currentCount = dailyRequestCount.incrementAndGet();
            processedCategoriesToday.add(categoryId);
            categoryLastRequestTime.put(categoryId, System.currentTimeMillis());

            logger.debug("Recorded request for category: {}. Total today: {}", categoryId, currentCount);

            // Log warning when approaching limit
            //if (currentCount >= 900 && currentCount < 1000) {
            if (currentCount >= 400 && currentCount < 500) {
                //logger.warn("Approaching daily API limit: {}/1000 requests", currentCount);
                logger.warn("Approaching daily API limit: {}/500 requests", currentCount);
            //} else if (currentCount >= 1000) {
            } else if (currentCount >= 500) {
                //logger.error("DAILY API LIMIT REACHED: 1000/1000 requests");
                logger.error("DAILY API LIMIT REACHED: 500/500 requests");
            }
        }
    }


    /**
     * Get the current request count
     */
    public int getCurrentRequestCount() {
        resetCounterIfNeeded();
        return dailyRequestCount.get();
    }

    /**
     * Get remaining requests for today
     */
    public int getRemainingRequests() {
        resetCounterIfNeeded();
        return Math.max(0, 1000 - dailyRequestCount.get());
    }

    /**
     * Check if a category has been processed today
     */
    public boolean isCategoryProcessedToday(String categoryId) {
        return processedCategoriesToday.contains(categoryId);
    }

    /**
     * Get all categories processed today
     */
    public Set<String> getProcessedCategoriesToday() {
        return new HashSet<>(processedCategoriesToday);
    }

    /**
     * Force reset (for testing or special cases)
     */
    public synchronized void forceReset() {
        dailyRequestCount.set(0);
        processedCategoriesToday.clear();
        lastResetTime = System.currentTimeMillis();
        logger.info("Rate limit counter force reset");
    }

    /**
     * Get time until next reset
     */
    public long getMillisUntilReset() {
        //long nextResetTime = lastResetTime + DAY_IN_MILLIS;
        long nextResetTime = lastResetTime + HOURLY_IN_MILLIS;
        return Math.max(0, nextResetTime - System.currentTimeMillis());
    }

    /**
     * Get formatted time until reset
     */
    public String getTimeUntilReset() {
        long millis = getMillisUntilReset();
        //long hours = millis / (60 * 60 * 1000);
        long hours = millis / (60 * 1000);
        //long minutes = (millis % (60 * 60 * 1000)) / (60 * 1000);
        long minutes = (millis % (60 * 1000)) / (1000);
        return String.format("%02d:%02d", hours, minutes);
    }



    /**
     * Reset the daily counter if needed
     */
    private synchronized void resetCounterIfNeeded() {
        long currentTime = System.currentTimeMillis();
        //if (currentTime - lastResetTime >= DAY_IN_MILLIS) {
        if (currentTime - lastResetTime >= HOURLY_IN_MILLIS) {
            int previousCount = dailyRequestCount.get();
            dailyRequestCount.set(0);
            processedCategoriesToday.clear();
            lastResetTime = currentTime;

            logger.info("Daily request counter reset. Previous day: {} requests", previousCount);
        }
    }
}
