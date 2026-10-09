package com.krizaka.web.cors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.krizaka.web.SampleController;
import com.krizaka.web.correlation.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = SampleController.class,
    properties = "krizaka.web.cors.allowed-origins=https://app.krizaka.com")
class KrizakaCorsTest {

  @Autowired MockMvc mvc;

  @Test
  void answersAPreflightFromADeclaredOrigin() throws Exception {
    mvc.perform(
            options("/sample/items")
                .header(HttpHeaders.ORIGIN, "https://app.krizaka.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.krizaka.com"))
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
  }

  @Test
  void refusesAnyOtherOrigin() throws Exception {
    mvc.perform(
            options("/sample/items")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
  }

  @Test
  void exposesTheRequestIdToTheFrontEnd() throws Exception {
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/sample/request-id")
                .header(HttpHeaders.ORIGIN, "https://app.krizaka.com"))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, CorrelationIdFilter.HEADER));
  }
}
