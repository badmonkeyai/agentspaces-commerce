# Building

Written for: contributors who build this repository.

```bash
mvn -B -ntp verify                                       # the records, the vectors, the JOSE layer, and the mandate checks: 7 tests
mvn -B -ntp verify -Pcentral-publish -Dgpg.skip=true     # plus the sources and javadoc jars, unsigned, as CI runs it
mvn -q clean install                                     # into the local repository, for a project that builds on it
node tools/docs-lint.mjs .                               # the README's shape and this folder's index
```

Java 21 and Maven. The AgentSpaces 0.6.0 artifacts (the `agentspaces-dependencies` BOM, `agentspaces-api`, and `agentspaces-common` for the tests) resolve from Maven Central. The tests make fresh P-256 keys and need no network.

## What the tests pin

- `CommerceGoldenTest` encodes a mandate, an order, and a merchant's capability advertisement, and compares the bytes with `mandate_cbor`, `order_cbor`, and `capability_commerce_cbor` under `src/test/resources/vectors`. It also holds each record to what its constructor refuses. A missing vector file is written on the next run, so delete a vector only when its bytes are meant to change, and say why in the pull request.
- `MandatesTest` builds a full chain (a merchant's checkout, a user's closed checkout mandate with a key binding, and a payment mandate) and breaks it each way the check names: an unknown issuer, a tampered checkout, a wrong signing key, an expired credential, a cart whose total lies, a key binding by the wrong key, a wrong amount, and a kind that disagrees with its type.
- `JoseTest` shows that a JWT signed here verifies under Nimbus directly and the other way round, that a JWK round-trips through Nimbus's parser in the order the records carry, and that the claim sets read back with longs and doubles.

## The twins in the other languages

The Python client (`agentspaces-python`, `tests/test_commerce.py`), the TypeScript client (`agentspaces-typescript`, `test/commerce.test.ts`), and the Clojure bindings (`agentspaces-clj`, `test/agentspaces/commerce_test.clj`) read the vectors from a checkout of this repository beside theirs, at `../agentspaces-commerce/src/test/resources/vectors`. Each suite skips its vector test when that directory is absent.

## Source files

Every Java source, the POM, and the workflows carry the Apache 2.0 header. The hex vectors are data that hex cannot annotate; `LICENSE` covers them.
