package com.ddtracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

public class DdTrackerPanel extends PluginPanel
{
	private final JLabel statusLabel = new JLabel();
	private final JLabel callHeader = new JLabel();
	private final JPanel callPanel = new JPanel();
	private final JLabel gearHeader = new JLabel();
	private final JPanel gearPanel = new JPanel();
	private final JPanel listPanel = new JPanel();

	DdTrackerPanel(DdTrackerPlugin plugin)
	{
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		final JPanel header = new JPanel(new BorderLayout(0, 4));
		header.setOpaque(false);

		final JLabel title = new JLabel("Wildy Agility");
		title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
		title.setForeground(Color.WHITE);
		header.add(title, BorderLayout.NORTH);

		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		header.add(statusLabel, BorderLayout.CENTER);

		final JButton resetButton = new JButton("Reset stats");
		resetButton.addActionListener(e -> plugin.requestResetStats());
		header.add(resetButton, BorderLayout.SOUTH);

		add(header, BorderLayout.NORTH);

		// Gear first: it is live "who is a problem right now", where the compliance list
		// below it is history you read after the fact.
		final JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setOpaque(false);

		callPanel.setLayout(new BoxLayout(callPanel, BoxLayout.Y_AXIS));
		callPanel.setOpaque(false);
		callPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		gearPanel.setLayout(new BoxLayout(gearPanel, BoxLayout.Y_AXIS));
		gearPanel.setOpaque(false);
		gearPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
		listPanel.setOpaque(false);
		listPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Live call first: while one is running it is the only thing you care about.
		body.add(sectionHeader(callHeader, "DD CALL"));
		body.add(callPanel);
		body.add(Box.createVerticalStrut(12));
		body.add(sectionHeader(gearHeader, "GEAR CHECK"));
		body.add(gearPanel);
		body.add(Box.createVerticalStrut(12));
		body.add(sectionHeader(new JLabel(), "DD COMPLIANCE"));
		body.add(listPanel);

		add(body, BorderLayout.CENTER);
		add(buildCommunityFooter(), BorderLayout.SOUTH);

		updateGear(java.util.Collections.emptyList());
		updateCall(null);
	}

	private JLabel sectionHeader(JLabel label, String text)
	{
		label.setText(text);
		label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		return label;
	}

