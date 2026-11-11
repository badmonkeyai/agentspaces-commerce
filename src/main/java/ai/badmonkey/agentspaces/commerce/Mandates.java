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

import java.security.PublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The verification of a mandate's credential chain, with no policy in it and no network: the issuer-signed JWT is
 * an ES256 JWT whose {@code kid} names a trusted issuer; its {@code vct} is one of the AP2 types; it has not
 * expired; a closed checkout mandate's {@code checkout_hash} is the base64url SHA-256 of the merchant-signed
 * {@code checkout_jwt} it carries, and that JWT verifies under a trusted issuer; a closed payment mandate's
 * {@code transaction_id} is the same digest of the checkout JWT the credential carries; a key-binding JWT, when
 * present, verifies under the {@code cnf.jwk} key of the issuer-signed payload and its {@code sd_hash} is the
 * digest of the presentation. The cart is checked against its items. What a verdict means for a trade is the
 * caller's business.
 */
public final class Mandates {

    public static final String VCT_CHECKOUT = "mandate.checkout.1";
    public static final String VCT_CHECKOUT_OPEN = "mandate.checkout.open.1";
    public static final String VCT_PAYMENT = "mandate.payment.1";
    public static final String VCT_PAYMENT_OPEN = "mandate.payment.open.1";
    public static final List<String> VCTS = List.of(VCT_CHECKOUT, VCT_CHECKOUT_OPEN, VCT_PAYMENT, VCT_PAYMENT_OPEN);

    /** The outcome: valid or not, why, and the issuer's {@code kid} when one signed. */
    public record Verdict(boolean valid, String reason, String issuer) {
        static Verdict no(String reason) {
            return new Verdict(false, reason, "");
        }
    }

    private Mandates() {
    }

    /**
     * @param mandate        the mandate as carried
     * @param trustedIssuers public keys by {@code kid}: the merchants, shoppers, and payment parties this reader trusts
     * @param now            the time the expiry is judged at
     */
    public static Verdict verify(Mandate mandate, Map<String, PublicKey> trustedIssuers, Instant now) {
        if (mandate.cart().itemsTotal() != mandate.cart().total()) {
            return Verdict.no("the cart's total " + mandate.cart().total() + " is not its items' " + mandate.cart().itemsTotal());
        }
        Object compact = mandate.credential().get(Mandate.ISSUER_SIGNED_JWT);
        if (compact == null || String.valueOf(compact).isBlank()) {
            return Verdict.no("the credential carries no issuer-signed JWT");
        }
        Jose.Jwt jwt;
        try {
            jwt = Jose.parse(String.valueOf(compact));
        } catch (IllegalArgumentException e) {
            return Verdict.no("the issuer-signed JWT does not parse: " + e.getMessage());
        }
        PublicKey issuer = trustedIssuers.get(jwt.kid());
        if (issuer == null) {
            return Verdict.no("the issuer " + (jwt.kid().isBlank() ? "(no kid)" : jwt.kid()) + " is not trusted");
        }
        if (!Jose.verify(jwt, issuer)) {
            return Verdict.no("the issuer-signed JWT's signature does not verify under " + jwt.kid());
        }
        String vct = String.valueOf(jwt.payload().get("vct"));
        if (!VCTS.contains(vct)) {
            return Verdict.no("the credential's vct " + vct + " is not an AP2 mandate type");
        }
        if (jwt.payload().get("exp") instanceof Number exp && now.getEpochSecond() > exp.longValue()) {
            return Verdict.no("the credential expired at " + Instant.ofEpochSecond(exp.longValue()));
        }
        boolean checkoutKind = Mandate.CHECKOUT.equals(mandate.kind());
        if (checkoutKind && !vct.startsWith("mandate.checkout") || !checkoutKind && !vct.startsWith("mandate.payment")) {
            return Verdict.no("a " + mandate.kind() + " mandate carries a " + vct + " credential");
        }
        if (VCT_CHECKOUT.equals(vct)) {
            Object checkoutJwt = jwt.payload().get("checkout_jwt");
            if (checkoutJwt == null) {
                return Verdict.no("the closed checkout mandate carries no checkout_jwt");
            }
            if (!Jose.digest(String.valueOf(checkoutJwt)).equals(String.valueOf(jwt.payload().get("checkout_hash")))) {
                return Verdict.no("checkout_hash is not the digest of checkout_jwt");
            }
            Verdict merchant = verifyCheckoutJwt(String.valueOf(checkoutJwt), trustedIssuers);
            if (!merchant.valid()) {
                return merchant;
            }
        }
        if (VCT_PAYMENT.equals(vct)) {
            Object checkoutJwt = mandate.credential().get(Mandate.CHECKOUT_JWT);
            if (checkoutJwt != null && !Jose.digest(String.valueOf(checkoutJwt)).equals(String.valueOf(jwt.payload().get("transaction_id")))) {
                return Verdict.no("transaction_id is not the digest of the checkout JWT the credential carries");
            }
            if (!(jwt.payload().get("payment_amount") instanceof Map<?, ?> amount) || !(amount.get("amount") instanceof Number paid)
                    || paid.longValue() != mandate.cart().total() || !mandate.cart().currency().equals(amount.get("currency"))) {
                return Verdict.no("payment_amount is not the cart's total and currency");
            }
        }
        Object kb = mandate.credential().get(Mandate.KB_JWT);
        if (kb != null && !String.valueOf(kb).isBlank()) {
            Verdict bound = verifyKeyBinding(String.valueOf(kb), jwt, mandate);
            if (!bound.valid()) {
                return bound;
            }
        }
        return new Verdict(true, "ok", jwt.kid());
    }

