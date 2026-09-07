# TrapGuard

TrapGuard is a client-only Fabric mod for Minecraft Java 1.21.11 that snapshots selected redstone/trap areas and warns when loaded blocks differ from the saved state.

## Main workflow

- Press **G** to open TrapGuard (configurable in Controls).
- Default selector item: `minecraft:stick`.
- Left click: set corner 1.
- Right click: set corner 2.
- Sneak + left/right click or drag: add/remove extra watched blocks outside the main cuboid.
- Choose **STRUCTURAL** or **EXACT** in the GUI, name the trap, and save it.
- Open a saved trap from the list for monitoring, overlay, mode, folder, resnapshot, difference viewer, schematic export, editor, and delete controls.

## Monitoring and alerts

- **H** toggles global monitoring by default; the key is configurable in Minecraft Controls.
- Global monitoring pause does not change individual trap enabled states.
- Alerts appear in a movable HUD panel instead of flooding chat.
- Default alert position is bottom-right.
- Each trap has its own configurable alert cooldown/lifetime, defaulting to 15 seconds.
- Alerts fade during the final 2.5 seconds when the cooldown is greater than 3 seconds. At 3 seconds or less they disappear without fading.
- Additional changes to an already-visible trap update its count without extending its lifetime.
- **F3+D** clears visible TrapGuard notifications.

## Folders

- Create custom folders with player-defined names.
- Assign traps to folders and filter the main trap list with the hover dropdown.
- The dropdown supports mouse-wheel scrolling when many folders exist.
- Deleting a folder moves its traps to `Ungrouped`.

## Difference Viewer and schematic export

- The Difference Viewer lists current discrepancies, including expected/actual block states where available.
- Selecting a difference can highlight that position in-world.
- **Convert to .Schem** exports the saved snapshot as a Sponge Schematic v2 file named after the trap into the instance `schematics` directory.
- Existing same-name schematic files are replaced only after a successful export.

## Comparison modes

**STRUCTURAL** ignores transient properties such as redstone power while still detecting block replacement and configuration changes such as facing, repeater delay, and comparator mode.

**EXACT** compares the complete saved block state. Active redstone clocks can therefore produce legitimate difference/resolution events.

When saving/resnapshotting/applying, TrapGuard detects direct face-to-face observer clock pairs when the relevant neighbor chunk is loaded and warns about Exact-mode behavior.

## Loaded chunks

TrapGuard never treats an unloaded client chunk as air. Watched positions are skipped until their real client chunk is present. If a trap changes while unloaded, the change is discovered after that chunk is loaded again and scanned.

## World/server scoping

New multiplayer traps are tied to the normalized server address. New singleplayer traps are tied to the local save folder. Older legacy traps can be rebound by resnapshotting or applying an editor update in the intended world.

## Commands

Commands remain as a fallback to the GUI:

- `/trapguard gui`
- `/trapguard global <true|false>`
- `/trapguard cooldown <1-300>`
- `/trapguard selector held|stick|enabled <true|false>`
- `/trapguard pos1`
- `/trapguard pos2`
- `/trapguard add`
- `/trapguard remove`
- `/trapguard clear`
- `/trapguard selection`
- `/trapguard save <name>`
- `/trapguard select <name>`
- `/trapguard apply <name>`
- `/trapguard resnapshot <name>`
- `/trapguard mode <name> structural|exact`
- `/trapguard enable <name> <true|false>`
- `/trapguard overlay <name> <true|false>`
- `/trapguard status <name>`
- `/trapguard list`
- `/trapguard delete <name>`

## Build

The repository includes `.github/workflows/build.yml`. A push triggers Java 21 + Gradle 9.2.1 and runs:

```text
gradle build
```

`build` also runs TrapGuard's dependency-free `coreSelfTest`. The workflow uploads the remapped mod JAR while excluding source/development JARs.

See `AUDIT.md` for the current verification notes and `PORTING.md` for future-version architecture.
