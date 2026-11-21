# NPC integrations

## MagicSpells currencies

[`currencies.yml`](../src/resources/currencies.yml) owns the mappings from currency IDs to MagicSpells MagicItem IDs (`item`) and player variables (`variable`). The deployed items and variables are defined in [MagicSpells-Data](https://github.com/Team-Sneakymouse/MagicSpells-Data), on branch `dev`; check those definitions when changing a mapping.

Currency IDs also occur in MagicItem stored-value PDC and persisted NPC Wallet balances and native-currency fields. Renaming an ID requires preserving compatibility with existing items and player data or migrating them, as well as updating deployed configurations. The stored-value keys and currency configuration format are described in [Shop Feature Design](shop-feature-design.md); NPC Wallet persistence is described in [Shop Feature Design V2](shop-feature-design-v2.md).

## Commands and placeholders

`ExternalMenu` is configured with exactly one of `magicspell` or `command`:

- `magicspell: <id>` invokes `ms cast as <Minecraft player name> <id>` as console. The spell is looked up on each click: if MagicSpells doesn't have it, the player is told the option is unavailable and nothing is dispatched. At reload an unknown id is a warning (listed in the reload summary as "has warnings"), not an error, so the NPC and its other options still load.
- `command: "<command>"` dispatches the command as console after expanding PlaceholderAPI placeholders, for example `command: "nadiyaslots open %player_name%"`. There is no `asConsole` option: external commands always run from the console. Without PlaceholderAPI, a command that contains placeholders is not dispatched and the player is told to report it.

`QuestMenu` invokes the `questcomplete` spell through the same command form after completing a Quest. These depend on the corresponding deployed MagicSpells definitions.

Configured Quest command rewards choose console or player execution through `asConsole` and expand placeholders through PlaceholderAPI when available. Without PlaceholderAPI, the command is dispatched unchanged and unresolved placeholders produce a warning; there is no separate `%player%` replacement in SneakyNPCs.

`TailorMenu` depends on [SneakyMannequins](https://github.com/Team-Sneakymouse/SneakyMannequins): it reads `%sneakymannequins_skin_session_uid%` and invokes `sneakymannequins:mannequin item shirt`, `pants`, or `shirt pants` as the player. Missing mannequin/item permissions are granted temporarily around dispatch and removed afterward. The external and tailor handoffs close the Menu before dispatching on the next tick.

## Bank

The bank menu (`type: bank`) reads and writes these SneakyVariables variables, defined in MagicSpells-Data's `spells-city/spells-city-bank.yml`: the coin balances from `currencies.yml` (`bankPenny`, `bankSilver`, `bankGold`), the display-only balances `bankDiamond`, `bankMythril` and `bankDragonCoin` (read directly, so they need no currency, and left blank if undefined) and the loans `bankLoanPenny`, `bankLoanSilver` and `bankLoanGold`. Deposits never push a balance past its variable's `max`; the menu refuses them instead. Its default Bank Gates read the Trading skill variables `skillTradingTier6A` (Bank Voucher: withdrawing notes), `skillTradingTier3A` (Loans License) and `skillTradingTier9C` (Trade Mule: exchanges) from `spells-skillTree/spells-skillTree-Trading.yml`.

The bank note MagicItems (`item-pennyNote1k` … `item-goldNote1m`, in `spells-items/spells-item-bankNote.yml`) and `item-holyWrit` (5 per loan) stay registered by MagicSpells. Reload fails for an NPC whose bank menu needs a variable, item or currency that isn't there.

Right-clicking a bank note deposits it while the player stands in the WorldGuard region named by the first loaded bank menu's `noteDepositRegion` (default `world:bank`). It goes through SneakyItems' `MagicItemClickEvent`. Without WorldGuard, notes are only deposited from the menu.
