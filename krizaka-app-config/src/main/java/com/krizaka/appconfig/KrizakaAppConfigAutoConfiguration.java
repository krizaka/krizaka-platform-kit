package com.krizaka.appconfig;

import com.krizaka.appconfig.store.AppConfigReader;
import com.krizaka.appconfig.store.AppConfigStore;
import com.krizaka.appconfig.store.InMemoryAppConfigStore;
import com.krizaka.appconfig.store.JdbcAppConfigStore;
import com.krizaka.appconfig.web.AdminGate;
import com.krizaka.appconfig.web.AppConfigAdminController;
import com.krizaka.appconfig.web.AppConfigController;
import com.krizaka.appconfig.web.ClientVersionFilter;
import com.krizaka.appconfig.web.SpringSecurityAdminGate;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.DispatcherServlet;
import tools.jackson.databind.json.JsonMapper;

/**
 * The app-version contract of a Spring MVC service: the public {@code /api/v1/app-config} endpoint,
 * the {@code 426} filter and, when declared, the admin API.
 *
 * <p>Active in a servlet application with Spring MVC, unless {@code krizaka.app-config.enabled} is
 * {@code false}. The store is declared: {@code jdbc} (default) needs the application's {@code
 * JdbcTemplate} and fails at startup, with the reason, when there is none; {@code memory} is for
 * tests. Each bean backs off when the application declares its own.
 */
@AutoConfiguration(
    afterName = {
      "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration",
      "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"
    })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnProperty(prefix = "krizaka.app-config", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(KrizakaAppConfigProperties.class)
public class KrizakaAppConfigAutoConfiguration {

  private static final String DEFAULT_PROBLEM_BASE = "https://krizaka.com/problems/";

  /** Creates the auto-configuration. */
  public KrizakaAppConfigAutoConfiguration() {}

  /**
   * The clock, UTC, unless the application has one.
   *
   * @return the clock
   */
  @Bean
  @ConditionalOnMissingBean(Clock.class)
  public Clock krizakaAppConfigClock() {
    return Clock.systemUTC();
  }

  /**
   * Where the parameters are kept.
   *
   * @param properties the declared store
   * @param jdbc the application's database, when it has one
   * @param json the JSON mapper
   * @param clock the clock
   * @return the store
   */
  @Bean
  @ConditionalOnMissingBean(AppConfigStore.class)
  public AppConfigStore krizakaAppConfigStore(
      KrizakaAppConfigProperties properties,
      ObjectProvider<JdbcTemplate> jdbc,
      JsonMapper json,
      Clock clock) {
    if (properties.store() == KrizakaAppConfigProperties.Store.MEMORY) {
      return new InMemoryAppConfigStore(clock);
    }
    JdbcTemplate template = jdbc.getIfAvailable();
    if (template == null) {
      throw new IllegalStateException(
          "krizaka.app-config.store=jdbc (the default) needs a JdbcTemplate: add spring-boot-starter-jdbc"
              + " and a datasource, or set krizaka.app-config.store=memory, or disable app-config with"
              + " krizaka.app-config.enabled=false");
    }
    return new JdbcAppConfigStore(template, json);
  }

  /**
   * The cached reader.
   *
   * @param store the store
   * @param properties the cache TTL
   * @param clock the clock
   * @return the reader
   */
  @Bean
  @ConditionalOnMissingBean
  public AppConfigReader krizakaAppConfigReader(
      AppConfigStore store, KrizakaAppConfigProperties properties, Clock clock) {
    return new AppConfigReader(store, properties.cacheTtl(), clock);
  }

  /**
   * The public endpoint.
   *
   * @param reader the parameters
   * @param json the mapper
   * @param properties the public {@code max-age}
   * @return the controller
   */
  @Bean
  @ConditionalOnMissingBean
  public AppConfigController krizakaAppConfigController(
      AppConfigReader reader, JsonMapper json, KrizakaAppConfigProperties properties) {
    return new AppConfigController(reader, json, properties.httpMaxAge());
  }

  /**
   * The {@code 426} filter, right after the correlation filter and before any security filter, so
   * an old client with an expired token still learns it must update.
   *
   * @param reader the parameters
   * @param json the mapper
   * @param properties the guarded paths
   * @param environment for the Problem Details base
   * @param clock the clock
   * @return the registration
   */
  @Bean
  @ConditionalOnMissingBean(name = "krizakaClientVersionFilter")
  @ConditionalOnProperty(
      prefix = "krizaka.app-config.guard",
      name = "enabled",
      matchIfMissing = true)
  public FilterRegistrationBean<ClientVersionFilter> krizakaClientVersionFilter(
      AppConfigReader reader,
      JsonMapper json,
      KrizakaAppConfigProperties properties,
      Environment environment,
      Clock clock) {
    String base = environment.getProperty("krizaka.web.problems.base-type", DEFAULT_PROBLEM_BASE);
    FilterRegistrationBean<ClientVersionFilter> registration =
        new FilterRegistrationBean<>(
            new ClientVersionFilter(
                reader,
                json,
                properties.guard().include(),
                properties.guard().exclude(),
                base,
                clock));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return registration;
  }

  /** The admin API, only when declared. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(
      prefix = "krizaka.app-config.admin",
      name = "enabled",
      havingValue = "true")
  static class Admin {

    @Bean
    @ConditionalOnMissingBean
    AppConfigAdminController krizakaAppConfigAdminController(
        AppConfigReader reader, AdminGate gate) {
      return new AppConfigAdminController(reader, gate);
    }

    @Bean
    @ConditionalOnMissingBean(AdminGate.class)
    @ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
    AdminGate krizakaAppConfigAdminGate(KrizakaAppConfigProperties properties) {
      return new SpringSecurityAdminGate(properties.admin().authority());
    }

    @Bean
    @ConditionalOnMissingBean(AdminGate.class)
    AdminGate krizakaAppConfigClosedAdminGate() {
      return request -> new AdminGate.Denied(401);
    }
  }
}
