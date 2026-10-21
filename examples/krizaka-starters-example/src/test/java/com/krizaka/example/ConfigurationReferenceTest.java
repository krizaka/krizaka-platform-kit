package com.krizaka.example;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The configuration contract of the kit: every {@code krizaka.*} property a module declares is in
 * the metadata Spring Boot's configuration processor generates (so an IDE completes it), with a
 * description written for the reader and its default — and the reference java.krizaka.com renders,
 * {@code docs/configuration-metadata.json}, is that metadata.
 *
 * <p>The metadata is read from each module's build output: the reactor builds every module before
 * this example. After a change to a property, regenerate the reference with {@code ./mvnw verify
 * -Dkrizaka.reference.write=true} and commit it.
 */
class ConfigurationReferenceTest {

  /** The modules of the kit that declare properties, in the order the reference lists them. */
  static final List<String> MODULES =
      List.of(
          "krizaka-web",
          "krizaka-security",
          "krizaka-observability",
          "krizaka-messaging",
          "krizaka-app-config");

  /**
   * The properties that have no default, on purpose: a value only the service can give (its secret,
   * its names), or a choice the kit refuses to make for it.
   */
  static final Set<String> WITHOUT_DEFAULT =
      Set.of(
          "krizaka.security.jwt.secret",
          "krizaka.observability.product",
          "krizaka.observability.service",
          "krizaka.observability.version",
          "krizaka.messaging.producer",
          "krizaka.messaging.dedup.store",
          "krizaka.web.cors.allowed-origins");

  static final Path ROOT = Path.of("../..");
  static final Path REFERENCE = ROOT.resolve("docs/configuration-metadata.json");

  static final JsonMapper JSON =
      JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

  @Test
  void everyModuleGeneratesItsMetadata() {
    for (String module : MODULES) {
      assertThat(metadataOf(module)).as(module + " has no generated metadata").isRegularFile();
    }
  }

  @Test
  void everyPropertyIsDescribedForTheReader() {
    Map<String, String> descriptions = new HashMap<>();
    for (Map<String, Object> property : properties()) {
      String name = (String) property.get("name");
      String description = (String) property.get("description");
      assertThat(description).as(name + ": description").isNotBlank();
      assertThat(Character.isUpperCase(description.charAt(0)))
          .as(name + ": starts with a capital — " + description)
          .isTrue();
      assertThat(description).as(name + ": one or more sentences").endsWith(".");
      assertThat(description).as(name + ": Javadoc tags in an IDE tooltip").doesNotContain("{@");
      String twin = descriptions.put(description, name);
      assertThat(twin).as(name + " has the description of " + twin).isNull();
    }
  }

  @Test
  void everyPropertyShowsItsDefault() {
    List<String> missing = new ArrayList<>();
    List<String> unexpected = new ArrayList<>();
    for (Map<String, Object> property : properties()) {
      String name = (String) property.get("name");
      boolean hasDefault = property.containsKey("defaultValue");
      if (!hasDefault && !WITHOUT_DEFAULT.contains(name)) {
        missing.add(name);
      }
      if (hasDefault && WITHOUT_DEFAULT.contains(name)) {
        unexpected.add(name);
      }
    }
    assertThat(missing).as("properties whose default the metadata does not show").isEmpty();
    assertThat(unexpected).as("listed without a default, but they have one").isEmpty();
  }

  @Test
  void theReferenceIsTheGeneratedMetadata() throws IOException {
    String generated = JSON.writeValueAsString(reference()) + "\n";
    if (Boolean.getBoolean("krizaka.reference.write")) {
      Files.createDirectories(REFERENCE.getParent());
      Files.writeString(REFERENCE, generated);
    }
    assertThat(REFERENCE)
        .as("docs/configuration-metadata.json — run ./mvnw verify -Dkrizaka.reference.write=true")
        .isRegularFile();
    assertThat(Files.readString(REFERENCE)).isEqualTo(generated);
  }

  private static Path metadataOf(String module) {
    return ROOT.resolve(module)
        .resolve("target/classes/META-INF/spring-configuration-metadata.json");
  }

  private static Map<String, Object> reference() {
    List<Map<String, Object>> modules = new ArrayList<>();
    for (String module : MODULES) {
      JsonNode metadata = JSON.readTree(metadataOf(module).toFile());
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("artifact", module);
      entry.put("properties", propertiesOf(metadata));
      entry.put("hints", hintsOf(metadata));
      modules.add(entry);
    }
    Map<String, Object> reference = new LinkedHashMap<>();
    reference.put("repository", "krizaka-platform-kit");
    reference.put("modules", modules);
    return reference;
  }

  private static List<Map<String, Object>> properties() {
    List<Map<String, Object>> all = new ArrayList<>();
    for (String module : MODULES) {
      all.addAll(propertiesOf(JSON.readTree(metadataOf(module).toFile())));
    }
    return all;
  }

  private static List<Map<String, Object>> propertiesOf(JsonNode metadata) {
    List<Map<String, Object>> properties = new ArrayList<>();
    for (JsonNode node : metadata.path("properties")) {
      String name = node.path("name").asString();
      if (!name.startsWith("krizaka.")) {
        continue;
      }
      Map<String, Object> property = new LinkedHashMap<>();
      property.put("name", name);
      property.put("type", node.path("type").asString());
      property.put("description", node.path("description").asString(""));
      if (node.has("defaultValue")) {
        property.put("defaultValue", node.get("defaultValue"));
      }
      if (node.has("deprecation")) {
        property.put("deprecation", node.get("deprecation"));
      }
      properties.add(property);
    }
    properties.sort(Comparator.comparing(p -> (String) p.get("name")));
    return properties;
  }

  private static List<Map<String, Object>> hintsOf(JsonNode metadata) {
    List<Map<String, Object>> hints = new ArrayList<>();
    for (JsonNode node : metadata.path("hints")) {
      Map<String, Object> hint = new LinkedHashMap<>();
      hint.put("name", node.path("name").asString());
      List<Map<String, String>> values = new ArrayList<>();
      for (JsonNode value : node.path("values")) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("value", value.path("value").asString());
        entry.put("description", value.path("description").asString(""));
        values.add(entry);
      }
      values.sort(Comparator.comparing(v -> v.get("value")));
      hint.put("values", values);
      hints.add(hint);
    }
    hints.sort(Comparator.comparing(h -> (String) h.get("name")));
    return hints;
  }
}
