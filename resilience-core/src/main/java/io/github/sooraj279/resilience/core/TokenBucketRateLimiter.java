package io.github.sooraj279.resilience.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class TokenBucketRateLimiter implements RateLimiter {

    private final long capacity; //Number of tokens one can have at max
    private final double refillTokensPerNano; //token to be refilled per nanosecond
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>(); //To keep the hashmap thread safe, so multiple requests can safely find/create buckets.

    public TokenBucketRateLimiter(long capacity, long refillTokens, long refillPeriod, TimeUnit refillUnit) {
        this.capacity = capacity;
        long refillPeriodNanos = refillUnit.toNanos(refillPeriod);
        this.refillTokensPerNano = (double) refillTokens / refillPeriodNanos;
    }

    /* when we call TokenBucketRateLimiter(10,10,1,TimeUnit.SECONDS)
    * We are saying max capacity = 10;
    * fill 10 token to the bucket in 1 Second
    * filling is not done in second but in nanosecond instead in decimal values but in the end we get 10 token in 1 second
    * */

    @Override
    public RateLimitResult tryAcquire(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity));
        synchronized (bucket){ //Uses synchronized (bucket), so two requests for the same user cannot both steal the same token at the same time.
            refill(bucket);
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return RateLimitResult.allow((long) bucket.tokens);
            }
            double deficit = 1.0 - bucket.tokens;
            long retryAfterMillis = (long) Math.ceil(deficit/refillTokensPerNano/1_000_000.0);
            return RateLimitResult.reject(retryAfterMillis);
        }
    }

    private void refill(Bucket bucket){
        long now = System.nanoTime();
        long elapsedNanos = now - bucket.lastRefillNanos;
        if(elapsedNanos > 0){
            double tokenToAdd = elapsedNanos * refillTokensPerNano;
            bucket.tokens = Math.min(capacity, bucket.tokens + tokenToAdd);
            bucket.lastRefillNanos = now;
        }
    }

    private static final class Bucket{
        double tokens;
        long lastRefillNanos;

        Bucket(long capacity){
            this.tokens = capacity; //start full
            this.lastRefillNanos = System.nanoTime(); //right timer for measuring elapsed time.
        }
    }
}
