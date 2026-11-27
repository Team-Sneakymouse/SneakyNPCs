# Handoff: NPC Screens (`/sneakynpcs opengui`, alias `opengui3`)

## What it is
Reimagined NPC menus that run beside the old `NPCGui` menus, which stay untouched until Rob swaps them. Each screen is a 6-row chest whose **title draws the whole window** as font glyphs: art, portrait and live text. **opengui3** is the current one and copies SneakyRoamin's `/roamin` technique: smooth 4x art drawn as two title glyphs (top 128 px, bottom 100 px) covering both the chest and the player inventory, with items sitting in drawn sockets. opengui2 (`screens2/`) is the earlier take; Rob called it "cheap" and it stays as it is.

## Status (2026-11-27): every NPC is on opengui3
**Per-NPC portraits (2026-11-27):** `tools/npc3_art.py` renders every portrait from the knowledge base's Citizens skins (`knowledge/world/reference/npc-skins`, `PORTRAITS` maps name -> Citizens id; slim arms supported in `npc2_render.py`). 17 portraits plus default; `screens.yml` sets one for every NPC with a Citizens skin, and all Jimmys use the orange-armour Jimmy (Citizens 23). No skin yet: DouglassWinechester, JackTimbers, RayCobbler, professoroakley, shop (default silhouette). Portrait glyphs moved to 0xE400+ (E210/E211 kept as aliases for older jars). The npc3 art now lives in craftengine-resources `resources/systems` (moved there by the dev-to-live commit), so regenerate into that module. Contact sheet: `docs/npc3_portraits.png`. Untested in game.
**Cleanup (2026-11-27, Rob: "we are set on using 3"):** opengui1 (old root/selection and shop chest menus) and opengui2 (`screens2/`, `npc2/*.yml`, its pack fonts) are deleted. `ShopMenu`, `SelectionMenu` and `CustomMenu` are now data only. `NPCGui` stays for the quest/tailor/taxes/bank/external rows. The "last bought" memory moved to `screens3/Npc3Memory.kt` and keeps its `npc2/` PDC key. `tools/npc2_render.py` and `tools/npc2_art/` stay because `npc3_art.py` reads them. The old art in craftengine-resources `resources/base` (`lom:npcs/*`, per-NPC `gui-<style>`) is still used by live's old SneakyNPCs and by the tailor/taxes/quest menus, so it stays; the selection `main` art, `tradewindow`, `pagecount`, `progressbar-gold/-reputation` and `friendship` can go once this build is on live.
Rob confirmed opengui3 works in game ("really good"), so the MythicHUD tile fix (TODO 0) is done. **`/sneakynpcs opengui <npc>` now opens opengui3 for every loaded NPC** (Citizens, SneakySneakymon and anything else that calls it); `opengui3` is an alias.
- `screens3/Npc3Config.kt` builds each hub from the NPC's own root menu in `npc/<id>.yml`: custom rows are dropped, each shop is a row (an NPC with several shops gets one per shop), a Sell row follows the last shop, and Farewell fills a free row, 4 at most (a warning is logged if more). npc2/*.yml is no longer read by opengui3 (opengui2 still uses it).
- Quest, tailor, taxes, bank and external rows open the old chest menu at that menu (`NPCGui(start, onExit)`); backing out of its first level (Esc or a click outside) reopens the opengui3 hub. External rows run their spell/command as before.
- Names, titles, portrait and row labels come from `src/main/resources/npc3/screens.yml` in the jar (no file on the server). Some titles there are guesses from the knowledge base.
- Still to do: every NPC uses the Goat Town palette and the default portrait (only Carl has his own); quest/tailor/taxes/bank still look like the old menus. Next steps are per-NPC portraits, district palettes, and opengui3-style quest/tailor/taxes/bank screens.

