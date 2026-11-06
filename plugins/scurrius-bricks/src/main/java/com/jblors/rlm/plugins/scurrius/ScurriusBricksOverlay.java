package com.jblors.rlm.plugins.scurrius;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Each falling brick is a spot animation (graphics object) on the tile it lands on, from the
 * moment its shadow appears until it hits. So every frame, mark the tile of each one still alive.
 */
class ScurriusBricksOverlay extends Overlay
{
	/** SpotanimID.VFX_RAT_BOSS_FALLING_DEBRIS_01 */
	static final int FALLING_DEBRIS = 2644;

	private static final Font WARN_FONT = new Font("Dialog", Font.BOLD, 16);

	private final Client client;
	private final ScurriusBricksConfig config;

	@Inject
	ScurriusBricksOverlay(Client client, ScurriusBricksConfig config)
	{
		this.client = client;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null)
		{
			return null;
		}

		Player me = client.getLocalPlayer();
		LocalPoint myTile = me == null ? null : me.getLocalLocation();
		boolean underBrick = false;
		BasicStroke stroke = new BasicStroke(config.outlineWidth());

		for (GraphicsObject go : wv.getGraphicsObjects())
		{
			if (go.getId() != FALLING_DEBRIS || go.finished())
			{
				continue;
			}
			LocalPoint lp = go.getLocation();
			if (lp == null)
			{
				continue;
			}
			Polygon poly = Perspective.getCanvasTilePoly(client, lp);
			if (poly != null)
			{
				OverlayUtil.renderPolygon(graphics, poly, config.outlineColor(), config.fillColor(), stroke);
			}
			if (myTile != null && sameTile(lp, myTile))
			{
				underBrick = true;
			}
		}

		if (underBrick && config.warnStanding())
		{
			graphics.setFont(WARN_FONT);
			Point p = me.getCanvasTextLocation(graphics, "MOVE!", me.getLogicalHeight() + 40);
			if (p != null)
			{
				OverlayUtil.renderTextLocation(graphics, p, "MOVE!", config.outlineColor());
			}
		}
		return null;
	}

	private static boolean sameTile(LocalPoint a, LocalPoint b)
	{
		return a.getSceneX() == b.getSceneX() && a.getSceneY() == b.getSceneY();
	}
}
