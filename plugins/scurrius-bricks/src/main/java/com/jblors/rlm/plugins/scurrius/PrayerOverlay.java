package com.jblors.rlm.plugins.scurrius;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Shows the prayer against Scurrius' last attack: a box around it in the prayer book, and its
 * icon above your character. Green while that prayer is on, red while it isn't.
 */
class PrayerOverlay extends Overlay
{
	static final Color ON = new Color(40, 220, 60);
	static final Color OFF = new Color(255, 40, 40);

	private final Client client;
	private final ScurriusBricksPlugin plugin;
	private final ScurriusBricksConfig config;
	private final SpriteManager spriteManager;

	@Inject
	PrayerOverlay(Client client, ScurriusBricksPlugin plugin, ScurriusBricksConfig config, SpriteManager spriteManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.spriteManager = spriteManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		AttackStyle style = plugin.getNextStyle();
		if (style == null)
		{
			return null;
		}
		boolean on = client.isPrayerActive(style.prayer);
		if (on && config.prayerHideWhenOn())
		{
			return null;
		}
		Color color = on ? ON : OFF;

		if (config.prayerHighlightBook())
		{
			Widget w = client.getWidget(style.widget);
			if (w != null && !w.isHidden())
			{
				Rectangle b = w.getBounds();
				g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 60));
				g.fill(b);
				g.setColor(color);
				g.setStroke(new BasicStroke(3));
				g.draw(b);
			}
		}

		if (config.prayerOverPlayer())
		{
			Player me = client.getLocalPlayer();
			BufferedImage icon = spriteManager.getSprite(style.sprite, 0);
			if (me != null && icon != null)
			{
				Point p = me.getCanvasImageLocation(icon, me.getLogicalHeight() + 70);
				if (p != null)
				{
					int pad = 4;
					Rectangle box = new Rectangle(p.getX() - pad, p.getY() - pad,
						icon.getWidth() + pad * 2, icon.getHeight() + pad * 2);
					g.setColor(new Color(0, 0, 0, 150));
					g.fill(box);
					g.setColor(color);
					g.setStroke(new BasicStroke(2));
					g.draw(box);
					g.drawImage(icon, p.getX(), p.getY(), null);
				}
			}
		}
		return null;
	}
}
