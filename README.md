# Wildy Agility

A RuneLite plugin for **Agility FC** masses at the Wilderness Agility Course.

It watches your friends chat and turns the calls people are already making — *dd*, *pker at
gate*, *pray range*, *mass bank* — into things you can actually see on screen, and keeps track
of who stacks when it matters.

> **New here?** Join the in-game friends chat **Agility FC** and read the rules first:
> **[discord.gg/agilityfc](https://discord.gg/agilityfc)**

---

## Quick start

1. Enable **Wildy Agility** in the RuneLite plugin list.
2. Go to the course. The DD tile is already set to AFC's official tile — if you want a stack
   somewhere else, hold **Shift**, right-click that tile and choose **Set DD tile**.
3. That's it. Callout locations ship preloaded with AFC's official markers, so nothing else
   needs setting up.

The **red DD icon** in the RuneLite sidebar opens the stats panel.

---

## DD tracking

A call starts when `dd` is called **twice within 20 seconds** by a
**ranked** FC member — either two ranks saying it once each, or one rank saying it twice.
A single "dd" in conversation does nothing.

While it's waiting for the second call you get a quiet `dd 1/2` in chat, so it's never
silently doing nothing. Turn that off, or change how many calls are needed, in the settings.

Everyone in your FC within **60 tiles** of the DD tile gets tracked for the next **60 seconds**
(about one lap), and gets colour-coded above their head:

| Colour | Meaning |
| --- | --- |
| 🟢 Green | On the DD tile right now |
| 🟡 Yellow | Made it to the tile during the call, has since moved off |
| 🔴 Red | Never made it |

A countdown shows over the tile. When it runs out you get a summary in chat —
`Wildy Agility: 5/7 stacked. Missed: PlayerA, PlayerB` — and the sidebar panel updates each
player's running record: calls seen, times they stacked, and their compliance %. Worst
offenders sort to the top. **Reset stats** in the panel clears the board.

**Call spam is handled.** Everyone echoes "dd" so the whole FC sees it, and people repeat it
for the same pker. Any calls within 65 seconds of each other fold into a single event, so one
pker is one entry in everyone's stats — not fifteen. The next lap's real call still counts,
and spam that arrives while a call is already running is counted toward the next one rather
than thrown away.

The tracking radius is maxed at 60 tiles so leechers on the far side of the course still get
counted. Be aware of the ceiling on that: the client can only see players the server has told
it about, and in a packed mass that's a subset of who's really there.

**If you're the one not stacked**, a pulsing **GET TO THE DD TILE** banner shows with your
distance and the hint arrow points at the tile. It only fires if you're actually at the
course, so being at the bank when a call goes out won't nag you.

If a call comes in and no DD tile is set, the plugin says so in chat rather than silently
doing nothing.

---

## Pker callouts

When someone calls a location — "pker gate", "2 magers at log", "he's at pipe" — a hint arrow
and an orange highlight appear on that spot with the caller's name, for 15 seconds.

Recognised spots: **gate, pipe, rope** (or swing), **log, rocks** (or cliff), **dispenser,
plank, multi, slip, ladder, lava, pit**.

Coordinates ship as AFC's official tile markers, so this works out of the box. To fix one or
add your own, stand on the spot and type `::ddloc <name>`.

Ordinary chat won't set it off. A blocklist stops things like *"don't let him log"* or
*"he's logging"* from pinging the log balance.

---

## Prayer calls

"pray range", "prot mage", "protect melee" → the **prayer tab flashes** with a big
`PRAY RANGE` banner for 15 seconds.

It needs both a pray word *and* a style, so "he's ranging you" or "mage incoming" won't
trigger it.

---

## Mass bank

When **"mass bank" is called twice within 20 seconds**, the course gates turn gold and the
hint arrow points at the nearest one, tagged `MASS BANK`. Two ranks saying it once each or
one rank saying it twice both count, and you get a `mass bank 1/2` in chat while it waits.
The plugin finds the gates itself from whatever is loaded around you, so there are no
coordinates to set.

It doesn't need the exact words — "mass bank in 2 mins", "ok everyone mass bank" and
"mass banking" all work, since the phrase can sit anywhere in the message and tolerates an
ending like *-ing*. Chat emoji don't break it either, even jammed straight against the words.

They stay gold for **3 minutes**, timed from the most recent mention — so while people trickle
back and the call gets repeated, the highlight extends rather than expiring mid-bank. Once
it's up, a single repeat is enough to push the timer out again.

A bank call never hides a pker callout. If a message somehow reads as both, you get both, and
the arrow points at the pker.

---

## Friends chat capacity

A friends chat caps at **500** members. As it fills up you get one warning at 480, 490 and
500:

> Wildy Agility: friends chat is at 487/500 members. 23 members are outside the mass world.

Each warning fires once and only re-arms after the count drops back clear of it, so it won't
spam at the boundary.

---

## Gear checks

Anyone running with nothing equipped is flagged **Unarmed** in orange above their head, and
anyone with no body **and** no leg armour is flagged **NAKED** in red. Both are on by default.

The same players are listed live in the **Gear check** section at the top of the sidebar
panel, so you can see who's a problem without hunting for orange text in a crowd. Naked
sorts first. The list refreshes a couple of times a second and only covers players near you.

Only *visible* equipment can be read client-side — nobody's inventory (food, phoenix
necklaces, etc.) is visible to the plugin, or to any plugin.

---

## Commands

| Command | What it does |
| --- | --- |
| `::ddloc` | Lists your saved callout locations |
| `::ddloc <name>` | Saves your current tile as that location (e.g. stand on the pipe, type `::ddloc pipe`) |
| `::ddloc remove <name>` | Deletes one |

Locations are saved between sessions.

---

## Settings worth knowing

Everything lives under the plugin's gear icon. The ones people actually change:

- **Show welcome message** — the Agility FC / Discord note on start-up. On by default.
- **Calls required** — how many times a call must be made within 20 seconds before it fires.
  Set to 1 if you want it instant.
- **Debug: explain call matching** — off by default. Turn it on if a call isn't being picked
  up and the plugin will say in chat why it ignored the message.
- **Ranked callers only** — on by default, so random members can't start calls. Turn it off
  if you want to test the plugin on yourself.
- **Listen to** — friends chat, clan chat, or both.
- **Mass worlds / warn at** — the worlds masses run on (318, 319) and the capacity thresholds.
- **Colours** — every highlight, banner and flash.

---

## Good to know

- A player who logs out, teleports or leaves the area mid-call counts as a miss — which is
  usually exactly what you want to know.
- Stats are per-session. Closing the client clears them.
- Name matching handles rank icons and the odd spacing RuneScape uses in chat names, so
  players are tracked correctly regardless of how their name displays.
- A pker callout takes priority over the "get to the DD tile" arrow — if someone gets called
  while you're unstacked, the arrow points at the pker, then goes back to the tile.

---

## Links

- **Friends chat:** `Agility FC` — open the chat-channel tab in game, click *Join Chat*, and
  enter that name.
- **Discord:** [discord.gg/agilityfc](https://discord.gg/agilityfc) — rules, gear guides,
 announcements.
---

Made by **Torza** for Agility FC. BSD 2-Clause licensed — see LICENSE.
