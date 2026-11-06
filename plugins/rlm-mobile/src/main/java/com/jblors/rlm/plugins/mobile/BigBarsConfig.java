package com.jblors.rlm.plugins.mobile;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup("rlmbigbars")
public interface BigBarsConfig extends Config
{
	@ConfigItem(keyName = "hitpoints", name = "Hitpoints", description = "Show a Hitpoints bar", position = 1)
	default boolean hitpoints()
	{
		return true;
	}

	@ConfigItem(keyName = "prayer", name = "Prayer", description = "Show a Prayer bar", position = 2)
	default boolean prayer()
	{
		return true;
	}

	@ConfigItem(keyName = "special", name = "Special attack", description = "Show a special attack energy bar", position = 3)
	default boolean special()
	{
		return false;
	}

	@ConfigItem(keyName = "run", name = "Run energy", description = "Show a run energy bar", position = 4)
	default boolean run()
	{
		return false;
	}

	@Range(min = 80, max = 600)
	@ConfigItem(keyName = "width", name = "Width", description = "Bar width", position = 10)
	default int width()
	{
		return 220;
	}

	@Range(min = 10, max = 60)
	@ConfigItem(keyName = "height", name = "Height", description = "Bar height", position = 11)
	default int height()
	{
		return 22;
	}

	@ConfigItem(keyName = "numbers", name = "Show numbers", description = "Show the current and maximum value on each bar", position = 12)
	default boolean numbers()
	{
		return true;
	}

	@Range(max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(keyName = "lowHp", name = "Low HP warning", description = "Flash the Hitpoints bar below this (0 = off)", position = 13)
	default int lowHp()
	{
		return 30;
	}

	@Range(max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(keyName = "lowPrayer", name = "Low prayer warning", description = "Flash the Prayer bar below this (0 = off)", position = 14)
	default int lowPrayer()
	{
		return 20;
	}
}
