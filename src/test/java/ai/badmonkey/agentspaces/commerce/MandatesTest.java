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

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ISSUE-CommerceCapability §14: a valid chain verifies (the merchant's checkout JWT, the shopper's closed checkout
 * mandate bound to it by hash, the key-binding JWT, the payment mandate with the transaction id); a tampered
 * credential, an unknown issuer, an expired one, a wrong amount, and a cart whose total lies are refused with the
 * reason; the JWK round-trips a P-256 key.
 */
class MandatesTest {

    static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");
    static final KeyPair MERCHANT = Jose.generateP256();
    static final KeyPair USER = Jose.generateP256();
    static final KeyPair AGENT = Jose.generateP256();
    static final Map<String, PublicKey> TRUSTED = Map.of("merchant-1", MERCHANT.getPublic(), "user-1", USER.getPublic());

    static Cart cart() {
        return new Cart(List.of(new Cart.Item("claims.policies", "Policy data set", 1, 19900)), 19900, "USD");
    }

    /** The merchant's signed UCP checkout, as the AP2 closed checkout mandate embeds it. */
    static String checkoutJwt() {
        Map<String, Object> checkout = new LinkedHashMap<>();
        checkout.put("id", "checkout_1");
        checkout.put("status", "ready_for_complete");
        checkout.put("currency", "USD");
        checkout.put("line_items", List.of(Map.of("id", "li-1", "item", Map.of("id", "claims.policies"), "quantity", 1)));
        checkout.put("totals", List.of(Map.of("type", "total", "amount", 19900)));
        return Jose.sign(Map.of("kid", "merchant-1", "typ", "JWT"), checkout, MERCHANT.getPrivate());
    }

