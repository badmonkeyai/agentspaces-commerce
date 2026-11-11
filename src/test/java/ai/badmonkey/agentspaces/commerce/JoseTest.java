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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The facade over Nimbus and Jackson keeps the wire: a JWT this class signs verifies under Nimbus
 * directly, one Nimbus signs verifies here, a JWK round-trips through Nimbus's parser in the order the records
 * carry, the claim sets read back with longs and doubles, and the refusals name what is wrong.
 */
class JoseTest {

    @Test
    void aJwtSignedHereVerifiesUnderNimbusAndTheOtherWayRound() throws Exception {
        KeyPair pair = Jose.generateP256();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vct", "mandate.checkout");
        payload.put("amount", 19900L);
        payload.put("rate", 0.5);
        payload.put("items", List.of(Map.of("id", "li-1")));
        String compact = Jose.sign(Map.of("kid", "user-1", "typ", "dc+sd-jwt"), payload, pair.getPrivate());
        JWSObject nimbus = JWSObject.parse(compact);
        assertThat(nimbus.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
        assertThat(nimbus.getHeader().getKeyID()).isEqualTo("user-1");
        assertThat(nimbus.getHeader().getType().toString()).isEqualTo("dc+sd-jwt");
        assertThat(nimbus.verify(new ECDSAVerifier((ECPublicKey) pair.getPublic()))).isTrue();
        assertThat(nimbus.getPayload().toString()).isEqualTo("{\"vct\":\"mandate.checkout\",\"amount\":19900,\"rate\":0.5,\"items\":[{\"id\":\"li-1\"}]}");
        Jose.Jwt parsed = Jose.parse(compact);
        assertThat(parsed.kid()).isEqualTo("user-1");
        assertThat(parsed.alg()).isEqualTo("ES256");
        assertThat(parsed.payload().get("amount")).isEqualTo(19900L);
        assertThat(parsed.payload().get("rate")).isEqualTo(0.5);
        assertThat(Jose.verify(parsed, pair.getPublic())).isTrue();
        assertThat(Jose.verify(parsed, Jose.generateP256().getPublic())).as("another key").isFalse();
        // Nimbus signs, this verifies
        JWSObject theirs = new JWSObject(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID("merchant-1").build(), new Payload("{\"id\":\"checkout_1\",\"totals\":[{\"type\":\"total\",\"amount\":19900}]}"));
        theirs.sign(new ECDSASigner((ECPrivateKey) pair.getPrivate()));
        Jose.Jwt ours = Jose.parse(theirs.serialize());
        assertThat(ours.kid()).isEqualTo("merchant-1");
        assertThat(((List<?>) ours.payload().get("totals")).get(0)).isEqualTo(Map.of("type", "total", "amount", 19900L));
        assertThat(Jose.verify(ours, pair.getPublic())).isTrue();
        // a tampered payload fails
        String[] parts = compact.split("\\.");
        String tampered = parts[0] + "." + Jose.base64url("{\"vct\":\"mandate.payment\"}".getBytes()) + "." + parts[2];
        assertThat(Jose.verify(Jose.parse(tampered), pair.getPublic())).isFalse();
        // the refusals
        assertThatThrownBy(() -> Jose.parse("eyJ.a")).hasMessageContaining("three parts");
        assertThatThrownBy(() -> Jose.parse("not.a.jwt")).hasMessageContaining("does not parse");
        assertThatThrownBy(() -> Jose.sign(Map.of("alg", "none"), Map.of(), pair.getPrivate())).isInstanceOf(RuntimeException.class);
        assertThat(Jose.digest("abc")).isEqualTo("ungWv48Bz-pBQUDeXa4iI7ADYaOWF3qctBD_YfIAFa0");
        assertThat(Jose.fromBase64url(Jose.base64url(new byte[] {0, 1, 2, (byte) 255}))).containsExactly(0, 1, 2, (byte) 255);
    }

    @Test
    void aJwkRoundTripsThroughNimbusInTheRecordsOrder() throws Exception {
        KeyPair pair = Jose.generateP256();
        Map<String, Object> jwk = Jose.toJwk((ECPublicKey) pair.getPublic(), "agent-1");
        assertThat(jwk.keySet()).containsExactly("kty", "crv", "x", "y", "kid", "alg", "use");
        assertThat(jwk).containsEntry("kty", "EC").containsEntry("crv", "P-256").containsEntry("alg", "ES256").containsEntry("use", "sig");
        ECKey nimbus = ECKey.parse(new LinkedHashMap<>(jwk));
        assertThat(nimbus.toECPublicKey()).isEqualTo(pair.getPublic());
        assertThat(Jose.fromJwk(jwk)).isEqualTo(pair.getPublic());
        assertThat(Jose.fromJwk(nimbus.toJSONObject())).as("a JWK Nimbus wrote reads too").isEqualTo(pair.getPublic());
        assertThatThrownBy(() -> Jose.fromJwk(Map.of("kty", "RSA"))).hasMessageContaining("not a P-256");
        assertThatThrownBy(() -> Jose.fromJwk(Map.of("kty", "EC", "crv", "P-256", "x", "AA", "y", "AA"))).hasMessageContaining("does not read");
    }

    @Test
    void theClaimSetsJsonIsJacksonsInDocumentOrder() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("z", 1L);
        m.put("a", java.util.Arrays.asList(true, null, "x\"y", 2.5));
        assertThat(Json.write(m)).isEqualTo("{\"z\":1,\"a\":[true,null,\"x\\\"y\",2.5]}");
        Map<String, Object> back = Json.readObject(" {\"z\": 1, \"a\": [true, null, \"x\\\"y\", 2.5], \"n\": {\"k\": 10000000000}} ");
        assertThat(back.keySet()).containsExactly("z", "a", "n");
        assertThat(back.get("z")).isEqualTo(1L);
        assertThat(((Map<?, ?>) back.get("n")).get("k")).isEqualTo(10000000000L);
        assertThat(((List<?>) back.get("a")).get(3)).isEqualTo(2.5);
        assertThatThrownBy(() -> Json.readObject("[1]")).hasMessageContaining("not a JSON object");
        assertThatThrownBy(() -> Json.readObject("{\"a\":")).hasMessageContaining("bad JSON");
    }
}
