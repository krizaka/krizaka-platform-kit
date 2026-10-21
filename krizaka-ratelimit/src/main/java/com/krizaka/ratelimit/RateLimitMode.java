package com.krizaka.ratelimit;

/** How the limit is applied, so it can be rolled out before it refuses anyone. */
public enum RateLimitMode {
  /** Keeps nothing and limits nothing. */
  OFF,
  /** Counts and flags (a response header, the observation) but never refuses. */
  OBSERVE,
  /** Answers {@code 429} with {@code Retry-After} once a key's bucket is empty. */
  ENFORCE
}
