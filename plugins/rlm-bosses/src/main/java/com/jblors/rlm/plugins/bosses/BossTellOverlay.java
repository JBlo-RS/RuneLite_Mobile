package com.jblors.rlm.plugins.bosses;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/** A big warning above a boss that announced its next attack ("STOMP incoming!"). */
class BossTellOverlay extends Overlay
{
	private static final Font FONT = new Font("Dialog", Font.BOLD, 22);
	private static final Color COLOR = new Color(255, 120, 0);

	private final BossHelperPlugin plugin;

	@Inject
	BossTellOverlay(BossHelperPlugin plugin)
	{
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		String warning = plugin.getWarning();
		NPC npc = plugin.getWarningNpc();
		if (warning == null || npc == null)
		{
			return null;
		}
		g.setFont(FONT);
		Point p = npc.getCanvasTextLocation(g, warning, npc.getLogicalHeight() + 80);
		if (p != null)
		{
			OverlayUtil.renderTextLocation(g, p, warning, COLOR);
		}
		return null;
	}
}
