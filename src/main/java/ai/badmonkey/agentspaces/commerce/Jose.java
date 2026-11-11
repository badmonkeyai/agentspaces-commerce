/*
 * Copyright 2026 Bad Monkey, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.badmonkey.agentspaces.commerce;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.util.Base64URL;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The part of JOSE the mandates use, as a wire-compatible facade over Nimbus JOSE + JWT: compact ES256 JWTs
 * (RFC 7515, RFC 7518, P-256 with the raw {@code r||s} signature), the base64url of RFC 4648 §5, the SHA-256 digest
 * the AP2 bindings use, and a P-256 public key as and from an RFC 7517 JWK. A header or payload is a map (the claim
 * set's shape, written and read by {@link Json}); the signing, the verification, the key parsing, and the encoding
 * are Nimbus's. A JWT this class signs verifies under Nimbus directly, and a JWT that
 * Nimbus signs verifies here. Signing needs a private key the caller holds (a vault's); this class keeps none.
 */
public final class Jose {

    public static final String ES256 = "ES256";

    private Jose() {
    }

    /** A parsed compact JWT: the header and payload as maps, the signed bytes, the signature; the compact form it came from. */
    public record Jwt(Map<String, Object> header, Map<String, Object> payload, byte[] signingInput, byte[] signature, String compact) {
        public String kid() {
            return header.get("kid") == null ? "" : String.valueOf(header.get("kid"));
        }

        public String alg() {
            return header.get("alg") == null ? "" : String.valueOf(header.get("alg"));
        }
    }

    public static String base64url(byte[] bytes) {
        return Base64URL.encode(bytes).toString();
    }

    public static byte[] fromBase64url(String s) {
        return new Base64URL(s).decode();
    }

    public static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The base64url SHA-256 of a string's UTF-8 bytes: how AP2 binds a mandate to a checkout JWT. */
    public static String digest(String s) {
        return base64url(sha256(s.getBytes(StandardCharsets.UTF_8)));
    }

    /** A compact ES256 JWT over {@code payload} with {@code header} ({@code alg} and {@code typ} filled when absent). */
    public static String sign(Map<String, Object> header, Map<String, Object> payload, PrivateKey key) {
        if (!(key instanceof ECPrivateKey ec)) {
            throw new IllegalArgumentException("ES256 signs with a P-256 private key");
        }
        Map<String, Object> h = new LinkedHashMap<>(header);
        h.putIfAbsent("alg", ES256);
        h.putIfAbsent("typ", "JWT");
        try {
            JWSObject jws = new JWSObject(JWSHeader.parse(h), new Payload(Json.write(payload)));
            jws.sign(new ECDSASigner(ec));
            return jws.serialize();
        } catch (ParseException e) {
            throw new IllegalArgumentException("the header does not read as a JWS header: " + e.getMessage(), e);
        } catch (JOSEException e) {
            throw new IllegalStateException("cannot sign: " + e.getMessage(), e);
        }
    }

    /** Parses a compact JWT without verifying it. */
    public static Jwt parse(String compact) {
        try {
            JWSObject jws = JWSObject.parse(compact);
            Map<String, Object> header = new LinkedHashMap<>(jws.getHeader().toJSONObject());
            Map<String, Object> payload = Json.readObject(jws.getPayload().toString());
            return new Jwt(header, payload, jws.getSigningInput(), jws.getSignature().decode(), compact);
        } catch (ParseException e) {
            throw new IllegalArgumentException(compact == null || compact.chars().filter(c -> c == '.').count() != 2 ? "a compact JWT has three parts" : "the JWT does not parse: " + e.getMessage(), e);
        }
    }

    /** Whether the JWT's ES256 signature verifies under {@code key}. */
    public static boolean verify(Jwt jwt, PublicKey key) {
        if (!ES256.equals(jwt.alg()) || !(key instanceof ECPublicKey ec)) {
            return false;
        }
        try {
            return JWSObject.parse(jwt.compact()).verify(new ECDSAVerifier(ec));
        } catch (ParseException | JOSEException e) {
            return false;
        }
    }

    /** A fresh P-256 key pair (for a vault to keep, or a test). */
    public static KeyPair generateP256() {
        try {
            return new ECKeyGenerator(Curve.P_256).generate().toKeyPair();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A P-256 public key as an RFC 7517 JWK map ({@code kty}, {@code crv}, {@code x}, {@code y}, {@code kid}, {@code alg}, {@code use}), in that order. */
    public static Map<String, Object> toJwk(ECPublicKey key, String kid) {
        ECKey ec = new ECKey.Builder(Curve.P_256, key).keyID(kid).algorithm(JWSAlgorithm.ES256).keyUse(KeyUse.SIGNATURE).build();
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "EC");
        jwk.put("crv", "P-256");
        jwk.put("x", ec.getX().toString());
        jwk.put("y", ec.getY().toString());
        jwk.put("kid", kid);
        jwk.put("alg", ES256);
        jwk.put("use", "sig");
        return jwk;
    }

    /** The P-256 public key an RFC 7517 JWK map names. */
    public static ECPublicKey fromJwk(Map<String, ?> jwk) {
        if (!"EC".equals(jwk.get("kty")) || !"P-256".equals(jwk.get("crv"))) {
            throw new IllegalArgumentException("the JWK is not a P-256 EC key");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) jwk;
            return ECKey.parse(map).toECPublicKey();
        } catch (ParseException | JOSEException e) {
            throw new IllegalArgumentException("the JWK does not read as a P-256 key: " + e.getMessage(), e);
        }
    }
}
