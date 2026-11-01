# Handoff: NPC Screens 2 (`/opengui2`)

## What it is
A reimagined NPC menu system for SneakyNPCs, drawn as CraftEngine screens in the style of SneakyWizards' Spell Book 2.0. It runs beside the original `NPCGui` menus, which stay untouched (code and `/opengui`), until Rob decides it's ready to replace them.

## Status
- Done: study of both systems, and Rob's design decisions (below).
- Not started: everything. Build **v1 = greeting/hub screen + shop screen** only.
- Later passes: quest, tailor, taxes, custom screens.

## Key files (existing)
- `src/main/kotlin/com/danidipp/sneakynpcs/NPCGui.kt`: old 54-slot chest holder with a menu stack. Back = close/outside click. Shop drag and shift-click routing.
- `menus/Menu.kt`: `NPCMenu` base class and `makeItem(model, cmd)` (BRICKS + item_model + custom model data).
- `menus/SelectionMenu.kt`: hub. Slot 0 is `lom:npcs/gui-<style>` with string `main`; the options are the slot bands 14-16/23-25/32-34/41-43.
- `menus/ShopMenu.kt`: 24 product slots, page toggle 44, sell slot 43, wallet 2. It uses `ShopTransactionService.purchase`, `ShopSessionLedger` and `RequirementService`. **Reuse these services; don't fork the money logic.**
- `commands/OpenGUICommand.kt`: copy its shape for `/opengui2`.
- `ConfigManager.kt` (1.8k lines): loads `npc/*.yml`.
- Spell Book 2.0 reference (Java, read via `git -C ../SneakyWizards show origin/main:<path>`; the local checkout sits on the `agents/repo-rules` branch):
  - `SpellBook.java`: `Book` holder; empty title; oversized panel item in `PANEL_SLOT` 49; buttons in the player's hotbar row.
  - `TextLayout.java` + `src/main/resources/spellbook-layout.json`: text packed into CustomModelData strings; fields have first/positions/lines/pitch/mono; flags and colours.
  - The pack's glyph models live in `repos/craftengine-resources` (search for `sneakywizards_spellbook2_panel`).

## Decisions (Rob, 2026-11-01)
- Command: `/opengui2 <npc> [player]`; permission like the old one.
- Art: **frames themed by district** (Goat Town, Moonbay, Slums, Southshire, Darkvale, Grove, Royal, Brickton, Magic School, Dwarven...), with all text generated live like Spellbook2 (NPC name, greeting, prices, rep). No baked-in text.
- Config: a **separate `npc2/` folder** (one yml per NPC: district, portrait, greeting lines, which shop/stock it reuses), kept apart from `npc/`.
- Repo: commit **straight to `main`** (Rob: "dani wont mind"), even though this is DaniDipp's repo.
- Wild features wanted (v1 can stub or skip; pick the cheap wins):
  1. NPCs remember you: greeting names your character and your last purchase.
  2. Moods: portrait, greeting and prices shift with time, weather and how you treated them.
  3. Gossip network: reputation leaks to friends in the same district.
  4. Haggling minigame: a swinging needle; stop it in the green; push too hard and you're kicked out for an hour.
  5. Friendship hearts and gifts: drag a gift onto the portrait; hearts unlock stock and discounts. (The old `friendship:` flag was never built.)
  6. Animated portraits: blink, talk while text shows, react on buy/fail.
  7. Hovering an offer shows it big in 3D on the counter, like Jimmy's wand bench.
  8. Shop as a page-flip catalog: ribbon-bookmark categories, page-turn sound.
  9. Phone/computer contacts: order from SneakyCellPhones, browse stock from SneakyComputer. (Later.)
- Rejected by Rob: typewriter text, live market, shoplifting, daily deal, dialogue trees, diaries, letters, receipts, jobs board, heist/casino tie-ins, haunted NPCs, caravans, player-run shops, strikes, birthdays.

## Gotchas
- Assets go in `repos/craftengine-resources/resources/dev`, `lom` namespace. Asset authoring only; follow that repo's README and the root CLAUDE.md item recipe. Pull and rebase before pushing; a push deploys to dev.
- Plugin push to main → publish.yml → Reposilite → jar in `~/dev-main/.update/`. Rob restarts the server; don't restart it.
- Windows build: `./gradlew check -x publishPreflight`, then `bash scripts/publish-preflight.sh .` if publishPreflight fails (WSL bash).
- Read `AGENTS.md`, `CONTEXT.md` and `docs/integrations.md` first (repo rules).
- An inventory click outside the window = back, in the old GUI. Keep that behaviour.
- Survival players get tooltips hidden (`shouldHideTooltip`).

## Open TODOs (priority)
1. Draw the screen layout (frame, portrait area, text fields, shop grid, hotbar buttons) and write `npc2-layout.json` à la spellbook-layout.json. Port or share TextLayout (Java → Kotlin, or copy).
2. Pack: one district frame for v1 (Goat Town, for CarlTheKobold) plus glyph models; others follow.
3. `npc2/` config loader, `NpcScreen` holder, hub screen, shop screen (reusing the shop services), `/opengui2`.
4. Remember-you greeting + last purchase (PlayerData); then moods; then haggle.
5. Tests for the pure pieces (layout packing, config parsing), like the existing 23 tests.

## How to test
On the dev server after a restart, run `/opengui2 CarlTheKobold`. Check that the frame and text render, the shop buys and sells the same as `/opengui CarlTheKobold`, Back works, and the old `/opengui` is unchanged.
