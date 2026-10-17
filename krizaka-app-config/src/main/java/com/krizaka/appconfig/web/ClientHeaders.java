package com.krizaka.appconfig.web;

/** The headers every Krizaka app sends, and the ones the backend answers with. */
public final class ClientHeaders {

  /** The app's version, e.g. {@code 1.4.0}. */
  public static final String VERSION = "X-Client-Version";

  /** The platform: {@code ios}, {@code android}, {@code macos}… */
  public static final String PLATFORM = "X-Client-Platform";

  /** The API contract the app speaks, an integer. Answered with the newest the backend speaks. */
  public static final String CONTRACT = "X-Contract-Version";

  /** The build number, informational. */
  public static final String BUILD = "X-Client-Build";

  /** RFC 9745: the contract the client speaks is deprecated. */
  public static final String DEPRECATION = "Deprecation";

  /** RFC 8594: when the deprecated contract stops being served. */
  public static final String SUNSET = "Sunset";

  private ClientHeaders() {}
}
