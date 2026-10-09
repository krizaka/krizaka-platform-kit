package com.krizaka.web.problem;

import com.krizaka.web.KrizakaWebProperties;
import com.krizaka.web.correlation.CorrelationId;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * One error format for every service: RFC 9457 Problem Details, with a stable {@code code} and the
 * request's {@code requestId}.
 *
 * <ul>
 *   <li>a {@link DomainException} becomes its 4xx: {@code type} = base type + code, {@code title}
 *       from the code, {@code detail} = the message, plus {@code code} and {@code requestId};
 *   <li>a bean validation failure ({@code @Valid} body, constrained parameters) becomes {@code 422}
 *       with code {@code validation-failed} and an {@code errors} array of {@code {field, code,
 *       message}};
 *   <li>Spring MVC's own failures (unreadable body, method not allowed…) keep Spring's status and
 *       body, with {@code requestId} added;
 *   <li>an exception annotated with {@link ResponseStatus} keeps its status and reason;
 *   <li>Spring Security's {@code AccessDeniedException} and {@code AuthenticationException} are
 *       left to Spring Security, which answers {@code 403}/{@code 401} — catching them here would
 *       turn a refusal into a {@code 500};
 *   <li>anything else is a {@code 500} with <b>no</b> {@code detail}: the message is logged with
 *       the request id, never sent — an exception message is not written for the caller.
 * </ul>
 */
@RestControllerAdvice
public class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

  /** The code of a bean validation failure. */
  public static final String VALIDATION_FAILED = ValidationException.CODE;

  /** The code of an unexpected failure. */
  public static final String INTERNAL = "internal";

  private static final Logger log = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

  private static final List<String> LEFT_TO_SPRING_SECURITY =
      List.of(
          "org.springframework.security.access.AccessDeniedException",
          "org.springframework.security.core.AuthenticationException");

  private final URI baseType;

  /**
   * Creates the advice.
   *
   * @param properties where problem {@code type} URIs are resolved from
   */
  public ProblemDetailsAdvice(KrizakaWebProperties properties) {
    this.baseType = properties.problems().baseType();
  }

  /**
   * Answers a business failure with its status and code.
   *
   * @param e the failure the service raised
   * @return the problem, with {@code code} and {@code requestId}
   */
  @ExceptionHandler(DomainException.class)
  public ProblemDetail onDomain(DomainException e) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
    describe(problem, e.code());
    return problem;
  }

  /**
   * Answers what no other handler claimed: {@code 500}, without the message.
   *
   * @param e the failure
   * @return the problem, with {@code code} and {@code requestId} but no {@code detail}
   * @throws Exception Spring Security's exceptions, unchanged, so that its filters answer them
   */
  @ExceptionHandler(Exception.class)
  public ProblemDetail onUnexpected(Exception e) throws Exception {
    if (isLeftToSpringSecurity(e)) {
      throw e;
    }
    ResponseStatus declared =
        AnnotatedElementUtils.findMergedAnnotation(e.getClass(), ResponseStatus.class);
    if (declared != null) {
      ProblemDetail problem = ProblemDetail.forStatus(declared.code());
      if (StringUtils.hasText(declared.reason())) {
        problem.setDetail(declared.reason());
      }
      problem.setProperty("requestId", CorrelationId.current());
      return problem;
    }
    log.error("unhandled request failure [requestId={}]", CorrelationId.current(), e);
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    describe(problem, INTERNAL);
    problem.setTitle(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
    return problem;
  }

  /**
   * Answers an invalid {@code @Valid} argument with {@code 422} and one entry per violation.
   *
   * @param ex the binding failure
   * @param headers the headers Spring prepared
   * @param status Spring's status ({@code 400}), replaced by {@code 422}
   * @param request the current request
   * @return the {@code validation-failed} problem
   */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<Map<String, String>> errors = new ArrayList<>();
    ex.getBindingResult()
        .getAllErrors()
        .forEach(
            error ->
                errors.add(
                    violation(
                        error instanceof FieldError field ? field.getField() : null,
                        error.getCode(),
                        error.getDefaultMessage())));
    return createResponseEntity(validationProblem(errors), headers, unprocessable(), request);
  }

  /**
   * Answers constrained controller parameters ({@code @RequestParam @Min…}) the same way as an
   * invalid body: {@code 422} with {@code errors}.
   *
   * @param ex the method validation failure
   * @param headers the headers Spring prepared
   * @param status Spring's status, replaced by {@code 422}
   * @param request the current request
   * @return the {@code validation-failed} problem
   */
  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<Map<String, String>> errors = new ArrayList<>();
    ex.getParameterValidationResults()
        .forEach(
            result ->
                result
                    .getResolvableErrors()
                    .forEach(
                        error ->
                            errors.add(
                                violation(
                                    error instanceof FieldError field
                                        ? field.getField()
                                        : result.getMethodParameter().getParameterName(),
                                    lastCode(error),
                                    error.getDefaultMessage()))));
    return createResponseEntity(validationProblem(errors), headers, unprocessable(), request);
  }

  /**
   * Adds the {@code requestId} to every problem this advice answers, including Spring MVC's own.
   *
   * @param body the response body Spring built
   * @param headers the response headers
   * @param statusCode the response status
   * @param request the current request
   * @return the response, its problem carrying {@code requestId}
   */
  @Override
  protected ResponseEntity<Object> createResponseEntity(
      Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    if (body instanceof ProblemDetail problem
        && (problem.getProperties() == null || !problem.getProperties().containsKey("requestId"))) {
      problem.setProperty("requestId", CorrelationId.current());
    }
    return super.createResponseEntity(body, headers, statusCode, request);
  }

  private ProblemDetail validationProblem(List<Map<String, String>> errors) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(unprocessable(), "The request has invalid fields.");
    describe(problem, VALIDATION_FAILED);
    problem.setProperty("errors", errors);
    return problem;
  }

  private void describe(ProblemDetail problem, String code) {
    problem.setType(baseType.resolve(code));
    problem.setTitle(code.replace('-', ' '));
    problem.setProperty("code", code);
    problem.setProperty("requestId", CorrelationId.current());
  }

  private static Map<String, String> violation(String field, String code, String message) {
    Map<String, String> violation = new LinkedHashMap<>();
    if (field != null) {
      violation.put("field", field);
    }
    if (code != null) {
      violation.put("code", code);
    }
    if (message != null) {
      violation.put("message", message);
    }
    return violation;
  }

  private static String lastCode(MessageSourceResolvable error) {
    String[] codes = error.getCodes();
    return codes == null || codes.length == 0 ? null : codes[codes.length - 1];
  }

  private static HttpStatus unprocessable() {
    return HttpStatus.UNPROCESSABLE_CONTENT;
  }

  private static boolean isLeftToSpringSecurity(Throwable e) {
    for (Class<?> type = e.getClass(); type != null; type = type.getSuperclass()) {
      if (LEFT_TO_SPRING_SECURITY.contains(type.getName())) {
        return true;
      }
    }
    return false;
  }
}
