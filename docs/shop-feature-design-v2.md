# Shop Feature Design V2

## Summary
V2 adds sell-side transactions and player-specific NPC wallets on top of the existing buy-side shop system.
Wallets are stored in player data, keyed by NPC id, and restock over real time even while the player is offline.

## Currency Config
`currencies.yml` adds an optional `sellable` boolean.

- Default: `false`
- Only items whose stored PDC value currency is marked `sellable: true` may be sold to NPCs
- Selling uses the item's stored currency directly

Example:

```yaml
currencies:
  silver:
    item: money-silver
    variable: bankSilver
    sellable: true
    exchangeTo: gold
    rate: 50
```

## NPC Config
NPC wallet config moves to a dedicated object:

```yaml
wallet:
  currency: silver
  max: 1000
  restockInterval: 3600
  restockAmount: 100
```

Meaning:
- `currency`: native wallet currency
- `max`: maximum wallet size in native currency units
- `restockInterval`: seconds between restocks
- `restockAmount`: native currency units added each interval

## Wallet Model
Each player has a separate wallet state per NPC.

Stored fields:
- `nativeCurrency`
- `lastRestockAt`
- `balances`

Runtime rules:
- wallets are bound to NPC ids, not menu instances
- first access initializes the wallet full in the native currency
- if the configured native currency changes, that wallet state is reinitialized
- the wallet may hold multiple currencies internally
- after restock or spending, balances are normalized to prefer the native currency first, then lower-value remainders

## Sell Flow
Sell entry points while a shop is open:
- place a stack into slot `44`
- drag a stack so only slot `44` is affected
- shift-click a stack from the player inventory

Slot `44` is functional only. The GUI does not render a prompt item there.

Prechecks:
- item has valid stored PDC value keys
- item currency exists and is `sellable`
- item currency is convertible with the NPC native wallet currency
- NPC wallet can cover the full stack value
- payout can be delivered

Payout rules:
- exact stored item value, no markdown
- payout uses the item's own currency
- bank payout is preferred when that currency has a variable
- otherwise payout is given as physical currency items
- if physical payout does not fit, the sale fails

V2 does not implement partial sales or NPC buyback inventory.

## Offline Restock
Wallet restock uses discrete interval catch-up.

- elapsed full intervals are computed from `lastRestockAt`
- each full interval adds `restockAmount` in native currency value
- total value is clamped to the configured max
- if the wallet reaches full cap, `lastRestockAt` is reset to the current time

## Persistence
Wallet state is saved into the same player YAML files that already store quests and reputation.

Example layout:

```yaml
npcWallets:
  jacktimbers:
    nativeCurrency: silver
    lastRestockAt: 1710000000000
    balances:
      silver: 940
      penny: 10
```
