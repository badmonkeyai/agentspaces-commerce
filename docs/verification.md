# Verification

Written for: developers who verify mandates, and reviewers who need to know what `Mandates.verify` proves and what it leaves to the caller.

`Mandates.verify(mandate, trustedIssuers, now)` checks one mandate's AP2 credential chain and returns a `Verdict(valid, reason, issuer)`. It reads no policy, calls no network, and keeps no state, so it runs wherever a member reads the entry. What a verdict means for a trade is the caller's decision.

## The trust map

`trustedIssuers` maps a key id (`kid`) to a public key: the merchants, users, and payment parties this reader trusts. The caller builds the map from whatever it trusts, such as the `jwk` parameter of a merchant's `aspace:cap/commerce` advertisement, a key store, or a directory. The library never fetches a key and never adds one.

## The checks, in order

The check stops at the first failure and names it in `reason`. On success, `reason` is `ok` and `issuer` is the `kid` of the issuer-signed JWT.

1. **The cart adds up.** The sum of the items' subtotals equals the cart's `total`.
2. **The credential carries an issuer-signed JWT.** The `issuerSignedJwt` key holds a compact JWT that parses.
3. **The issuer is trusted.** The JWT's header `kid` names a key in the trust map.
4. **The signature verifies.** The JWT is ES256 and its signature verifies under that key.
5. **The type is an AP2 mandate type.** The payload's `vct` is one of `mandate.checkout.1`, `mandate.checkout.open.1`, `mandate.payment.1`, or `mandate.payment.open.1` (`Mandates.VCTS`).
6. **It has not expired.** When the payload has `exp`, `now` is not past it.
7. **The kind matches the type.** A `checkout` mandate carries a checkout type, and a `payment` mandate a payment type.
8. **A closed checkout mandate binds its checkout.** For `mandate.checkout.1`, the payload carries `checkout_jwt`, its `checkout_hash` is the base64url SHA-256 of that JWT, and that JWT is an ES256 JWT that verifies under a trusted `kid` (the merchant's).
9. **A closed payment mandate binds the transaction and the amount.** For `mandate.payment.1`, when the credential carries `checkoutJwt`, the payload's `transaction_id` is its digest. The payload's `payment_amount` holds the cart's total as `amount` and the cart's currency as `currency`.
10. **The key binding holds.** When the credential carries `kbJwt`, the issuer-signed payload carries `cnf.jwk`, the key-binding JWT verifies under that key, and its `sd_hash` is the digest of the presentation. The presentation is the issuer-signed JWT, a `~`, and each disclosure followed by a `~`.

The open mandate types (`mandate.checkout.open.1`, `mandate.payment.open.1`) pass steps 1 to 7 and 10. Their constraints apply when an agent closes them, so steps 8 and 9 do not apply.

## What it does not check

- **No policy.** It does not decide whether a trade is allowed, whether a shopper's mandate needs a basis, or whether an amount is within a limit. The caller applies its own rules to a valid verdict.
- **No network.** It does not fetch keys, profiles, status lists, or revocation data, and it does not call a merchant or a payment network.
- **No key storage.** It holds no private key and signs nothing. `Jose.sign` takes a private key the caller holds, such as one from a vault.
- **No replay or nonce state.** It does not remember mandates it has seen, and it does not check a key-binding JWT's `aud`, `nonce`, or `iat`. A caller that needs those checks reads them from the parsed JWT.
- **No disclosure contents.** It binds the disclosures through `sd_hash` but does not decode them.

## Jose

`Jose` is the small part of JOSE that mandates use, as a thin layer over Nimbus JOSE + JWT:

| Method | What it does |
|---|---|
| `sign(header, payload, privateKey)` | A compact ES256 JWT (RFC 7515, RFC 7518) over the payload, with `alg` and `typ` filled in when the header lacks them. The key must be a P-256 private key. |
| `parse(compact)` | The header and payload as maps, the signing input, and the signature, without verifying anything. |
| `verify(jwt, publicKey)` | Whether the JWT is ES256 and its signature verifies under the P-256 key. |
| `digest(s)` | The base64url SHA-256 of a string's UTF-8 bytes, which is how AP2 binds one JWT to another. |
| `base64url`, `fromBase64url`, `sha256` | The encodings of RFC 4648 §5 and the SHA-256 digest. |
| `generateP256()` | A fresh P-256 key pair, for a vault to keep or for a test. |
| `toJwk(publicKey, kid)`, `fromJwk(map)` | A P-256 public key as an RFC 7517 JWK map (`kty`, `crv`, `x`, `y`, `kid`, `alg`, `use`, in that order), and back. |

Nimbus does the signing, the verification, the key parsing, and the encoding, so a JWT that `Jose` signs verifies under Nimbus directly, and the other way round. Jackson writes and reads the claim sets: keys keep insertion order on write, integral numbers read back as `Long`, and other numbers as `Double`. A verifier therefore compares amounts and times the same way whichever side wrote them. The versions of both libraries come from the `agentspaces-dependencies` BOM.
