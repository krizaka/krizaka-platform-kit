package com.krizaka.appconfig.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An app version, {@code major.minor.patch}, compared numerically.
 *
 * <p>Only the numeric core counts: {@code 1.4}, {@code 1.4.0}, {@code v1.4.0}, {@code 1.4.0-beta.2}
 * and {@code 1.4.0+42} all read as {@code 1.4.0}. A string that does not start with a number is not
 * a version.
 *
 * @param major the major number
 * @param minor the minor number, 0 when absent
 * @param patch the patch number, 0 when absent
 */
public record AppVersion(int major, int minor, int patch) implements Comparable<AppVersion> {

  /** The smallest version: "no minimum". */
  public static final AppVersion ZERO = new AppVersion(0, 0, 0);

  private static final Pattern SYNTAX =
      Pattern.compile("^v?(\\d{1,6})(?:\\.(\\d{1,6}))?(?:\\.(\\d{1,6}))?(?:[-+].*)?$");

  /** Rejects a negative number. */
  public AppVersion {
    if (major < 0 || minor < 0 || patch < 0) {
      throw new IllegalArgumentException("a version has no negative number");
    }
  }

  /**
   * Reads a version.
   *
   * @param text the text, possibly {@code null}
   * @return the version, or empty when the text is not one
   */
  public static Optional<AppVersion> parse(String text) {
    if (text == null) {
      return Optional.empty();
    }
    Matcher matcher = SYNTAX.matcher(text.trim());
    if (!matcher.matches()) {
      return Optional.empty();
    }
    return Optional.of(
        new AppVersion(
            Integer.parseInt(matcher.group(1)),
            matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2)),
            matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3))));
  }

  /**
   * Reads a version that must be valid.
   *
   * @param text the text
   * @return the version
   * @throws IllegalArgumentException when the text is not a version
   */
  @JsonCreator
  public static AppVersion of(String text) {
    return parse(text)
        .orElseThrow(() -> new IllegalArgumentException("not a version: '" + text + "'"));
  }

  @Override
  public int compareTo(AppVersion other) {
    Objects.requireNonNull(other, "other");
    int byMajor = Integer.compare(major, other.major);
    if (byMajor != 0) {
      return byMajor;
    }
    int byMinor = Integer.compare(minor, other.minor);
    return byMinor != 0 ? byMinor : Integer.compare(patch, other.patch);
  }

  /**
   * Whether this version is older than another.
   *
   * @param other the version to compare with
   * @return {@code true} when this one is strictly older
   */
  public boolean isBefore(AppVersion other) {
    return compareTo(other) < 0;
  }

  @Override
  @JsonValue
  public String toString() {
    return major + "." + minor + "." + patch;
  }
}