	private JPanel buildCommunityFooter()
	{
		final JPanel footer = new JPanel();
		footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
		footer.setOpaque(false);
		footer.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

		final JLabel fcLabel = new JLabel("In game: join FC \"Agility FC\"");
		fcLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		fcLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		footer.add(fcLabel);

		final JLabel discordLabel = new JLabel("Discord: discord.gg/agilityfc");
		discordLabel.setForeground(ColorScheme.BRAND_ORANGE);
		discordLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		discordLabel.setToolTipText("Open the Agility FC Discord invite in your browser");
		discordLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		discordLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse("https://discord.gg/agilityfc");
			}
		});
		footer.add(discordLabel);

		return footer;
	}

	// ---- Live DD call ----

	void updateCall(DdStatus status)
	{
		SwingUtilities.invokeLater(() ->
		{
			callPanel.removeAll();

			if (status == null)
			{
				callHeader.setText("DD CALL");
				callPanel.add(hint("No call running."));
			}
			else
			{
				callHeader.setText("DD CALL — " + status.getOnTile().size()
					+ " / " + status.getTracked() + " stacked");

				final JLabel meta = hint(status.getElapsedSeconds() + "s · called by "
					+ status.getCaller() + " · " + status.getComplied() + " have made it");
				callPanel.add(meta);
				callPanel.add(Box.createVerticalStrut(4));

				for (String name : status.getOnTile())
				{
					callPanel.add(buildCallRow(name, "on tile", ColorScheme.PROGRESS_COMPLETE_COLOR));
					callPanel.add(Box.createVerticalStrut(4));
				}
				for (String name : status.getOffTile())
				{
					callPanel.add(buildCallRow(name, "off", ColorScheme.PROGRESS_ERROR_COLOR));
					callPanel.add(Box.createVerticalStrut(4));
				}
			}

			callPanel.revalidate();
			callPanel.repaint();
		});
	}

	private JPanel buildCallRow(String name, String state, Color color)
	{
		final JPanel row = new JPanel(new GridLayout(1, 2));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		final JLabel nameLabel = new JLabel(name);
		nameLabel.setForeground(Color.WHITE);
		row.add(nameLabel);

		final JLabel stateLabel = new JLabel(state, JLabel.RIGHT);
		stateLabel.setForeground(color);
		row.add(stateLabel);
		return row;
	}

	// ---- Gear check (live) ----

	void updateGear(List<GearFlag> flags)
	{
		SwingUtilities.invokeLater(() ->
		{
			gearHeader.setText(flags.isEmpty()
				? "GEAR CHECK"
				: "GEAR CHECK (" + flags.size() + ")");

			gearPanel.removeAll();

			if (flags.isEmpty())
			{
				gearPanel.add(hint("Nobody nearby is flagged."));
			}
			else
			{
				for (GearFlag flag : flags)
				{
					gearPanel.add(buildGearRow(flag));
					gearPanel.add(Box.createVerticalStrut(4));
				}
			}

			gearPanel.revalidate();
			gearPanel.repaint();
		});
	}

	private JPanel buildGearRow(GearFlag flag)
	{
		final JPanel row = new JPanel(new GridLayout(1, 2));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		final JLabel name = new JLabel(flag.getDisplayName());
		name.setForeground(Color.WHITE);
		row.add(name);

		final JLabel warning = new JLabel(flag.getWarning(), JLabel.RIGHT);
		warning.setForeground(flag.isNaked()
			? ColorScheme.PROGRESS_ERROR_COLOR
			: ColorScheme.BRAND_ORANGE);
		row.add(warning);

		row.setToolTipText(flag.getDisplayName() + " — " + flag.getWarning()
			+ " (visible equipment only)");
		return row;
	}

	// ---- DD compliance (history) ----

	void update(List<PlayerStats> stats, boolean tileSet)
	{
		SwingUtilities.invokeLater(() ->
		{
			statusLabel.setText(tileSet
				? "DD tile: set"
				: "DD tile: not set (Shift + right-click a tile)");

			listPanel.removeAll();

			if (stats.isEmpty())
			{
				listPanel.add(hint("No calls tracked yet."));
			}
			else
			{
				stats.sort(Comparator
					.comparingInt(PlayerStats::getMissed).reversed()
					.thenComparing(PlayerStats::getCompliancePct)
					.thenComparing(PlayerStats::getDisplayName, String.CASE_INSENSITIVE_ORDER));

				for (PlayerStats ps : stats)
				{
					listPanel.add(buildRow(ps));
					listPanel.add(Box.createVerticalStrut(4));
				}
			}

			listPanel.revalidate();
			listPanel.repaint();
		});
	}

	private JLabel hint(String text)
	{
		final JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private JPanel buildRow(PlayerStats ps)
	{
		final JPanel row = new JPanel(new GridLayout(1, 2));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		final JLabel name = new JLabel(ps.getDisplayName());
		name.setForeground(Color.WHITE);
		row.add(name);

		final int pct = ps.getCompliancePct();
		final JLabel score = new JLabel(
			ps.getDdCount() + "/" + ps.getCallsSeen() + "  (" + pct + "%)",
			JLabel.RIGHT);
		if (pct >= 80)
		{
			score.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
		}
		else if (pct >= 50)
		{
			score.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
		}
		else
		{
			score.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
		}
		row.add(score);

		row.setToolTipText(ps.getDisplayName() + " — DD'd " + ps.getDdCount()
			+ " of " + ps.getCallsSeen() + " calls, missed " + ps.getMissed());
		return row;
	}
}
