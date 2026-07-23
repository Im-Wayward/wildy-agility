package com.ddtracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

public class DdTrackerOverlay extends Overlay
{
	private final Client client;
	private final DdTrackerPlugin plugin;
	private final DdTrackerConfig config;

	@Inject
	private DdTrackerOverlay(Client client, DdTrackerPlugin plugin, DdTrackerConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
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
			final int ticksLeft = Math.max(0, call.getEndTick() - client.getTickCount());
			final int secsLeft = (int) Math.ceil(ticksLeft * 0.6);
			renderTileText(graphics, lp, "DD! " + secsLeft + "s", Color.WHITE);
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
