# agentspaces-commerce

**Open commerce records for AgentSpaces agents**, from Bad Monkey AI. A group that trades keeps its mandates and orders in the reserved `commerce` space. A `Mandate` carries an Agent Payments Protocol (AP2) Checkout or Payment Mandate, an `Order` carries the Universal Commerce Protocol (UCP) order it led to, and `Mandates.verify` checks a mandate's credential chain with no policy and no network. It is for Java developers whose agents sell, shop, or pay, and for anyone who reads and verifies another party's mandates.

## Quick start: sign a mandate and verify it

One plain Java program plays three parts here. A merchant signs a UCP checkout. A user signs a closed AP2 checkout mandate that binds that checkout by its hash. A reader then checks the chain under the keys it trusts.

**1. Add the dependency.** The project is `shop:checkout:1.0.0` on Java 21, with the Maven exec plugin set to run `shop.Checkout`. It needs this library alone, which brings `agentspaces-api`, Jackson, and Nimbus JOSE + JWT with it:

```xml
<dependency><groupId>ai.badmonkey.agentspaces</groupId><artifactId>agentspaces-commerce</artifactId><version>0.6.0</version></dependency>
```

**2. Write the program.** The listing leaves out the package and import lines.

```java
public class Checkout {

    public static void main(String[] args) {
        KeyPair merchant = Jose.generateP256();   // each party's key lives in its own vault; these are for the demo
        KeyPair user = Jose.generateP256();
        Instant now = Instant.now();

        // 1. The merchant signs the UCP checkout.
        String checkoutJwt = Jose.sign(Map.of("kid", "merchant-1"),
                Map.of("id", "checkout-1", "currency", "EUR", "totals", List.of(Map.of("type", "total", "amount", 3000))),
                merchant.getPrivate());

        // 2. The user signs a closed checkout mandate that binds that checkout by its hash.
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("vct", Mandates.VCT_CHECKOUT);
        claims.put("checkout_jwt", checkoutJwt);
        claims.put("checkout_hash", Jose.digest(checkoutJwt));
        claims.put("exp", now.plusSeconds(600).getEpochSecond());
        String issuerSigned = Jose.sign(Map.of("kid", "user-1", "typ", "dc+sd-jwt"), claims, user.getPrivate());

        // 3. The shopper carries it as a Mandate, the record a group's commerce space holds.
        Cart cart = new Cart(List.of(new Cart.Item("kettle-1", "Kettle", 1, 3000)), 3000, "EUR");
        Mandate mandate = new Mandate(Mandate.VERSION, "mnd-1", Mandate.CHECKOUT, "shopper", cart, "merchant-1",
                new Basis("approval-7", ""), Map.of(Mandate.ISSUER_SIGNED_JWT, issuerSigned), Mandate.ISSUED, now);

        // 4. Any reader checks the chain under the keys it trusts, by kid.
        Map<String, PublicKey> trusted = Map.of("merchant-1", merchant.getPublic(), "user-1", user.getPublic());
        System.out.println("trusted: " + Mandates.verify(mandate, trusted, now));
        System.out.println("user only: " + Mandates.verify(mandate, Map.of("user-1", user.getPublic()), now));
    }
}
```

**3. Run it:**

```
mvn -q compile exec:java
```

It prints:

```
trusted: Verdict[valid=true, reason=ok, issuer=user-1]
user only: Verdict[valid=false, reason=the checkout's merchant merchant-1 is not trusted, issuer=]
```

The first reader trusts both keys, so the chain verifies. The second reader trusts only the user, so it refuses the merchant's checkout and names the reason. In a fleet, the shopper writes the `Mandate` into its group's `commerce` space, and each member that reads it runs the same check.

## What it is

- `Mandate`, `Order`, `Cart`, and `Basis`: the records of a group's `commerce` space. Golden CBOR vectors pin their bytes, and the Python, TypeScript, and Clojure clients read the same vectors.
- `Mandates.verify`: a pure check of an AP2 credential chain: ES256 signatures under trusted key ids, the AP2 type, the expiry, the checkout hash, the payment amount, and the key binding.
- `Jose`: compact ES256 JWTs, base64url, SHA-256 digests, and P-256 keys as JWKs, over Nimbus JOSE + JWT.
- Java 21; it depends on `agentspaces-api`, Jackson, and Nimbus JOSE + JWT, and on no framework. The reserved names live in the core's `Reserved`.

## Add it to your build

The library is on Maven Central as `ai.badmonkey.agentspaces:agentspaces-commerce:0.6.0`. Maven:

```xml
<dependency>
  <groupId>ai.badmonkey.agentspaces</groupId>
  <artifactId>agentspaces-commerce</artifactId>
  <version>0.6.0</version>
</dependency>
```

Gradle (Kotlin DSL):

```kotlin
implementation("ai.badmonkey.agentspaces:agentspaces-commerce:0.6.0")
```

The library releases from its own repository, so it carries its version even beside the `agentspaces-dependencies` BOM. [Records](docs/records.md) describes every record and the reserved names, [Verification](docs/verification.md) describes what the check covers, and [Building](docs/building.md) builds from a clone.

## Where to read next

1. **[The AgentSpaces developer guide](https://www.badmonkey.ai/agentspaces/developer-guide.html)**: groups, spaces, leases, capabilities, and the hosts that give an agent its `Space`.
2. **[The AgentSpaces specification](https://github.com/badmonkeyai/agentspaces-spec/blob/main/SPEC.md)**: the `commerce` space (§7.6) and the `aspace:cap/commerce` capability (§8).
3. **[AP2](https://ap2-protocol.org) and [UCP](https://ucp.dev)**: the mandates and the checkout these records carry.
4. **[The tests](src/test/java/ai/badmonkey/agentspaces/commerce/MandatesTest.java)**: a full chain with a key binding and a payment mandate, and each way a chain breaks.
5. **[The documents](docs/README.md)**: everything this README leaves out, one topic per document, each with the reader it is written for.

## License

Apache License 2.0, Copyright 2026 Bad Monkey, Inc. See [LICENSE](LICENSE) and [NOTICE](NOTICE). Contributions are welcome under the Developer Certificate of Origin; see [CONTRIBUTING.md](CONTRIBUTING.md) and the [Code of Conduct](CODE_OF_CONDUCT.md). Report a vulnerability privately, as [SECURITY.md](SECURITY.md) describes. [Releasing](docs/releasing.md) has the release steps, and [CHANGELOG.md](CHANGELOG.md) what each release changed.
