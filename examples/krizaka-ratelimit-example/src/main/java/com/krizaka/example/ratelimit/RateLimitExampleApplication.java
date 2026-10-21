package com.krizaka.example.ratelimit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** A quotes API that limits each API key. */
@SpringBootApplication
public class RateLimitExampleApplication {

  /**
   * Starts the example.
   *
   * @param args the command line
   */
  public static void main(String[] args) {
    SpringApplication.run(RateLimitExampleApplication.class, args);
  }
}
