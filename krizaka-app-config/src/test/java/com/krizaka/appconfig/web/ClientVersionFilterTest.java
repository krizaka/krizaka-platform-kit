package com.krizaka.appconfig.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.krizaka.appconfig.model.AppConfigUpdate;
import com.krizaka.appconfig.model.ClientPlatform;
import com.krizaka.appconfig.store.AppConfigReader;
import com.krizaka.appconfig.store.InMemoryAppConfigStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/** What the filter leaves alone: it only ever refuses what it understands. */
class ClientVersionFilterTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    InMemoryAppConfigStore store = new InMemoryAppConfigStore(CLOCK);
    store.publish(
        new AppConfigUpdate(
                "2.0.0", "2.0.0", null, null, false, null, null, 2, 3, null, null, "min 2.0")
            .toConfig(ClientPlatform.IOS),
        "test",
        "fixture");
    AppConfigReader reader = new AppConfigReader(store, Duration.ZERO, CLOCK);
    ClientVersionFilter filter =
        new ClientVersionFilter(
            reader,
            JsonMapper.builder().build(),
            List.of("/api/**"),
            List.of("/api/v1/app-config", "/api/v1/admin/app-config/**"),
            "https://krizaka.com/problems/",
            CLOCK);
    mvc = MockMvcBuilders.standaloneSetup(new Things()).addFilters(filter).build();
  }

  @Test
  void aRequestWithoutAClientPlatformIsNotAnApp() throws Exception {
    mvc.perform(get("/api/v1/things").header(ClientHeaders.VERSION, "0.0.1"))
        .andExpect(status().isOk());
  }

  @Test
  void anUnknownPlatformOrAnUnreadableVersionIsNotRefused() throws Exception {
    mvc.perform(
            get("/api/v1/things")
                .header(ClientHeaders.PLATFORM, "playstation")
                .header(ClientHeaders.VERSION, "0.0.1"))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/things")
                .header(ClientHeaders.PLATFORM, "ios")
                .header(ClientHeaders.VERSION, "latest")
                .header(ClientHeaders.CONTRACT, "2"))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/things")
                .header(ClientHeaders.PLATFORM, "ios")
                .header(ClientHeaders.VERSION, "2.0.0")
                .header(ClientHeaders.CONTRACT, "abc"))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/things")
                .header(ClientHeaders.PLATFORM, "ios")
                .header(ClientHeaders.VERSION, "2.0.0")
                .header(ClientHeaders.CONTRACT, "0"))
        .andExpect(status().isOk());
  }

  @Test
  void aPreflightAndTheUnguardedPathsAreLeftAlone() throws Exception {
    mvc.perform(
            options("/api/v1/things")
                .header(ClientHeaders.PLATFORM, "ios")
                .header(ClientHeaders.VERSION, "0.0.1"))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/admin/app-config/ios")
                .header(ClientHeaders.PLATFORM, "ios")
                .header(ClientHeaders.VERSION, "0.0.1"))
        .andExpect(status().isOk());
    mvc.perform(
            get("/public/page")
                .header(ClientHeaders.PLATFORM, "ios")
                .header(ClientHeaders.VERSION, "0.0.1"))
        .andExpect(status().isOk());
  }

  @Test
  void theAnswerAlwaysSaysWhichContractTheBackendSpeaks() throws Exception {
    mvc.perform(
            get("/api/v1/things")
                .header(ClientHeaders.PLATFORM, "ios")
                .header(ClientHeaders.VERSION, "2.0.0")
                .header(ClientHeaders.CONTRACT, "3"))
        .andExpect(status().isOk())
        .andExpect(header().string(ClientHeaders.CONTRACT, "3"));
  }

  @Test
  void aContractBelowTheMinimumIsRefusedInTheLanguageAsked() throws Exception {
    String body =
        mvc.perform(
                get("/api/v1/things")
                    .header(ClientHeaders.PLATFORM, "ios")
                    .header(ClientHeaders.VERSION, "2.0.0")
                    .header(ClientHeaders.CONTRACT, "1")
                    .header("Accept-Language", "fr-CA,fr;q=0.9"))
            .andExpect(status().isUpgradeRequired())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).contains("contract-unsupported").contains("\"locale\":\"en\"");
  }

  /** An API endpoint and a public page. */
  @RestController
  static class Things {
    @RequestMapping({"/api/v1/things", "/api/v1/admin/app-config/ios", "/public/page"})
    String things() {
      return "[]";
    }
  }
}
