package com.jblors.rlm.plugins.skilling;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("rlmskilling")
public interface SkillingHelperConfig extends Config
{
	@ConfigItem(keyName = "onlyIdle", name = "Only when idle", description = "Only highlight while you're not already chopping, mining or fishing", position = 1)
	default boolean onlyIdle()
	{
		return true;
	}

	@ConfigItem(keyName = "bankWhenFull", name = "Bank when inventory is full", description = "With a full inventory, highlight the nearest bank instead (turn off when power-dropping)", position = 2)
	default boolean bankWhenFull()
	{
		return true;
	}

	@Range(max = 64)
	@ConfigItem(keyName = "maxDistance", name = "Max distance", description = "Ignore resources further away than this many tiles", position = 3)
	default int maxDistance()
	{
		return 25;
	}

	@Alpha
	@ConfigItem(keyName = "resourceColor", name = "Resource colour", description = "Outline of the next tree, rock or fishing spot", position = 10)
	default Color resourceColor()
	{
		return new Color(40, 230, 90, 230);
	}

	@Alpha
	@ConfigItem(keyName = "bankColor", name = "Bank colour", description = "Outline of the bank when your inventory is full", position = 11)
	default Color bankColor()
	{
		return new Color(255, 200, 40, 230);
	}

	@Range(min = 1, max = 8)
	@ConfigItem(keyName = "outlineWidth", name = "Outline width", description = "Width of the outline", position = 12)
	default int outlineWidth()
	{
		return 3;
	}
}
