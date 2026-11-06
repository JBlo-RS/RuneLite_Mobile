package com.jblors.rlm.plugins.skilling;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.NPC;
import net.runelite.api.TileObject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

class SkillingHelperOverlay extends Overlay
{
	private final SkillingHelperPlugin plugin;
	private final SkillingHelperConfig config;
	private final ModelOutlineRenderer outlines;

	@Inject
	SkillingHelperOverlay(SkillingHelperPlugin plugin, SkillingHelperConfig config, ModelOutlineRenderer outlines)
	{
		this.plugin = plugin;
		this.config = config;
		this.outlines = outlines;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Color color = plugin.isNextBank() ? config.bankColor() : config.resourceColor();
		TileObject o = plugin.getNextObject();
		if (o != null)
		{
			outlines.drawOutline(o, config.outlineWidth(), color, 2);
		}
		NPC n = plugin.getNextNpc();
		if (n != null)
		{
			outlines.drawOutline(n, config.outlineWidth(), color, 2);
		}
		return null;
	}
}
