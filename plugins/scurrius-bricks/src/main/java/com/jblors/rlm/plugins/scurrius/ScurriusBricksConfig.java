package com.jblors.rlm.plugins.scurrius;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(ScurriusBricksConfig.GROUP)
public interface ScurriusBricksConfig extends Config
{
	String GROUP = "scurriusbricks";

	@Alpha
	@ConfigItem(
		keyName = "outlineColor",
		name = "Outline colour",
		description = "Outline of a tile a brick is about to land on",
		position = 1
	)
	default Color outlineColor()
	{
		return new Color(255, 40, 40, 230);
	}

	@Alpha
	@ConfigItem(
		keyName = "fillColor",
		name = "Fill colour",
		description = "Fill of a tile a brick is about to land on",
		position = 2
	)
	default Color fillColor()
	{
		return new Color(255, 40, 40, 70);
	}

	@Range(min = 1, max = 6)
	@ConfigItem(
		keyName = "outlineWidth",
		name = "Outline width",
		description = "Width of the tile outline",
		position = 3
	)
	default int outlineWidth()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "warnStanding",
		name = "Warn when standing on one",
		description = "Shows MOVE! over your character while a brick is falling on your tile",
		position = 4
	)
	default boolean warnStanding()
	{
		return true;
	}
}
