# Agent instructions (SneakyNPCs)

Team Sneakymouse / Lords of Minecraft plugin. Follow the shared conventions in EmptyPlugin's AGENTS.md.

## Repo-specific rules

- **Use the domain vocabulary.** Use the terms from CONTEXT.md (NPC, Menu, Root Menu, Shop Offer, Sellable Item, NPC Wallet, Physical vs Banked Currency) in code, config keys, messages and commits; avoid the 'Avoid' words it lists (mob, GUI, vendor, store).
- **Validate in ConfigManager, with a test.** Every new NPC YAML field or menu type is parsed and validated in ConfigManager.kt (throw ConfigValidationException with the source id) and gets a ConfigManager*ValidationTest beside the existing ones. Update ExampleNPC.yml so it still loads.
- **Money moves only through ShopTransactionService.** All buying, selling, wallet and stock changes go through shop/ShopTransactionService (with BalanceService, NpcWalletService, ShopItemStockService and the ShopSessionLedger) so they are all-or-nothing and logged; never adjust MagicSpells bank variables or items directly from a menu class.
- **Currency ids are shared with MagicSpells.** currencies.yml maps currency ids to MagicSpells item names (money-silver) and variables (bankSilver). Do not rename either side without changing MagicSpells-Data and SneakyNPCs together; keep exchangeTo/rate chains acyclic (CurrencyGraphService validates).
- **Player data writes are tmp + move.** Persist player data through PersistenceManager.savePlayerData (tmp file then moveFile, with backup on corrupt load) and mark PlayerData dirty; on disable the save stays synchronous. Do not write players/*.yml elsewhere.
- **Reload keeps old NPCs on error.** Reload must stay non-destructive: an NPC whose config fails validation keeps the previous instance and reports ConfigErrorInfo. Run reloads through reloadNpcConfigs (single-flight AtomicBoolean) and close open NpcGuis after.
- **Dispatching commands from config.** Console or player commands from NPC YAML run on the main thread via Bukkit.dispatchCommand after placeholder substitution; any new command source must substitute %player% safely and run on the main thread.
