package com.jblors.rlm.plugins.mobile;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

/** Draws the big menu over the game's (which stays open underneath, receiving the taps). */
class BigMenuOverlay extends Overlay
{
	private static final Pattern COL = Pattern.compile("<col=([0-9a-fA-F]{1,6})>");
	// The game's own menu: a 0x5D5447 frame, black header with "Choose Option" in the frame
	// colour, black-outlined option area, white text with a shadow, yellow for the hovered option
	private static final Color FRAME = new Color(0x5D5447);
	private static final Color HOVER_TEXT = new Color(0xFFFF00);

	private final Client client;
	private final BigMenuPlugin plugin;
	private final BigMenuConfig config;

	@Inject
	BigMenuOverlay(Client client, BigMenuPlugin plugin, BigMenuConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
		setPriority(PRIORITY_HIGHEST);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!client.isMenuOpen())
		{
			plugin.setLayout(null);
			return null;
		}
		MenuEntry[] entries = client.getMenu().getMenuEntries();
		int n = entries.length;
		if (n == 0)
		{
			plugin.setLayout(null);
			return null;
		}
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		Font font = FontManager.getRunescapeBoldFont().deriveFont((float) config.fontSize());
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();

		int w = Math.max(config.minWidth(), client.getMenuWidth() * 3 / 2);
		for (MenuEntry e : entries)
		{
			w = Math.max(w, fm.stringWidth(label(e)) + 28);
		}
		int canvasW = client.getCanvasWidth(), canvasH = client.getCanvasHeight();
		w = Math.min(w, canvasW);
		int header = Math.max(22, config.fontSize() + 10);
		int rowH = config.rowHeight();
		if (header + n * rowH > canvasH)
		{
			rowH = Math.max(BigMenuLayout.NATIVE_ROW, (canvasH - header) / n);
		}
		int h = header + n * rowH;
		int x = clamp(client.getMenuX(), 0, canvasW - w);
		int y = clamp(client.getMenuY(), 0, canvasH - h);
		BigMenuLayout l = new BigMenuLayout(client.getMenuX(), client.getMenuY(), client.getMenuWidth(), n, x, y, w, header, rowH);

		// Frame, black header, black outline round the options (as the game draws its menu)
		g.setColor(FRAME);
		g.fillRect(x, y, w, h);
		g.setColor(Color.BLACK);
		g.fillRect(x + 1, y + 1, w - 2, header - 2);
		g.drawRect(x + 1, y + header, w - 3, h - header - 2);
		int textY = y + (header + fm.getAscent() - fm.getDescent()) / 2;
		g.setColor(FRAME);
		g.drawString("Choose Option", x + 6, textY);

		Point mouse = client.getMouseCanvasPosition();
		for (int row = 0; row < n; row++)
		{
			MenuEntry e = entries[n - 1 - row]; // the game lists the newest entry on top
			int ry = y + header + row * rowH;
			// The game's cursor is moved to the matching native row, so compare in that space
			boolean hovered = mouse != null
				&& mouse.getY() >= l.menuY + BigMenuLayout.NATIVE_HEADER + row * BigMenuLayout.NATIVE_ROW
				&& mouse.getY() < l.menuY + BigMenuLayout.NATIVE_HEADER + (row + 1) * BigMenuLayout.NATIVE_ROW
				&& mouse.getX() >= l.menuX && mouse.getX() < l.menuX + l.menuW;
			int ty = ry + (rowH + fm.getAscent() - fm.getDescent()) / 2;
			String target = e.getTarget() == null ? "" : e.getTarget();
			drawColoured(g, fm, e.getOption() + (target.isEmpty() ? "" : " " + target), x + 6, ty,
				hovered ? HOVER_TEXT : Color.WHITE);
		}
		plugin.setLayout(l);
		return null;
	}

	private static String label(MenuEntry e)
	{
		return Text.removeTags(e.getOption()) + " " + Text.removeTags(e.getTarget() == null ? "" : e.getTarget());
	}

	/** Draws text with the game's colour tags (e.g. NPC name yellow, combat level green). */
	private static void drawColoured(Graphics2D g, FontMetrics fm, String text, int x, int y, Color base)
	{
		Color color = base;
		Matcher m = COL.matcher(text);
		int pos = 0;
		while (true)
		{
			boolean found = m.find(pos);
			String part = Text.removeTags(text.substring(pos, found ? m.start() : text.length()));
			if (!part.isEmpty())
			{
				g.setColor(Color.BLACK); // the game's text shadow
				g.drawString(part, x + 1, y + 1);
				g.setColor(color);
				g.drawString(part, x, y);
				x += fm.stringWidth(part);
			}
			if (!found)
			{
				return;
			}
			color = new Color(Integer.parseInt(m.group(1), 16));
			pos = m.end();
		}
	}

	private static int clamp(int v, int lo, int hi)
	{
		return Math.max(lo, Math.min(v, Math.max(lo, hi)));
	}
}
