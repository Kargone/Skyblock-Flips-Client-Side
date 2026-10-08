# Skyblock Flips Client Side (Fabric, Minecraft 26.1.2)

The in-game half of Skyblock Flips. The mod captures Bazaar and Auction House chat messages and
reads the forge menu, then sends what it finds to the Skyblock Flips server (the
[Skyblock-Flips](https://github.com/Kargone/Skyblock-Flips) repo). It also draws the server's
numbers over the game's menus: craft flips on the Auction House, forge flips and your running forges
in the forge, and mayor-driven reminders on the Bazaar.

The server is the source of truth for what a flip is. The mod mirrors the dashboard's maths and
never invents its own, so a number read off an overlay is the same number the dashboard shows.

## Setup

1. **Requirements**
   - JDK 25 (Minecraft 26.1.2 requires Java 25)
   - Fabric Loader 0.19.3+ and Fabric API 0.155.2+
   - The Skyblock Flips server, on this computer or another one on your network (see [Server](#server))

2. **Building**
   - Run `./gradlew build`.
   - The mod is `build/libs/skyblockflips-<version>.jar`. The `-sources.jar` next to it is only the
     source code, for publishing; it does nothing in a mods folder.

3. **Installing**
   - Put the jar in your mods folder. Lunar Client uses `%APPDATA%\.minecraft` as its game directory,
     so the mod's config file ends up in `%APPDATA%\.minecraft\config\skyblockflips.json`.

4. **Running in development**
   - Run `./gradlew runClient` to start Minecraft with the mod loaded.

## Server

Everything the overlays show comes from the server, on the same endpoints the web dashboard uses.
Fetching happens on one background thread, only while a panel is open, and backs off for 30 seconds
when the server is not running; the panels say so instead of retrying every frame.

| Endpoint | Used for | Refresh |
| --- | --- | --- |
| `GET /api/product-list` | recipes, item types, api names, forge times | 10 min |
| `GET /api/market-data` | Bazaar order books and lowest BIN prices | 1 min |
| `GET /api/trader-data` | held materials ("have"), running forges, forge profit | 15 s, or straight away after a forge change |
| `GET /api/mayor-data` | mayor, election, Spooky Festival, reminders, tax multiplier | 1 min |
| `POST /` | chat messages and forge snapshots | as they happen |

The server's address is `serverUrl` in `config/skyblockflips.json`, read once when the game starts
(`util/ModConfig`). It defaults to `http://localhost:8000` and is written into the file the first
time the mod runs. To use a server on another computer, set it to that machine and restart the game:

```json
{
  "serverUrl": "http://192.168.1.50:8000"
}
```

## Features

### Chat capture

System messages containing any of these are sent to the server as `{plainText, fullJson,
detectedRarity}`, where `detectedRarity` is read off the colour of a Beastmaster Crest's name:

`[Bazaar]`, `You sold`, `BIN auction`, `You collected`, `cancelled`, `Supercrafted`, `[Auction]`,
`Beastmaster`, `purchased`, `You claimed`

### Auction House: Auction Products

Opening any Auction House menu draws the dashboard's **Auction Products** table beside it, ranked by
margin: up to 40 products in two columns, down the left column first and then the right.

```
AUCTION PRODUCTS
updated 12s ago
1. Treasure Artifact                    11. Wood Singularity
71.99M > 105.00M +30.37M/+27.10M        8.10M > 9.20M +1.01M/+0.86M
2. Bait Ring            have 33.95M     12. Beastmaster Crest
37.78M > 54.40M +15.52M/+13.78M         2.41M > 3.10M +0.65M/+0.52M
cost > value  buy order/insta - click a name for its materials
```

Each entry is the materials' buy order cost, what the crafted item sells for, and the margin: with
buy orders, and after the slash with the materials bought instantly. "have" is the part of the recipe
already covered by materials you hold. It drops to one column when the window is too narrow for two,
and shows fewer entries when it is too short.

A material that has a recipe of its own is costed by crafting it, all the way down the recipe tree,
rather than at its price. The lowest BIN is always looked up by display name, and an item only counts
as a Bazaar item when the Bazaar actually lists it.

### The breakdown window

Clicking a product name opens its breakdown underneath the panel (above it when there is no room
below); clicking the same name again closes it.

- **Title**: click the name to run `/viewrecipe <ITEM_ID>`. The id is the name upper cased with
  spaces and hyphens as underscores, plus the rarity from the product's `tier`: "Beastmaster Crest -
  EPIC" asks for `BEASTMASTER_CREST_EPIC`. Forge products use their api name instead, since some are
  named differently in game (Drill Motor is `DRILL_ENGINE`).
- **x1 multiplier** next to the title: right click raises it, left click lowers it. Every quantity
  and cost is scaled for that many crafts, and it resets to 1 when another product is opened.
- **Sub recipes**: every material in the tree that has a recipe of its own, indented by depth.
  Clicking one runs `/viewrecipe` for it.
- **Materials**: what actually has to be bought, summed across the whole tree, with the quantity,
  buy order cost (insta-buy premium in brackets) and what you already hold, then a total line.

| Material colour | Meaning | Click |
| --- | --- | --- |
| White | a Bazaar item | `/bazaar <display name>` |
| Gold | not on the Bazaar, so bought on the Auction House | `/ahsearch <display name>` |

Commands are issued the way typing them would be, so they show up in your chat history.

### Filling the amount sign

Clicking a material remembers how many the recipe needs (already scaled by the multiplier). When
Hypixel opens the amount sign, a green button beside it types that number in with one click. It is
for one purchase: filling the sign, picking another item, or three minutes passing clears it.

In the **Create BIN Auction** menu, the item you place is looked up in the lowest BIN prices, and the
price sign offers the lowest BIN minus 100k the same way.

### Forge: Forge Products and your forge

Opening the forge draws the dashboard's **Forge Products** table: the top 20 forge recipes by margin,
with each one's forge time and where it sells. The **AH / BZ** button on the title line switches
between recipes that sell on the Auction House and those that sell on the Bazaar.

Above the recipes, a **Your forge** block lists what is forging right now, from the server:

```
Your forge | 2 forging - 1 ready - 6.00M value  today +1.05M
#1 Drill Motor READY +645K
#2 Bejeweled Collar 1h 0m +1.95M
```

Each process shows its time left and what it should make once sold, valued the same way as its row in
the table.

### Forge tracking

Hypixel says nothing in chat about the forge, so `feature/ForgeTracker` reads "The Forge" menu
instead. It reads slots #1-#7 (container slots 10-16) as empty, forging (with the time remaining) or
ready, and posts a `forge-snapshot` to the server whenever one of them changes; the countdown alone
does not count as a change. The server works out what started and what was claimed:

- **Started**: the recipe's materials are taken out of your investments.
- **Finished**: once its end time passes; nothing has to watch for it.
- **Claimed**: the product goes into your investments marked as forged, so selling it later, on the
  Bazaar or the Auction House, counts as forge profit.

Items with no forge recipe in the product list are not tracked.

### Bazaar: Market Reminders

Bazaar menus get a reminders window, built from your notes in the server's `mayor-info.json`:

```
MARKET REMINDERS                                   Year 518
Mayor Marina   Election 3d 5h - Paul leading
-- now: Marina --
buy   Great White Shark Tooth   4.12M / 4.74M   ● bottom
 • Great White Shark Tooth seen at 1.9m, 3.2m
-- coming: Paul (leading) --
sell  Recombobulator 3000       6.10M / 6.30M   ▼ 4%
-- after: Diana (6d 4h left) --
buy   Griffin Feather         231.00K / 236.00K ▼ 13%
-- Spooky Festival in 6h 8m --
      Ectoplasm               188.16K / 229.02K – flat
Your forge | 5 forging - 18.54M value  today +0
```

- **now**: what to buy, sell or watch while the current mayor is in office, plus your notes.
- **coming**: what to buy or sell before the election leader takes office.
- **after**: the SkyBlock years after a mayor's term, for things like Griffin Feather after Diana.
- **Trend**: against the same time yesterday, from prices the server records every half hour:
  ▼ falling, ● bottom (not falling and within 5% of its low), ▲ rising, – flat, or "collecting"
  for the first day.
- **Spooky Festival** countdown with Ectoplasm's price, and the forge block.

Prices are Bazaar buy order / insta-buy. Clicking a product opens it in the Bazaar. While Derpy's
QUAD TAXES perk is active, a red line says so, and the tax multiplier switches to 4 automatically in
the mod, the server and the dashboard.

### Moving the panels

Press anywhere on a panel and drag it. Positions are saved to `config/skyblockflips.json` when you let
go, and used from then on instead of the automatic placement. Delete an entry to go back to automatic
placement for that panel. Clicks on a panel never reach the menu underneath.

| Key | Panel |
| --- | --- |
| `auctionPanel` | Auction Products |
| `forgePanel` | Forge Products |
| `breakdownPanel` | breakdown window |
| `signAmountButton` | amount sign button |
| `remindersPanel` | Market Reminders |

## Code map

| Package | What lives there |
| --- | --- |
| `mixin` | `ChatListenerMixin` (chat capture), `ContainerScreenMixin` / `ScreenMixin` / `SignEditScreenMixin` (draw overlays, take panel clicks) |
| `overlay` | `OverlayManager` (which overlay goes on which menu), `CraftProductsOverlay`, `BreakdownWindow`, `MarketRemindersOverlay`, `SignAmountOverlay`, `AuctionPanelInput` (dragging and clicks for every panel), `PanelPosition` |
| `flip` | `ServerData` (fetching), `CraftCostModel` (port of the dashboard's cost functions), `CraftFlipRanking`, `ForgeStatus`, `MayorData`, `SkyblockFees` |
| `feature` | `ForgeTracker`, `AuctionPriceTracker`, `PendingAmount` |
| `util` | `SkyblockText` (formatting that matches the dashboard's `formatCoin`), `ContainerUtils`, `ScreenConstants` |

## License

This project is licensed under the MIT License.
