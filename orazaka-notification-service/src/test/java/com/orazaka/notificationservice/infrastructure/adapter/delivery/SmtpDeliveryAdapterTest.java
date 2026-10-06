package com.orazaka.notificationservice.infrastructure.adapter.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.orazaka.notification.domain.model.Channel;
import com.orazaka.notificationservice.domain.exception.DeliveryException;
import com.orazaka.notificationservice.domain.model.Notification;
import com.orazaka.notificationservice.infrastructure.config.NotificationProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpDeliveryAdapterTest {

  private final JavaMailSender mailSender = mock(JavaMailSender.class);
  private final SmtpDeliveryAdapter adapter =
      new SmtpDeliveryAdapter(
          mailSender,
          new NotificationProperties(
              "Orazaka <no-reply@test>",
              null,
              new NotificationProperties.Links("http://x/v?t={token}", "http://x/r?t={token}"),
              null,
              null));

  private static final Notification MAIL =
      new Notification(Channel.EMAIL, "ada@example.com", "welcome", "Hello", "Body");

  @Test
  void sendsAPlainTextMail() {
    adapter.deliver(MAIL);

    ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(sent.capture());
    assertThat(sent.getValue().getFrom()).isEqualTo("Orazaka <no-reply@test>");
    assertThat(sent.getValue().getTo()).containsExactly("ada@example.com");
    assertThat(sent.getValue().getSubject()).isEqualTo("Hello");
    assertThat(sent.getValue().getText()).isEqualTo("Body");
    assertThat(adapter.channel()).isEqualTo(Channel.EMAIL);
    assertThat(adapter.available()).isTrue();
  }

  @Test
  void translatesAnSmtpFailure() {
    doThrow(new MailSendException("down")).when(mailSender).send(any(SimpleMailMessage.class));

    assertThatThrownBy(() -> adapter.deliver(MAIL)).isInstanceOf(DeliveryException.class);
  }
}
