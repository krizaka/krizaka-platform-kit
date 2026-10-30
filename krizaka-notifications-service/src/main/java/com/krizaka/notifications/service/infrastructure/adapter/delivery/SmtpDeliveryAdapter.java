package com.krizaka.notifications.service.infrastructure.adapter.delivery;

import com.krizaka.notifications.domain.model.Channel;
import com.krizaka.notifications.service.domain.exception.DeliveryException;
import com.krizaka.notifications.service.domain.model.Notification;
import com.krizaka.notifications.service.domain.port.DeliveryClient;
import com.krizaka.notifications.service.infrastructure.config.NotificationProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * {@link Channel#EMAIL} over SMTP. Locally the SMTP server is the Mailpit container of the
 * workspace's docker-compose ({@code localhost:1025}, inbox on {@code :8025}); in any other
 * environment it is whatever {@code spring.mail.*} points at.
 */
@Component
class SmtpDeliveryAdapter implements DeliveryClient {

  private final JavaMailSender mailSender;
  private final String fromAddress;

  SmtpDeliveryAdapter(JavaMailSender mailSender, NotificationProperties properties) {
    this.mailSender = mailSender;
    this.fromAddress = properties.fromAddress();
  }

  @Override
  public Channel channel() {
    return Channel.EMAIL;
  }

  @Override
  public boolean available() {
    return true;
  }

  @Override
  public void deliver(Notification notification) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(fromAddress);
    message.setTo(notification.recipient());
    message.setSubject(notification.subject());
    message.setText(notification.body());
    try {
      mailSender.send(message);
    } catch (MailException refused) {
      throw new DeliveryException("SMTP delivery failed", refused);
    }
  }
}
