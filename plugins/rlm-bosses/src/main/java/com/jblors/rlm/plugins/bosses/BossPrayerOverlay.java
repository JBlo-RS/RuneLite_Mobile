package com.jblors.rlm.plugins.bosses;

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
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

/** Boxes the prayer to use in the prayer book (green while on, red while not). */
class BossPrayerOverlay extends Overlay
{
	private static final Color ON = new Color(40, 220, 60), OFF = new Color(255, 40, 40);
	private static final int FIRST_BUTTON = InterfaceID.Prayerbook.PRAYER1, MAX_BUTTONS = 64;

	private final Client client;
	private final BossHelperPlugin plugin;
	private final BossHelperConfig config;
	private final SpriteManager spriteManager;
	private final int[] buttonCache = new int[Style.values().length];

	@Inject
	BossPrayerOverlay(Client client, BossHelperPlugin plugin, BossHelperConfig config, SpriteManager spriteManager)
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
		Style style = plugin.getStyle();
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
		if (config.prayerBook())
		{
			Widget w = prayerButton(style);
			plugin.prayerUi = w == null ? "prayer button not found" : w.isHidden() ? "closed" : "box shown";
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
		if (config.prayerIcon())
		{
			Player me = client.getLocalPlayer();
			BufferedImage icon = spriteManager.getSprite(style.sprite, 0);
			Point p = me == null || icon == null ? null : me.getCanvasImageLocation(icon, me.getLogicalHeight() + 70);
			if (p != null)
			{
				Rectangle box = new Rectangle(p.getX() - 4, p.getY() - 4, icon.getWidth() + 8, icon.getHeight() + 8);
				g.setColor(new Color(0, 0, 0, 150));
				g.fill(box);
				g.setColor(color);
				g.setStroke(new BasicStroke(2));
				g.draw(box);
				g.drawImage(icon, p.getX(), p.getY(), null);
			}
		}
		return null;
	}

	/** The prayer's button: from the game's prayer list (as RuneLite's prayer plugin), else by name. */
	private Widget prayerButton(Style style)
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
		EnumComposition prayers = client.getEnum(EnumID.PRAYERS_NORMAL);
		if (prayers != null)
		{
			for (int obj : prayers.getIntVals())
			{
				ItemComposition def = client.getItemDefinition(obj);
				if (def == null || !style.prayerName.equalsIgnoreCase(Text.removeTags(def.getName()).trim()))
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
		for (int id = FIRST_BUTTON; id < FIRST_BUTTON + MAX_BUTTONS; id++)
		{
			Widget w = client.getWidget(id);
			if (w != null && w.getName() != null && style.prayerName.equalsIgnoreCase(Text.removeTags(w.getName()).trim()))
			{
				buttonCache[style.ordinal()] = id;
				return w;
			}
		}
		return null;
	}
}
