package com.krizaka.appconfig.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Allows a request whose authentication holds the declared authority (default {@code ROLE_ADMIN}).
 */
public final class SpringSecurityAdminGate implements AdminGate {

  private final String authority;

  /**
   * Creates the gate.
   *
   * @param authority the authority an admin holds
   */
  public SpringSecurityAdminGate(String authority) {
    this.authority = authority;
  }

  @Override
  public Decision check(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      return new Denied(401);
    }
    boolean allowed =
        authentication.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    return allowed ? new Allowed(authentication.getName()) : new Denied(403);
  }
}