## Status (2026-11-03)
**Fix for the art boxes is pushed and waiting for a restart (2026-11-03 ~06:30).** With MythicHUD on dev, the big art glyphs showed as missing-glyph boxes and the text drifted left. This hit both opengui3 and `/roamin`; see TODO 0 for the cause. The fix tiles the art: craftengine-resources `d4864c2fa` (npc3) and `58e50a830` (roamin), SneakyNPCs `b57da80`, SneakyRoamin `5944d7c`. Rob's later `/roamin` shot (https://fast.rawb.tv/Fdg3k.png) still showed one box. It was taken before the deploy: `sneakyroamin-2026.11.03-5944d7cb8cd1.jar` was still in `~/dev-main/.update/`, and the "Build and activate dev candidate" run was still queued. The next step is Rob's re-test after a restart and the pack build. If it still breaks, check that the live pack's `lom/font/sneakyroamin/art.json` lists `_0_0`-style tile files and that the player rejoined to get the new pack. Rounds 1-3 visuals are still unverified behind this.
- **Hub**: name/title header; mirrored 3/4 Carl portrait in one brass frame with the standing under it; 4 option sockets (shop, sell, leave; the 4th is blank on purpose, "for future design use"); reputation `N / 100` with a gold bar in a track (fills over 0..100); an iron strap at y 126.5-138; a ledger over the hidden player inventory (`Npc3Mask`):
  - "Your Wallet" with Silver (light grey), Gold (gold) and Dragon Coins (light red), via `COIN_COLOURS`;
  - "<FirstName>'s Wallet" as a green 24-segment bar of balance / `wallet.max`. Its numbers, gold per hour and "Full again in 3h 20m" are on the hover tooltip (`walletLore`, `NpcWalletService.fill`);
  - "You last bought X here"; close X on hotbar col 4.
- **Shop**: 36 product sockets; a "Wallet … Till …" line; back (col 0), prev (2), page, next (4), glowing sell box (col 8). The player inventory isn't masked: shift-click and drag-to-sell work, and double-click is blocked.
- **Rob's feedback rounds, all pushed** (latest SneakyNPCs `ba771f6`, craftengine-resources `acffc564c`):
  - Round 1: fixed alignment (name-font space width), mirrored portrait, ledger contents, thousands separators, drag-to-sell.
  - Round 2: removed every nested panel ("extra boxes … look AI like"), so only sockets and the outer frame remain. The window is drawn as one board, so no seam. The iron strap hides the vanilla "Inventory" label.
  - Round 3: Your Wallet with coloured coins, the NPC wallet bar with hover details, reputation out of 100. Style reference: https://fast.rawb.tv/sBTWY.jpg.
