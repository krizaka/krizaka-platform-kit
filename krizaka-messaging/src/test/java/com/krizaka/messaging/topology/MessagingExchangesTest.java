package com.krizaka.messaging.topology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MessagingExchangesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(MessagingExchangesAutoConfiguration.class));

  @Test
  void aStandaloneBlockRunsOnItsOwnExchanges() {
    runner.run(
        context -> {
          MessagingExchanges exchanges = context.getBean(MessagingExchanges.class);
          assertThat(exchanges.events()).isEqualTo("krizaka.events");
          assertThat(exchanges.deadLetter()).isEqualTo("krizaka.dlx");
        });
  }

  @Test
  void theHostingApplicationNamesItsBus() {
    runner
        .withPropertyValues(
            "krizaka.messaging.exchanges.events=platform.events",
            "krizaka.messaging.exchanges.dead-letter=platform.dlx")
        .run(
            context ->
                assertThat(context.getBean(MessagingExchanges.class))
                    .isEqualTo(new MessagingExchanges("platform.events", "platform.dlx")));
  }

  @Test
  void eventsAndDeadLettersCannotShareAnExchange() {
    assertThatIllegalArgumentException().isThrownBy(() -> new MessagingExchanges("x", "x"));
  }
}
