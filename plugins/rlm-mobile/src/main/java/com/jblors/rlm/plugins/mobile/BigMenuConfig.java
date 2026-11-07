package com.jblors.rlm.plugins.mobile;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("rlmbigmenu")
public interface BigMenuConfig extends Config
{
	@Range(min = 2, max = 5)
	@ConfigItem(keyName = "scale", name = "Size", description = "How many times bigger than the game's menu (whole numbers keep its pixels crisp)", position = 1)
	default int scale()
	{
		return 3;
	}
}
