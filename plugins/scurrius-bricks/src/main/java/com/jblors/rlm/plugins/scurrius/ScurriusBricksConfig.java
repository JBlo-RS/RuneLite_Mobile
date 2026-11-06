package com.jblors.rlm.plugins.scurrius;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(ScurriusBricksConfig.GROUP)
public interface ScurriusBricksConfig extends Config
{
	String GROUP = "scurriusbricks";

	@ConfigSection(
		name = "Falling bricks",
		description = "Tiles where falling bricks are about to land",
		position = 0
	)
	String bricks = "bricks";

	@ConfigSection(
		name = "Prayer",
		description = "The prayer against Scurrius' last attack",
		position = 10
	)
	String prayer = "prayer";

	@Alpha
	@ConfigItem(
		keyName = "outlineColor",
		section = bricks,
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
		section = bricks,
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
		section = bricks,
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
		section = bricks,
		name = "Warn when standing on one",
		description = "Shows MOVE! over your character while a brick is falling on your tile",
		position = 4
	)
	default boolean warnStanding()
	{
		return true;
	}

	@ConfigItem(
		keyName = "prayerHelper",
		name = "Show prayer",
		description = "Show the prayer against Scurrius' last attack (melee, ranged or magic)",
		section = prayer,
		position = 11
	)
	default boolean prayerHelper()
	{
		return true;
	}

	@ConfigItem(
		keyName = "prayerHighlightBook",
		name = "Box in prayer book",
		description = "Draw a box around the prayer in the prayer tab",
		section = prayer,
		position = 12
	)
	default boolean prayerHighlightBook()
	{
		return true;
	}

	@ConfigItem(
		keyName = "prayerOverPlayer",
		name = "Icon over character",
		description = "Show the prayer's icon above your character",
		section = prayer,
		position = 13
	)
	default boolean prayerOverPlayer()
	{
		return true;
	}

	@ConfigItem(
		keyName = "prayerHideWhenOn",
		name = "Hide when praying it",
		description = "Only show the prayer while it isn't on (otherwise it turns green)",
		section = prayer,
		position = 14
	)
	default boolean prayerHideWhenOn()
	{
		return false;
	}
}
