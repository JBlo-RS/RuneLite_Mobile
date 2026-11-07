package com.jblors.rlm.plugins.lazyflick;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Traffic lights to the tick to pray: the countdown in red (2 and more), orange (1), then the
 * prayer's button lit in the pray colour (green) with PRAY, on the prayer button and above the NPC.
 */
class LazyFlickOverlay extends Overlay
{
	static final Color RED = new Color(255, 45, 45), ORANGE = new Color(255, 150, 0);
	private static final Font COUNT_FONT = new Font("Dialog", Font.BOLD, 16);
	private static final Font BUTTON_FONT = new Font("Dialog", Font.BOLD, 14);

	private final Client client;
	private final LazyFlickPlugin plugin;
	private final LazyFlickConfig config;
	private final SpriteManager spriteManager;
	private final int[] buttonCache = new int[Style.values().length];

	@Inject
	LazyFlickOverlay(Client client, LazyFlickPlugin plugin, LazyFlickConfig config, SpriteManager spriteManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.spriteManager = spriteManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGHEST);
	}

	/** The colour for a countdown value: 0 = pray (green), 1 = orange, more = red */
	Color colorOf(int toPress)
	{
		return toPress <= 0 ? config.color() : toPress == 1 ? ORANGE : RED;
	}

	static String textOf(int toPress)
	{
		return toPress <= 0 ? "PRAY" : Integer.toString(toPress);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Set<Style> now = plugin.getFlickNow();
		Map<Style, Integer> counts = plugin.getPrayerCount();

		// The prayer book: the pray tick lights the button; the countdown is written on it
		for (Style s : Style.values())
		{
			boolean pray = now.contains(s);
			Integer count = counts.get(s);
			if (!pray && (count == null || !config.countdown()))
			{
				continue;
			}
			Widget w = PrayerButtons.find(client, s, buttonCache);
			if (w == null || w.isHidden())
			{
				continue;
			}
			Rectangle b = w.getBounds();
			Color c = colorOf(pray ? 0 : count);
			if (pray)
			{
				g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.min(c.getAlpha(), 110)));
				g.fill(b);
			}
			g.setColor(c);
			g.setStroke(new BasicStroke(pray ? 4 : 2));
			g.draw(b);
			if (config.countdown())
			{
				centred(g, BUTTON_FONT, textOf(pray ? 0 : count), b, c);
			}
		}

		if (config.aboveMe())
		{
			int above = 0;
			Player me = client.getLocalPlayer();
			for (Style s : now)
			{
				BufferedImage icon = spriteManager.getSprite(s.sprite, 0);
				Point p = me == null || icon == null ? null : me.getCanvasImageLocation(icon, me.getLogicalHeight() + 70);
				if (p != null)
				{
					int x = p.getX() + above * (icon.getWidth() + 12);
					Rectangle box = new Rectangle(x - 4, p.getY() - 4, icon.getWidth() + 8, icon.getHeight() + 8);
					g.setColor(new Color(0, 0, 0, 150));
					g.fill(box);
					g.setColor(config.color());
					g.setStroke(new BasicStroke(3));
					g.draw(box);
					g.drawImage(icon, x, p.getY(), null);
					above++;
				}
			}
		}

		if (config.countdown())
		{
			g.setFont(COUNT_FONT);
			for (Map.Entry<NPC, Integer> e : plugin.getTicksLeft().entrySet())
			{
				String text = textOf(e.getValue());
				Point p = e.getKey().getCanvasTextLocation(g, text, e.getKey().getLogicalHeight() + 40);
				if (p != null)
				{
					OverlayUtil.renderTextLocation(g, p, text, colorOf(e.getValue()));
				}
			}
		}
		return null;
	}

	/** Text in the middle of a box, with a dark outline so it reads over the prayer icon */
	private static void centred(Graphics2D g, Font font, String text, Rectangle b, Color c)
	{
		Font f = font;
		g.setFont(f);
		FontMetrics fm = g.getFontMetrics();
		if (fm.stringWidth(text) > b.width - 2) // PRAY on a small button
		{
			f = font.deriveFont((float) Math.max(8, font.getSize() * (b.width - 2) / (double) fm.stringWidth(text)));
			g.setFont(f);
			fm = g.getFontMetrics();
		}
		int x = b.x + (b.width - fm.stringWidth(text)) / 2;
		int y = b.y + (b.height - fm.getHeight()) / 2 + fm.getAscent();
		g.setColor(Color.BLACK);
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				if (dx != 0 || dy != 0)
				{
					g.drawString(text, x + dx, y + dy);
				}
			}
		}
		g.setColor(c);
		g.drawString(text, x, y);
	}
}
