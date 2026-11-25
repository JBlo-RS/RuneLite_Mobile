package com.starhopper;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

class StarHopperPanel extends PluginPanel
{
	private static final int MAX_ROWS = 25;

	private final StarHopperPlugin plugin;
	private final JLabel statusLabel = new JLabel();
	private final JLabel locationLabel = new JLabel();
	private final JPanel list = new JPanel();

	StarHopperPanel(StarHopperPlugin plugin)
	{
		super();
		this.plugin = plugin;
		setLayout(new BorderLayout(0, 8));
		setBorder(new EmptyBorder(8, 8, 8, 8));

		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

		JLabel title = new JLabel("Star Hopper");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(new Color(255, 196, 0));
		header.add(title);

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		header.add(statusLabel);

		locationLabel.setFont(FontManager.getRunescapeSmallFont());
		locationLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		header.add(locationLabel);

		header.add(Box.createVerticalStrut(6));

		JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0));
		buttons.setAlignmentX(LEFT_ALIGNMENT); // as the labels, so the header lines up on the left
		JButton hopBest = new JButton("Hop to best");
		hopBest.addActionListener(e -> plugin.hopToSuggestion());
		JButton refresh = new JButton("Refresh");
		refresh.addActionListener(e -> plugin.refreshNow());
		buttons.add(hopBest);
		buttons.add(refresh);
		header.add(buttons);

		add(header, BorderLayout.NORTH);

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		add(list, BorderLayout.CENTER);

		update(java.util.Collections.emptyList(), "Loading...", null);
	}

	void update(List<Suggestion> suggestions, String status, String currentLocation)
	{
		statusLabel.setText("<html>" + escape(status) + "</html>");
		locationLabel.setText(currentLocation == null ? "" : "<html>Current spot: " + escape(currentLocation) + "</html>");

		list.removeAll();
		if (suggestions.isEmpty())
		{
			JLabel none = new JLabel("<html>No stars match your filters.</html>");
			none.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			list.add(none);
		}
		int shown = 0;
		for (Suggestion s : suggestions)
		{
			if (shown++ >= MAX_ROWS)
			{
				break;
			}
			list.add(row(s));
			list.add(Box.createVerticalStrut(4));
		}
		list.revalidate();
		list.repaint();
	}

	private JPanel row(Suggestion s)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, s.isSameLocation() ? Color.GREEN : ColorScheme.DARKER_GRAY_COLOR),
			new EmptyBorder(6, 6, 6, 6)));

		StarReport star = s.getStar();
		String tier = star.getTier() > 0 ? "T" + s.getDisplayTier() : "T?";
		String age = star.getReportedAtMillis() > 0
			? ((System.currentTimeMillis() - star.getReportedAtMillis()) / 60000) + "m ago"
			: "";
		JLabel text = new JLabel("<html><b>W" + star.getWorld() + "</b> · " + tier
			+ (age.isEmpty() ? "" : " · " + age)
			+ "<br>" + escape(star.getLocation()) + "</html>");
		text.setForeground(Color.WHITE);
		row.add(text, BorderLayout.CENTER);

		JButton hop = new JButton("Hop");
		hop.setPreferredSize(new Dimension(56, 28));
		hop.addActionListener(e -> plugin.hopTo(s));
		row.add(hop, BorderLayout.EAST);
		return row;
	}

	private static String escape(String s)
	{
		if (s == null)
		{
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
