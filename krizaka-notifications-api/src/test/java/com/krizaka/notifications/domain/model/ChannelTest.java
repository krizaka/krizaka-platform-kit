package com.krizaka.notifications.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChannelTest {

  @Test
  void declaresTheThreeDeliveryMedia() {
    assertThat(Channel.values()).containsExactly(Channel.EMAIL, Channel.SMS, Channel.WEBHOOK);
  }
}
