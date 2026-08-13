# Reputation Shop Offer Requirement Design

## Goal

Allow an individual Shop Offer to require a minimum amount of the player's Reputation with the NPC that owns that offer.

## Configuration

Each Shop Item Requirement defines exactly one requirement type. A Reputation Requirement uses `reputation` as its inclusive minimum threshold:

```yaml
requirements:
  - reputation: 12.5
    hideOffer: true
```

`reputation` must be a finite, non-negative YAML number. Numeric strings, negative values, `NaN`, and infinities are invalid. A Reputation Requirement cannot include `variable` or `min`, and a variable requirement cannot include `reputation`. `hideOffer` remains optional and defaults to `false`.

The eventual implementation will add a fractional, hidden Reputation Requirement to `ExampleNPC.yml`.

## Evaluation

- The Reputation belongs implicitly to the NPC that owns the Shop Offer; configuration cannot reference another NPC.
- The requirement passes when the player's current Reputation is greater than or equal to the configured threshold. Fractional values are compared without integer truncation.
- Missing Reputation is Reputation `0.0`, so a zero threshold passes for every player.
- All Shop Item Requirements are conjunctive. Mixed variable and Reputation Requirements must all pass.
- Reputation Requirements mirror variable requirements: evaluate them when filtering visible offers and again immediately before Purchase.
- An unmet requirement with `hideOffer: true` hides the Shop Offer. An unmet requirement with `hideOffer: false`, or with the field omitted, leaves the offer visible but prevents Purchase.
- The Shop Menu does not refresh automatically when Reputation changes externally. A subsequent normal render reflects the new visibility, while the Purchase-time check prevents stale authorization.
- A rejected Purchase retains the existing generic message: `You do not meet the requirements.`

## Scope Boundaries

This requirement gates only Purchase of individual Shop Offers. It does not gate opening the Shop Menu, displaying bank balances, or selling items to the NPC.

