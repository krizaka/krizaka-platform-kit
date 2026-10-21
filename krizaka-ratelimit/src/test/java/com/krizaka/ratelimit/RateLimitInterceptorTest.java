package com.krizaka.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.krizaka.web.KrizakaWebProperties;
import com.krizaka.web.problem.ProblemDetailsAdvice;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class RateLimitInterceptorTest {

  @RestController
  static class Hello {
    @GetMapping("/hello")
    String hello() {
      return "hi";
    }
  }

  private final RecordedObservations observations = new RecordedObservations();

  private MockMvc mvc(RateLimitMode mode, int limit) {
    var interceptor =
        new RateLimitInterceptor(
            new RateLimiter(mode, limit, 100, Clock.systemUTC()),
            request -> RateLimitKey.of(request.getHeader("X-Caller")),
            observations.registry());
    return MockMvcBuilders.standaloneSetup(new Hello())
        .setControllerAdvice(new ProblemDetailsAdvice(properties()))
        .addInterceptors(interceptor)
        .build();
  }

  private static KrizakaWebProperties properties() {
    return new KrizakaWebProperties(null, null);
  }

  @Test
  void aCallWithinTheLimitCarriesTheHeaders() throws Exception {
    mvc(RateLimitMode.ENFORCE, 3)
        .perform(get("/hello").header("X-Caller", "a"))
        .andExpect(status().isOk())
        .andExpect(header().string("RateLimit-Limit", "3"))
        .andExpect(header().string("RateLimit-Remaining", "2"));
    assertThat(observations.named(RateLimitInterceptor.OBSERVATION).getFirst().low())
        .containsEntry("outcome", "allowed");
  }

  @Test
  void theCallOverTheLimitIsA429ProblemWithRetryAfter() throws Exception {
    MockMvc mvc = mvc(RateLimitMode.ENFORCE, 1);
    mvc.perform(get("/hello").header("X-Caller", "a")).andExpect(status().isOk());

    mvc.perform(get("/hello").header("X-Caller", "a"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "60"))
        .andExpect(header().string("RateLimit-Remaining", "0"))
        .andExpect(jsonPath("$.code").value("rate-limit-exceeded"))
        .andExpect(jsonPath("$.type").value("https://krizaka.com/problems/rate-limit-exceeded"))
        .andExpect(
            jsonPath("$.detail").value("Too many requests: at most 1 a minute. Retry in 60 s."));
    assertThat(observations.named(RateLimitInterceptor.OBSERVATION).get(1).low())
        .containsEntry("outcome", "limited");
  }

  @Test
  void anotherKeyIsNotAffected() throws Exception {
    MockMvc mvc = mvc(RateLimitMode.ENFORCE, 1);
    mvc.perform(get("/hello").header("X-Caller", "a"));

    mvc.perform(get("/hello").header("X-Caller", "b")).andExpect(status().isOk());
  }

  @Test
  void observeNeverRefusesButSaysSo() throws Exception {
    MockMvc mvc = mvc(RateLimitMode.OBSERVE, 1);
    mvc.perform(get("/hello").header("X-Caller", "a"));

    mvc.perform(get("/hello").header("X-Caller", "a")).andExpect(status().isOk());
    assertThat(observations.named(RateLimitInterceptor.OBSERVATION).get(1).low())
        .containsEntry("outcome", "observed");
  }

  @Test
  void offLetsEverythingThroughWithoutObserving() throws Exception {
    MockMvc mvc = mvc(RateLimitMode.OFF, 1);

    mvc.perform(get("/hello").header("X-Caller", "a")).andExpect(status().isOk());
    mvc.perform(get("/hello").header("X-Caller", "a")).andExpect(status().isOk());
    assertThat(observations.named(RateLimitInterceptor.OBSERVATION)).isEmpty();
  }

  @Test
  void aRequestWithoutAKeyIsNotLimited() throws Exception {
    var interceptor =
        new RateLimitInterceptor(
            new RateLimiter(RateLimitMode.ENFORCE, 1, 100, Clock.systemUTC()),
            request -> null,
            observations.registry());
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new Hello()).addInterceptors(interceptor).build();

    mvc.perform(get("/hello")).andExpect(status().isOk());
    mvc.perform(get("/hello")).andExpect(status().isOk());
  }
}
