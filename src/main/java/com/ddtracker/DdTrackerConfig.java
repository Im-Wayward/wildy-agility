package com.ddtracker;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("ddtracker")
public interface DdTrackerConfig extends Config
{
	enum Channel
	{
		FRIENDS_CHAT,
		CLAN_CHAT,
		BOTH
	}

	enum GearCheckMode
	{
		OFF,
		CROSSBOW,
		ANY_WEAPON
	}

	@ConfigSection(
		name = "DD calls",
		description = "Settings for DD call detection and tracking",
		position = 0
	)
	String ddSection = "ddSection";

	@ConfigSection(
		name = "Callouts",
		description = "Location callouts (gate, pipe, rope, ...) and prayer alerts",
		position = 1
	)
	String calloutSection = "calloutSection";

	@ConfigSection(
		name = "Gear check",
		description = "Settings for flagging runners without proper gear",
		position = 2
	)
	String gearSection = "gearSection";

	@ConfigSection(
		name = "Colors",
		description = "Highlight colors",
		position = 3
	)
	String colorSection = "colorSection";

	@ConfigSection(
		name = "Agility FC community",
		description = "How to join Agility FC in game and on Discord",
		position = 4,
		closedByDefault = true
	)
	String communitySection = "communitySection";

	// ---- DD calls ----

	@ConfigItem(
		keyName = "triggerWords",
		name = "Trigger words",
		description = "Comma-separated words that start a DD call",
		position = 1,
		section = ddSection
	)
	default String triggerWords()
	{
		return "dd,ddd,stack";
	}

	@ConfigItem(
		keyName = "matchAnywhere",
		name = "Match anywhere in message",
		description = "Trigger if any word in the message is a trigger word (e.g. 'dd dd dd', 'everyone dd on me'). If off, only the first word counts.",
		position = 2,
		section = ddSection
	)
	default boolean matchAnywhere()
	{
		return true;
	}

	@ConfigItem(
		keyName = "channel",
		name = "Listen to",
		description = "Which chat channel to listen to",
		position = 3,
		section = ddSection
	)
	default Channel channel()
	{
		return Channel.FRIENDS_CHAT;
	}

	@ConfigItem(
		keyName = "ranksOnly",
		name = "Ranked callers only",
		description = "Only react to calls from ranked members of the channel",
		position = 4,
		section = ddSection
	)
	default boolean ranksOnly()
	{
		return true;
	}

	@ConfigItem(
		keyName = "membersOnly",
		name = "Track channel members only",
		description = "Only track players who are members of your friends chat / clan. Untick to track everyone nearby.",
		position = 5,
		section = ddSection
	)
	default boolean membersOnly()
	{
		return true;
	}

	@Range(min = 1, max = 60)
	@ConfigItem(
		keyName = "radius",
		name = "Tracking radius",
		description = "Players within this many tiles of the DD tile when the call happens are tracked",
		position = 6,
		section = ddSection
	)
	default int radius()
	{
		return 15;
	}

	@Range(min = 1, max = 180)
	@ConfigItem(
		keyName = "graceSeconds",
		name = "Grace period (s)",
		description = "How long players have to get on the DD tile after the call. ~60s covers a full lap.",
		position = 7,
		section = ddSection
	)
	default int graceSeconds()
	{
		return 60;
	}

	@Range(min = 1, max = 180)
	@ConfigItem(
		keyName = "minCallGapSeconds",
		name = "Min seconds between calls",
		description = "Minimum time between distinct DD calls, measured from when a call starts. Any calls within this window (chat spam, or repeats for the same pker) fold into that one call. ~65s = one call per lap.",
		position = 8,
		section = ddSection
	)
	default int minCallGapSeconds()
	{
		return 65;
	}

