package com.jblors.rlm.plugins.mobile;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
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

/**
 * Draws a copy of the game's menu at its real size, as the game draws it, then shows it k times
 * bigger with nearest-neighbour scaling, so it looks like the original with bigger pixels.
 */
class BigMenuOverlay extends Overlay
{
	private static final Pattern COL = Pattern.compile("<col=([0-9a-fA-F]{1,6})>");
	private static final Color FRAME = new Color(0x5D5447);
	private static final Color HOVER_TEXT = new Color(0xFFFF00);

	private final Client client;
	private final BigMenuPlugin plugin;
	private final BigMenuConfig config;
	private BufferedImage image;

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
		int mw = client.getMenuWidth(), mh = client.getMenuHeight();
		if (mh <= 0)
		{
			mh = BigMenuLayout.NATIVE_HEADER + n * BigMenuLayout.NATIVE_ROW + 3;
		}
		if (n == 0 || mw <= 0)
		{
			plugin.setLayout(null);
			return null;
		}
		int canvasW = client.getCanvasWidth(), canvasH = client.getCanvasHeight();
		// The game sizes its menu to its text; if our copy of the font runs wider, widen the drawing
		FontMetrics measure = image != null ? image.createGraphics().getFontMetrics(FontManager.getRunescapeBoldFont())
			: new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics().getFontMetrics(FontManager.getRunescapeBoldFont());
		int dw = mw;
		for (MenuEntry e : entries)
		{
			String t = e.getTarget() == null || e.getTarget().isEmpty() ? "" : " " + e.getTarget();
			dw = Math.max(dw, measure.stringWidth(Text.removeTags(e.getOption() + t)) + 8);
		}
		int k = config.scale();
		while (k > 1 && (dw * k > canvasW || mh * k > canvasH))
		{
			k--; // a long menu: as big as still fits
		}
		int x = clamp(client.getMenuX(), 0, canvasW - dw * k);
		int y = clamp(client.getMenuY(), 0, canvasH - mh * k);
		BigMenuLayout l = new BigMenuLayout(client.getMenuX(), client.getMenuY(), mw, dw, mh, n, x, y, k);

		// The menu at the game's size
		if (image == null || image.getWidth() < dw || image.getHeight() < mh)
		{
			image = new BufferedImage(Math.max(dw, 64), Math.max(mh, 64), BufferedImage.TYPE_INT_ARGB);
		}
		Graphics2D m = image.createGraphics();
		m.setComposite(java.awt.AlphaComposite.Clear);
		m.fillRect(0, 0, image.getWidth(), image.getHeight());
		m.setComposite(java.awt.AlphaComposite.SrcOver);
		m.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		m.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics fm = m.getFontMetrics();
		m.setColor(FRAME);
		m.fillRect(0, 0, dw, mh);
		m.setColor(Color.BLACK);
		m.fillRect(1, 1, dw - 2, 16);
		m.drawRect(1, 18, dw - 3, mh - 20);
		m.setColor(FRAME);
		m.drawString("Choose Option", 3, 14);

		// The game's cursor sits on its own menu (the listener moves it there)
		Point mouse = client.getMouseCanvasPosition();
		int hovered = -1;
		if (mouse != null && mouse.getX() >= l.menuX && mouse.getX() < l.menuX + mw)
		{
			int dy = mouse.getY() - l.menuY - BigMenuLayout.NATIVE_HEADER;
			hovered = dy >= 0 ? dy / BigMenuLayout.NATIVE_ROW : -1;
		}
		for (int row = 0; row < n; row++)
		{
			MenuEntry e = entries[n - 1 - row]; // the game lists the newest entry on top
			String target = e.getTarget() == null ? "" : e.getTarget();
			drawColoured(m, fm, e.getOption() + (target.isEmpty() ? "" : " " + target), 3,
				BigMenuLayout.NATIVE_HEADER + 12 + row * BigMenuLayout.NATIVE_ROW, row == hovered ? HOVER_TEXT : Color.WHITE);
		}
		m.dispose();

		// Enlarged with nearest-neighbour: same pixels, k times bigger
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.drawImage(image, x, y, x + dw * k, y + mh * k, 0, 0, dw, mh, null);
		plugin.setLayout(l);
		return null;
	}

	/** Draws text with the game's colour tags and its 1 px black shadow. */
	private static void drawColoured(Graphics2D g, FontMetrics fm, String text, int x, int y, Color base)
	{
		Color color = base;
		Matcher matcher = COL.matcher(text);
		int pos = 0;
		while (true)
		{
			boolean found = matcher.find(pos);
			String part = Text.removeTags(text.substring(pos, found ? matcher.start() : text.length()));
			if (!part.isEmpty())
			{
				g.setColor(Color.BLACK);
				g.drawString(part, x + 1, y + 1);
				g.setColor(color);
				g.drawString(part, x, y);
				x += fm.stringWidth(part);
			}
			if (!found)
			{
				return;
			}
			color = new Color(Integer.parseInt(matcher.group(1), 16));
			pos = matcher.end();
		}
	}

	private static int clamp(int v, int lo, int hi)
	{
		return Math.max(lo, Math.min(v, Math.max(lo, hi)));
	}
}
