# Handoff: NPC Screens (`/sneakynpcs opengui3`, also opengui2)

## What it is
Reimagined NPC menus that run beside the old `NPCGui` menus, which stay untouched until Rob swaps them. Each screen is a 6-row chest whose **title draws the whole window** as font glyphs: art, portrait and live text. **opengui3** is the current one and copies SneakyRoamin's `/roamin` technique: smooth 4x art drawn as two title glyphs (top 128 px, bottom 100 px) covering both the chest and the player inventory, with items sitting in drawn sockets. opengui2 (`screens2/`) is the earlier take; Rob called it "cheap" and it stays as it is.

## Status (2026-11-03)
**Blocked by MythicHUD (new on dev 2026-11-03).** Rob's re-test (https://fast.rawb.tv/aTweE.png): the big art glyphs (window halves, portrait) render as missing-glyph boxes, so the pen drifts and the text lands left of the window. `/roamin` breaks the same way (https://fast.rawb.tv/4mlOB.png). Rob confirmed MythicHUD is the cause. Ruled out: the live dev pack (`packs/dev/daa31743…zip`, URL in `~/dev-main/CraftEngine/config.yml`) holds valid `lom:npc3/art` + textures; plugin glyph codes match (`..`, `+`); MythicHUD's `text.vsh` shader only moves glyphs at huge y offsets. Prime suspect: its packet rewriting, `disable-netty-injection: false` in craftengine-resources `mythichud/config.yml`. Proposed test: set it to `true` and push (one line; Rob hadn't said yes yet, and `mythichud/` belongs to the SneakyHud/MythicHUD work). Rounds 1-3 visuals are still unverified behind this. Rob restarts the dev server; the pack rebuilds via the "Build and activate dev candidate" workflow on push.
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
- Config: `npc2/<id>.yml` is shared with opengui2 (re-read on every open; example `src/main/resources/npc2/CarlTheKobold.yml`). The NPC's wallet/shop comes from `npc/<id>.yml`.

## Decisions and why
- **Title-font art** instead of item-model glyphs: much less machinery. The cost is that changing text means reopening, which recentres the mouse, so per-click data lives in tooltips.
- **No panels inside panels**: Rob's explicit dislike. Put things straight on the board; a rule or a bar track is fine.
- **One board**: `window()` draws the full 228 px height once and `split()` cuts it at 128. Two separately drawn halves gave a visible band.
- **"Inventory" label**: the client draws it after the title, so art can't cover it. The strap's face is exactly `#404040`, the label's colour, and the label has no shadow, so it vanishes. A lang override or shader would hide it on every chest, so that was rejected.
- **Reputation out of 100** matches the old NPCGui progress bar. The stored rep is uncapped, and the bar clamps.
- Commit straight to `main` in both repos (Rob: "dani wont mind").

## Gotchas
- Regenerate: `uv run --no-project --with pillow python tools/npc3_art.py ../craftengine-resources/resources/dev/resourcepack/assets/lom docs/npc3_preview.png`, then commit both repos.
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
After Rob restarts: `/sneakynpcs opengui3 CarlTheKobold`.
- Hub: hover the right ledger for the wallet details; the options open the shop, sell or close.
- Shop: buy, sell (sell box, shift-click, drag across the sell box), pages, back. Esc or a click outside returns to the hub.
- `/sneakynpcs opengui` (old) and `opengui2` are unchanged.
