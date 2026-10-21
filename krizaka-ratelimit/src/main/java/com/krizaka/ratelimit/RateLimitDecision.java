package com.krizaka.ratelimit;

/**
 * The verdict on one call.
 *
 * @param allowed whether the call may proceed (always {@code true} outside enforce mode)
 * @param overLimit whether the bucket was empty: the call is refused in enforce mode
 * @param limit the requests per minute applied
 * @param remaining whole calls left in the bucket
 * @param retryAfterSeconds when {@code overLimit}, seconds until a call can succeed (at least 1)
 */
public record RateLimitDecision(
    boolean allowed, boolean overLimit, int limit, int remaining, long retryAfterSeconds) {}
