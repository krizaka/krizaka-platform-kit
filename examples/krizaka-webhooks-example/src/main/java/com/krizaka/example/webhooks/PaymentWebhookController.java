package com.krizaka.example.webhooks;

import com.krizaka.webhooks.WebhookVerifier;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /webhooks/payments}: the body is read as a {@code String} and verified before it is
 * trusted — a bad signature is a {@code 401} Problem Details raised by {@code verify}. A sender may
 * deliver one message twice, so the {@code webhook-id} is remembered (a table in a real service).
 */
@RestController
class PaymentWebhookController {

  private final WebhookVerifier verifier;
  private final Set<String> settled = ConcurrentHashMap.newKeySet();

  PaymentWebhookController(WebhookVerifier verifier) {
    this.verifier = verifier;
  }

  @PostMapping("/webhooks/payments")
  ResponseEntity<Void> payments(@RequestHeader HttpHeaders headers, @RequestBody String body) {
    String id = verifier.verify(headers::getFirst, body);
    settled.add(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/api/v1/settled")
  Set<String> settled() {
    return settled;
  }
}
