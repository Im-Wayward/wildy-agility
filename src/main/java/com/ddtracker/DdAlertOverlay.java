package com.ddtracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The "you are not on the DD tile" banner. Drawn above the widget layer so it is visible
 * over the inventory and any open interface.
 *
 * <p>Deliberately separate from {@link DdPrayerOverlay} so prayer calls keep their own
 * timing and colour and neither alert can suppress the other.
 */
public class DdAlertOverlay extends Overlay
{
	private final Client client;
	private final DdTrackerPlugin plugin;
	private final DdTrackerConfig config;

	@Inject
	private DdAlertOverlay(Client client, DdTrackerPlugin plugin, DdTrackerConfig config)
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
		if (!plugin.isSelfStackAlertActive())
		{
			return null;
		}

		final Color base = config.selfStackColor();
		drawCenteredBanner(graphics, "GET TO THE DD TILE", 0.20, 20f, base, 0);

		final int distance = plugin.getSelfStackDistance();
		if (distance > 0)
		{
			drawCenteredBanner(graphics,
				distance + (distance == 1 ? " tile away" : " tiles away"),
				0.20, 13f, base, 24);
		}
		return null;
	}

	private void drawCenteredBanner(Graphics2D graphics, String text, double heightFraction,
		float fontSize, Color color, int yOffset)
	{
		final Font original = graphics.getFont();
		graphics.setFont(original.deriveFont(Font.BOLD, fontSize));
		final FontMetrics fm = graphics.getFontMetrics();
		final int w = fm.stringWidth(text);

		final int x = Math.max(4, (client.getCanvasWidth() - w) / 2);
		final int y = (int) (client.getCanvasHeight() * heightFraction) + yOffset;

		graphics.setColor(new Color(0, 0, 0, 190));
		graphics.fillRect(x - 6, y - fm.getAscent() - 3, w + 12, fm.getHeight() + 6);
		graphics.setColor(Color.BLACK);
		graphics.drawString(text, x + 1, y + 1);
		graphics.setColor(pulse(color));
		graphics.drawString(text, x, y);
		graphics.setFont(original);
	}

	private Color pulse(Color base)
	{
		final double phase = (System.currentTimeMillis() % 1000) / 1000.0;
		final int alpha = (int) (110 + 110 * Math.abs(Math.sin(phase * Math.PI)));
		return new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.min(255, alpha));
	}
}
