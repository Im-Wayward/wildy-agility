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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.kit.KitType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
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

	/** Hard cap Jagex puts on a friends chat. */
	private static final int FC_CAPACITY = 500;
	/** Ticks between friends chat headcounts (~6s). Cheap, but no need to do it every tick. */
	private static final int FC_CHECK_TICKS = 10;
	/** Member count must fall this far below a fired threshold before it can fire again. */
	private static final int FC_HYSTERESIS = 3;
	/** Don't nag about stacking if you are this far from the tile - you are not at the course. */
	private static final int SELF_STACK_MAX_DISTANCE = 50;

	/** Only gates this close to the DD tile (i.e. at the course) are worth tracking. */
	private static final int GATE_SEARCH_RADIUS = 64;
	/** Ticks between gate rescans while a bank call is live (~6s). */
	private static final int GATE_RESCAN_TICKS = 10;
	/**
	 * A single-word trigger like "bank" only counts when the message is this many words or
	 * fewer, so it cannot fire from inside "pkers at ladder brb banking".
	 */
	private static final int BARE_WORD_LIMIT = 1;

	private static final Color CHAT_HIGHLIGHT = new Color(0, 200, 255);

	/**
	 * Rolling window of repeated calls. Holds the tick and sender of each matching message
	 * inside the window so a confirmed call can be dated and credited to whoever started it,
	 * not to whoever happened to send the third message.
	 */
	private static final class RepeatWindow
	{
		/** Belt and braces: a hard-spammed window cannot grow without bound. */
		private static final int MAX_ENTRIES = 64;

		private final List<Integer> ticks = new ArrayList<>();
		private final List<String> senders = new ArrayList<>();

		/** @return true when the window holds {@code required} messages */
		boolean add(int now, String sender, int required, int windowTicks)
		{
			// A tick count that went backwards means a reconnect, so the old entries are
			// meaningless - and would never prune, since the age comparison goes negative.
			if (!ticks.isEmpty() && now < ticks.get(ticks.size() - 1))
			{
				clear();
			}

			while (!ticks.isEmpty()
				&& (now - ticks.get(0) > windowTicks || ticks.size() >= MAX_ENTRIES))
			{
				ticks.remove(0);
				senders.remove(0);
			}

			ticks.add(now);
			senders.add(sender);
			return ticks.size() >= required;
		}

		int size()
		{
			return ticks.size();
		}

		/**
		 * Index of the first message in the confirming run - the last {@code required}
		 * entries - so an older stray still sitting inside the window cannot take credit
		 * for a call that was actually made just now.
		 */
		private int runStartIndex(int required)
		{
			return Math.max(0, ticks.size() - required);
		}

		int runStartTick(int required)
		{
			return ticks.isEmpty() ? 0 : ticks.get(runStartIndex(required));
		}

		String runStarter(int required)
		{
			return senders.isEmpty() ? "" : senders.get(runStartIndex(required));
		}

		void clear()
		{
			ticks.clear();
			senders.clear();
		}
	}

	private static final String BANK_ALERT_LABEL = "MASS BANK";

	/**
	 * A single message is never a call. A real one is either echoed by a second rank or
	 * repeated by the same one, so we wait for this many messages inside the window before
	 * acting - which keeps a one-off mention in ordinary chat from triggering anything.
	 * The count is config; the window is not.
	 */
	private static final int REPEAT_WINDOW_SECONDS = 20;

	/** Ticks between sidebar gear-list refreshes (~1.8s). */
	private static final int GEAR_PANEL_TICKS = 3;

	/** Only one hint arrow can exist at a time, so the highest-priority claim wins it. */
	private enum HintArrowOwner
	{
		NONE(0),
		SELF_STACK(1),
		BANK(2),
		CALLOUT(3);

		private final int priority;

		HintArrowOwner(int priority)
		{
			this.priority = priority;
		}
	}

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
	private DdAlertOverlay alertOverlay;

	@Inject
	private ClientThread clientThread;

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

	@Getter
	private boolean selfStackAlertActive;

	@Getter
	private int selfStackDistance;

	@Getter
	private String bankAlertLabel;

	@Getter
	private int bankAlertEndTick;

	/** Gate objects currently in the scene, gold-outlined while a bank call is live. */
	@Getter
	private final Set<TileObject> bankGates = new HashSet<>();

	/**
	 * Object ids already resolved to gate / not-a-gate, so a scene load does not look up
	 * a composition for every object in it. Ids whose name varies by varbit are rare
	 * enough at the course not to matter.
	 */
	private final Set<Integer> gateIds = new HashSet<>();
	private final Set<Integer> notGateIds = new HashSet<>();

	private int bankRescanTick;

	private HintArrowOwner hintArrowOwner = HintArrowOwner.NONE;
	private WorldPoint hintArrowPoint;

	/** Welcome note is once per plugin start, not once per login. */
	private boolean welcomeSent;

	/** Highest capacity threshold already announced, so each one only fires once. */
	private int fcHighestWarned;

	private int fcNextCheckTick;

	private final RepeatWindow ddRepeats = new RepeatWindow();
	private final RepeatWindow bankRepeats = new RepeatWindow();

	private int gearPanelTick;
	private String lastGearSignature;

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
		welcomeSent = false;
		resetTransientState();
		overlayManager.add(overlay);
		overlayManager.add(prayerOverlay);
		overlayManager.add(alertOverlay);
		panel = new DdTrackerPanel(this);
		navButton = NavigationButton.builder()
			.tooltip("Wildy Agility")
			.icon(createIcon())
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		updatePanel();

		// Enabling the plugin mid-session gets no GameStateChanged, so cover that case here.
		clientThread.invokeLater(this::maybeSendWelcome);
	}

	/**
	 * Clears everything keyed off {@link Client#getTickCount()} plus the panel's change
	 * cache. Called on start-up and whenever the connection drops: the tick counter can
	 * restart low, and a stale future tick would silently block DD calls, gear refreshes
	 * and capacity warnings for the rest of the session.
	 */
	private void resetTransientState()
	{
		fcHighestWarned = 0;
		fcNextCheckTick = 0;
		gearPanelTick = 0;
		bankRescanTick = 0;
		callBlockUntilTick = 0;
		lastGearSignature = null;
		ddRepeats.clear();
		bankRepeats.clear();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlayManager.remove(prayerOverlay);
		overlayManager.remove(alertOverlay);
		clientToolbar.removeNavigation(navButton);
		activeCall = null;
		prayerAlertLabel = null;
		bankAlertLabel = null;
		bankGates.clear();
		gateIds.clear();
		notGateIds.clear();
		resetTransientState();
		releaseHintArrow(HintArrowOwner.BANK);
		clearSelfStackAlert();
		clearCallout();
		ddTile = null;
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
	public void onGameStateChanged(GameStateChanged event)
	{
		final GameState state = event.getGameState();

		if (state == GameState.LOGGED_IN)
		{
			maybeSendWelcome();
		}

		if (state == GameState.LOADING || state == GameState.HOPPING
			|| state == GameState.LOGIN_SCREEN)
		{
			// Scene is being rebuilt; every tracked object reference is about to go stale.
			bankGates.clear();
		}

		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING
			|| state == GameState.CONNECTION_LOST)
		{
			// Every alert below expires on a tick count that a reconnect can reset, and a
			// call from before you left is stale anyway - so drop them rather than risk one
			// sticking on screen. The in-flight DD call goes too: you cannot judge who
			// stacked for a call you were not there to watch.
			activeCall = null;
			prayerAlertLabel = null;
			bankAlertLabel = null;
			releaseHintArrow(HintArrowOwner.BANK);
			clearSelfStackAlert();
			clearCallout();
			resetTransientState();

			if (panel != null)
			{
				panel.updateGear(new ArrayList<>());
			}
		}
	}

	// ---- Gate tracking (for the mass bank reminder) ----

	/**
	 * Rebuilds {@link #bankGates} from the loaded scene. Done on demand, and repeated every
	 * {@link #GATE_RESCAN_TICKS} while a call is live, rather than driven by object spawn
	 * events: a bank call is a short discrete event, and rescanning means we never hold a
	 * TileObject reference that has gone stale because a gate opened, closed or despawned.
	 */
	private void rescanGates()
	{
		bankGates.clear();

		if (!config.massBankReminder())
		{
			return;
		}

		final Scene scene = client.getScene();
		final Tile[][][] tiles = scene == null ? null : scene.getTiles();
		final WorldPoint centre = gateSearchCentre();
		if (tiles == null || centre == null)
		{
			return;
		}

		for (Tile[][] plane : tiles)
		{
			if (plane == null)
			{
				continue;
			}
			for (Tile[] column : plane)
			{
				if (column == null)
				{
					continue;
				}
				for (Tile tile : column)
				{
					if (tile == null)
					{
						continue;
					}
					checkGate(tile.getWallObject(), centre);
					checkGate(tile.getDecorativeObject(), centre);
					final GameObject[] gameObjects = tile.getGameObjects();
					if (gameObjects == null)
					{
						continue;
					}
					for (GameObject gameObject : gameObjects)
					{
						checkGate(gameObject, centre);
					}
				}
			}
		}
	}

	/** The DD tile marks the course; without one, fall back to wherever the player is. */
	private WorldPoint gateSearchCentre()
	{
		if (ddTile != null)
		{
			return ddTile;
		}
		final Player local = client.getLocalPlayer();
		return local == null ? null : local.getWorldLocation();
	}

	private void checkGate(TileObject object, WorldPoint centre)
	{
		if (object == null)
		{
			return;
		}

		final WorldPoint wp = object.getWorldLocation();
		// distanceTo is MAX_VALUE across planes, which also filters other floors out.
		if (wp == null || wp.distanceTo(centre) > GATE_SEARCH_RADIUS)
		{
			return;
		}

		final int id = object.getId();
		if (notGateIds.contains(id))
		{
			return;
		}
		if (gateIds.contains(id))
		{
			bankGates.add(object);
			return;
		}

		final ObjectComposition comp = objectDefinition(id);
		if (comp == null)
		{
			// May be a varbit that has not resolved yet, so leave it uncached and let the
			// next rescan try again rather than writing the id off for the whole session.
			return;
		}

		final String name = comp.getName();
		if (name == null || name.isEmpty() || "null".equals(name))
		{
			notGateIds.add(id);
			return;
		}

		final String lower = name.toLowerCase(Locale.ROOT);
		for (String want : config.bankGateNames().toLowerCase(Locale.ROOT).split(","))
		{
			final String w = want.trim();
			if (!w.isEmpty() && lower.contains(w))
			{
				gateIds.add(id);
				bankGates.add(object);
				return;
			}
		}
		notGateIds.add(id);
	}

	private ObjectComposition objectDefinition(int id)
	{
		try
		{
			final ObjectComposition comp = client.getObjectDefinition(id);
			if (comp == null)
			{
				return null;
			}
			return comp.getImpostorIds() == null ? comp : comp.getImpostor();
		}
		catch (RuntimeException ex)
		{
			log.debug("Could not resolve object definition {}", id, ex);
			return null;
		}
	}

	private void maybeSendWelcome()
	{
		if (welcomeSent || !config.showWelcomeMessage()
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		welcomeSent = true;
		gameMessage("Wildy Agility: This plugin is designed to be used with '"
			+ ColorUtil.wrapWithColorTag(config.fcInfo(), CHAT_HIGHLIGHT)
			+ "'. If you are new, please join and read the discord ("
			+ ColorUtil.wrapWithColorTag(config.discordInfo(), CHAT_HIGHLIGHT)
			+ ") to understand how these masses work.");
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if ("ddtracker".equals(event.getGroup()))
		{
			parseLandmarks();
			if ("bankGateNames".equals(event.getKey()))
			{
				// The cached id -> is-a-gate answers were decided by the old name list.
				gateIds.clear();
				notGateIds.clear();
				bankGates.clear();
				bankRescanTick = 0;
			}
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

		// Chat emoji arrive as <img=29> tags and get typed flush against a word
		// ("hurry<img=29>mass bank"). Deleting them outright glues that into "hurrymass
		// bank" and destroys the phrase boundary, so they become a space instead.
		final String message = event.getMessage()
			.replaceAll("<[^>]*>", " ")
			.trim()
			.toLowerCase(Locale.ROOT);

		if (config.ranksOnly() && !isRanked(normalize(event.getName()), fc))
		{
			debugMatch(senderDisplay, message, "ignored - not a ranked caller");
			return;
		}

		if (config.prayerAlerts())
		{
			checkPrayerCall(message, senderDisplay);
		}

		// A bank call deliberately does NOT suppress the location callout: swallowing a
		// pker callout on a false match would be far worse than showing both. The callout
		// blocklist is what keeps ordinary chat from pinging an obstacle.
		if (config.massBankReminder())
		{
			checkMassBank(message, senderDisplay);
		}

		if (config.locationCallouts())
		{
			checkCallout(message, senderDisplay);
		}

		if (!isTrigger(message))
		{
			return;
		}

		final int now = client.getTickCount();

		// Recorded BEFORE the guards below on purpose. Spam that lands during a live call
		// or its min-gap still counts toward the NEXT call's run of three - otherwise a
		// pker returning at the end of a lap gets its call swallowed, because the burst
		// that announced it was spent while the previous call was still running.
		final int required = config.callsRequired();
		final boolean confirmed =
			ddRepeats.add(now, senderDisplay, required, repeatWindowTicks());

		// De-duplicate spammed calls: everyone repeats "dd" so the whole FC sees it,
		// but it is all one event for compliance purposes.
		if (activeCall != null && !activeCall.isFinished())
		{
			return;
		}
		if (now < callBlockUntilTick)
		{
			return;
		}
		if (!confirmed)
		{
			reportCallProgress("dd", ddRepeats.size(), required);
			return;
		}

		final String caller = ddRepeats.runStarter(required);
		final int calledAt = ddRepeats.runStartTick(required);
		ddRepeats.clear();

		if (ddTile == null)
		{
			gameMessage("Wildy Agility: DD called by " + caller
				+ " but no DD tile is set. Shift + right-click a tile to set one.");
			return;
		}

		startCall(caller, calledAt);
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

	/**
	 * @param calledAt tick of the FIRST message in the run of repeats, not the one that
	 * confirmed it - so the grace period and the min-gap both run from when the call
	 * actually went out in chat.
	 */
	private void startCall(String caller, int calledAt)
	{
		// Clamped to the present: the run of repeats can span most of the 20s window, so a
		// short grace period could otherwise produce a call that has already expired and
		// records everyone as a miss on the very next tick.
		final int now = client.getTickCount();
		final int end = Math.max(now + 1,
			calledAt + (int) Math.ceil(config.graceSeconds() * 1000.0 / TICK_MS));
		callBlockUntilTick = Math.max(now,
			calledAt + (int) Math.ceil(config.minCallGapSeconds() * 1000.0 / TICK_MS));

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

		if (bankAlertLabel != null)
		{
			if (now >= bankAlertEndTick || !config.massBankReminder())
			{
				bankAlertLabel = null;
				bankGates.clear();
				releaseHintArrow(HintArrowOwner.BANK);
			}
			else
			{
				if (now >= bankRescanTick)
				{
					bankRescanTick = now + GATE_RESCAN_TICKS;
					rescanGates();
				}
				// Re-claimed every tick on purpose: a pker callout outranks this arrow, and
				// a one-shot claim would be lost for good once that callout expired.
				if (config.bankHintArrow())
				{
					claimHintArrow(nearestGate(), HintArrowOwner.BANK);
				}
			}
		}

		if (now >= gearPanelTick)
		{
			gearPanelTick = now + GEAR_PANEL_TICKS;
			updateGearPanel();
		}

		if (config.fcCapacityWarnings() && now >= fcNextCheckTick)
		{
			fcNextCheckTick = now + FC_CHECK_TICKS;
			checkFcCapacity();
		}

		if (activeCall == null)
		{
			clearSelfStackAlert();
			return;
		}

		if (!activeCall.isFinished())
		{
			checkCompliance();
			updateSelfStackAlert();
			if (now >= activeCall.getEndTick())
			{
				finishCall(now);
			}
		}
		else
		{
			clearSelfStackAlert();
			if (now >= activeCall.getClearTick())
			{
				activeCall = null;
			}
		}
	}

	// ---- "you are not stacked" ----

	private void updateSelfStackAlert()
	{
		final Player local = client.getLocalPlayer();
		if (!config.selfStackAlert() || ddTile == null || local == null || isOnDdTile(local))
		{
			clearSelfStackAlert();
			return;
		}

		final int distance = local.getWorldLocation().distanceTo(ddTile);
		if (distance > SELF_STACK_MAX_DISTANCE)
		{
			// Banking, at Ferox, anywhere but the course - not worth a banner.
			clearSelfStackAlert();
			return;
		}

		selfStackAlertActive = true;
		selfStackDistance = distance;
		if (config.selfStackHintArrow())
		{
			setSelfStackArrow();
		}
	}

	private void clearSelfStackAlert()
	{
		selfStackAlertActive = false;
		selfStackDistance = 0;
		clearSelfStackArrow();
	}

	private void setSelfStackArrow()
	{
		claimHintArrow(ddTile, HintArrowOwner.SELF_STACK);
	}

	private void clearSelfStackArrow()
	{
		releaseHintArrow(HintArrowOwner.SELF_STACK);
	}

	/**
	 * Takes the hint arrow if nothing higher-priority holds it. A lower-priority claim is
	 * simply dropped, and re-made on a later tick once the holder releases.
	 */
	private void claimHintArrow(WorldPoint wp, HintArrowOwner owner)
	{
		if (wp == null || owner.priority < hintArrowOwner.priority)
		{
			return;
		}
		if (owner == hintArrowOwner && wp.equals(hintArrowPoint))
		{
			return;
		}
		client.setHintArrow(wp);
		hintArrowOwner = owner;
		hintArrowPoint = wp;
	}

	private void releaseHintArrow(HintArrowOwner owner)
	{
		if (hintArrowOwner == owner)
		{
			client.clearHintArrow();
			hintArrowOwner = HintArrowOwner.NONE;
			hintArrowPoint = null;
		}
	}

	// ---- Mass bank ----

	private void checkMassBank(String message, String sender)
	{
		final String padded = phraseText(message);

		// "brb bank" and "i need to bank" are people narrating their own trip, not calling one.
		if (containsAnyPhrase(padded, config.massBankBlocklist()))
		{
			debugMatch(sender, message, "mass bank suppressed by the ignore list");
			return;
		}

		if (!containsAnyPhrase(padded, config.massBankPhrases(), BARE_WORD_LIMIT))
		{
			debugMatch(sender, message, "no mass bank phrase matched");
			return;
		}

		final int now = client.getTickCount();

		// Already running: a repeat just pushes the timer back out, no re-confirmation
		// needed, so the gates stay gold for 3 minutes past the last call.
		final int required = config.callsRequired();
		if (bankAlertLabel == null
			&& !bankRepeats.add(now, sender, required, repeatWindowTicks()))
		{
			reportCallProgress("mass bank", bankRepeats.size(), required);
			debugMatch(sender, message, "mass bank heard, waiting for call "
				+ (bankRepeats.size() + 1) + " of " + required);
			return;
		}

		final boolean fresh = bankAlertLabel == null;
		final String caller = fresh ? bankRepeats.runStarter(required) : sender;
		bankRepeats.clear();

		bankAlertLabel = BANK_ALERT_LABEL;
		bankAlertEndTick = now + (int) Math.ceil(config.massBankSeconds() * 1000.0 / TICK_MS);

		rescanGates();
		bankRescanTick = now + GATE_RESCAN_TICKS;

		if (config.bankHintArrow())
		{
			claimHintArrow(nearestGate(), HintArrowOwner.BANK);
		}

		if (fresh)
		{
			gameMessage("Wildy Agility: "
				+ ColorUtil.wrapWithColorTag("mass bank", CHAT_HIGHLIGHT)
				+ " called by " + caller + ".");
		}
	}

	/** Nearest tracked gate, falling back to the saved "gate" landmark if none are in view. */
	private WorldPoint nearestGate()
	{
		final Player local = client.getLocalPlayer();
		if (local != null)
		{
			WorldPoint best = null;
			int bestDistance = Integer.MAX_VALUE;
			for (TileObject gate : bankGates)
			{
				final WorldPoint wp = gate.getWorldLocation();
				if (wp == null)
				{
					continue;
				}
				final int distance = local.getWorldLocation().distanceTo(wp);
				if (distance < bestDistance)
				{
					bestDistance = distance;
					best = wp;
				}
			}
			if (best != null)
			{
				return best;
			}
		}
		return landmarks.get("gate");
	}

	// ---- Friends chat capacity ----

	private void checkFcCapacity()
	{
		final FriendsChatManager mgr = client.getFriendsChatManager();
		if (mgr == null)
		{
			fcHighestWarned = 0;
			return;
		}

		final FriendsChatMember[] members = mgr.getMembers();
		if (members == null)
		{
			return;
		}

		final Set<Integer> massWorlds = parseMassWorlds();
		// getCount() is authoritative: getMembers() may hand back a capacity-sized
		// array padded with nulls rather than one sized to the member count.
		final int count = mgr.getCount();

		int offWorld = 0;
		if (!massWorlds.isEmpty())
		{
			for (FriendsChatMember m : members)
			{
				if (m != null && !massWorlds.contains(m.getWorld()))
				{
					offWorld++;
				}
			}
		}

		int highest = 0;
		for (int threshold : parseCapacityThresholds())
		{
			if (count >= threshold && threshold > highest)
			{
				highest = threshold;
			}
		}

		if (highest > fcHighestWarned)
		{
			fcHighestWarned = highest;
			announceFcCapacity(count, offWorld, !massWorlds.isEmpty());
		}
		else if (highest < fcHighestWarned && count < fcHighestWarned - FC_HYSTERESIS)
		{
			// Dropped clear of the threshold, so it may fire again.
			fcHighestWarned = highest;
		}
	}

	private void announceFcCapacity(int count, int offWorld, boolean haveMassWorlds)
	{
		final StringBuilder sb = new StringBuilder("Wildy Agility: friends chat is at ")
			.append(ColorUtil.wrapWithColorTag(count + "/" + FC_CAPACITY, CHAT_HIGHLIGHT))
			.append(" members.");

		// With no mass worlds configured the off-world count is always 0, and saying so
		// would read as "nobody is off-world" rather than "not checked".
		if (haveMassWorlds)
		{
			sb.append(' ')
				.append(ColorUtil.wrapWithColorTag(String.valueOf(offWorld), CHAT_HIGHLIGHT))
				.append(offWorld == 1 ? " member is" : " members are")
				.append(" outside the mass world.");
		}

		gameMessage(sb.toString());
	}

	private Set<Integer> parseMassWorlds()
	{
		final Set<Integer> worlds = new LinkedHashSet<>();
		for (String part : config.massWorlds().split(","))
		{
			final String t = part.trim();
			if (t.isEmpty())
			{
				continue;
			}
			try
			{
				worlds.add(Integer.parseInt(t));
			}
			catch (NumberFormatException ex)
			{
				log.debug("Bad mass world: {}", t);
			}
		}
		return worlds;
	}

	private List<Integer> parseCapacityThresholds()
	{
		final List<Integer> thresholds = new ArrayList<>();
		for (String part : config.fcCapacityThresholds().split(","))
		{
			final String t = part.trim();
			if (t.isEmpty())
			{
				continue;
			}
			try
			{
				thresholds.add(Integer.parseInt(t));
			}
			catch (NumberFormatException ex)
			{
				log.debug("Bad capacity threshold: {}", t);
			}
		}
		return thresholds;
	}

	private void finishCall(int now)
	{
		activeCall.setFinished(true);
		activeCall.setClearTick(now + RESULT_DISPLAY_TICKS);
		clearSelfStackAlert();

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
		return containsAnyPhrase(phraseText(message), config.calloutBlocklist());
	}

	/**
	 * Flattens a chat message for whole-phrase matching. Apostrophes are dropped rather
	 * than spaced out, so "don't let him log" matches a "dont let" phrase; every other
	 * non-alphanumeric character becomes a space, and the result is padded so " phrase "
	 * cannot match mid-word.
	 */
	private static String phraseText(String message)
	{
		return " " + message
			.replaceAll("['\u2018\u2019`]", "")
			.replaceAll("[^a-z0-9 ]", " ")
			.replaceAll("\\s+", " ")
			.trim() + " ";
	}

	/**
	 * @param paddedMessage output of {@link #phraseText(String)}
	 * @param phrases comma-separated phrase list as stored in config
	 */
	private static boolean containsAnyPhrase(String paddedMessage, String phrases)
	{
		return containsAnyPhrase(paddedMessage, phrases, 0);
	}

	/**
	 * @param bareWordLimit a single-word phrase only counts when the whole message is this
	 * many words or fewer; 0 disables the rule. Blocklists pass 0 so they match anywhere,
	 * while trigger lists pass {@link #BARE_WORD_LIMIT} so a bare word has to stand alone
	 * to fire.
	 */
	private static boolean containsAnyPhrase(String paddedMessage, String phrases, int bareWordLimit)
	{
		final int wordCount = bareWordLimit <= 0 ? 0 : countWords(paddedMessage);

		for (String phrase : phrases.toLowerCase(Locale.ROOT).split(","))
		{
			final String p = phrase.trim();
			if (p.isEmpty() || !containsPhrase(paddedMessage, p))
			{
				continue;
			}
			if (bareWordLimit > 0 && p.indexOf(' ') < 0 && wordCount > bareWordLimit)
			{
				continue;
			}
			return true;
		}
		return false;
	}

	/**
	 * A call that needs repeating is completely invisible until it fires, which reads as
	 * the plugin being broken - it is what made mass bank look like it ignored long
	 * messages. This says out loud that the call was heard and how many more are needed.
	 */
	/**
	 * Troubleshooting aid, off by default. Only speaks about messages that look like they
	 * were meant to be a call, so it stays quiet in a busy friends chat.
	 */
	private void debugMatch(String sender, String message, String outcome)
	{
		if (!config.debugMatching() || !isDebugWorthy(message))
		{
			return;
		}
		gameMessage("[wa] " + sender + ": " + outcome + " | \"" + phraseText(message).trim() + "\"");
	}

	/** True if the message mentions banking or a DD trigger word, so it is worth reporting. */
	private boolean isDebugWorthy(String message)
	{
		if (message.contains("bank"))
		{
			return true;
		}
		for (String word : config.triggerWords().toLowerCase(Locale.ROOT).split(","))
		{
			final String w = word.trim();
			if (!w.isEmpty() && message.contains(w))
			{
				return true;
			}
		}
		return false;
	}

	private void reportCallProgress(String label, int seen, int required)
	{
		if (!config.showCallProgress() || seen <= 0 || seen >= required)
		{
			return;
		}
		gameMessage("Wildy Agility: " + label + " " + seen + "/" + required + ".");
	}

	private int repeatWindowTicks()
	{
		return (int) Math.ceil(REPEAT_WINDOW_SECONDS * 1000.0 / TICK_MS);
	}

	/**
	 * Whole-word phrase match that tolerates a suffix on the final word, so "mass bank"
	 * also catches "mass banking" and a blocklist entry of "no bank" catches "no banking".
	 * The message is pre-flattened by {@link #phraseText(String)}, so it is padded with
	 * spaces and contains only [a-z0-9 ].
	 */
	private static boolean containsPhrase(String paddedMessage, String phrase)
	{
		final String needle = " " + phrase;
		int from = 0;

		while (true)
		{
			final int at = paddedMessage.indexOf(needle, from);
			if (at < 0)
			{
				return false;
			}

			int end = at + needle.length();
			while (end < paddedMessage.length()
				&& Character.isLetterOrDigit(paddedMessage.charAt(end)))
			{
				end++;
			}

			if (end < paddedMessage.length() && paddedMessage.charAt(end) == ' ')
			{
				return true;
			}
			from = at + 1;
		}
	}

	private static int countWords(String paddedMessage)
	{
		final String trimmed = paddedMessage.trim();
		return trimmed.isEmpty() ? 0 : trimmed.split(" ").length;
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
		// A pker callout outranks every other arrow, so this claim always wins.
		claimHintArrow(wp, HintArrowOwner.CALLOUT);
	}

	private void clearCallout()
	{
		calloutPoint = null;
		calloutLabel = null;
		releaseHintArrow(HintArrowOwner.CALLOUT);
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

	// ---- Gear list for the sidebar ----

	/** Channel members currently showing a gear problem, naked first then alphabetical. */
	List<GearFlag> getGearFlags()
	{
		final List<GearFlag> flags = new ArrayList<>();
		for (Player p : client.getPlayers())
		{
			if (p == null || p.getName() == null)
			{
				continue;
			}
			final String warning = gearWarning(p);
			if (warning != null)
			{
				flags.add(new GearFlag(Text.toJagexName(Text.removeTags(p.getName())), warning));
			}
		}
		flags.sort(Comparator
			.comparingInt((GearFlag f) -> f.isNaked() ? 0 : 1)
			.thenComparing(GearFlag::getDisplayName, String.CASE_INSENSITIVE_ORDER));
		return flags;
	}

	/**
	 * Pushes the gear list to the panel only when it actually changed - this runs a few
	 * times a second and rebuilding the Swing rows every time would be wasteful.
	 */
	private void updateGearPanel()
	{
		if (panel == null)
		{
			return;
		}

		final List<GearFlag> flags = getGearFlags();
		final StringBuilder sb = new StringBuilder();
		for (GearFlag f : flags)
		{
			sb.append(f.getDisplayName()).append('|').append(f.getWarning()).append(';');
		}

		final String signature = sb.toString();
		if (signature.equals(lastGearSignature))
		{
			return;
		}
		lastGearSignature = signature;
		panel.updateGear(flags);
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
