package com.krizaka.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

class JdbcOutboxStoreTest {

  private final JdbcOutboxStore store =
      new JdbcOutboxStore(
          mock(JdbcTemplate.class), JsonMapper.shared(), "krizaka_outbox", Duration.ofMinutes(15));

  @Test
  void theBackoffDoublesAndIsCapped() {
    assertThat(store.backoffSeconds(0)).isEqualTo(1);
    assertThat(store.backoffSeconds(1)).isEqualTo(2);
    assertThat(store.backoffSeconds(5)).isEqualTo(32);
    assertThat(store.backoffSeconds(20)).isEqualTo(900);
    assertThat(store.backoffSeconds(Integer.MAX_VALUE)).isEqualTo(900);
  }

  @Test
  void itLeases() {
    assertThat(store.leasesRows()).isTrue();
  }

  @Test
  void refusesATableNameThatIsNotOne() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new JdbcOutboxStore(
                    mock(JdbcTemplate.class),
                    JsonMapper.shared(),
                    "outbox; DROP TABLE x",
                    Duration.ofMinutes(1)));
  }
}
