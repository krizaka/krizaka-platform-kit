package com.krizaka.ratelimit;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The limit per key ({@code krizaka.ratelimit}).
 *
 * @param mode How the limit is applied: 'enforce' answers 429 once a key's bucket is empty,
 *     'observe' counts and flags but never refuses (roll the limit out with it), 'off' keeps
 *     nothing.
 * @param requestsPerMinute Requests per minute a key may make, for a key that brings no limit of
 *     its own. A bucket holds one minute's worth, so a client may burst up to it.
 * @param maxKeys How many keys are tracked per instance before idle ones are forgotten; a busy key
 *     is never forgotten.
 * @param trustedProxies Addresses or CIDR ranges of the proxies in front of the service. Only
 *     behind one is the forwarded address of a request trusted as its client; empty, the direct
 *     peer is always the client.
 * @param clientIpHeader Header a trusted proxy sets to the client's address, read before
 *     X-Forwarded-For; blank for none.
 * @param includePaths Ant patterns of the paths that are limited.
 * @param excludePaths Ant patterns of the paths that never are (health checks, probes).
 */
@ConfigurationProperties(prefix = "krizaka.ratelimit")
public record KrizakaRateLimitProperties(
    @DefaultValue("enforce") RateLimitMode mode,
    @DefaultValue("600") int requestsPerMinute,
    @DefaultValue("10000") int maxKeys,
    List<String> trustedProxies,
    @DefaultValue("") String clientIpHeader,
    @DefaultValue("/**") List<String> includePaths,
    @DefaultValue("/actuator/**") List<String> excludePaths) {

  /** Applies the defaults. */
  public KrizakaRateLimitProperties {
    mode = mode == null ? RateLimitMode.ENFORCE : mode;
    trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
    includePaths = includePaths == null ? List.of("/**") : List.copyOf(includePaths);
    excludePaths = excludePaths == null ? List.of("/actuator/**") : List.copyOf(excludePaths);
  }
}
