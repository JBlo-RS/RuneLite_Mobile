package com.jblors.rlm.plugins.bosses;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("rlmbosses")
public interface BossHelperConfig extends Config
{
	@ConfigSection(name = "Prayer", description = "The prayer against the boss's attack", position = 0)
	String prayer = "prayer";

	@ConfigSection(name = "Tiles", description = "Tiles about to be hit", position = 10)
	String tiles = "tiles";

	@ConfigSection(name = "Bosses", description = "Which bosses to help with", position = 20)
	String bosses = "bosses";

	@ConfigItem(keyName = "prayerBook", name = "Box in prayer book", description = "Box the prayer to use in the prayer tab", section = prayer, position = 1)
	default boolean prayerBook()
	{
		return true;
	}

	@ConfigItem(keyName = "prayerIcon", name = "Icon over character", description = "Also show the prayer's icon above your character", section = prayer, position = 2)
	default boolean prayerIcon()
	{
		return false;
	}

	@ConfigItem(keyName = "prayerHideWhenOn", name = "Hide when praying it", description = "Only show the prayer while it isn't on (otherwise it turns green)", section = prayer, position = 3)
	default boolean prayerHideWhenOn()
	{
		return false;
	}

	@Alpha
	@ConfigItem(keyName = "tileOutline", name = "Outline colour", description = "Outline of a tile about to be hit", section = tiles, position = 11)
	default Color tileOutline()
	{
		return new Color(255, 40, 40, 230);
	}

	@Alpha
	@ConfigItem(keyName = "tileFill", name = "Fill colour", description = "Fill of a tile about to be hit", section = tiles, position = 12)
	default Color tileFill()
	{
		return new Color(255, 40, 40, 70);
	}

	@ConfigItem(keyName = "warnStanding", name = "Warn when standing on one", description = "Show MOVE! over your character while you stand on a marked tile", section = tiles, position = 13)
	default boolean warnStanding()
	{
		return true;
	}

	@ConfigItem(keyName = "scurrius", name = "Scurrius", description = "Helper for Scurrius", section = bosses, position = 100)
	default boolean scurrius()
	{
		return true;
	}

	@ConfigItem(keyName = "jad", name = "TzTok-Jad / JalTok-Jad", description = "Helper for TzTok-Jad / JalTok-Jad", section = bosses, position = 101)
	default boolean jad()
	{
		return true;
	}

	@ConfigItem(keyName = "dagannothKings", name = "Dagannoth Kings", description = "Helper for Dagannoth Kings", section = bosses, position = 102)
	default boolean dagannothKings()
	{
		return true;
	}

	@ConfigItem(keyName = "phantomMuspah", name = "Phantom Muspah", description = "Helper for Phantom Muspah", section = bosses, position = 103)
	default boolean phantomMuspah()
	{
		return true;
	}

	@ConfigItem(keyName = "sarachnis", name = "Sarachnis", description = "Helper for Sarachnis", section = bosses, position = 104)
	default boolean sarachnis()
	{
		return true;
	}

	@ConfigItem(keyName = "chaosFanatic", name = "Chaos Fanatic", description = "Helper for Chaos Fanatic", section = bosses, position = 105)
	default boolean chaosFanatic()
	{
		return true;
	}

	@ConfigItem(keyName = "crazyArchaeologist", name = "Crazy Archaeologist", description = "Helper for Crazy Archaeologist", section = bosses, position = 106)
	default boolean crazyArchaeologist()
	{
		return true;
	}

	@ConfigItem(keyName = "derangedArchaeologist", name = "Deranged Archaeologist", description = "Helper for Deranged Archaeologist", section = bosses, position = 107)
	default boolean derangedArchaeologist()
	{
		return true;
	}

	default boolean enabled(Boss boss)
	{
		switch (boss)
		{
			case SCURRIUS:
				return scurrius();
			case JAD:
				return jad();
			case DAGANNOTH_KINGS:
				return dagannothKings();
			case PHANTOM_MUSPAH:
				return phantomMuspah();
			case SARACHNIS:
				return sarachnis();
			case CHAOS_FANATIC:
				return chaosFanatic();
			case CRAZY_ARCHAEOLOGIST:
				return crazyArchaeologist();
			case DERANGED_ARCHAEOLOGIST:
				return derangedArchaeologist();
			default:
				return false;
		}
	}
}