- **Not started** (Rob's picks, rough priority): haggling needle minigame; friendship hearts and gifts dropped on the portrait; gossip (rep leaking within a district); animated portraits; a 3D offer on a counter; page-flip catalog with ribbon categories; phone/computer contacts. Also more district themes, real per-NPC portraits, and quest/tailor/taxes screens.
- **Rejected by Rob**: typewriter text, moods/mood prices, NPC talk/greetings, live market, shoplifting, daily deal, dialogue trees, diaries, letters, receipts, jobs board, heist/casino, haunted, caravans, player-run shops, strikes, birthdays.

## Key files
- `screens3/Npc3Screen.kt`: holder and listener, hub/shop titles and items, buy/sell, wallet bar/lore, `STANDINGS`, `MAX_REP`, `COIN_COLOURS`, `PURSE_CURRENCIES`.
- `screens3/Npc3Text.kt`: title text builder (`text/right/center/art`, `fit`, widths from `npc3_font.txt`) and `Npc3Layout` (pixel layout and colours).
- `screens3/Npc3Mask.kt`: packet mask that shows fake items over the player's inventory (copied from SneakyRoamin).
- `commands/OpenGUI3Command.kt`: `/sneakynpcs opengui3 <npc> [player]`.
- `shop/NpcWalletService.kt`: wallet restock; `fill()` gives the fraction and the time it's full again.
- `tools/npc3_art.py`: draws all art, fonts and the preview, and writes `src/main/resources/npc3_font.txt`. The palette dict `GOATTOWN` and layout constants are at the top.
- `docs/npc3_preview.png`: the latest preview (hub left, shop right).
- `screens3/Npc3Config.kt` + `src/main/resources/npc3/screens.yml`: hub rows from `npc/<id>.yml`, labels/names from the jar table. opengui2 still reads `npc2/<id>.yml`.

## Decisions and why
- **Title-font art** instead of item-model glyphs: much less machinery. The cost is that changing text means reopening, which recentres the mouse, so per-click data lives in tooltips.
- **No panels inside panels**: Rob's explicit dislike. Put things straight on the board; a rule or a bar track is fine.
- **One board**: `window()` draws the full 228 px height once and `split()` cuts it at 128. Two separately drawn halves gave a visible band.
- **"Inventory" label**: the client draws it after the title, so art can't cover it. The strap's face is exactly `#404040`, the label's colour, and the label has no shadow, so it vanishes. A lang override or shader would hide it on every chest, so that was rejected.
- **Reputation out of 100** matches the old NPCGui progress bar. The stored rep is uncapped, and the bar clamps.
- Commit straight to `main` in both repos (Rob: "dani wont mind").

## Gotchas
- Regenerate: `uv run --no-project --with pillow python tools/npc3_art.py ../craftengine-resources/resources/systems/resourcepack/assets/lom docs/npc3_preview.png`, then commit both repos.
- Layout numbers live in both `npc3_art.py` and `Npc3Layout`; change them together. Slot math: chest rows y 18+18r, player rows 139+18r, hotbar 197. Glyph ascent = 13 - y.
- Text fonts `lom:npc3/line_<y>` (y 5..222) and `lom:npc3/name` (y 4). Every glyph is followed by KERN (-1). ASCII 32-126 plus `BAR` only.
- **Widths gotcha**: if a font's space advance in `npc3_font.txt` differs from the provider's `space` advance, every later glyph drifts. That caused round 1's misalignment.
- The preview font bakes a shadow, so the stand-in "Inventory" shows faintly in the preview only.
- The title root colour must be white, or the art gets tinted.
- `./gradlew check` works on Windows here (no publishPreflight).
- craftengine-resources may hold other sessions' uncommitted work (e.g. `sneakyfishing_lure.bbmodel`); use `git pull --rebase --autostash` and stage only `assets/lom`.

## Open TODOs and known bugs
0. MythicHUD breakage: **fixed 2026-11-03, awaiting Rob's re-test.** Cause, from Rob's client log: ImmediatelyFast logs "Resource pack … is not compatible with font atlas resizing" for the MythicHUD pack (it overrides core text shaders) and turns resizing off. Vanilla's 256×256 font atlas page can't hold a 704 px glyph, so it showed the missing glyph. Players without ImmediatelyFast always saw that. Fix: window halves are cut into 128 px tiles (`TILE`/`TILE_BASE` 0xE220 in `npc3_art.py`, `tile` lines in `npc3_font.txt`, `Npc3Text.art` places them). SneakyRoamin got the same fix (`roamin_font.py`, `MenuText.art`). Rule: never make one font glyph bigger than 256 px. Earlier notes: **netty is ruled out:** MythicHUD 1.3.5's `ChannelDuplexHandlerImpl` touches only BossEvent, Bundle, SetExperience, SetHealth and UpdateAttributes packets, never OpenScreen, so `disable-netty-injection` can't change titles. Its pack (separate `packs/mythichud/…zip`) has fonts only in `mythichud:`, `minecraft/textures/font/*` overrides, `gui/bars.png`, and text shaders that just discard alpha < 0.1. Only the single 704×512 / 704×400 glyphs fail (the small 52 px portrait and line fonts work), and the advance falls back to the missing glyph's, so the client fails to load or bake those glyphs. Next: Rob's client `logs/latest.log` (lines with `font`, `glyph`, `npc3`, `sneakyroamin`, `atlas`, or errors after the pack reload), plus whether his client or mods changed. If the log shows a bake/atlas failure, cut the art into ≤256×256 tiles (art.py + `Npc3Text.art`, and SneakyRoamin's `art.json` too).
1. Rob's in-game re-test of rounds 1-3; act on his screenshots. Check: the strap really hides "Inventory", the bars line up with their tracks, and the wallet tooltip shows on hover over the ledger's right half.
2. Then his wish list above, ported to opengui3 style, as he picks.
3. Other districts need their own palette dict and art glyphs.

## How to test
After Rob restarts: `/sneakynpcs opengui CarlTheKobold` (any NPC id works).
- Hub: hover the right ledger for the wallet details; the options open the shop, sell or close.
- Shop: buy, sell (sell box, shift-click, drag across the sell box), pages, back. Esc or a click outside returns to the hub.
