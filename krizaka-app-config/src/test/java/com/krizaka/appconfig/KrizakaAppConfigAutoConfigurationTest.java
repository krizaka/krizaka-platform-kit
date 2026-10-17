package com.krizaka.appconfig;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.appconfig.model.AppConfigPayload;
import com.krizaka.appconfig.store.AppConfigReader;
import com.krizaka.appconfig.store.AppConfigStore;
import com.krizaka.appconfig.store.InMemoryAppConfigStore;
import com.krizaka.appconfig.web.AdminGate;
import com.krizaka.appconfig.web.AppConfigAdminController;
import com.krizaka.appconfig.web.AppConfigController;
import com.krizaka.appconfig.web.ClientVersionFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.DispatcherServlet;

class KrizakaAppConfigAutoConfigurationTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  JacksonAutoConfiguration.class, KrizakaAppConfigAutoConfiguration.class))
          .withPropertyValues("krizaka.app-config.store=memory");

  @Test
  void contributesTheEndpointAndTheFilter() {
    runner.run(
        context -> {
          assertThat(context)
              .hasSingleBean(AppConfigController.class)
              .hasSingleBean(AppConfigReader.class)
              .hasSingleBean(AppConfigStore.class)
              .hasBean("krizakaClientVersionFilter");
          assertThat(context.getBean(AppConfigStore.class))
              .isInstanceOf(InMemoryAppConfigStore.class);
          assertThat(
                  context
                      .getBean("krizakaClientVersionFilter", FilterRegistrationBean.class)
                      .getOrder())
              .isLessThan(Ordered.LOWEST_PRECEDENCE)
              .isEqualTo(Ordered.HIGHEST_PRECEDENCE + 10);
        });
  }

  @Test
  void theAdminApiIsOffUnlessDeclared() {
    runner.run(context -> assertThat(context).doesNotHaveBean(AppConfigAdminController.class));
  }

  @Test
  void theAdminApiFailsClosedWithoutSpringSecurityAuthentication() {
    runner
        .withPropertyValues("krizaka.app-config.admin.enabled=true")
        .run(
            context -> {
              assertThat(context)
                  .hasSingleBean(AppConfigAdminController.class)
                  .hasSingleBean(AdminGate.class);
              assertThat(context.getBean(AdminGate.class))
                  .isInstanceOf(com.krizaka.appconfig.web.SpringSecurityAdminGate.class);
            });
  }

  @Test
  void theAdminApiIsClosedWhenSpringSecurityIsAbsent() {
    runner
        .withClassLoader(
            new FilteredClassLoader(org.springframework.security.core.Authentication.class))
        .withPropertyValues("krizaka.app-config.admin.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(AdminGate.class);
              assertThat(context.getBean(AdminGate.class).check(null))
                  .isEqualTo(new AdminGate.Denied(401));
            });
  }

  @Test
  void theGuardCanBeSwitchedOffAlone() {
    runner
        .withPropertyValues("krizaka.app-config.guard.enabled=false")
        .run(
            context ->
                assertThat(context)
                    .hasSingleBean(AppConfigController.class)
                    .doesNotHaveBean("krizakaClientVersionFilter"));
  }

  @Test
  void canBeDisabledAsAWhole() {
    runner
        .withPropertyValues("krizaka.app-config.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(AppConfigController.class));
  }

  @Test
  void theJdbcStoreWithoutADatabaseFailsAtStartupWithTheReason() {
    new WebApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                JacksonAutoConfiguration.class, KrizakaAppConfigAutoConfiguration.class))
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("needs a JdbcTemplate")
                    .hasMessageContaining("krizaka.app-config.store=memory"));
  }

  @Test
  void anApplicationWithoutSpringMvcIsLeftAlone() {
    runner
        .withClassLoader(new FilteredClassLoader(DispatcherServlet.class))
        .run(
            context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(AppConfigController.class));
  }

  @Test
  void backsOffForTheApplicationsOwnStore() {
    InMemoryAppConfigStore own = new InMemoryAppConfigStore(java.time.Clock.systemUTC());
    runner
        .withBean("ownStore", AppConfigStore.class, () -> own)
        .run(context -> assertThat(context.getBean(AppConfigStore.class)).isSameAs(own));
  }

  @Test
  void aNegativeCacheTtlRefusesToStart() {
    runner
        .withPropertyValues("krizaka.app-config.cache-ttl=-1s")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void payloadIsAPublicContractType() {
    assertThat(AppConfigPayload.class).isPublic();
    assertThat(ClientVersionFilter.class).isPublic();
  }
}
