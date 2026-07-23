# Wildy Agility — RuneLite plugin

Tracks which players actually stack ("dd") when a call goes out in your friends chat —
built for the Wilderness Agility Course / AFC-style anti-pk scouting.

## How it works

1. **Set the DD tile**: hold **Shift** and right-click the gate tile (or wherever your FC stacks) → **Set DD tile**. Shift + right-click the same tile again to clear it.
2. When a **ranked** member of your friends chat types a trigger word (`dd`, `ddd`, or `stack` — configurable) as the start of a message, a call starts.
3. Every FC member within the tracking radius (default 15 tiles) at call time is tracked for the grace period (default **60 seconds**, about one lap).
4. During the call:
   - **Green** highlight = currently on the DD tile
   - **Yellow** = reached the tile during the call but stepped off
   - **Red** = never reached the tile
   - A countdown is drawn over the DD tile.
5. When the grace period ends, a game message summarizes results (e.g. `Wildy Agility: 5/7 stacked. Missed: PlayerA, PlayerB`) and the **side panel** (red DD icon in the sidebar) updates per-player stats: calls seen, times dd'd, and compliance %. Players with the most misses sort to the top.

**Call spam is de-duplicated** by a single "min seconds between calls" setting (default 65s, ~one lap), measured from when a call starts. Any DD calls inside that window — chat spam as everyone echoes "dd", or repeats for the same pker — fold into the one call, so a single event is one entry in everyone's stats. The next lap's genuine call still registers. Stats are per-session; use **Reset stats** in the panel to clear.

## Config options

- Trigger words, channel (friends chat / clan chat / both)
- Ranked callers only (on by default)
- Track channel members only vs. everyone nearby
- Tracking radius, grace period, tile tolerance (count adjacent tiles as stacked)
- All highlight colors, chat summary toggle

## Location callouts

When a (ranked) FC message mentions a course landmark — **gate, pipe, rope/swing, stones/steps, log, cliff/rocks, dispenser** — the plugin shows a hint arrow to it plus an orange area highlight with the caller's name, auto-clearing after 15s (configurable). So "pker gate" or "2 mager at log" instantly shows everyone where to look.

Coordinates ship as AFC's official tile markers, so no setup is needed. To add your own spots (e.g. `bank`) or adjust one: stand on the tile in game and type `::ddloc <name>`. `::ddloc` alone lists saved locations; `::ddloc remove <name>` deletes one. Locations persist between sessions.

A **callout blocklist** stops false pings from ordinary chat — "dont let him log", "he's logging", "trying to log out" etc. will not fire the Log obstacle callout. Edit the phrase list in config if your FC uses other wording.

## Prayer call alerts

When a rank calls a protection prayer — "pray range", "prot mage", "protect melee" — the prayer tab icon flashes with a **PRAY RANGE** banner over it for 15s. It requires both a pray word *and* a style, so "he's ranging you" or "mage incoming" won't fire it.

## Gear check

AFC expects runners to carry a crossbow so they can interrupt fights from range mid-lap. The plugin flags FC members whose visible weapon isn't a crossbow ("No xbow") or who have nothing equipped ("Unarmed") in orange above their head, and anyone running with no body **and** no leg armour as **"NAKED"** in red — AFC kicks players who show up naked. Config: check for crossbows specifically, any weapon, or off. Note: only *visible* equipment is readable client-side — inventory (food, phoenix necklace count, etc.) of other players is not.

## Building & running

Requires JDK 11+ and Gradle (or just open in IntelliJ IDEA, which handles both).

**Easiest way to test (IntelliJ):**
1. `File → Open` this folder; let Gradle import.
2. Run `DdTrackerPluginTest.main()` (in `src/test/java`). This launches RuneLite with the plugin loaded — log in and enable **Wildy Agility** in the plugin list.

**Command line build:**
```
gradle build
```

**Using it with your normal client:** RuneLite only loads third-party plugins from the Plugin Hub, so for day-to-day use you'd either run via the test launcher above or submit this to the Plugin Hub (https://github.com/runelite/plugin-hub) — the project already follows the hub layout (`runelite-plugin.properties`, standard Gradle structure).

## Notes

- A player who logs out, teleports, or leaves the area mid-call counts as a miss — which is usually exactly what you want to know.
- Name matching handles rank icons and non-breaking spaces in chat names.
- If a call comes in with no DD tile set, the plugin reminds you in the chatbox instead of silently doing nothing.
