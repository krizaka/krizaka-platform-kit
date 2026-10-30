package com.krizaka.notifications.service.domain.exception;

/** No template exists for the requested key, in the requested locale nor in the default one. */
public class TemplateNotFoundException extends RuntimeException {

  public TemplateNotFoundException(String template, String locale) {
    super("No notification template '" + template + "' for locale '" + locale + "' or 'en'");
  }
}
