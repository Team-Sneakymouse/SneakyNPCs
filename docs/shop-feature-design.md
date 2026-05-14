# Shop Feature Design Document (MVP + V2 Notes)

## Summary
This document specifies the MVP shop system for SneakyNPCs and the high-level V2 direction.
MVP is buy-only, uses strict greenfield configs, and integrates with MagicItems + MagicVariables.

Price metadata is read from MagicItem PDC:
- `magicspells:magicspellpermanentdata_store_value_currency` (string currency id)
- `magicspells:magicspellpermanentdata_store_value_amount` (string that must parse to int)

## Scope
MVP includes:
- Buy-only transactions.
- Strict config loading from `currencies.yml` and `npc/*.yml`.
- Shop GUI with unbounded pagination in 24-item pages.
- Inventory-first, bank-second payment source order.
- Requirement checks against MagicVariables.

V2 notes only:
- NPC wallet restock logic.
- Sell-to-NPC flow.

## Data Model
### CurrencyDefinition
- `id`
- `item` (optional MagicItem id for physical currency)
- `variable` (optional MagicVariable id for bank storage)
- `exchangeTo` (optional single target currency id)
- `rate` (optional positive int; units of source needed for 1 target unit)

Reverse exchange is auto-generated from configured forward links.

### ShopMenuItem
- `item` (MagicItem id)
- `buyStacks` (bool, default false)
- `requirements` (optional list of `{ variable, min }`)
- `price` (derived from item PDC, not from NPC config)

## Config Layout
### `currencies.yml` (top-level)
- Required section: `currencies:`
- Currency ids are case-sensitive.
- Each currency may define item storage, variable storage, or both.
- Exchange links must reference existing currencies and use `rate > 0`.

### NPC configs
- Location: `plugins/SneakyNPCs/npc/*.yml`
- Strict key style uses camelCase (`buyStacks`, `maxGold`, `restockInterval`, `restockAmount`).
- Legacy key spellings are not supported.

Reserved fields for V2 at NPC root:
- `maxGold`
- `restockInterval`
- `restockAmount`

## Runtime Behavior
### Loading
1. Load and validate `currencies.yml`.
2. Build currency graph and auto-inverse links.
3. Validate exchange consistency.
4. Load and validate `npc/*.yml`.

### Shop GUI
- Keep existing 24-slot product layout.
- If items > 24, page toggle appears in slot `44`.
- Shops can contain any number of configured items; pages are generated in 24-item chunks.
- Left click buys 1.
- Shift click buys max stack (respect item max stack size) when `buyStacks: true`.

### Purchase Flow
1. Validate requirements.
2. Validate destination inventory space for bought items.
3. Build payment plan with multi-hop currency conversion support.
4. Deduct inventory currencies first, then bank variables.
5. Apply change policy:
   - If price currency has a variable, change always goes to bank variable.
   - If price currency has no variable, exact payment is required.
6. Grant purchased item.

### Feedback
- Success: play `lom:buy`, no success chat message.
- Failure: play `lom:fail_wrong` and send short hardcoded chat reason.

## Validation Rules
- Unknown currency id in item PDC: config error.
- Price amount PDC not parseable int: config error.
- Invalid exchange target/rate: currency config error.

## Test Scenarios
1. Parsing and validation
- Valid PDC amount string parses.
- Invalid amount string fails.
- Unknown currency id fails.
- Invalid exchange definitions fail.
- Shops with more than 48 items parse and paginate.

2. Currency conversion behavior
- Forward + auto-inverse conversions are consistent.
- Multi-hop conversion supports affordability checks.
- Deterministic conversion values across reloads.

3. Payment ordering
- Inventory funds are consumed before bank funds.

4. Change policy
- Change always credits bank when price currency has variable.
- Item-only price currencies require exact payment.

5. Requirement checks
- Variable minimums gate purchases correctly.

6. GUI behavior
- Page toggle on slot 44.
- Page boundaries enforced.
- Left-click and shift-click quantity behavior.

7. Failure atomicity
- Failed prechecks do not mutate balances or inventory.

## V2 Notes (High-Level)
- Activate NPC wallet fields for sell-side transactions.
- Implement NPC buyback behavior and wallet depletion/restock.
- Add richer requirement/message/sound customization if needed.
