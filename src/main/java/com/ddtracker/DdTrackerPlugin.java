package com.ddtracker;

import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.FriendsChatRank;
import net.runelite.api.ItemComposition;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Tile;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.kit.KitType;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Wildy Agility",
	description = "Tracks DD compliance, location callouts, prayer calls, and runner gear at the Wilderness Agility Course",
	tags = {"wilderness", "agility", "dd", "stack", "friends chat", "afc", "pvp"}
)
public class DdTrackerPlugin extends Plugin
{
	private static final int TICK_MS = 600;
	private static final int RESULT_DISPLAY_TICKS = 10;

	// maps spoken aliases -> canonical landmark keys
	private static final Map<String, String> LANDMARK_ALIASES = new HashMap<>();

	static
	{
		LANDMARK_ALIASES.put("gate", "gate");
		LANDMARK_ALIASES.put("gates", "gate");
		LANDMARK_ALIASES.put("pipe", "pipe");
		LANDMARK_ALIASES.put("pipes", "pipe");
		LANDMARK_ALIASES.put("rope", "rope");
		LANDMARK_ALIASES.put("ropeswing", "rope");
		LANDMARK_ALIASES.put("swing", "rope");
		LANDMARK_ALIASES.put("log", "log");
		LANDMARK_ALIASES.put("logs", "log");
		LANDMARK_ALIASES.put("cliff", "rocks");
		LANDMARK_ALIASES.put("rock", "rocks");
		LANDMARK_ALIASES.put("rocks", "rocks");
		LANDMARK_ALIASES.put("dispenser", "dispenser");
		LANDMARK_ALIASES.put("disp", "dispenser");
		LANDMARK_ALIASES.put("plank", "plank");
		LANDMARK_ALIASES.put("planks", "plank");
		LANDMARK_ALIASES.put("multi", "multi");
		LANDMARK_ALIASES.put("slip", "slip");
		LANDMARK_ALIASES.put("ladder", "ladder");
		LANDMARK_ALIASES.put("ladders", "ladder");
		LANDMARK_ALIASES.put("lava", "lava");
		LANDMARK_ALIASES.put("pit", "pit");
	}

	private static final Set<String> PRAY_WORDS = new HashSet<>(Arrays.asList(
		"pray", "prays", "prayer", "prayers", "praying", "prot", "protect", "pro"));

	private static final Map<String, String> PRAY_STYLES = new HashMap<>();

	static
	{
		PRAY_STYLES.put("range", "RANGE");
		PRAY_STYLES.put("ranged", "RANGE");
		PRAY_STYLES.put("rng", "RANGE");
		PRAY_STYLES.put("missiles", "RANGE");
		PRAY_STYLES.put("missile", "RANGE");
		PRAY_STYLES.put("mage", "MAGE");
		PRAY_STYLES.put("magic", "MAGE");
		PRAY_STYLES.put("magi", "MAGE");
		PRAY_STYLES.put("melee", "MELEE");
		PRAY_STYLES.put("mele", "MELEE");
	}

	@Inject
	private Client client;

	@Inject
	private DdTrackerConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private DdTrackerOverlay overlay;

	@Inject
	private DdPrayerOverlay prayerOverlay;

	@Inject
	private ClientToolbar clientToolbar;

	@Getter
	private WorldPoint ddTile;

	@Getter
	private DdCall activeCall;

	@Getter
	private WorldPoint calloutPoint;

	@Getter
	private String calloutLabel;

	@Getter
	private int calloutEndTick;

	@Getter
	private String prayerAlertLabel;

	@Getter
	private int prayerAlertEndTick;

	private boolean hintArrowSet;

	/** Tick before which no new DD call may start (min gap since the last call start). */
	private int callBlockUntilTick;

	private final Map<String, WorldPoint> landmarks = new LinkedHashMap<>();

	// key: normalized lowercase name
	private final Map<String, PlayerStats> stats = new HashMap<>();

	private DdTrackerPanel panel;
	private NavigationButton navButton;