    /** The merchant-signed checkout JWT: a trusted issuer's ES256 JWT whose payload is the UCP checkout. */
    static Verdict verifyCheckoutJwt(String compact, Map<String, PublicKey> trustedIssuers) {
        Jose.Jwt checkout;
        try {
            checkout = Jose.parse(compact);
        } catch (IllegalArgumentException e) {
            return Verdict.no("checkout_jwt does not parse: " + e.getMessage());
        }
        PublicKey merchant = trustedIssuers.get(checkout.kid());
        if (merchant == null) {
            return Verdict.no("the checkout's merchant " + checkout.kid() + " is not trusted");
        }
        if (!Jose.verify(checkout, merchant)) {
            return Verdict.no("checkout_jwt's signature does not verify under " + checkout.kid());
        }
        return new Verdict(true, "ok", checkout.kid());
    }

    @SuppressWarnings("unchecked")
    static Verdict verifyKeyBinding(String compact, Jose.Jwt issuerSigned, Mandate mandate) {
        Jose.Jwt kb;
        try {
            kb = Jose.parse(compact);
        } catch (IllegalArgumentException e) {
            return Verdict.no("the key-binding JWT does not parse: " + e.getMessage());
        }
        if (!(issuerSigned.payload().get("cnf") instanceof Map<?, ?> cnf) || !(cnf.get("jwk") instanceof Map<?, ?> jwk)) {
            return Verdict.no("the issuer-signed JWT carries no cnf.jwk for the key binding");
        }
        PublicKey holder;
        try {
            holder = Jose.fromJwk((Map<String, Object>) jwk);
        } catch (IllegalArgumentException e) {
            return Verdict.no("cnf.jwk: " + e.getMessage());
        }
        if (!Jose.verify(kb, holder)) {
            return Verdict.no("the key-binding JWT does not verify under cnf.jwk");
        }
        StringBuilder presentation = new StringBuilder(issuerSigned.compact()).append('~');
        if (mandate.credential().get(Mandate.DISCLOSURES) instanceof List<?> ds) {
            for (Object d : ds) {
                presentation.append(d).append('~');
            }
        }
        if (!Jose.digest(presentation.toString()).equals(String.valueOf(kb.payload().get("sd_hash")))) {
            return Verdict.no("sd_hash is not the digest of the presentation");
        }
        return new Verdict(true, "ok", issuerSigned.kid());
    }
}
