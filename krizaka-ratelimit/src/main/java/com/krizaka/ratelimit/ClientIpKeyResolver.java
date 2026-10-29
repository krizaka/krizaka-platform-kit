package com.krizaka.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

/**
 * The default key: the address a request really comes from, safe against a forged {@code
 * X-Forwarded-For}.
 *
 * <p>A peer that is not a trusted proxy is the client itself and its headers are ignored. Behind a
 * trusted proxy (which <em>appends</em> its own peer to whatever the client wrote) the header is
 * {@code <anything the client wrote>, <hops we can trust>}: it is read from the right, skipping
 * trusted proxies, and the first address that is not one is the client. Only literal addresses
 * count — a hostname or garbage is never an identity.
 */
public final class ClientIpKeyResolver implements RateLimitKeyResolver {

  private final List<Network> trusted;
  private final String clientIpHeader;

  /**
   * A resolver.
   *
   * @param trustedProxies addresses or CIDR ranges of the proxies in front of the service
   * @param clientIpHeader a header the trusted proxy sets to the client's address, or blank
   */
  public ClientIpKeyResolver(List<String> trustedProxies, String clientIpHeader) {
    this.trusted = trustedProxies.stream().map(String::strip).map(Network::parse).toList();
    this.clientIpHeader = clientIpHeader == null ? "" : clientIpHeader.strip();
  }

  @Override
  public RateLimitKey resolve(HttpServletRequest request) {
    return RateLimitKey.of(clientAddress(request));
  }

  String clientAddress(HttpServletRequest request) {
    String peer = request.getRemoteAddr();
    if (!isTrusted(peer)) {
      return peer;
    }
    if (!clientIpHeader.isEmpty()) {
      String direct = literal(request.getHeader(clientIpHeader));
      if (direct != null) {
        return direct;
      }
    }
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded == null || forwarded.isBlank()) {
      return peer;
    }
    return forwardedClient(forwarded.split(","), peer);
  }

  private String forwardedClient(String[] hops, String peer) {
    for (int i = hops.length - 1; i >= 0; i--) {
      String hop = literal(hops[i]);
      if (hop == null) {
        return peer;
      }
      if (!isTrusted(hop)) {
        return hop;
      }
    }
    String first = literal(hops[0]);
    return first != null ? first : peer;
  }

  private boolean isTrusted(String address) {
    byte[] bytes = bytes(address);
    return bytes != null && trusted.stream().anyMatch(network -> network.contains(bytes));
  }

  private static String literal(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.strip().toLowerCase(Locale.ROOT);
    if (value.isEmpty() || value.length() > 45 || !value.matches("[0-9a-f:.]+")) {
      return null;
    }
    return value;
  }

  /** The bytes of a literal address, or {@code null}: no name is ever resolved. */
  private static byte[] bytes(String address) {
    if (literal(address) == null) {
      return null;
    }
    try {
      return InetAddress.getByName(address).getAddress();
    } catch (UnknownHostException notAnAddress) {
      return null;
    }
  }

  private record Network(byte[] address, int prefixBits) {

    static Network parse(String spec) {
      int slash = spec.indexOf('/');
      String host = slash < 0 ? spec : spec.substring(0, slash);
      byte[] address = bytes(host);
      if (address == null) {
        throw new IllegalArgumentException(
            "a trusted proxy is an address or a CIDR range: " + spec);
      }
      int bits = slash < 0 ? address.length * 8 : Integer.parseInt(spec.substring(slash + 1));
      if (bits < 0 || bits > address.length * 8) {
        throw new IllegalArgumentException("not a valid prefix length: " + spec);
      }
      return new Network(address, bits);
    }

    boolean contains(byte[] candidate) {
      if (candidate.length != address.length) {
        return false;
      }
      int whole = prefixBits / 8;
      for (int i = 0; i < whole; i++) {
        if (candidate[i] != address[i]) {
          return false;
        }
      }
      int rest = prefixBits % 8;
      if (rest == 0) {
        return true;
      }
      int mask = 0xFF << (8 - rest) & 0xFF;
      return (candidate[whole] & mask) == (address[whole] & mask);
    }
  }
}
