package com.jblors.rlm.plugins.bosses;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GraphicsObject;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Projectile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Marks tiles about to be hit: the bosses' warning graphics (until they finish) and where
 * their ground-targeted projectiles will land (until they land).
 */
class BossTileOverlay extends Overlay
{
	private static final Font WARN_FONT = new Font("Dialog", Font.BOLD, 16);

	private final Client client;
	private final BossHelperPlugin plugin;
	private final BossHelperConfig config;

	@Inject
	BossTileOverlay(Client client, BossHelperPlugin plugin, BossHelperConfig config)
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
		// Only near an enabled boss
		List<Boss> here = new ArrayList<>();
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			Boss b = plugin.bossOf(npc);
			if (b != null && !here.contains(b))
			{
				here.add(b);
			}
		}
		if (here.isEmpty())
		{
			return null;
		}

		List<LocalPoint> tiles = new ArrayList<>();
		for (GraphicsObject go : client.getTopLevelWorldView().getGraphicsObjects())
		{
			if (go.finished() || go.getLocation() == null)
			{
				continue;
			}
			for (Boss b : here)
			{
				if (b.markedGraphics.contains(go.getId()))
				{
					tiles.add(go.getLocation());
					break;
				}
			}
		}
		for (Projectile p : client.getProjectiles())
		{
			if (p.getRemainingCycles() <= 0 || p.getTargetActor() != null || p.getTarget() == null)
			{
				continue;
			}
			Boss b = plugin.bossOf(p.getSourceActor());
			if (b != null && b.markGroundProjectiles)
			{
				tiles.add(p.getTarget());
			}
		}
		if (tiles.isEmpty())
		{
			return null;
		}

		BasicStroke stroke = new BasicStroke(2);
		Player me = client.getLocalPlayer();
		LocalPoint mine = me == null ? null : me.getLocalLocation();
		boolean underAttack = false;
		for (LocalPoint lp : tiles)
		{
			Polygon poly = Perspective.getCanvasTilePoly(client, lp);
			if (poly != null)
			{
				OverlayUtil.renderPolygon(g, poly, config.tileOutline(), config.tileFill(), stroke);
			}
			if (mine != null && lp.getSceneX() == mine.getSceneX() && lp.getSceneY() == mine.getSceneY())
			{
				underAttack = true;
			}
		}
		if (underAttack && config.warnStanding())
		{
			g.setFont(WARN_FONT);
			Point p = me.getCanvasTextLocation(g, "MOVE!", me.getLogicalHeight() + 40);
			if (p != null)
			{
				OverlayUtil.renderTextLocation(g, p, "MOVE!", config.tileOutline());
			}
		}
		return null;
	}
}
