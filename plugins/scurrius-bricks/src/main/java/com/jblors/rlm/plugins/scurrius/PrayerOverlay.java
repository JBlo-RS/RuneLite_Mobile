package com.jblors.rlm.plugins.scurrius;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.ItemComposition;
import net.runelite.api.ParamID;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;
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
			Widget w = prayerButton(style);
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

		if (config.prayerIconOverPlayer())
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

	/** Components of the prayer book that can hold prayer buttons (PRAYER1 onwards). */
	private static final int FIRST_BUTTON = InterfaceID.Prayerbook.PRAYER1, MAX_BUTTONS = 64;

	private final int[] buttonCache = new int[AttackStyle.values().length];

	/**
	 * The prayer book button for a style's prayer, found by name. The component numbers don't
	 * follow the book's order (each prayer's component comes from the game's data, and new
	 * prayers were added over time), and the book can be rearranged.
	 */
	private Widget prayerButton(AttackStyle style)
	{
		int cached = buttonCache[style.ordinal()];
		if (cached != 0)
		{
			Widget w = client.getWidget(cached);
			if (w != null)
			{
				return w;
			}
			buttonCache[style.ordinal()] = 0;
		}
		// 1. The game's prayer list, as RuneLite's prayer plugin uses it: each prayer is an item
		//    definition whose OC_PRAYER_COMPONENT param is its button in the prayer book
		EnumComposition prayers = client.getEnum(EnumID.PRAYERS_NORMAL);
		if (prayers != null)
		{
			for (int obj : prayers.getIntVals())
			{
				ItemComposition def = client.getItemDefinition(obj);
				if (def == null || !style.name.equalsIgnoreCase(Text.removeTags(def.getName()).trim()))
				{
					continue;
				}
				int component = def.getIntValue(ParamID.OC_PRAYER_COMPONENT);
				Widget w = component > 0 ? client.getWidget(component) : null;
				if (w != null)
				{
					buttonCache[style.ordinal()] = component;
					return w;
				}
			}
		}
		// 2. Fallback: a button in the prayer book with the prayer's name
		for (int id = FIRST_BUTTON; id < FIRST_BUTTON + MAX_BUTTONS; id++)
		{
			Widget w = client.getWidget(id);
			if (w != null && w.getName() != null && style.name.equalsIgnoreCase(Text.removeTags(w.getName()).trim()))
			{
				buttonCache[style.ordinal()] = id;
				return w;
			}
		}
		return null;
	}
}