    /** The user's closed checkout mandate over that checkout, key-bound to the agent's key. */
    static Map<String, Object> checkoutCredential(String checkoutJwt, long exp) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vct", Mandates.VCT_CHECKOUT);
        payload.put("checkout_jwt", checkoutJwt);
        payload.put("checkout_hash", Jose.digest(checkoutJwt));
        payload.put("cnf", Map.of("jwk", Jose.toJwk((ECPublicKey) AGENT.getPublic(), "agent-1")));
        payload.put("iat", NOW.getEpochSecond());
        payload.put("exp", exp);
        String issuerSigned = Jose.sign(Map.of("kid", "user-1", "typ", "dc+sd-jwt"), payload, USER.getPrivate());
        List<String> disclosures = List.of("WyJzYWx0IiwgImNsYWltIiwgInZhbHVlIl0");
        String presentation = issuerSigned + "~" + disclosures.get(0) + "~";
        String kb = Jose.sign(Map.of("typ", "kb+jwt"), Map.of("aud", "merchant", "nonce", "n-1", "sd_hash", Jose.digest(presentation), "iat", NOW.getEpochSecond()), AGENT.getPrivate());
        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put(Mandate.ISSUER_SIGNED_JWT, issuerSigned);
        credential.put(Mandate.DISCLOSURES, disclosures);
        credential.put(Mandate.KB_JWT, kb);
        return credential;
    }

    static Mandate checkoutMandate(Map<String, Object> credential) {
        return new Mandate(Mandate.VERSION, "mnd-1", Mandate.CHECKOUT, "shopper", cart(), "merchant-1", new Basis("apr-1", ""), credential, Mandate.ISSUED, NOW);
    }

    @Test
    void aValidChainVerifiesAndEveryBreakIsNamed() {
        String checkout = checkoutJwt();
        Mandate good = checkoutMandate(checkoutCredential(checkout, NOW.plusSeconds(3600).getEpochSecond()));
        Mandates.Verdict ok = Mandates.verify(good, TRUSTED, NOW);
        assertThat(ok.valid()).as(ok.reason()).isTrue();
        assertThat(ok.issuer()).isEqualTo("user-1");

        // an unknown issuer
        assertThat(Mandates.verify(good, Map.of("merchant-1", MERCHANT.getPublic()), NOW).reason()).contains("user-1 is not trusted");
        // a tampered credential: the checkout JWT replaced, so the hash no longer binds
        Map<String, Object> tampered = new LinkedHashMap<>(good.credential());
        String other = Jose.sign(Map.of("kid", "merchant-1"), Map.of("id", "checkout_2", "totals", List.of(Map.of("type", "total", "amount", 1))), MERCHANT.getPrivate());
        Map<String, Object> issuer = Jose.parse(String.valueOf(tampered.get(Mandate.ISSUER_SIGNED_JWT))).payload();
        Map<String, Object> forged = new LinkedHashMap<>(issuer);
        forged.put("checkout_jwt", other);
        tampered.put(Mandate.ISSUER_SIGNED_JWT, Jose.sign(Map.of("kid", "user-1"), forged, USER.getPrivate()));
        assertThat(Mandates.verify(checkoutMandate(tampered), TRUSTED, NOW).reason()).contains("checkout_hash");
        // a signature that does not verify: the issuer-signed JWT re-signed by the agent under the user's kid
        Map<String, Object> wrongKey = new LinkedHashMap<>(good.credential());
        wrongKey.put(Mandate.ISSUER_SIGNED_JWT, Jose.sign(Map.of("kid", "user-1"), issuer, AGENT.getPrivate()));
        assertThat(Mandates.verify(checkoutMandate(wrongKey), TRUSTED, NOW).reason()).contains("does not verify under user-1");
        // expired
        assertThat(Mandates.verify(checkoutMandate(checkoutCredential(checkout, NOW.minusSeconds(1).getEpochSecond())), TRUSTED, NOW).reason()).contains("expired");
        // a cart whose total lies
        Mandate lying = new Mandate(1, "m", Mandate.CHECKOUT, "shopper", new Cart(cart().items(), 1, "USD"), "", null, good.credential(), Mandate.ISSUED, NOW);
        assertThat(Mandates.verify(lying, TRUSTED, NOW).reason()).contains("total");
        // a key-binding JWT by the wrong key
        Map<String, Object> badKb = new LinkedHashMap<>(good.credential());
        badKb.put(Mandate.KB_JWT, Jose.sign(Map.of(), Map.of("sd_hash", "x"), USER.getPrivate()));
        assertThat(Mandates.verify(checkoutMandate(badKb), TRUSTED, NOW).reason()).contains("cnf.jwk");
        // a payment mandate: the transaction id is the checkout's digest and the amount the cart's
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("vct", Mandates.VCT_PAYMENT);
        payment.put("transaction_id", Jose.digest(checkout));
        payment.put("payee", Map.of("id", "merchant-1", "name", "Claims fleet"));
        payment.put("payment_amount", Map.of("amount", 19900, "currency", "USD"));
        payment.put("payment_instrument", Map.of("id", "card-1", "type", "card", "description", "Card ••••4242"));
        payment.put("exp", NOW.plusSeconds(600).getEpochSecond());
        Map<String, Object> paymentCredential = Map.of(Mandate.ISSUER_SIGNED_JWT, Jose.sign(Map.of("kid", "user-1"), payment, USER.getPrivate()), Mandate.CHECKOUT_JWT, checkout);
        Mandate pay = new Mandate(1, "mnd-2", Mandate.PAYMENT, "payment", cart(), "merchant-1", Basis.NONE, paymentCredential, Mandate.ISSUED, NOW);
        assertThat(Mandates.verify(pay, TRUSTED, NOW).valid()).isTrue();
        Map<String, Object> wrongAmount = new LinkedHashMap<>(payment);
        wrongAmount.put("payment_amount", Map.of("amount", 1, "currency", "USD"));
        Mandate short_ = new Mandate(1, "mnd-3", Mandate.PAYMENT, "payment", cart(), "merchant-1", Basis.NONE,
                Map.of(Mandate.ISSUER_SIGNED_JWT, Jose.sign(Map.of("kid", "user-1"), wrongAmount, USER.getPrivate())), Mandate.ISSUED, NOW);
        assertThat(Mandates.verify(short_, TRUSTED, NOW).reason()).contains("payment_amount");
        // the kinds must agree with the vct
        Mandate mixed = new Mandate(1, "mnd-4", Mandate.PAYMENT, "payment", cart(), "", Basis.NONE, good.credential(), Mandate.ISSUED, NOW);
        assertThat(Mandates.verify(mixed, TRUSTED, NOW).reason()).contains("carries a mandate.checkout.1");
    }

    @Test
    void aJwkRoundTripsAP256KeyAndJsonRoundTripsAClaimSet() {
        Map<String, Object> jwk = Jose.toJwk((ECPublicKey) USER.getPublic(), "user-1");
        assertThat(jwk).containsEntry("kty", "EC").containsEntry("crv", "P-256").containsEntry("kid", "user-1");
        assertThat(Jose.fromJwk(jwk)).isEqualTo(USER.getPublic());
        String json = Json.write(Map.of("a", java.util.Arrays.asList(1, 2.5, "x\"y", true, null), "b", Map.of("c", -3)));
        Map<String, Object> back = Json.readObject(json);
        assertThat(back.get("a")).isEqualTo(java.util.Arrays.asList(1L, 2.5, "x\"y", true, null));
        assertThat(((Map<?, ?>) back.get("b")).get("c")).isEqualTo(-3L);
    }
}
