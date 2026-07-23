package com.ddtracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Flashes the prayer tab icon and shows a banner when someone calls a protection prayer.
 * Drawn above widgets so it is visible on top of the inventory/tab area.
 */
public class DdPrayerOverlay extends Overlay
{
	private static final int[] PRAYER_ICON_IDS = {
		ComponentID.FIXED_VIEWPORT_PRAYER_ICON,
		ComponentID.RESIZABLE_VIEWPORT_PRAYER_ICON,
		ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_PRAYER_ICON
	};

	private static final int[] PRAYER_TAB_IDS = {
		ComponentID.FIXED_VIEWPORT_PRAYER_TAB,
		ComponentID.RESIZABLE_VIEWPORT_PRAYER_TAB,
		ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_PRAYER_TAB
	};

	private final Client client;
	private final DdTrackerPlugin plugin;
	private final DdTrackerConfig config;

	@Inject
	private DdPrayerOverlay(Client client, DdTrackerPlugin plugin, DdTrackerConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final String label = plugin.getPrayerAlertLabel();
		if (label == null)
		{
			return null;
		}

		final Rectangle bounds = findPrayerIconBounds();
		if (bounds == null)
		{
			return null;
		}

		// Pulse so it is hard to miss without being a solid block of color
		final double phase = (System.currentTimeMillis() % 1000) / 1000.0;
		final int alpha = (int) (110 + 110 * Math.abs(Math.sin(phase * Math.PI)));
		final Color base = config.prayerAlertColor();
		final Color pulse = new Color(base.getRed(), base.getGreen(), base.getBlue(),
			Math.min(255, alpha));

		final Rectangle box = new Rectangle(bounds.x - 2, bounds.y - 2,
			bounds.width + 4, bounds.height + 4);

		graphics.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 55));
		graphics.fill(box);
		graphics.setColor(pulse);
		graphics.draw(box);

		drawLabel(graphics, label, box, base);
		return null;
	}

	private void drawLabel(Graphics2D graphics, String label, Rectangle box, Color color)
	{
		final Font original = graphics.getFont();
		graphics.setFont(original.deriveFont(Font.BOLD, 14f));
		final FontMetrics fm = graphics.getFontMetrics();
		final int w = fm.stringWidth(label);

		int x = box.x + box.width / 2 - w / 2;
		int y = box.y - 6;

		// Keep the banner on screen
		x = Math.max(4, Math.min(x, client.getCanvasWidth() - w - 4));
		y = Math.max(fm.getAscent() + 2, y);

		graphics.setColor(new Color(0, 0, 0, 190));
		graphics.fillRect(x - 4, y - fm.getAscent() - 2, w + 8, fm.getHeight() + 4);
		graphics.setColor(Color.BLACK);
		graphics.drawString(label, x + 1, y + 1);
		graphics.setColor(color);
		graphics.drawString(label, x, y);
		graphics.setFont(original);
	}

	private Rectangle findPrayerIconBounds()
	{
		Rectangle r = firstVisibleBounds(PRAYER_ICON_IDS);
		if (r == null)
		{
			r = firstVisibleBounds(PRAYER_TAB_IDS);
		}
		return r;
	}

	private Rectangle firstVisibleBounds(int[] componentIds)
	{
		for (int id : componentIds)
		{
			final Widget w = client.getWidget(id);
			if (w != null && !w.isHidden())
			{
				final Rectangle b = w.getBounds();
				if (b != null && b.width > 0 && b.height > 0)
				{
					return b;
				}
			}
		}
		return null;
	}
}