	@Provides
	DdTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DdTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		parseLandmarks();
		loadDdTile();
		overlayManager.add(overlay);
		overlayManager.add(prayerOverlay);
		panel = new DdTrackerPanel(this);
		navButton = NavigationButton.builder()
			.tooltip("Wildy Agility")
			.icon(createIcon())
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		updatePanel();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlayManager.remove(prayerOverlay);
		clientToolbar.removeNavigation(navButton);
		activeCall = null;
		ddTile = null;
		prayerAlertLabel = null;
		clearCallout();
	}

	static String normalize(String name)
	{
		if (name == null)
		{
			return "";
		}
		return Text.toJagexName(Text.removeTags(name)).toLowerCase(Locale.ROOT);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if ("ddtracker".equals(event.getGroup()))
		{
			parseLandmarks();
		}
	}

	// ---- DD tile marking ----

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!client.isKeyPressed(KeyCode.KC_SHIFT) || !"Walk here".equals(event.getOption()))
		{
			return;
		}

		final Tile selectedTile = client.getSelectedSceneTile();
		if (selectedTile == null)
		{
			return;
		}

		final WorldPoint wp = selectedTile.getWorldLocation();
		if (wp.equals(ddTile))
		{
			client.createMenuEntry(-1)
				.setOption("Clear DD tile")
				.setTarget("")
				.setType(MenuAction.RUNELITE)
				.onClick(e ->
				{
					ddTile = null;
					activeCall = null;
					saveDdTile();
					updatePanel();
				});
		}
		else
		{
			client.createMenuEntry(-1)
				.setOption("Set DD tile")
				.setTarget("")
				.setType(MenuAction.RUNELITE)
				.onClick(e ->
				{
					ddTile = wp;
					saveDdTile();
					updatePanel();
				});
		}
	}

	// ---- Chat handling ----

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		final ChatMessageType type = event.getType();
		final boolean fc = type == ChatMessageType.FRIENDSCHAT;
		final boolean clan = type == ChatMessageType.CLAN_CHAT || type == ChatMessageType.CLAN_GUEST_CHAT;

		if (!fc && !clan)
		{
			return;
		}
		if (config.channel() == DdTrackerConfig.Channel.FRIENDS_CHAT && !fc)
		{
			return;
		}
		if (config.channel() == DdTrackerConfig.Channel.CLAN_CHAT && !clan)
		{
			return;
		}

		final String senderDisplay = Text.toJagexName(Text.removeTags(event.getName()));
		if (config.ranksOnly() && !isRanked(normalize(event.getName()), fc))
		{
			return;
		}

		final String message = Text.removeTags(event.getMessage()).trim().toLowerCase(Locale.ROOT);

		if (config.prayerAlerts())
		{
			checkPrayerCall(message, senderDisplay);
		}

		if (config.locationCallouts())
		{
			checkCallout(message, senderDisplay);
		}

		if (!isTrigger(message))
		{
			return;
		}

		// De-duplicate spammed calls: everyone repeats "dd" so the whole FC sees it,
		// but it is all one event for compliance purposes.
		if (activeCall != null && !activeCall.isFinished())
		{
			return;
		}
		if (client.getTickCount() < callBlockUntilTick)
		{
			return;
		}

		if (ddTile == null)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"Wildy Agility: DD called by " + senderDisplay
					+ " but no DD tile is set. Shift + right-click a tile to set one.", null);
			return;
		}

		startCall(senderDisplay);
	}

	private boolean isTrigger(String message)
	{
		if (message.isEmpty())
		{
			return false;
		}

		final Set<String> triggers = new HashSet<>();
		for (String word : config.triggerWords().toLowerCase(Locale.ROOT).split(","))
		{
			final String w = word.trim();
			if (!w.isEmpty())
			{
				triggers.add(w);
			}
		}

		final String[] tokens = message.split("\\s+");
		final int limit = config.matchAnywhere() ? tokens.length : 1;
		for (int i = 0; i < limit; i++)
		{
			final String t = tokens[i].replaceAll("[^a-z0-9]", "");
			if (t.isEmpty())
			{
				continue;
			}
			if (triggers.contains(t))
			{
				return true;
			}
			// "dd" also matches any run of d's: ddd, dddd, ...
			if (triggers.contains("dd") && t.matches("d{2,}"))
			{
				return true;
			}
		}
		return false;
	}

	private boolean isRanked(String normalizedName, boolean friendsChat)
	{
		if (friendsChat)
		{
			final FriendsChatManager mgr = client.getFriendsChatManager();
			if (mgr == null)
			{
				return false;
			}
			final FriendsChatMember member = mgr.findByName(normalizedName);
			return member != null && member.getRank() != null
				&& member.getRank() != FriendsChatRank.UNRANKED;
		}

		final ClanChannel channel = client.getClanChannel();
		if (channel != null)
		{
			final ClanChannelMember member = channel.findMember(normalizedName);
			if (member != null && member.getRank() != null)
			{
				return member.getRank().getRank() > 0;
			}
		}
		final ClanChannel guest = client.getGuestClanChannel();
		if (guest != null)
		{
			final ClanChannelMember member = guest.findMember(normalizedName);
			if (member != null && member.getRank() != null)
			{
				return member.getRank().getRank() > 0;
			}
		}
		return false;
	}

	// ---- DD call lifecycle ----

	private void startCall(String caller)
	{
		final int now = client.getTickCount();
		final int end = now + (int) Math.ceil(config.graceSeconds() * 1000.0 / TICK_MS);
		callBlockUntilTick = now + (int) Math.ceil(config.minCallGapSeconds() * 1000.0 / TICK_MS);

		activeCall = new DdCall(caller, end);
		snapshotPlayers();
		checkCompliance();
	}

	private void snapshotPlayers()
	{
		final Player local = client.getLocalPlayer();
		for (Player p : client.getPlayers())
		{
			if (p == null || p.getName() == null)
			{
				continue;
			}
			if (!isMember(p, local))
			{
				continue;
			}
			if (p.getWorldLocation().distanceTo(ddTile) > config.radius())
			{
				continue;
			}
			final String display = Text.toJagexName(Text.removeTags(p.getName()));
			activeCall.getTracked().putIfAbsent(normalize(display), new DdCall.TrackedPlayer(display));
		}
	}

	private boolean isMember(Player p, Player local)
	{
		return !config.membersOnly() || p == local
			|| p.isFriendsChatMember() || p.isClanMember();
	}

	boolean isOnDdTile(Player p)
	{
		return ddTile != null
			&& p.getWorldLocation().distanceTo(ddTile) <= config.tileTolerance();
	}

	private void checkCompliance()
	{
		for (Player p : client.getPlayers())
		{
			if (p == null || p.getName() == null)
			{
				continue;
			}
			final DdCall.TrackedPlayer tp = activeCall.getTracked().get(normalize(p.getName()));
			if (tp != null && !tp.complied && isOnDdTile(p))
			{
				tp.complied = true;
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		final int now = client.getTickCount();

		if (calloutPoint != null && now >= calloutEndTick)
		{
			clearCallout();
		}

		if (prayerAlertLabel != null && now >= prayerAlertEndTick)
		{
			prayerAlertLabel = null;
		}

		if (activeCall == null)
		{
			return;
		}

		if (!activeCall.isFinished())
		{
			checkCompliance();
			if (now >= activeCall.getEndTick())
			{
				finishCall(now);
			}
		}
		else if (now >= activeCall.getClearTick())
		{
			activeCall = null;
		}
	}

	private void finishCall(int now)
	{
		activeCall.setFinished(true);
		activeCall.setClearTick(now + RESULT_DISPLAY_TICKS);

		final List<String> missed = new ArrayList<>();
		for (Map.Entry<String, DdCall.TrackedPlayer> e : activeCall.getTracked().entrySet())
		{
			final DdCall.TrackedPlayer tp = e.getValue();
			stats.computeIfAbsent(e.getKey(), k -> new PlayerStats(tp.displayName)).record(tp.complied);
			if (!tp.complied)
			{
				missed.add(tp.displayName);
			}
		}

		if (config.chatSummary() && !activeCall.getTracked().isEmpty())
		{
			final int total = activeCall.getTracked().size();
			final int good = activeCall.getCompliedCount();
			String msg = "Wildy Agility: " + good + "/" + total + " stacked (call by " + activeCall.getCaller() + ").";
			if (!missed.isEmpty())
			{
				msg += " Missed: " + String.join(", ", missed);
			}
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, null);
		}

		updatePanel();
	}

	// ---- Prayer calls ----

	private void checkPrayerCall(String message, String sender)
	{
		boolean sawPrayWord = false;
		String style = null;

		for (String token : message.split("\\s+"))
		{
			final String t = token.replaceAll("[^a-z0-9]", "");
			if (t.isEmpty())
			{
				continue;
			}
			if (PRAY_WORDS.contains(t))
			{
				sawPrayWord = true;
				continue;
			}
			final String s = PRAY_STYLES.get(t);
			if (s != null)
			{
				style = s;
			}
		}

		// Require both an explicit pray word and a style, so "he's ranging" or
		// "mage coming" alone does not fire.
		if (sawPrayWord && style != null)
		{
			prayerAlertLabel = "PRAY " + style;
			prayerAlertEndTick = client.getTickCount()
				+ (int) Math.ceil(config.prayerAlertSeconds() * 1000.0 / TICK_MS);
		}
	}

	// ---- Location callouts ----

	private void parseLandmarks()
	{
		landmarks.clear();
		for (String entry : config.landmarks().split(";"))
		{
			final String[] kv = entry.trim().split("=");
			if (kv.length != 2)
			{
				continue;
			}
			final String[] coords = kv[1].split(",");
			if (coords.length < 2)
			{
				continue;
			}
			try
			{
				final int x = Integer.parseInt(coords[0].trim());
				final int y = Integer.parseInt(coords[1].trim());
				final int plane = coords.length > 2 ? Integer.parseInt(coords[2].trim()) : 0;
				landmarks.put(kv[0].trim().toLowerCase(Locale.ROOT), new WorldPoint(x, y, plane));
			}
			catch (NumberFormatException ex)
			{
				log.debug("Bad landmark entry: {}", entry);
			}
		}
	}

	private void loadDdTile()
	{
		ddTile = null;
		final String value = config.ddTileLocation();
		if (value == null || value.trim().isEmpty())
		{
			return;
		}
		final String[] parts = value.split(",");
		if (parts.length < 2)
		{
			return;
		}
		try
		{
			final int x = Integer.parseInt(parts[0].trim());
			final int y = Integer.parseInt(parts[1].trim());
			final int plane = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 0;
			ddTile = new WorldPoint(x, y, plane);
		}
		catch (NumberFormatException ex)
		{
			log.debug("Bad ddTileLocation: {}", value);
		}
	}

	private void saveDdTile()
	{
		configManager.setConfiguration("ddtracker", "ddTileLocation",
			ddTile == null ? "" : ddTile.getX() + "," + ddTile.getY() + "," + ddTile.getPlane());
	}

	/**
	 * True if the message contains a phrase that should suppress location callouts,
	 * e.g. "dont let him log" should not ping the Log obstacle.
	 */
	private boolean isCalloutBlocked(String message)
	{
		final String padded = " " + message.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ") + " ";
		for (String phrase : config.calloutBlocklist().toLowerCase(Locale.ROOT).split(","))
		{
			final String p = phrase.trim();
			if (!p.isEmpty() && padded.contains(" " + p + " "))
			{
				return true;
			}
		}
		return false;
	}

	private void checkCallout(String message, String sender)
	{
		if (isCalloutBlocked(message))
		{
			return;
		}

		for (String token : message.split("\\s+"))
		{
			final String t = token.replaceAll("[^a-z0-9]", "");
			if (t.isEmpty())
			{
				continue;
			}
			String key = LANDMARK_ALIASES.get(t);
			if (key == null && landmarks.containsKey(t))
			{
				key = t;
			}
			if (key == null)
			{
				continue;
			}
			final WorldPoint wp = landmarks.get(key);
			if (wp == null)
			{
				continue;
			}
			setCallout(wp, key.toUpperCase(Locale.ROOT) + " - " + sender);
			return;
		}
	}

	private void setCallout(WorldPoint wp, String label)
	{
		calloutPoint = wp;
		calloutLabel = label;
		calloutEndTick = client.getTickCount()
			+ (int) Math.ceil(config.calloutSeconds() * 1000.0 / TICK_MS);
		client.setHintArrow(wp);
		hintArrowSet = true;
	}

	private void clearCallout()
	{
		calloutPoint = null;
		calloutLabel = null;
		if (hintArrowSet)
		{
			client.clearHintArrow();
			hintArrowSet = false;
		}
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		if (!"ddloc".equalsIgnoreCase(event.getCommand()))
		{
			return;
		}

		final String[] args = event.getArguments();
		if (args.length == 0)
		{
			gameMessage("Wildy Agility: '::ddloc <name>' saves your current tile as callout location <name>. "
				+ "'::ddloc remove <name>' deletes one. Known: " + String.join(", ", landmarks.keySet()));
			return;
		}

		if ("remove".equalsIgnoreCase(args[0]) && args.length > 1)
		{
			final String name = args[1].toLowerCase(Locale.ROOT);
			if (landmarks.remove(name) != null)
			{
				saveLandmarks();
				gameMessage("Wildy Agility: removed callout location '" + name + "'.");
			}
			else
			{
				gameMessage("Wildy Agility: no callout location named '" + name + "'.");
			}
			return;
		}

		final Player local = client.getLocalPlayer();
		if (local == null)
		{
			return;
		}
		final String name = args[0].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
		if (name.isEmpty())
		{
			return;
		}
		final WorldPoint wp = local.getWorldLocation();
		landmarks.put(name, wp);
		saveLandmarks();
		gameMessage("Wildy Agility: callout location '" + name + "' set to ("
			+ wp.getX() + ", " + wp.getY() + ", " + wp.getPlane() + ").");
	}

	private void saveLandmarks()
	{
		final StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, WorldPoint> e : landmarks.entrySet())
		{
			if (sb.length() > 0)
			{
				sb.append(';');
			}
			sb.append(e.getKey()).append('=')
				.append(e.getValue().getX()).append(',')
				.append(e.getValue().getY()).append(',')
				.append(e.getValue().getPlane());
		}
		configManager.setConfiguration("ddtracker", "landmarks", sb.toString());
	}

	private void gameMessage(String msg)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, null);
	}

	// ---- Gear check ----

	/**
	 * @return a warning label for this player, or null if their gear is fine
	 * (or gear checking doesn't apply to them).
	 */
	String gearWarning(Player p)
	{
		final DdTrackerConfig.GearCheckMode mode = config.gearCheck();
		if (mode == DdTrackerConfig.GearCheckMode.OFF && !config.armourCheck())
		{
			return null;
		}
		if (!isMember(p, client.getLocalPlayer()))
		{
			return null;
		}
		final PlayerComposition comp = p.getPlayerComposition();
		if (comp == null)
		{
			return null;
		}

		// Naked: no body and no leg armour. getEquipmentId returns -1 when the slot
		// is showing a base character kit rather than a real item.
		if (config.armourCheck()
			&& comp.getEquipmentId(KitType.TORSO) == -1
			&& comp.getEquipmentId(KitType.LEGS) == -1)
		{
			return "NAKED";
		}

		if (mode == DdTrackerConfig.GearCheckMode.OFF)
		{
			return null;
		}

		final int weaponId = comp.getEquipmentId(KitType.WEAPON);
		if (weaponId == -1)
		{
			return "Unarmed";
		}
		if (mode == DdTrackerConfig.GearCheckMode.CROSSBOW)
		{
			final ItemComposition def = client.getItemDefinition(weaponId);
			final String name = def == null ? null : def.getName();
			if (name == null || !name.toLowerCase(Locale.ROOT).contains("crossbow"))
			{
				return "No xbow";
			}
		}
		return null;
	}

	boolean isNakedWarning(String warning)
	{
		return "NAKED".equals(warning);
	}

	// ---- Stats / panel ----

	List<PlayerStats> getStatsSnapshot()
	{
		return new ArrayList<>(stats.values());
	}

	void resetStats()
	{
		stats.clear();
		updatePanel();
	}

	boolean isDdTileSet()
	{
		return ddTile != null;
	}

	private void updatePanel()
	{
		if (panel != null)
		{
			panel.update(getStatsSnapshot(), isDdTileSet());
		}
	}

	private BufferedImage createIcon()
	{
		try
		{
			final BufferedImage loaded = ImageUtil.loadImageResource(DdTrackerPlugin.class, "icon.png");
			if (loaded != null)
			{
				return loaded;
			}
		}
		catch (IllegalArgumentException ex)
		{
			log.debug("icon.png resource not found, using drawn fallback");
		}
		return drawFallbackIcon();
	}

	private BufferedImage drawFallbackIcon()
	{
		final BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(190, 40, 40));
		g.fillOval(1, 1, 22, 22);
		g.setColor(Color.WHITE);
		g.setStroke(new BasicStroke(1.5f));
		g.drawOval(1, 1, 22, 22);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
		final FontMetrics fm = g.getFontMetrics();
		final String s = "WA";
		g.drawString(s, (24 - fm.stringWidth(s)) / 2, (24 + fm.getAscent() - fm.getDescent()) / 2);
		g.dispose();
		return img;
	}
}
