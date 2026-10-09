package com.krizaka.web.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class DomainExceptionTest {

  static final class Gone extends DomainException {
    private static final long serialVersionUID = 1L;

    Gone(HttpStatus status, String code) {
      super(status, code, "gone");
    }
  }

  @Test
  void theConcreteExceptionsCarryTheirStatusAndStableCode() {
    assertThat(new NotFoundException("x").status()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(new NotFoundException("x").code()).isEqualTo("not-found");
    assertThat(new ConflictException("x").status()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(new ConflictException("x").code()).isEqualTo("conflict");
    assertThat(new ForbiddenException("x").status()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(new ForbiddenException("x").code()).isEqualTo("forbidden");
    assertThat(new ValidationException("x").status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(new ValidationException("x").code()).isEqualTo("validation-failed");
  }

  @Test
  void aServiceMayMakeTheCodeMorePrecise() {
    ConflictException e = new ConflictException("auction-closed", "The auction is closed.");

    assertThat(e.code()).isEqualTo("auction-closed");
    assertThat(e.getMessage()).isEqualTo("The auction is closed.");
  }

  @Test
  void isAlwaysAClientError() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Gone(HttpStatus.INTERNAL_SERVER_ERROR, "gone"));
    assertThatIllegalArgumentException().isThrownBy(() -> new Gone(HttpStatus.OK, "gone"));
    assertThatIllegalArgumentException().isThrownBy(() -> new Gone(null, "gone"));
    assertThat(new Gone(HttpStatus.GONE, "gone").status()).isEqualTo(HttpStatus.GONE);
  }

  @Test
  void theCodeIsALowercaseSlug() {
    assertThatIllegalArgumentException().isThrownBy(() -> new NotFoundException("Not Found", "x"));
    assertThatIllegalArgumentException().isThrownBy(() -> new NotFoundException("not_found", "x"));
    assertThatIllegalArgumentException().isThrownBy(() -> new NotFoundException("-x", "x"));
    assertThatIllegalArgumentException().isThrownBy(() -> new NotFoundException("", "x"));
    assertThatIllegalArgumentException().isThrownBy(() -> new NotFoundException(null, "x"));
  }
}
