package com.krizaka.example.webhooks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** A payments receiver that trusts only signed webhooks. */
@SpringBootApplication
public class WebhooksExampleApplication {

  /**
   * Starts the example.
   *
   * @param args the command line
   */
  public static void main(String[] args) {
    SpringApplication.run(WebhooksExampleApplication.class, args);
  }
}
