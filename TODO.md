# Skyblock Flips - Client Side TODO

## High Priority
- [x] **Fix Overlay Visibility**: Verify if strings are rendering in Bazaar/Auction House. Try different screen coordinates if still invisible.
- [x] **Server Communication**: Test the `/batch` endpoint on the local HTTP server to ensure it handles the new JSON array format.
- [x] **Configurable server address**: `serverUrl` in `config/skyblockflips.json` (`util/ModConfig`), so the
      server can run on another computer on the network.
- [ ] **Test a full forge cycle in game**: start, finish, claim, then sell one. Check the dashboard's Forge
      tab and the forge panel both show it, and that the sale lands in forge profit.

## Features
- [ ] **Bazaar Analyzer**:
    - [ ] Calculate "Profit per flip" from item data.
    - [ ] Highlight "Best Buy" items in the Bazaar menu.
    - [ ] Have the strings to view recipes
- [x] **Market Reminders** (Bazaar):
    - [x] Mayor, election leader and countdown, from the server's `/api/mayor-data`.
    - [x] Buy / sell / watch reminders from `mayor-info.json`: during the mayor, before the election
          leader takes office, and in the years after a term.
    - [x] Trend hint (falling / bottom / rising / flat) from half-hourly price history.
    - [x] Spooky Festival countdown with Ectoplasm's price.
    - [x] Derpy's QUAD TAXES sets the tax multiplier automatically.
    - [ ] Check the ▼ ▲ ● – symbols render in game; swap for plain characters if they show as boxes.
    - [ ] The window can get taller than the screen at small GUI scales; collapse notes or cap each section.
- [x] **Auction Helper**:
    - [x] Compare BIN prices with Bazaar prices for potential "Craft Flips".
        - `overlay/CraftProductsOverlay` draws the dashboard's Auction Products table in game, ranked by margin.
        - `flip/CraftCostModel` is a port of the dashboard's cost functions; it is diffed against
          `frontend/script.js` on live server data, and all 54 products match on all 5 columns.
    - [x] Have the recipe list, with the profits
        - Clicking a product name opens a breakdown window with its crafting materials, matching the
          dashboard's expanded sub-table; its numbers are diffed against `frontend/script.js` too.
        - The product name there runs `/viewrecipe`, and a material name runs `/bazaar`.
    - [x] Craft multiplier (x1 button) and the full recipe tree in the breakdown window.
    - [x] Lowest BIN offered on the Create BIN Auction price sign.
    - [ ] Option to cost a sub-item at whichever is cheaper, crafting it or buying it. Today a material
          with a recipe is always costed by its recipe. Has to change in `script.js` too.
- [x] **Forge Tracker**:
    - [x] Track time remaining for Forge processes (the forge menu is read and sent to the server).
    - [x] Show running forges, time left and projected profit in the forge panel and the Bazaar window.
    - [x] Place in gui which ones are best to craft (Forge Products, with an AH / BZ toggle).
    - [ ] Send a notification (or chat message) when a Forge item is ready to claim.
    - [ ] Follow a changed end time: today a forge sped up part way through keeps its original end time
          until it shows Ready.

## Optimization & Refactoring
- [ ] **Logging**: Add a toggle command (e.g., `/sbflips debug`) to turn debug logs on/off in-game instead of
      cluttering the console. `ChatListenerMixin` prints every system message.
- [ ] **Lunar Client Stability**: Monitor for any further render stalls or GPU resets.

## Future Ideas
- [x] **Sign amount button**: clicking a material remembers the quantity the recipe needs, and the
      Bazaar amount sign gets a one-click button that types it in.
- [ ] **Auto-Sell Calculator**: Calculate if it's better to sell to NPC or Bazaar based on current prices.
- [x] **Recipe Integration**: Pull recipe data to calculate crafting costs on the fly.
- [ ] have the mod go read when you aren't selling the max of a material in inv

## Final things
- [x] Update the readme
- [ ] Update the version number
- [ ] Update the fabric.mod.json (its description still only mentions sending chat messages)

## Other:
- [ ] Control + Shift + F9 to save the hotswapped classes
