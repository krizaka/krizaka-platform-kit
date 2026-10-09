package com.krizaka.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.web.correlation.CorrelationIdFilter;
import com.krizaka.web.json.KrizakaJsonDefaults;
import com.krizaka.web.problem.ProblemDetailsAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

class KrizakaWebAutoConfigurationTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  KrizakaWebAutoConfiguration.class, WebMvcAutoConfiguration.class));

  @Test
  void contributesTheBaselineToAServletApplication() {
    runner.run(
        context ->
            assertThat(context)
                .hasSingleBean(ProblemDetailsAdvice.class)
                .hasSingleBean(ResponseEntityExceptionHandler.class)
                .hasBean("krizakaCorrelationIdFilter")
                .hasSingleBean(KrizakaJsonDefaults.class)
                .hasSingleBean(KrizakaWebProperties.class));
  }

  @Test
  void theCorrelationFilterRunsFirst() {
    runner.run(
        context ->
            assertThat(
                    context
                        .getBean("krizakaCorrelationIdFilter", FilterRegistrationBean.class)
                        .getOrder())
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE));
  }

  @Test
  void springBootsOwnProblemDetailsHandlerBacksOff() {
    runner
        .withPropertyValues("spring.mvc.problemdetails.enabled=true")
        .run(
            context ->
                assertThat(context.getBeansOfType(ResponseEntityExceptionHandler.class))
                    .containsOnlyKeys("krizakaProblemDetailsAdvice"));
  }

  @Test
  void opensNoCorsWithoutDeclaredOrigins() {
    runner.run(
        context ->
            assertThat(context)
                .doesNotHaveBean("corsConfigurationSource")
                .doesNotHaveBean("krizakaCorsMappings"));
  }

  @Test
  void appliesCorsToTheDeclaredOriginsWhicheverWayTheyAreWritten() {
    runner
        .withPropertyValues(
            "krizaka.web.cors.allowed-origins=https://app.krizaka.com,https://admin.krizaka.com")
        .run(
            context -> {
              assertThat(context).hasBean("corsConfigurationSource").hasBean("krizakaCorsMappings");
              CorsConfiguration cors =
                  context
                      .getBean("corsConfigurationSource", CorsConfigurationSource.class)
                      .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/items"));
              assertThat(cors.getAllowedOrigins())
                  .containsExactly("https://app.krizaka.com", "https://admin.krizaka.com");
              assertThat(cors.getExposedHeaders()).containsExactly(CorrelationIdFilter.HEADER);
            });
    runner
        .withPropertyValues("krizaka.web.cors.allowed-origins[0]=https://app.krizaka.com")
        .run(context -> assertThat(context).hasBean("corsConfigurationSource"));
  }

  @Test
  void refusesToStartWithAWildcardOrigin() {
    runner
        .withPropertyValues("krizaka.web.cors.allowed-origins=*")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void refusesToStartWithARelativeProblemBase() {
    runner
        .withPropertyValues("krizaka.web.problems.base-type=/problems/")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void backsOffForTheApplicationsOwnBeans() {
    ResponseEntityExceptionHandler own = new ResponseEntityExceptionHandler() {};
    FilterRegistrationBean<CorrelationIdFilter> ownFilter =
        new FilterRegistrationBean<>(new CorrelationIdFilter());
    KrizakaJsonDefaults ownJson = new KrizakaJsonDefaults();
    CorsConfigurationSource ownCors = request -> null;
    runner
        .withPropertyValues("krizaka.web.cors.allowed-origins=https://app.krizaka.com")
        .withBean("ownAdvice", ResponseEntityExceptionHandler.class, () -> own)
        .withBean("krizakaCorrelationIdFilter", FilterRegistrationBean.class, () -> ownFilter)
        .withBean("krizakaJsonDefaults", KrizakaJsonDefaults.class, () -> ownJson)
        .withBean("corsConfigurationSource", CorsConfigurationSource.class, () -> ownCors)
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(ProblemDetailsAdvice.class);
              assertThat(context.getBean(ResponseEntityExceptionHandler.class)).isSameAs(own);
              assertThat(context.getBean("krizakaCorrelationIdFilter")).isSameAs(ownFilter);
              assertThat(context.getBean(KrizakaJsonDefaults.class)).isSameAs(ownJson);
              assertThat(context.getBean("corsConfigurationSource")).isSameAs(ownCors);
            });
  }

  @Test
  void anApplicationWithoutSpringMvcStartsWithoutTheBaseline() {
    runner
        .withClassLoader(new FilteredClassLoader(DispatcherServlet.class))
        .run(
            context ->
                assertThat(context)
                    .hasNotFailed()
                    .doesNotHaveBean(ProblemDetailsAdvice.class)
                    .doesNotHaveBean("krizakaCorrelationIdFilter"));
  }

  @Test
  void anApplicationWithoutJacksonStartsWithoutTheJsonDefaults() {
    runner
        .withClassLoader(
            new FilteredClassLoader(
                org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer.class))
        .run(
            context ->
                assertThat(context)
                    .hasNotFailed()
                    .hasSingleBean(ProblemDetailsAdvice.class)
                    .doesNotHaveBean(KrizakaJsonDefaults.class));
  }

  @Test
  void aNonWebApplicationIsLeftAlone() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(KrizakaWebAutoConfiguration.class))
        .run(
            context ->
                assertThat(context)
                    .hasNotFailed()
                    .doesNotHaveBean(ProblemDetailsAdvice.class)
                    .doesNotHaveBean(KrizakaWebProperties.class));
  }
}
