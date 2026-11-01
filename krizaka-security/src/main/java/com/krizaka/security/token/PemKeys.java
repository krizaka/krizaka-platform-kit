package com.krizaka.security.token;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Reads the P-256 keys of ADR-001 from their PEM text: the caller's own private key (PKCS#8) and
 * the public keys of the callers it accepts (SPKI). One place, so no service parses a key its own
 * way.
 */
public final class PemKeys {

  private PemKeys() {}

  /**
   * @param pem a PKCS#8 PEM private key (P-256)
   * @return the key
   * @throws IllegalArgumentException when the text is not such a key; the message never contains it
   */
  public static ECPrivateKey privateKey(String pem) {
    try {
      return (ECPrivateKey)
          KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der(pem)));
    } catch (GeneralSecurityException | RuntimeException e) {
      throw new IllegalArgumentException("not a valid P-256 PKCS#8 private key PEM");
    }
  }

  /**
   * @param pem an SPKI PEM public key (P-256)
   * @return the key
   * @throws IllegalArgumentException when the text is not such a key
   */
  public static ECPublicKey publicKey(String pem) {
    try {
      return (ECPublicKey)
          KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der(pem)));
    } catch (GeneralSecurityException | RuntimeException e) {
      throw new IllegalArgumentException("not a valid P-256 SPKI public key PEM");
    }
  }

  private static byte[] der(String pem) {
    return Base64.getMimeDecoder().decode(pem.replaceAll("-----[A-Z ]+-----", "").strip());
  }
}