	@Range(min = 0, max = 3)
	@ConfigItem(
		keyName = "tileTolerance",
		name = "Tile tolerance",
		description = "Count players within this many tiles of the DD tile as stacked (0 = exact tile only)",
		position = 9,
		section = ddSection
	)
	default int tileTolerance()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "chatSummary",
		name = "Chat summary",
		description = "Print a game message with results when the grace period ends",
		position = 10,
		section = ddSection
	)
	default boolean chatSummary()
	{
		return true;
	}

	@ConfigItem(
		keyName = "ddTileLocation",
		name = "DD tile (saved)",
		description = "x,y,plane of the DD tile. Set in game with Shift + right-click; persisted between sessions. Default is AFC's official DD tile.",
		position = 11,
		section = ddSection
	)
	default String ddTileLocation()
	{
		return "2998,3931,0";
	}

	// ---- Callouts ----

	@ConfigItem(
		keyName = "locationCallouts",
		name = "Enable location callouts",
		description = "When a call mentions a course landmark (gate, pipe, rope, log, slip, multi, ...), show a hint arrow and highlight the area",
		position = 1,
		section = calloutSection
	)
	default boolean locationCallouts()
	{
		return true;
	}

	@Range(min = 3, max = 60)
	@ConfigItem(
		keyName = "calloutSeconds",
		name = "Callout duration (s)",
		description = "How long the arrow/highlight stays up",
		position = 2,
		section = calloutSection
	)
	default int calloutSeconds()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "calloutBlocklist",
		name = "Callout blocklist",
		description = "Comma-separated phrases that suppress location callouts. Stops things like 'dont let him log' from pinging the Log obstacle.",
		position = 3,
		section = calloutSection
	)
	default String calloutBlocklist()
	{
		return "log out,logs out,log in,logging,logged,let him log,let her log,let them log,gonna log,about to log,he log,she log,they log,tried to log,trying to log";
	}

	@ConfigItem(
		keyName = "landmarks",
		name = "Landmark coordinates",
		description = "name=x,y,plane pairs separated by ';'. Easiest way to set these: stand on the spot in game and type ::ddloc <name>",
		position = 4,
		section = calloutSection
	)
	default String landmarks()
	{
		// Official AFC tile markers (converted from their Discord ground-marker export)
		return "gate=2998,3931,0;dispenser=3005,3936,0;plank=2998,3924,0;multi=2998,3913,0;"
			+ "slip=3001,3923,0;ladder=3005,3965,0;pipe=3006,3944,0;rocks=2993,3940,0;"
			+ "log=3000,3950,0;lava=2993,3960,0;rope=3008,3955,0;pit=3003,10352,0";
	}

	@ConfigItem(
		keyName = "prayerAlerts",
		name = "Prayer call alerts",
		description = "When someone calls 'pray range' / 'prot mage' / 'pray melee', flash the prayer tab icon and show a banner",
		position = 5,
		section = calloutSection
	)
	default boolean prayerAlerts()
	{
		return true;
	}

	@Range(min = 3, max = 60)
	@ConfigItem(
		keyName = "prayerAlertSeconds",
		name = "Prayer alert duration (s)",
		description = "How long the prayer alert stays up",
		position = 6,
		section = calloutSection
	)
	default int prayerAlertSeconds()
	{
		return 15;
	}

	// ---- Gear check ----

	@ConfigItem(
		keyName = "gearCheck",
		name = "Weapon check",
		description = "Flag channel members with no weapon (ANY_WEAPON) or without a crossbow (CROSSBOW) equipped",
		position = 1,
		section = gearSection
	)
	default GearCheckMode gearCheck()
	{
		return GearCheckMode.CROSSBOW;
	}

	@ConfigItem(
		keyName = "armourCheck",
		name = "Naked check",
		description = "Flag channel members running with no body and no leg armour equipped",
		position = 2,
		section = gearSection
	)
	default boolean armourCheck()
	{
		return true;
	}

	// ---- Colors ----

	@Alpha
	@ConfigItem(
		keyName = "tileColor",
		name = "DD tile",
		description = "Color of the marked DD tile",
		position = 1,
		section = colorSection
	)
	default Color tileColor()
	{
		return new Color(0, 255, 255, 160);
	}

	@Alpha
	@ConfigItem(
		keyName = "onTileColor",
		name = "Stacked",
		description = "Players currently on the DD tile",
		position = 2,
		section = colorSection
	)
	default Color onTileColor()
	{
		return new Color(0, 255, 0, 200);
	}

	@Alpha
	@ConfigItem(
		keyName = "compliedColor",
		name = "Complied",
		description = "Players who reached the DD tile during the call but have since moved off",
		position = 3,
		section = colorSection
	)
	default Color compliedColor()
	{
		return new Color(255, 255, 0, 200);
	}

	@Alpha
	@ConfigItem(
		keyName = "offTileColor",
		name = "Not stacked",
		description = "Tracked players who have not reached the DD tile",
		position = 4,
		section = colorSection
	)
	default Color offTileColor()
	{
		return new Color(255, 0, 0, 200);
	}

	@Alpha
	@ConfigItem(
		keyName = "calloutColor",
		name = "Callout area",
		description = "Highlight color for called-out locations",
		position = 5,
		section = colorSection
	)
	default Color calloutColor()
	{
		return new Color(255, 140, 0, 180);
	}

	@Alpha
	@ConfigItem(
		keyName = "gearWarnColor",
		name = "Gear warning",
		description = "Color of the 'No xbow' / 'Unarmed' label",
		position = 6,
		section = colorSection
	)
	default Color gearWarnColor()
	{
		return new Color(255, 140, 0, 220);
	}

	@Alpha
	@ConfigItem(
		keyName = "nakedWarnColor",
		name = "Naked warning",
		description = "Color of the 'NAKED' label",
		position = 7,
		section = colorSection
	)
	default Color nakedWarnColor()
	{
		return new Color(255, 140, 0, 220);
	}

	@Alpha
	@ConfigItem(
		keyName = "prayerAlertColor",
		name = "Prayer alert",
		description = "Color of the prayer tab flash and banner",
		position = 8,
		section = colorSection
	)
	default Color prayerAlertColor()
	{
		return new Color(0, 200, 255, 230);
	}

	// ---- Community info ----

	@ConfigItem(
		keyName = "fcInfo",
		name = "In-game friends chat",
		description = "Join in game: open the chat-channel tab, click 'Join Chat', and enter this channel name",
		position = 1,
		section = communitySection
	)
	default String fcInfo()
	{
		return "Agility FC";
	}

	@ConfigItem(
		keyName = "discordInfo",
		name = "Discord",
		description = "Rules, gear guides, mass times, and announcements. The sidebar panel has a clickable link.",
		position = 2,
		section = communitySection
	)
	default String discordInfo()
	{
		return "discord.gg/agilityfc";
	}
}
