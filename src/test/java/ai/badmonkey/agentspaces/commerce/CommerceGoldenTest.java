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

import ai.badmonkey.agentspaces.api.ad.CapabilityAdvertisement;
import ai.badmonkey.agentspaces.api.ad.Reserved;
import ai.badmonkey.agentspaces.common.codec.CborCodec;
import ai.badmonkey.agentspaces.common.id.GroupId;
import ai.badmonkey.agentspaces.common.id.PeerId;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ISSUE-CommerceCapability FR-6: the three vectors pin the bytes; the records refuse what they must. */
class CommerceGoldenTest {

    static final CborCodec CODEC = CborCodec.defaultCodec();
    static final Instant AT = Instant.parse("2026-11-05T12:00:00Z");

    static Cart cart() {
        return new Cart(List.of(new Cart.Item("claims.policies", "Policy data set, one month", 1, 19900)), 19900, "USD");
    }

    static Mandate mandate() {
        return new Mandate(Mandate.VERSION, "mnd-1", Mandate.CHECKOUT, "shopper", cart(), "https://merchant.example/.well-known/ucp",
                new Basis("apr-7", ""), Map.of(Mandate.ISSUER_SIGNED_JWT, "eyJ.a.b", Mandate.DISCLOSURES, List.of("d1"), Mandate.KB_JWT, "eyJ.c.d",
                        Mandate.CHECKOUT_JWT, "eyJ.e.f"), Mandate.ISSUED, AT);
    }

    static Order order() {
        return new Order(Order.VERSION, "ord-1", "mnd-1", "completed", Map.of("method", "digital"), "https://merchant.example/orders/ord-1", AT);
    }

    static CapabilityAdvertisement capability() {
        return new CapabilityAdvertisement("cap-commerce-1", PeerId.of("zPeerMerchant"), GroupId.fromFounding("claims-fleet-v1".getBytes(StandardCharsets.UTF_8)),
                AT, Duration.ofHours(1), Reserved.CAPABILITY_COMMERCE, "1", "rest",
                Map.of(Reserved.ROLE, Reserved.ROLE_MERCHANT, Reserved.PROFILE, "https://merchant.example/.well-known/ucp", Reserved.UCP, "dev.ucp.shopping.checkout"), Map.of());
    }

    @Test
    void theRecordsRoundTripAndMatchTheirVectors() throws IOException {
        pin("mandate_cbor", mandate(), Mandate.class);
        pin("order_cbor", order(), Order.class);
        pin("capability_commerce_cbor", capability(), CapabilityAdvertisement.class);
        assertThat(mandate().cart().itemsTotal()).isEqualTo(19900);
        assertThat(mandate().withState(Mandate.SETTLED).state()).isEqualTo(Mandate.SETTLED);
        assertThat(Reserved.COMMERCE_ROLES).containsExactly("merchant", "shopper", "payment");
    }

    @Test
    void theRecordsRefuseWhatTheyMust() {
        assertThatThrownBy(() -> new Mandate(1, "m", "refund", "shopper", cart(), "", null, null, Mandate.ISSUED, AT)).hasMessageContaining("kind");
        assertThatThrownBy(() -> new Mandate(1, "m", Mandate.CHECKOUT, "broker", cart(), "", null, null, Mandate.ISSUED, AT)).hasMessageContaining("role");
        assertThatThrownBy(() -> new Mandate(1, "m", Mandate.CHECKOUT, "shopper", cart(), "", null, null, "lost", AT)).hasMessageContaining("state");
        assertThatThrownBy(() -> new Order(1, "o", "m", "shipped", null, "", AT)).hasMessageContaining("status");
        assertThatThrownBy(() -> new Cart(java.util.Collections.nCopies(65, new Cart.Item("x", "", 1, 1)), 65, "USD")).hasMessageContaining("at most 64");
        assertThatThrownBy(() -> new Cart(List.of(), 0, "dollars")).hasMessageContaining("ISO 4217");
        assertThat(new Basis(null, null).absent()).isTrue();
        assertThat(new String(CODEC.toBytes(new Basis("a", "")), java.nio.charset.StandardCharsets.ISO_8859_1)).as("only the two fields reach the wire").doesNotContain("empty").doesNotContain("absent");
    }

    private static <T> void pin(String name, T value, Class<T> type) throws IOException {
        byte[] bytes = CODEC.toBytes(value);
        assertThat(CODEC.fromBytes(bytes, type)).isEqualTo(value);
        Path vector = Path.of("src/test/resources/vectors/" + name + ".hex");
        String hex = HexFormat.of().formatHex(bytes);
        if (!Files.exists(vector)) {
            Files.createDirectories(vector.getParent());
            Files.writeString(vector, hex + "\n", StandardCharsets.UTF_8);
        }
        assertThat(hex).as(name + " pins the canonical bytes").isEqualTo(Files.readString(vector, StandardCharsets.UTF_8).trim());
    }
}
