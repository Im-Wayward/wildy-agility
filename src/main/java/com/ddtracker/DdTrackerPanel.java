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
		resetButton.addActionListener(e -> plugin.resetStats());
		header.add(resetButton, BorderLayout.SOUTH);

		add(header, BorderLayout.NORTH);

		listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
		listPanel.setOpaque(false);
		add(listPanel, BorderLayout.CENTER);

		add(buildCommunityFooter(), BorderLayout.SOUTH);
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
				final JLabel empty = new JLabel("No calls tracked yet.");
				empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				empty.setAlignmentX(Component.LEFT_ALIGNMENT);
				listPanel.add(empty);
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
