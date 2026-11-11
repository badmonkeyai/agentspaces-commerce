# Security Policy

AgentSpaces Commerce defines the mandates and orders a trading group keeps in
its `commerce` space, and the check a member runs before it acts on a mandate.
A reader trusts `Mandates.verify` to tell it whether a credential chain holds,
so that check carries part of the trust in a trade. The library inherits the
security model of the AgentSpaces core (signed, self-certifying peers and
agents; the threat model in the AgentSpaces specification, SPEC §11).

## Reporting a vulnerability

Please report vulnerabilities **privately** via GitHub's security advisories:

> https://github.com/badmonkeyai/agentspaces-commerce/security/advisories/new

A vulnerability in the space API, the wire codec, peering, or signatures
belongs in the core repository's advisories:
https://github.com/badmonkeyai/agentspaces/security/advisories/new

Do not open public issues for suspected vulnerabilities, and do not include
exploit details in public discussions until we release a fix.

We will acknowledge your report within five business days, keep you informed of
progress, credit you in the advisory unless you prefer otherwise, and
coordinate the disclosure timeline with you. There is currently no bug bounty
program.

Questions can be sent to `oss [at] badmonkey.ai`

## What Mandates.verify checks

`Mandates.verify(mandate, trustedIssuers, now)` checks one mandate's AP2
credential chain:

- the cart's items add up to its total;
- the issuer-signed JWT is ES256 and verifies under the key its `kid` names
  in the caller's trust map;
- its `vct` is an AP2 mandate type that matches the mandate's kind, and it
  has not expired at `now`;
- a closed checkout mandate's `checkout_hash` is the digest of the
  `checkout_jwt` it carries, and that JWT verifies under a trusted merchant;
- a closed payment mandate's `transaction_id` is the digest of the checkout
  JWT the credential carries, and its `payment_amount` is the cart's total and
  currency;
- a key-binding JWT verifies under the `cnf.jwk` key, and its `sd_hash` is the
  digest of the presentation.

[docs/verification.md](docs/verification.md) lists each check in order.

## What it does not check

- **No policy.** It does not decide whether a trade is allowed, whether a
  shopper's mandate needs a basis, or whether an amount is within a limit.
- **No network.** It fetches no key, profile, status list, or revocation
  record, and it calls no merchant or payment network.
- **No key storage.** It holds no private key and signs nothing. `Jose.sign`
  takes a key the caller holds.
- **Trust is the caller's map of kids.** A key in `trustedIssuers` is trusted
  for every role. The library does not tie a `kid` to a role, a peer, or a
  domain, so a caller that must keep a merchant's key from signing as a user
  builds a separate map for each check.
- **No replay state.** It remembers no mandate it has seen, and it does not
  check a key-binding JWT's `aud`, `nonce`, or `iat`.

## Scope

In scope: anything in this repository that makes `Mandates.verify` return a
valid verdict for a chain that one of its checks should refuse, or that makes
a record carry what it must not. For example:
- a JWT that verifies under a key other than the one its `kid` names in the
  trust map, or under an algorithm other than ES256;
- a closed checkout mandate whose `checkout_hash` does not bind the checkout
  JWT it carries, and that still verifies;
- a payment mandate whose amount or currency differs from the cart's, and
  that still verifies;
- a key-binding JWT that verifies under a key other than `cnf.jwk`, or with an
  `sd_hash` over another presentation;
- a `Jose.fromJwk` that accepts a key outside P-256, or a point off the curve;
- bytes that decode into a record its constructor refuses.

Out of scope: the parties that sign and pay, which live in their own projects,
and the policy a caller applies to a valid verdict. Who may join a group and
write to its `commerce` space is the group's admission policy, which the core
enforces.

## Supported versions

During 0.x, only the latest minor release receives security fixes.
