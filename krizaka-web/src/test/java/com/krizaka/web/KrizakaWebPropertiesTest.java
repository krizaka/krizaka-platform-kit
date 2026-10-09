package com.krizaka.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class KrizakaWebPropertiesTest {

  @Test
  void defaults() {
    KrizakaWebProperties properties = new KrizakaWebProperties(null, null);

    assertThat(properties.problems().baseType()).hasToString("https://krizaka.com/problems/");
    assertThat(properties.cors().allowedOrigins()).isEmpty();
  }

  @Test
  void refusesABaseTypeThatWouldResolveCodesElsewhere() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new KrizakaWebProperties.Problems(URI.create("https://x.dev/problems")));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new KrizakaWebProperties.Problems(URI.create("/problems/")));
  }

  @Test
  void refusesAWildcardOrABlankOrigin() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new KrizakaWebProperties.Cors(List.of("*")));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new KrizakaWebProperties.Cors(List.of("https://*.krizaka.com")));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new KrizakaWebProperties.Cors(List.of(" ")));
  }

  @Test
  void copiesTheOrigins() {
    List<String> origins = new java.util.ArrayList<>(List.of("https://app.krizaka.com"));
    KrizakaWebProperties.Cors cors = new KrizakaWebProperties.Cors(origins);
    origins.add("https://evil.example");

    assertThat(cors.allowedOrigins()).containsExactly("https://app.krizaka.com");
  }
}
