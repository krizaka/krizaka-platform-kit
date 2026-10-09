package com.krizaka.web.correlation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.krizaka.web.SampleController;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(SampleController.class)
class CorrelationIdFilterTest {

  @Autowired MockMvc mvc;

  @Test
  void keepsTheCallersIdAndPutsItInTheMdc() throws Exception {
    mvc.perform(get("/sample/request-id").header(CorrelationIdFilter.HEADER, "edge-7f3a"))
        .andExpect(status().isOk())
        .andExpect(header().string(CorrelationIdFilter.HEADER, "edge-7f3a"))
        .andExpect(jsonPath("$.requestId").value("edge-7f3a"));
  }

  @Test
  void createsAnIdWhenTheCallerSentNone() throws Exception {
    MvcResult result =
        mvc.perform(get("/sample/request-id")).andExpect(status().isOk()).andReturn();

    String id = result.getResponse().getHeader(CorrelationIdFilter.HEADER);
    assertThat(UUID.fromString(id)).isNotNull();
    assertThat(result.getResponse().getContentAsString()).contains(id);
  }

  @Test
  void replacesAnIdThatCouldForgeALogLine() throws Exception {
    MvcResult result =
        mvc.perform(
                get("/sample/request-id")
                    .header(CorrelationIdFilter.HEADER, "abc\" level=ERROR msg=\"forged"))
            .andReturn();

    assertThat(UUID.fromString(result.getResponse().getHeader(CorrelationIdFilter.HEADER)))
        .isNotNull();
  }

  @Test
  void replacesAnIdThatIsTooLong() throws Exception {
    MvcResult result =
        mvc.perform(get("/sample/request-id").header(CorrelationIdFilter.HEADER, "a".repeat(129)))
            .andReturn();

    assertThat(result.getResponse().getHeader(CorrelationIdFilter.HEADER)).hasSize(36);
  }

  @Test
  void leavesTheMdcAsItFoundIt() throws Exception {
    mvc.perform(get("/sample/request-id").header(CorrelationIdFilter.HEADER, "edge-1"));

    assertThat(CorrelationId.current()).isNull();
  }
}
