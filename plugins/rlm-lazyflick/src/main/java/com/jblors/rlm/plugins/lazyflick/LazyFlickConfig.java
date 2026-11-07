package com.jblors.rlm.plugins.lazyflick;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(LazyFlickConfig.GROUP)
public interface LazyFlickConfig extends Config
{
	String GROUP = "rlmlazyflick";

	enum PrayerChoice
	{
		AUTO, MELEE, MISSILES, MAGIC
	}

	@ConfigItem(keyName = "prayer", name = "Prayer", position = 0,
		description = "Auto: the wiki's attack style when the NPC has only one, else the protection prayer you had on when that attack last landed (learned as you fight), else Protect from Melee")
	default PrayerChoice prayer()
	{
		return PrayerChoice.AUTO;
	}

	@Alpha
	@ConfigItem(keyName = "color", name = "Highlight colour", position = 1,
		description = "Colour of the prayer button on the tick to press it")
	default Color color()
	{
		return new Color(255, 230, 0, 230);
	}

	@ConfigItem(keyName = "aboveMe", name = "Also show above you", position = 2,
		description = "Also flash the prayer's icon above your character on the tick to press it")
	default boolean aboveMe()
	{
		return false;
	}

	@ConfigItem(keyName = "countdown", name = "Show ticks to attack", position = 3,
		description = "Show the ticks until the NPC's next attack above it (the highlight comes at 1)")
	default boolean countdown()
	{
		return false;
	}

	@ConfigItem(keyName = "wiki", name = "Look up the wiki", position = 4,
		description = "For an NPC not in the plugin's own database yet, fetch its attack speed and style from the OSRS wiki (once; saved in the database)")
	default boolean wiki()
	{
		return true;
	}

	@ConfigItem(keyName = "forget", name = "Forget the database", position = 5,
		description = "Tick to forget every NPC's saved attack speed and prayer (unticks itself)")
	default boolean forget()
	{
		return false;
	}
}
