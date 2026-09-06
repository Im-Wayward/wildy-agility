package com.ddtracker;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import java.awt.Stroke;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

public class DdTrackerOverlay extends Overlay
{
	private static final Stroke GATE_STROKE = new BasicStroke(2f);
	private static final int GATE_OUTLINE_WIDTH = 3;
	private static final int GATE_OUTLINE_FEATHER = 4;

	private final Client client;
	private final DdTrackerPlugin plugin;
	private final DdTrackerConfig config;
	private final ModelOutlineRenderer modelOutlineRenderer;

	@Inject
	private DdTrackerOverlay(Client client, DdTrackerPlugin plugin, DdTrackerConfig config,
		ModelOutlineRenderer modelOutlineRenderer)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.modelOutlineRenderer = modelOutlineRenderer;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		renderBankGates(graphics);
		renderGearWarnings(graphics);
		renderCallout(graphics);
		renderDd(graphics);
		return null;
	}

	// ---- DD tile + call ----

	private void renderDd(Graphics2D graphics)
	{
		final WorldPoint tile = plugin.getDdTile();
		if (tile == null || tile.getPlane() != client.getPlane())
		{
			return;
		}

		final LocalPoint lp = LocalPoint.fromWorld(client, tile);
		if (lp != null)
		{
			final Polygon poly = Perspective.getCanvasTilePoly(client, lp);
			if (poly != null)
			{
				OverlayUtil.renderPolygon(graphics, poly, config.tileColor());
			}
		}

		final DdCall call = plugin.getActiveCall();
		if (call == null)
		{
			return;
		}

		if (!call.isFinished())
		{
			renderActiveCall(graphics, call, lp);
		}
		else if (lp != null)
		{
			final String txt = call.getCompliedCount() + "/" + call.getTracked().size() + " DD'd";
			renderTileText(graphics, lp, txt, Color.WHITE);
		}
	}

	private void renderActiveCall(Graphics2D graphics, DdCall call, LocalPoint lp)
	{
		if (lp != null)
		{
			// Deliberately not a countdown: the call runs until someone clears it, so the
			// useful number over the tile is how many are actually on it.
			renderTileText(graphics, lp,
				"DD  " + call.getCompliedCount() + "/" + call.getTracked().size(),
				Color.WHITE);
		}

		for (Player p : client.getPlayers())
		{
			if (p == null || p.getName() == null)
			{
				continue;
			}
			final DdCall.TrackedPlayer tp = call.getTracked().get(DdTrackerPlugin.normalize(p.getName()));
			if (tp == null)
			{
				continue;
			}

			final Color color;
			if (plugin.isOnDdTile(p))
			{
				color = config.onTileColor();
			}
			else if (tp.complied)
			{
				color = config.compliedColor();
			}
			else
			{
				color = config.offTileColor();
			}

			final Polygon poly = p.getCanvasTilePoly();
			if (poly != null)
			{
				OverlayUtil.renderPolygon(graphics, poly, color);
			}

			final String name = p.getName();
			final Point textLoc = p.getCanvasTextLocation(graphics, name, p.getLogicalHeight() + 40);
			if (textLoc != null)
			{
				OverlayUtil.renderTextLocation(graphics, textLoc, name, color);
			}
		}
	}

	// ---- Mass bank gates ----

	private void renderBankGates(Graphics2D graphics)
	{
		final String label = plugin.getBankAlertLabel();
		if (label == null || !config.massBankReminder())
		{
			return;
		}

		final Color color = config.massBankColor();
		final Color fill = new Color(color.getRed(), color.getGreen(), color.getBlue(), 40);
		final Player local = client.getLocalPlayer();

		TileObject nearest = null;
		int nearestDistance = Integer.MAX_VALUE;

		for (TileObject gate : plugin.getBankGates())
		{
			if (gate.getPlane() != client.getPlane())
			{
				continue;
			}

			modelOutlineRenderer.drawOutline(gate, GATE_OUTLINE_WIDTH, color, GATE_OUTLINE_FEATHER);

			final Shape clickbox = gate.getClickbox();
			if (clickbox != null)
			{
				OverlayUtil.renderPolygon(graphics, clickbox, color, fill, GATE_STROKE);
			}

			if (local != null)
			{
				final WorldPoint wp = gate.getWorldLocation();
				if (wp != null)
				{
					final int distance = local.getWorldLocation().distanceTo(wp);
					if (distance < nearestDistance)
					{
						nearestDistance = distance;
						nearest = gate;
					}
				}
			}
		}

		// One label only, on the closest gate, so a double gate is not written on twice.
		// No countdown here on purpose - the gates just stay gold until the timer lapses.
		if (nearest != null)
		{
			final LocalPoint lp = nearest.getLocalLocation();
			if (lp != null)
			{
				renderTileText(graphics, lp, label, color);
			}
		}
	}

	// ---- Location callout ----

	private void renderCallout(Graphics2D graphics)
	{
		final WorldPoint wp = plugin.getCalloutPoint();
		if (wp == null || wp.getPlane() != client.getPlane())
		{
			return;
		}

		final LocalPoint lp = LocalPoint.fromWorld(client, wp);
		if (lp == null)
		{
			// Off-scene: the hint arrow (minimap + edge of screen) still points there
			return;
		}

		final Polygon area = Perspective.getCanvasTileAreaPoly(client, lp, 3);
		if (area != null)
		{
			OverlayUtil.renderPolygon(graphics, area, config.calloutColor());
		}

		final int ticksLeft = Math.max(0, plugin.getCalloutEndTick() - client.getTickCount());
		final int secsLeft = (int) Math.ceil(ticksLeft * 0.6);
		final String label = plugin.getCalloutLabel() + " (" + secsLeft + "s)";
		renderTileText(graphics, lp, label, config.calloutColor());
	}

	// ---- Gear check ----

	private void renderGearWarnings(Graphics2D graphics)
	{
		if (config.gearCheck() == DdTrackerConfig.GearCheckMode.OFF && !config.armourCheck())
		{
			return;
		}

		for (Player p : client.getPlayers())
		{
			if (p == null || p.getName() == null)
			{
				continue;
			}
			final String warning = plugin.gearWarning(p);
			if (warning == null)
			{
				continue;
			}
			final Color color = plugin.isNakedWarning(warning)
				? config.nakedWarnColor()
				: config.gearWarnColor();
			final Point loc = p.getCanvasTextLocation(graphics, warning, p.getLogicalHeight() + 90);
			if (loc != null)
			{
				OverlayUtil.renderTextLocation(graphics, loc, warning, color);
			}
		}
	}

	private void renderTileText(Graphics2D graphics, LocalPoint lp, String text, Color color)
	{
		final Point pt = Perspective.getCanvasTextLocation(client, graphics, lp, text, 0);
		if (pt != null)
		{
			OverlayUtil.renderTextLocation(graphics, pt, text, color);
		}
	}
}
