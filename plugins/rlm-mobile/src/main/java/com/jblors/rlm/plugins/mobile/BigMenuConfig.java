package com.jblors.rlm.plugins.mobile;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("rlmbigmenu")
public interface BigMenuConfig extends Config
{
	@Range(min = 20, max = 80)
	@ConfigItem(keyName = "rowHeight", name = "Row height", description = "Height of each option (the game's is 15)", position = 1)
	default int rowHeight()
	{
		return 38;
	}

	@Range(min = 12, max = 40)
	@ConfigItem(keyName = "fontSize", name = "Text size", description = "Size of the option text", position = 2)
	default int fontSize()
	{
		return 17;
	}

	@Range(min = 120, max = 600)
	@ConfigItem(keyName = "minWidth", name = "Minimum width", description = "The menu is at least this wide", position = 3)
	default int minWidth()
	{
		return 220;
	}
}
