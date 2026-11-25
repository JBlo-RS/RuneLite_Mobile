package com.starhopper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class StarHopperOverlay extends OverlayPanel
{
	private final StarHopperPlugin plugin;

	@Inject
	StarHopperOverlay(StarHopperPlugin plugin)
	{
		super(plugin);
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_LEFT);
		getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, StarHopperPlugin.HOP_OPTION, "Star Hopper"));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.getConfig().showOverlay())
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Next star")
			.color(new Color(255, 196, 0))
			.build());

		Suggestion s = plugin.getSuggestion();
		if (s == null)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("No matching stars")
				.build());
		}
		else
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("World")
				.right(String.valueOf(s.getStar().getWorld()))
				.build());
			panelComponent.getChildren().add(LineComponent.builder()
				.left(s.getStar().getLocation())
				.build());
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Est. tier")
				.right(s.getStar().getTier() > 0 ? "T" + s.getDisplayTier() : "?")
				.rightColor(s.isSameLocation() ? Color.GREEN : Color.WHITE)
				.build());
			if (s.isSameLocation())
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left("Same spot, just hop")
					.leftColor(Color.GREEN)
					.build());
			}
		}
		return super.render(graphics);
	}
}
