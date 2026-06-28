# Skyblock Flips Client Side (Fabric 1.21.1)

This mod captures specific chat messages (Bazaar, Auction House, etc.) and sends them to a local server for processing.

## Setup

1. **Requirements**:
   - JDK 21 (Minecraft 1.21.1 requires Java 21)
   - Fabric Loader 0.16.5+

2. **Building**:
   - Run `./gradlew build` to compile the mod.
   - The compiled jar will be in `build/libs/`.

3. **Running in Development**:
   - Run `./gradlew runClient` to start Minecraft with the mod loaded.

## Features

- Listens for chat messages containing:
  - `[Bazaar]`
  - `You sold`
  - `created a BIN auction`
  - `You collected`
  - `You cancelled`
  - `You Supercrafted`
  - `[Auction]`
  - `You purchased`
- Sends the raw message content as a POST request to `http://localhost:8000`.

## Configuration

The mod logic is located in `com.github.kargone.skyblockflips2.ExampleMod`.
The HTTP request logic is in `com.github.kargone.skyblockflips2.HttpClientExample`.

## License

This project is licensed under the MIT License.
