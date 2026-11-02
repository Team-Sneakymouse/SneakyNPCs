# Handoff: NPC Screens 2 (`/sneakynpcs opengui2`)

## What it is
A reimagined NPC menu system that runs beside the old `NPCGui` menus, which stay untouched until Rob swaps them. Each screen is a 6-row chest whose **title draws the whole screen**: a district frame image, the NPC's portrait, and live text placed with shifted copies of the vanilla font. It's the same idea as dragonband_gui, styled after Spell Book 2.0. Nothing about an NPC's words is baked into art.

## Status (2026-11-01)
- **v1 done and pushed** (SneakyNPCs `main`, craftengine-resources `main` 2249572a7). It has **not been tested in game yet**; Rob restarts the server.
  - Hub: frame, portrait, name plaque, greeting in a speech bubble, up to 3 option plaques (`shop`, `leave`), and standing (rep label/number) + mood.
  - Shop: the old shop's offers via the same services (`purchase`/`sell`, requirements, stock limits), with a price in the lore. It has pages, a sell box (slot 43; shift-click from your inventory also sells), purse/till tooltips, and back. Esc or a click outside returns to the hub.
  - Wild picks done: **remember you** (the last purchase per character + NPC, in player PDC `sneakynpcs:npc2/<npc>/<charuuid>`; returning greeting) and **moods** (night = sleepy, rain = gloomy; picks the greeting pool).
- **Moods change prices** (2026-11-01): buy prices x1.1 sleepy, x0.9 gloomy (rounded, min 1), overridable per NPC with `moodPrices: {night, rain, default}`. Lore strikes through the base price; the hub shows `+10%`/`-10%` under the mood. Sell payouts are unchanged.
- Not started (Rob's picks, in rough priority): haggling needle minigame, friendship hearts + gifts dropped on the portrait, gossip (rep leaking within a district), animated portraits, the 3D offer on a counter, page-flip catalog with ribbon categories, phone/computer contacts. Also: more district frames, real per-NPC portraits, quest/tailor/taxes screens.
- Rejected by Rob: typewriter text, live market, shoplifting, daily deal, dialogue trees, diaries, letters, receipts, jobs board, heist/casino, haunted, caravans, player-run shops, strikes, birthdays.

## Key files
- `screens2/Npc2Screen.kt`: holder + listener, hub/shop pages, buy/sell, back/Esc handling.
- `screens2/Npc2Layout.kt`: pixel layout, `Npc2District` (frame chars), `Npc2Portraits`, `Npc2Text` (vanilla widths, wrap, space chars), `Npc2Title` (builds the title).
- `screens2/Npc2Config.kt`: `npc2/<id>.yml` (re-read on every open, so no reload needed), options, `Npc2Mood`.
- `screens2/Npc2Memory.kt`: character name/uuid via PlaceholderAPI (`%sneakycharacters_character_name_noformat%`), last purchase, greeting.
- `commands/OpenGUI2Command.kt`: `/sneakynpcs opengui2 <npc> [player]`. The old one is `/sneakynpcs opengui`.
- `src/main/resources/npc2/CarlTheKobold.yml`: example, saved to the data folder when `npc2/` doesn't exist.
- `tools/npc2_pack.py`: generates the frames, portrait and fonts into craftengine-resources `resources/dev/resourcepack/assets/lom/{font,textures/font}/npc2/`. Re-run it after layout changes and commit there.
- Only change to old code: `ShopMenu.items` / `bankDisplayCurrencyIds` are now `internal`.

## Decisions and why
- **Title-font rendering instead of Spellbook2's item-model glyphs**: far less machinery, and the pack already does GUI fonts this way (dragonband_gui). The cost: changing text means reopening the inventory, which recentres the mouse. So text that changes on every click (balances) lives in tooltips, and the title only changes on page/screen changes or after a buy/sell.
- Fonts: `lom:npc2/frame` (space chars `+i` = +2^i and `+i` = -2^i; frames `` hub and `` shop; portrait ``), and `lom:npc2/text_<top>` for each line top from 5 to 140 (vanilla `ascii.png`, ascent 13 - top).
- `npc2/` folder kept apart from `npc/` (Rob). Districts are frames (Rob); only Goat Town exists so far.
- Commits go straight to `main` (Rob: "dani wont mind").

## Gotchas
- First in-game test (2026-11-02) found: the title's default grey (0x404040) tinted the art to 25% (fixed: title root is white); text fonts with top < 5 have ascent > height and don't load, so the name showed as boxes and every later text shifted right (fixed: tops 5..140, name plaque moved to y 0..15); the frame now ends at y 128 so the "Inventory" label is clear.
- Layout numbers live in both `Npc2Layout.kt` and `tools/npc2_pack.py`; change them together.
- Text fonts only draw ASCII 32–126 (anything else becomes `?`). Widths are the vanilla table in `Npc2Text`.
- The hub art always draws 3 option plaques; with 2 options the third is blank.
- Build: `./gradlew check` works in this repo on Windows (it has no publishPreflight task).
- Previews (`tools/npc2_pack.py <assets/lom> <preview-dir>`) draw the frames only; the in-game text uses the client font.

## How to test
After Rob restarts the dev server (the pack must be rebuilt by the "Build and activate dev candidate" workflow), run `/sneakynpcs opengui2 CarlTheKobold`. Check:
- The frame lines up with the slots and the text sits inside its panels.
- Buy, sell (sell box and shift-click), pages, Back and Esc all work.
- Reopening after a purchase gives the returning greeting.
- `/sneakynpcs opengui CarlTheKobold` is unchanged.
If the frame or text is offset, adjust `FRAME_X/FRAME_Y` or the text tops.
