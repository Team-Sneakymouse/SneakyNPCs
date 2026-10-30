# NPC integrations

## MagicSpells currencies

[`currencies.yml`](../src/resources/currencies.yml) owns the mappings from currency IDs to MagicSpells MagicItem IDs (`item`) and player variables (`variable`). The deployed items and variables are defined in [MagicSpells-Data](https://github.com/Team-Sneakymouse/MagicSpells-Data), on branch `dev`; check those definitions when changing a mapping.

Currency IDs also occur in MagicItem stored-value PDC and persisted NPC Wallet balances and native-currency fields. Renaming an ID requires preserving compatibility with existing items and player data or migrating them, as well as updating deployed configurations. The stored-value keys and currency configuration format are described in [Shop Feature Design](shop-feature-design.md); NPC Wallet persistence is described in [Shop Feature Design V2](shop-feature-design-v2.md).

## Commands and placeholders

`ExternalMenu` invokes `ms cast as <Minecraft player name> <magicSpellId>` as console. `QuestMenu` invokes the `questcomplete` spell through the same command form after completing a Quest. These depend on the corresponding deployed MagicSpells definitions.

Configured Quest command rewards choose console or player execution through `asConsole` and expand placeholders through PlaceholderAPI when available. Without PlaceholderAPI, the command is dispatched unchanged and unresolved placeholders produce a warning; there is no separate `%player%` replacement in SneakyNPCs.

`TailorMenu` depends on [SneakyMannequins](https://github.com/Team-Sneakymouse/SneakyMannequins): it reads `%sneakymannequins_skin_session_uid%` and invokes `sneakymannequins:mannequin item shirt`, `pants`, or `shirt pants` as the player. Missing mannequin/item permissions are granted temporarily around dispatch and removed afterward. The external and tailor handoffs close the Menu before dispatching on the next tick.
