package com.krizaka.appconfig.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.krizaka.appconfig.store.AppConfigReader;
import com.krizaka.appconfig.store.InMemoryAppConfigStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminApiTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);
  private static final String BODY =
      """
      {"minSupportedVersion":"1.2.0","latestVersion":"1.4.0","updateOverride":null,
       "storeUrl":"https://apps.apple.com/app/id1","maintenanceOn":false,
       "maintenanceMessage":{},"message":{"en":"Update","fr":"Mettez à jour"},
       "contractMin":1,"contractMax":2,"contractSunset":"2026-12-31","flags":{"voice":true},
       "reason":"ship 1.4"}
      """;

  private final AtomicReference<AdminGate.Decision> decision = new AtomicReference<>();
  private MockMvc mvc;
  private AppConfigReader reader;

  @BeforeEach
  void setUp() {
    reader = new AppConfigReader(new InMemoryAppConfigStore(CLOCK), Duration.ofMinutes(5), CLOCK);
    decision.set(new AdminGate.Allowed("ana"));
    mvc =
        MockMvcBuilders.standaloneSetup(
                new AppConfigAdminController(reader, request -> decision.get()),
                new AppConfigController(
                    reader,
                    tools.jackson.databind.json.JsonMapper.builder().build(),
                    Duration.ofSeconds(60)))
            .build();
  }

  @Test
  void anAdminPublishesAndThePublicEndpointSeesItAtOnce() throws Exception {
    mvc.perform(
            put("/api/v1/admin/app-config/ios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revision").value(1))
        .andExpect(jsonPath("$.updatedBy").value("ana"));
    mvc.perform(get("/api/v1/app-config").param("platform", "ios").param("version", "1.0.0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.updateMode").value("force"))
        .andExpect(jsonPath("$.minSupportedVersion").value("1.2.0"));
  }

  @Test
  void everyPublicationIsInTheAuditTrailWithItsReason() throws Exception {
    mvc.perform(
            put("/api/v1/admin/app-config/ios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/admin/app-config/ios/history"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].reason").value("ship 1.4"))
        .andExpect(jsonPath("$[0].changedBy").value("ana"));
  }

  @Test
  void rollbackRestoresAnEarlierRevisionAsANewOne() throws Exception {
    mvc.perform(
        put("/api/v1/admin/app-config/ios").contentType(MediaType.APPLICATION_JSON).content(BODY));
    mvc.perform(
        put("/api/v1/admin/app-config/ios")
            .contentType(MediaType.APPLICATION_JSON)
            .content(BODY.replace("1.2.0", "1.3.0").replace("ship 1.4", "raise min")));
    mvc.perform(
            post("/api/v1/admin/app-config/ios/rollback/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"1.3 min was too eager\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revision").value(3))
        .andExpect(jsonPath("$.minSupportedVersion").value("1.2.0"));
    mvc.perform(
            post("/api/v1/admin/app-config/ios/rollback/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"x\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void aBrokenBoundIsA400WithTheReasonAndNothingIsPublished() throws Exception {
    mvc.perform(
            put("/api/v1/admin/app-config/ios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    BODY.replace(
                        "\"minSupportedVersion\":\"1.2.0\"", "\"minSupportedVersion\":\"9.0.0\"")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-app-config"));
    mvc.perform(get("/api/v1/admin/app-config/ios")).andExpect(jsonPath("$.revision").value(1));
  }

  @Test
  void aMissingReasonIsA400() throws Exception {
    mvc.perform(
            put("/api/v1/admin/app-config/ios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("ship 1.4", " ")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anUnknownPlatformIsA404() throws Exception {
    mvc.perform(get("/api/v1/admin/app-config/playstation")).andExpect(status().isNotFound());
  }

  @Test
  void failsClosed() throws Exception {
    decision.set(new AdminGate.Denied(401));
    mvc.perform(get("/api/v1/admin/app-config")).andExpect(status().isUnauthorized());
    decision.set(new AdminGate.Denied(403));
    mvc.perform(
            put("/api/v1/admin/app-config/ios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/app-config/ios/history")).andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/admin/app-config/ios/rollback/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void theEndpointValidatesItsInputs() throws Exception {
    mvc.perform(get("/api/v1/app-config")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/app-config").param("platform", "ios").param("version", "soon"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-version"));
  }

  @Test
  void theEndpointSupportsConditionalGets() throws Exception {
    String etag =
        mvc.perform(get("/api/v1/app-config").param("platform", "ios"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader("ETag");
    mvc.perform(get("/api/v1/app-config").param("platform", "ios").header("If-None-Match", etag))
        .andExpect(status().isNotModified());
  }
}
