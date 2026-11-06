package com.jblors.rlm.plugins.mobile;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Big Status Bars",
	description = "Large Hitpoints and Prayer bars (optionally special attack and run energy), easy to read on a phone",
	tags = {"mobile", "hitpoints", "hp", "prayer", "bars", "special", "run", "rlm"}
)
public class BigBarsPlugin extends Plugin
{
	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BigBarsOverlay overlay;

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
	BigBarsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BigBarsConfig.class);
	}
}
