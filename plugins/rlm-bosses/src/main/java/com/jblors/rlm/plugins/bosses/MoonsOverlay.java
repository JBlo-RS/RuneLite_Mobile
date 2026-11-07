package com.jblors.rlm.plugins.bosses;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/** Perilous Moons hazards with tick countdowns (see MoonsTracker). */
class MoonsOverlay extends Overlay
{
	private static final Font FONT = new Font("Dialog", Font.BOLD, 14);
	private static final Color WAIT = new Color(255, 200, 0, 230), WAIT_FILL = new Color(255, 200, 0, 50);
	private static final Color MOVE = new Color(255, 30, 30, 240), MOVE_FILL = new Color(255, 30, 30, 90);
	private static final Color TORNADO = new Color(170, 90, 255, 230), TORNADO_FILL = new Color(170, 90, 255, 50);
	private static final Color ECLIPSE = new Color(0, 230, 255, 240);

	private final Client client;
	private final BossHelperPlugin plugin;
	private final BossHelperConfig config;

	@Inject
	MoonsOverlay(Client client, BossHelperPlugin plugin, BossHelperConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.moonsOfPeril())
		{
			return null;
		}
		MoonsTracker moons = plugin.getMoons();
		int now = client.getTickCount();
		Player me = client.getLocalPlayer();
		WorldPoint mine = me == null ? null : me.getWorldLocation();
		g.setFont(FONT);
		boolean move = false;

		for (Map.Entry<TileObject, Integer> e : moons.objects.entrySet())
		{
			TileObject o = e.getKey();
			MoonsTracker.Kind kind = MoonsTracker.kindOfObject(o.getId());
			if (kind == MoonsTracker.Kind.BLOOD_POOL && !config.moonsBloodPools()
				|| kind == MoonsTracker.Kind.BLOOD_RAIN && !config.moonsBloodRain())
			{
				continue;
			}
			int left = moons.ticksLeft(o.getId(), e.getValue(), now);
			boolean go = left <= config.moonsMoveAt();
			countdown(g, o.getCanvasTilePoly(), o.getCanvasTextLocation(g, label(left, go), 0), left, go);
			move |= go && mine != null && mine.equals(o.getWorldLocation());
		}
		for (Map.Entry<NPC, Integer> e : moons.npcs.entrySet())
		{
			NPC n = e.getKey();
			MoonsTracker.Kind kind = MoonsTracker.kindOfNpc(n.getId());
			Polygon poly = n.getCanvasTilePoly(); // the NPC's true tile(s), whatever its model does
			if (poly == null || kind == null)
			{
				continue;
			}
			switch (kind)
			{
				case ICICLE:
					if (config.moonsIcicles())
					{
						int left = moons.ticksLeft(n.getId(), e.getValue(), now);
						boolean go = left <= config.moonsMoveAt();
						countdown(g, poly, n.getCanvasTextLocation(g, label(left, go), 0), left, go);
						move |= go && mine != null && mine.equals(n.getWorldLocation());
					}
					break;
				case TORNADO:
					if (config.moonsTornados())
					{
						OverlayUtil.renderPolygon(g, poly, TORNADO, TORNADO_FILL, new BasicStroke(2));
					}
					break;
				case ECLIPSE:
					if (config.moonsEclipse())
					{
						OverlayUtil.renderPolygon(g, poly, ECLIPSE, new Color(0, 0, 0, 0), new BasicStroke(3));
					}
					break;
				default:
					break;
			}
		}
		if (move && me != null)
		{
			Point p = me.getCanvasTextLocation(g, "MOVE!", me.getLogicalHeight() + 40);
			if (p != null)
			{
				OverlayUtil.renderTextLocation(g, p, "MOVE!", MOVE);
			}
		}
		return null;
	}

	private static String label(int left, boolean go)
	{
		return go ? left + " MOVE" : Integer.toString(left);
	}

	private static void countdown(Graphics2D g, Polygon poly, Point text, int left, boolean go)
	{
		if (poly != null)
		{
			OverlayUtil.renderPolygon(g, poly, go ? MOVE : WAIT, go ? MOVE_FILL : WAIT_FILL, new BasicStroke(2));
		}
		if (text != null)
		{
			OverlayUtil.renderTextLocation(g, text, label(left, go), go ? MOVE : WAIT);
		}
	}
}
