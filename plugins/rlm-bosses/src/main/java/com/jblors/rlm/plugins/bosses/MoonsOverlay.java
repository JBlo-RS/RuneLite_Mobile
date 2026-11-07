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
	private static final Color ICE = new Color(150, 220, 255, 240), ICE_FILL = new Color(150, 220, 255, 80);
	static final int ICE_FLOOR_SPAWN = 2769, ICE_FLOOR_IDLE = 2770, ICE_FLOOR_ATTACK = 2771;

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
			countdown(g, o.getCanvasTilePoly(), o.getCanvasTextLocation(g, label(left), 0), left);
			move |= go && mine != null && mine.equals(o.getWorldLocation());
		}
		for (Map.Entry<NPC, Integer> e : moons.npcs.entrySet())
		{
			NPC n = e.getKey();
			MoonsTracker.Kind kind = MoonsTracker.kindOfNpc(n.getId());
			Polygon poly = trueTile(n); // where the game says it is, not where its model is drawn
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
						countdown(g, poly, n.getCanvasTextLocation(g, label(left), 0), left);
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
					if (config.moonsEclipseClones() && moons.clonePhase())
					{
						break; // clone phase: the tile to step to (below) instead of the bosses
					}
					if (config.moonsEclipse())
					{
						OverlayUtil.renderPolygon(g, poly, ECLIPSE, new Color(0, 0, 0, 0), new BasicStroke(3));
					}
					break;
				default:
					break;
			}
		}
		// Eclipse clone phase: the tile next to the player in the direction the latest one appeared
		NPC latest = moons.latestEclipse();
		if (config.moonsEclipseClones() && me != null && mine != null && latest != null && moons.clonePhase()
			&& latest.getWorldLocation() != null)
		{
			WorldPoint nw = latest.getWorldLocation();
			int size = latest.getComposition() == null ? 1 : Math.max(1, latest.getComposition().getSize());
			int[] d = MoonsTracker.direction(mine.getX(), mine.getY(), nw.getX(), nw.getY(), size);
			if (d[0] != 0 || d[1] != 0)
			{
				WorldPoint step = new WorldPoint(mine.getX() + d[0], mine.getY() + d[1], mine.getPlane());
				net.runelite.api.WorldView wv = me.getWorldView() != null ? me.getWorldView() : client.getTopLevelWorldView();
				net.runelite.api.coords.LocalPoint lp = net.runelite.api.coords.LocalPoint.fromWorld(wv, step);
				Polygon p = lp == null ? null : net.runelite.api.Perspective.getCanvasTilePoly(client, lp);
				if (p != null)
				{
					OverlayUtil.renderPolygon(g, p, ECLIPSE, new Color(0, 230, 255, 90), new BasicStroke(3));
				}
			}
		}

		// Blue Moon's ice on the floor: graphics VFX_DJINN_ICE_FLOOR_SPAWN/_IDLE/_ATTACK_01, while there
		if (config.moonsIceFloor())
		{
			java.util.Set<net.runelite.api.WorldView> views = new java.util.LinkedHashSet<>();
			views.add(client.getTopLevelWorldView());
			for (NPC n : plugin.getTracked())
			{
				if (n.getWorldView() != null)
				{
					views.add(n.getWorldView());
				}
			}
			for (net.runelite.api.WorldView v : views)
			{
				for (net.runelite.api.GraphicsObject go : v.getGraphicsObjects())
				{
					int id = go.getId();
					if ((id == ICE_FLOOR_SPAWN || id == ICE_FLOOR_IDLE || id == ICE_FLOOR_ATTACK) && !go.finished() && go.getLocation() != null)
					{
						Polygon p = net.runelite.api.Perspective.getCanvasTilePoly(client, go.getLocation());
						if (p != null)
						{
							OverlayUtil.renderPolygon(g, p, ICE, ICE_FILL, new BasicStroke(2));
						}
						WorldPoint w = WorldPoint.fromLocal(client, go.getLocation());
						move |= mine != null && mine.equals(w);
					}
				}
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

	/**
	 * Counts down to the tick to move: on screen 4 ticks with MOVE at 2 left shows 2, 1, MOVE, and
	 * after that the tile just stays red with no number (too late to matter).
	 */
	private String label(int left)
	{
		int moveAt = config.moonsMoveAt();
		return left > moveAt ? Integer.toString(left - moveAt) : left == moveAt ? "MOVE" : "";
	}

	private void countdown(Graphics2D g, Polygon poly, Point text, int left)
	{
		boolean go = left <= config.moonsMoveAt();
		if (poly != null)
		{
			OverlayUtil.renderPolygon(g, poly, go ? MOVE : WAIT, go ? MOVE_FILL : WAIT_FILL, new BasicStroke(2));
		}
		String s = label(left);
		if (text != null && !s.isEmpty())
		{
			OverlayUtil.renderTextLocation(g, text, s, go ? MOVE : WAIT);
		}
	}

	/** An NPC's true tile(s): its game location, not its smoothly moving model (as NPC Indicators). */
	private Polygon trueTile(NPC n)
	{
		WorldPoint wp = n.getWorldLocation();
		net.runelite.api.WorldView wv = n.getWorldView() != null ? n.getWorldView() : client.getTopLevelWorldView();
		net.runelite.api.coords.LocalPoint lp = wp == null ? null : net.runelite.api.coords.LocalPoint.fromWorld(wv, wp);
		if (lp == null)
		{
			return n.getCanvasTilePoly();
		}
		int size = n.getComposition() == null ? 1 : Math.max(1, n.getComposition().getSize());
		net.runelite.api.coords.LocalPoint centre = lp.plus(net.runelite.api.Perspective.LOCAL_TILE_SIZE * (size - 1) / 2,
			net.runelite.api.Perspective.LOCAL_TILE_SIZE * (size - 1) / 2);
		return net.runelite.api.Perspective.getCanvasTileAreaPoly(client, centre, size);
	}
}
