package com.krizaka.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** The example service: everything it does comes from the starters and application.yml. */
@SpringBootApplication
public class ExampleApplication {

  /** Spring instantiates it. */
  public ExampleApplication() {}

  /**
   * Starts the service.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(ExampleApplication.class, args);
  }
}
