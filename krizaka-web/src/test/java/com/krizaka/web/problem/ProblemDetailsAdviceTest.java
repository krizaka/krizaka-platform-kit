package com.krizaka.web.problem;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.krizaka.web.SampleController;
import com.krizaka.web.correlation.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SampleController.class)
class ProblemDetailsAdviceTest {

  @Autowired MockMvc mvc;

  @Test
  void aDomainExceptionIsItsStatusWithAStableCode() throws Exception {
    mvc.perform(get("/sample/items/42").header(CorrelationIdFilter.HEADER, "req-1"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("https://krizaka.com/problems/item-not-found"))
        .andExpect(jsonPath("$.title").value("item not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").value("No item 42."))
        .andExpect(jsonPath("$.code").value("item-not-found"))
        .andExpect(jsonPath("$.requestId").value("req-1"));
  }

  @Test
  void anInvalidBodyIs422WithOneErrorPerViolation() throws Exception {
    mvc.perform(
            post("/sample/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"\", \"quantity\": 0}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value("https://krizaka.com/problems/validation-failed"))
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.requestId").isNotEmpty())
        .andExpect(jsonPath("$.errors", hasSize(2)))
        .andExpect(jsonPath("$.errors[?(@.field == 'name')].code").value("NotBlank"))
        .andExpect(jsonPath("$.errors[?(@.field == 'quantity')].code").value("Positive"));
  }

  @Test
  void aConstrainedParameterIs422Too() throws Exception {
    mvc.perform(get("/sample/search").param("limit", "500"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.errors[0].field").value("limit"))
        .andExpect(jsonPath("$.errors[0].code").value("Max"));
  }

  @Test
  void aValidBodyReachesTheHandler() throws Exception {
    mvc.perform(
            post("/sample/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"lamp\", \"quantity\": 1, \"colour\": \"red\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("conflict"));
  }

  @Test
  void anUnexpectedFailureIs500WithoutItsMessage() throws Exception {
    mvc.perform(get("/sample/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.type").value("https://krizaka.com/problems/internal"))
        .andExpect(jsonPath("$.code").value("internal"))
        .andExpect(jsonPath("$.requestId").isNotEmpty())
        .andExpect(jsonPath("$.detail").doesNotExist())
        .andExpect(content().string(not(containsString("hunter2"))));
  }

  @Test
  void springMvcsOwnFailuresCarryTheRequestId() throws Exception {
    mvc.perform(put("/sample/items").header(CorrelationIdFilter.HEADER, "req-405"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.status").value(405))
        .andExpect(jsonPath("$.requestId").value("req-405"));
  }

  @Test
  void anExceptionThatDeclaresItsStatusKeepsIt() throws Exception {
    mvc.perform(get("/sample/throttled"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.detail").value("slow down"))
        .andExpect(header().exists(CorrelationIdFilter.HEADER));
  }

  @Test
  void springSecuritysRefusalsAreLeftToSpringSecurity() {
    assertThatThrownBy(() -> mvc.perform(get("/sample/denied")))
        .hasRootCauseInstanceOf(AccessDeniedException.class);
  }
}
