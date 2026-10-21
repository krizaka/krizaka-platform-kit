package com.krizaka.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpKeyResolverTest {

  private static MockHttpServletRequest request(String peer, String forwarded) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(peer);
    if (forwarded != null) {
      request.addHeader("X-Forwarded-For", forwarded);
    }
    return request;
  }

  private static String who(ClientIpKeyResolver resolver, MockHttpServletRequest request) {
    return resolver.resolve(request).id();
  }

  @Test
  void aPeerThatIsNotATrustedProxyIsTheClientAndItsHeadersAreIgnored() {
    var resolver = new ClientIpKeyResolver(List.of("10.0.0.0/8"), "");

    assertThat(who(resolver, request("203.0.113.9", "1.2.3.4"))).isEqualTo("203.0.113.9");
  }

  @Test
  void withoutTrustedProxiesTheDirectPeerIsAlwaysTheClient() {
    var resolver = new ClientIpKeyResolver(List.of(), "");

    assertThat(who(resolver, request("10.0.0.5", "1.2.3.4"))).isEqualTo("10.0.0.5");
  }

  @Test
  void behindATrustedProxyTheForwardedChainIsReadFromTheRight() {
    var resolver = new ClientIpKeyResolver(List.of("10.0.0.0/8"), "");

    assertThat(who(resolver, request("10.0.0.5", "6.6.6.6, 203.0.113.9, 10.0.0.7")))
        .as("what the client wrote on the left is never believed")
        .isEqualTo("203.0.113.9");
  }

  @Test
  void aForgedLeftmostHopCannotChooseTheKey() {
    var resolver = new ClientIpKeyResolver(List.of("10.0.0.5"), "");

    assertThat(who(resolver, request("10.0.0.5", "9.9.9.9, 203.0.113.9"))).isEqualTo("203.0.113.9");
  }

  @Test
  void aHeaderTheProxySetsIsReadFirst() {
    var resolver = new ClientIpKeyResolver(List.of("10.0.0.0/8"), "CF-Connecting-IP");
    var request = request("10.0.0.5", "1.1.1.1");
    request.addHeader("CF-Connecting-IP", "203.0.113.9");

    assertThat(who(resolver, request)).isEqualTo("203.0.113.9");
  }

  @Test
  void garbageIsNeverAnIdentity() {
    var resolver = new ClientIpKeyResolver(List.of("10.0.0.0/8"), "");

    assertThat(who(resolver, request("10.0.0.5", "evil.example.com"))).isEqualTo("10.0.0.5");
    assertThat(who(resolver, request("10.0.0.5", "203.0.113.9, not-an-ip"))).isEqualTo("10.0.0.5");
  }

  @Test
  void everyHopTrustedMeansAnInternalCallerTheFirstHop() {
    var resolver = new ClientIpKeyResolver(List.of("10.0.0.0/8"), "");

    assertThat(who(resolver, request("10.0.0.5", "10.1.1.1, 10.0.0.7"))).isEqualTo("10.1.1.1");
  }

  @Test
  void matchesCidrRangesAndIpv6() {
    var resolver = new ClientIpKeyResolver(List.of("192.168.4.0/22", "fd00::/8"), "");

    assertThat(who(resolver, request("192.168.7.1", "203.0.113.9"))).isEqualTo("203.0.113.9");
    assertThat(who(resolver, request("192.168.8.1", "203.0.113.9"))).isEqualTo("192.168.8.1");
    assertThat(who(resolver, request("fd12::1", "203.0.113.9"))).isEqualTo("203.0.113.9");
  }

  @Test
  void aBadProxyListFailsAtStartup() {
    assertThatThrownBy(() -> new ClientIpKeyResolver(List.of("not-an-ip"), ""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ClientIpKeyResolver(List.of("10.0.0.0/40"), ""))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
