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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An AP2 mandate carried as an entry of the {@code commerce} space: {@code kind} is {@code checkout} or
 * {@code payment}; {@code role} is who wrote it ({@code merchant}, {@code shopper}, {@code payment}); {@code cart}
 * what it is about; {@code counterparty} the other party (a peer id or a profile URL); {@code basis} what a
 * shopper's rests on; {@code credential} the AP2 verifiable credential as issued (its public parts: the
 * issuer-signed JWT, the disclosures, the key-binding JWT, the checkout JWT it binds to); {@code state} its life.
 * No instrument, key, or token appears here.
 */
public record Mandate(int schemaVersion, String id, String kind, String role, Cart cart, String counterparty, Basis basis,
                      Map<String, Object> credential, String state, Instant at) {

    public static final int VERSION = 1;
    public static final String CHECKOUT = "checkout";
    public static final String PAYMENT = "payment";
    public static final List<String> KINDS = List.of(CHECKOUT, PAYMENT);
    public static final List<String> ROLES = List.of("merchant", "shopper", "payment");
    public static final String ISSUED = "issued";
    public static final String SETTLED = "settled";
    public static final String REFUSED = "refused";
    public static final String EXPIRED = "expired";
    public static final List<String> STATES = List.of(ISSUED, SETTLED, REFUSED, EXPIRED);
    /** The credential map's keys. */
    public static final String ISSUER_SIGNED_JWT = "issuerSignedJwt";
    public static final String DISCLOSURES = "disclosures";
    public static final String KB_JWT = "kbJwt";
    public static final String CHECKOUT_JWT = "checkoutJwt";

    public Mandate {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("a mandate has an id");
        }
        if (!KINDS.contains(kind)) {
            throw new IllegalArgumentException("kind must be one of " + KINDS + ": " + kind);
        }
        if (!ROLES.contains(role)) {
            throw new IllegalArgumentException("role must be one of " + ROLES + ": " + role);
        }
        Objects.requireNonNull(cart, "cart");
        counterparty = counterparty == null ? "" : counterparty;
        basis = basis == null ? Basis.NONE : basis;
        credential = credential == null ? Map.of() : Map.copyOf(credential);
        if (!STATES.contains(state)) {
            throw new IllegalArgumentException("state must be one of " + STATES + ": " + state);
        }
        Objects.requireNonNull(at, "at");
    }

    public Mandate withState(String s) {
        return new Mandate(schemaVersion, id, kind, role, cart, counterparty, basis, credential, s, at);
    }
}
