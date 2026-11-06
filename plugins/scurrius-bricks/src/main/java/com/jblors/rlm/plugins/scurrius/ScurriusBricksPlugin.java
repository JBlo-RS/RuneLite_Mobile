package com.jblors.rlm.plugins.scurrius;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Scurrius Falling Bricks",
	description = "Marks the tiles where Scurrius' falling bricks are about to land",
	tags = {"scurrius", "rat", "boss", "bricks", "debris", "falling", "tiles", "rlm"}
)
public class ScurriusBricksPlugin extends Plugin
{
	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ScurriusBricksOverlay overlay;

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
	}

	@Provides
	ScurriusBricksConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ScurriusBricksConfig.class);
	}
}
