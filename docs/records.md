# Records

Written for: Java developers who write or read mandates and orders, and anyone who builds a twin of these records in another language.

A group that trades keeps its mandates and orders as entries of its `commerce` space. This library holds the records those entries carry. The AgentSpaces specification defines the names (SPEC §7.6 and §8), and golden CBOR vectors pin the bytes.

## The reserved names

The core's `ai.badmonkey.agentspaces.api.ad.Reserved` holds every name, so a peer can advertise and find a trading party without this library:

| Name | Constant | What it means |
|---|---|---|
| `aspace:cap/commerce` | `Reserved.CAPABILITY_COMMERCE` | A capability a peer advertises to say what it does in a trade. |
| `role` | `Reserved.ROLE` | The capability's required parameter: `merchant`, `shopper`, or `payment` (`Reserved.COMMERCE_ROLES`). |
| `profile` | `Reserved.PROFILE` | An optional parameter: the URL of the merchant's UCP business profile, at `/.well-known/ucp`. |
| `ucp` | `Reserved.UCP` | An optional parameter: a comma-separated list of the UCP capability ids the peer serves. |
| `jwk` | `Reserved.JWK` | An optional parameter: the public key the peer signs mandates with, as an RFC 7517 JWK in JSON. |
| `commerce` | `Reserved.SPACE_COMMERCE` | The space for mandates and orders. A group creates it with `LEASE_RACE`, `AUTHORIZER` admission, and a one-year lease on its entries, and only when it trades. |

A reader without this library sees `commerce` entries as typed maps.

## Mandate

`Mandate(schemaVersion, id, kind, role, cart, counterparty, basis, credential, state, at)` carries one AP2 mandate as an entry.

| Field | What it holds |
|---|---|
| `schemaVersion` | `Mandate.VERSION`, which is 1. |
| `id` | The mandate's id. It must not be blank. |
| `kind` | `checkout` or `payment` (`Mandate.KINDS`). |
| `role` | Who wrote it: `merchant`, `shopper`, or `payment` (`Mandate.ROLES`). |
| `cart` | What the mandate is about, as a `Cart`. |
| `counterparty` | The other party: a peer id or a profile URL. It defaults to the empty string. |
| `basis` | What a shopper's mandate rests on, as a `Basis`. It defaults to `Basis.NONE`. |
| `credential` | The AP2 verifiable credential as issued, its public parts only, as a map. |
| `state` | `issued`, `settled`, `refused`, or `expired` (`Mandate.STATES`). `withState` returns a copy in another state. |
| `at` | When the mandate was written. |

The `credential` map uses four keys:

| Key | Constant | What it holds |
|---|---|---|
| `issuerSignedJwt` | `Mandate.ISSUER_SIGNED_JWT` | The issuer-signed JWT of the credential, in compact form. |
| `disclosures` | `Mandate.DISCLOSURES` | The selective disclosures, as a list of strings. |
| `kbJwt` | `Mandate.KB_JWT` | The key-binding JWT, in compact form. |
| `checkoutJwt` | `Mandate.CHECKOUT_JWT` | The merchant-signed checkout JWT that a payment mandate binds to. |

No instrument, private key, or payment token appears in a mandate. The issuer signs with a key it keeps outside this library.

## Cart and Basis

`Cart(items, total, currency)` holds at most 64 items (`Cart.MAX_ITEMS`). The total is in ISO 4217 minor units, so 3000 in `EUR` is 30 euros, and the currency is a three-letter ISO 4217 code. Each `Cart.Item(sku, title, quantity, unitPrice)` names the UCP item id as its `sku`, with a quantity of at least one and a unit price that is not negative. `itemsTotal()` sums the items, and `Mandates.verify` compares that sum with `total`.

`Basis(approval, decision)` names what a shopper's mandate rests on: the id of an approval a person gave, or the id of a decision a jury of agents took. Both are empty for a merchant's or a payment party's mandate. The record accepts a shopper's mandate without a basis, and a reader that requires one refuses it by its own rule.

## Order

`Order(schemaVersion, id, mandate, status, fulfillment, permalink, at)` carries the UCP order a mandate led to. `mandate` is the id of the checkout mandate. `status` is one of the UCP checkout statuses as published: `incomplete`, `requires_escalation`, `ready_for_complete`, `complete_in_progress`, `completed`, or `canceled` (`Order.STATUSES`). `fulfillment` is the fulfillment as the merchant reports it, in UCP's shape, and `permalink` is where a person sees the order.

## What a record refuses

Each constructor refuses what the record must never carry, so a bad value fails where code builds it:

- a mandate with a null or blank id, an unknown kind, role, or state, a null cart, or a null time;
- an order with a null id, an unknown status, or a null time;
- a cart with more than 64 items, a negative total, or a currency that is not three letters;
- an item with a null sku, a quantity below one, or a negative unit price.

A null optional field (a counterparty, a basis, a credential, a fulfillment, a permalink, a title) becomes its empty value.

## The bytes

Records travel between peers in the canonical CBOR of `agentspaces-common`. Three golden vectors under `src/test/resources/vectors` pin the bytes: `mandate_cbor`, `order_cbor`, and `capability_commerce_cbor` (a merchant's `CapabilityAdvertisement`). The Python client (`agentspaces.commerce`), the TypeScript client (`commerce.ts`), and the Clojure bindings (`agentspaces.commerce`) read the same files and round-trip them byte for byte. [CONTRIBUTING.md](../CONTRIBUTING.md) says how to change a record without breaking those readers.
