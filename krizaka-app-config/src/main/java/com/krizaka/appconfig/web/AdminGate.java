package com.krizaka.appconfig.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Decides who may use the admin API. The kit never decides an identity itself: the service's
 * security chain authenticates, and the gate reads the result. With nothing to read it refuses (the
 * admin API fails closed).
 */
@FunctionalInterface
public interface AdminGate {

  /** The decision for one request. */
  sealed interface Decision permits Denied, Allowed {}

  /**
   * Refused.
   *
   * @param status {@code 401} when nobody is authenticated, {@code 403} when someone is but may not
   */
  record Denied(int status) implements Decision {}

  /**
   * Allowed.
   *
   * @param actor who is acting, for the audit trail
   */
  record Allowed(String actor) implements Decision {}

  /**
   * Checks a request.
   *
   * @param request the request
   * @return allowed with the actor's name, or denied
   */
  Decision check(HttpServletRequest request);
}
