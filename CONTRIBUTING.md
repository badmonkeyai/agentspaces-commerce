# Contributing to AgentSpaces Commerce

Thank you for your interest in contributing. AgentSpaces Commerce is an Apache 2.0
project from Bad Monkey, Inc., and everything in this repository stays
Apache 2.0.

This repository holds the commerce records a trading group keeps in its
`commerce` space (`Mandate`, `Order`, `Cart`, `Basis`), the pure check of an
AP2 credential chain (`Mandates.verify`), and the small JOSE layer it uses
(`Jose`). The reserved names (`aspace:cap/commerce`, the roles, the `commerce`
space) and the space API live in the
[AgentSpaces core repository](https://github.com/badmonkeyai/agentspaces), and
the wire rules live in the
[AgentSpaces specification](https://github.com/badmonkeyai/agentspaces-spec).

## Before you start

- **Questions and ideas** are welcome as [GitHub issues](https://github.com/badmonkeyai/agentspaces-commerce/issues).
- **Security issues** must not be filed as public issues. See
  [SECURITY.md](SECURITY.md) for private reporting.
- **Code of conduct:** by participating you agree to the
  [code of conduct](CODE_OF_CONDUCT.md).

## Building and testing

```bash
mvn -B -ntp verify                                       # the records, the vectors, the JOSE layer, and the mandate checks
mvn -B -ntp verify -Pcentral-publish -Dgpg.skip=true     # plus the sources and javadoc jars, as CI runs it
node tools/docs-lint.mjs .                               # the README's shape and the docs index
```

Java 21 and Maven. The AgentSpaces 0.6.0 artifacts resolve from Maven Central.
The tests make fresh P-256 keys and need no network.
[docs/building.md](docs/building.md) says what each test pins.

## The golden vectors and their twins

Every record travels between peers in canonical CBOR, and three golden vectors
under `src/test/resources/vectors` pin those bytes: `mandate_cbor`,
`order_cbor`, and `capability_commerce_cbor`. Three other repositories read
the same files from a checkout of this one beside theirs:

| Twin | Repository | Test |
|---|---|---|
| `agentspaces.commerce` (Python) | `agentspaces-python` | `tests/test_commerce.py` |
| `commerce.ts` (TypeScript) | `agentspaces-typescript` | `test/commerce.test.ts` |
| `agentspaces.commerce` (Clojure) | `agentspaces-clj` | `test/agentspaces/commerce_test.clj` |

A test that fails on a vector means the change broke every one of those
readers. To run the twins against your change, clone the three repositories
beside this one and run their suites: `python -m pytest tests/test_commerce.py`,
`npm test`, and `clojure -M:test`.

## How to change a record

- **Do not change the bytes of a version.** A field's name, order, or type is
  part of the record's bytes. Peers in other languages decode the same bytes,
  and the strict wire read refuses a field a record does not name, so a new
  field breaks every reader that has not upgraded.
- **Add a version or a record instead.** A new field goes into a new
  `schemaVersion` of the record, or into a new record type beside it. Open an
  issue first, because the change also needs text in the AgentSpaces
  specification and a twin in each client.
- **Change the twins in the same release.** A pull request that changes a
  record links the matching pull requests in `agentspaces-python`,
  `agentspaces-typescript`, and `agentspaces-clj`.
- **Regenerate a vector only on purpose.** `CommerceGoldenTest` writes a
  missing vector file on the next run. Delete a vector only when its bytes are
  meant to change, and say why in the pull request.
- **Keep AP2 and UCP as published.** The `cart`, the `credential`, and the
  order `status` follow AP2 and UCP as published on 2026-11-05. A re-pin to a
  newer AP2 or UCP changes the vectors and this repository, and nothing in the
  core.

## The rules a change must keep

- **Verification stays pure.** `Mandates.verify` reads no policy, calls no
  network, keeps no state, and holds no key. A check that needs any of those
  belongs in the caller. A new check needs a test in `MandatesTest` that
  breaks the chain that way and asserts the reason.
- **No secret in a record.** No instrument, private key, or payment token
  appears in a `Mandate` or an `Order`. The `credential` map carries the
  public parts of a credential only.
- **Small and framework-free.** The library depends on `agentspaces-api`,
  Jackson, and Nimbus JOSE + JWT. Spring, Micronaut, and any payment SDK stay
  out of it; a host brings them.
- **The open side stays open.** The records and the checks name no product
  built on top of them.

## Pull requests

Open an issue first for anything beyond a fix. Keep the change small, add the
test that fails without it, and sign off your commits (DCO). CI runs the build,
the docs lint, and a dependency scan on every push and pull request.
