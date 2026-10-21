package com.krizaka.example.idempotency;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** An order API whose creation is safe to retry. */
@SpringBootApplication
public class IdempotencyExampleApplication {

  /**
   * Starts the example.
   *
   * @param args the command line
   */
  public static void main(String[] args) {
    SpringApplication.run(IdempotencyExampleApplication.class, args);
  }
}
