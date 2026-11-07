package com.jblors.rlm.plugins.lazyflick;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
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

/** Flashes the prayer to press on the tick before the attack; optional tick countdown. */
class LazyFlickOverlay extends Overlay
{
	private static final Font COUNT_FONT = new Font("Dialog", Font.BOLD, 16);

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

	@Override
	public Dimension render(Graphics2D g)
	{
		Color c = config.color();
		Set<Style> now = plugin.getFlickNow();
		int above = 0;
		for (Style s : now)
		{
			Widget w = PrayerButtons.find(client, s, buttonCache);
			if (w != null && !w.isHidden())
			{
				Rectangle b = w.getBounds();
				g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.min(c.getAlpha(), 110)));
				g.fill(b);
				g.setColor(c);
				g.setStroke(new BasicStroke(4));
				g.draw(b);
			}
			if (config.aboveMe())
			{
				Player me = client.getLocalPlayer();
				BufferedImage icon = spriteManager.getSprite(s.sprite, 0);
				Point p = me == null || icon == null ? null : me.getCanvasImageLocation(icon, me.getLogicalHeight() + 70);
				if (p != null)
				{
					int x = p.getX() + above * (icon.getWidth() + 12);
					Rectangle box = new Rectangle(x - 4, p.getY() - 4, icon.getWidth() + 8, icon.getHeight() + 8);
					g.setColor(new Color(0, 0, 0, 150));
					g.fill(box);
					g.setColor(c);
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
				String text = Integer.toString(e.getValue());
				Point p = e.getKey().getCanvasTextLocation(g, text, e.getKey().getLogicalHeight() + 40);
				if (p != null)
				{
					OverlayUtil.renderTextLocation(g, p, text, e.getValue() == 1 ? c : Color.WHITE);
				}
			}
		}
		return null;
	}
}
