package com.jblors.rlm.plugins.bosses;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(BossHelperConfig.GROUP)
public interface BossHelperConfig extends Config
{
	String GROUP = "rlmbosses";

	@ConfigSection(name = "Prayer", description = "The prayer against the boss's attack", position = 0)
	String prayer = "prayer";

	@ConfigSection(name = "Tiles", description = "Tiles about to be hit", position = 10)
	String tiles = "tiles";

	@ConfigSection(name = "Bosses", description = "Which bosses to help with", position = 20)
	String bosses = "bosses";

	@ConfigSection(name = "Perilous Moons", description = "Blood Moon, Blue Moon and Eclipse Moon hazards (no prayers: they don't work there)", position = 25)
	String moons = "moons";

	@ConfigItem(keyName = "moonsBloodPools", name = "Blood pools", description = "Mark blood pools with a tick countdown", section = moons, position = 1)
	default boolean moonsBloodPools()
	{
		return true;
	}

	@ConfigItem(keyName = "moonsBloodRain", name = "Blood rain", description = "Mark blood rain with a tick countdown", section = moons, position = 2)
	default boolean moonsBloodRain()
	{
		return true;
	}

	@ConfigItem(keyName = "moonsIcicles", name = "Icicles", description = "Mark the tiles icicles hit, with a tick countdown", section = moons, position = 3)
	default boolean moonsIcicles()
	{
		return true;
	}

	@ConfigItem(keyName = "moonsIceFloor", name = "Ice floor", description = "Mark the Blue Moon's ice on the floor", section = moons, position = 3)
	default boolean moonsIceFloor()
	{
		return true;
	}

	@ConfigItem(keyName = "moonsTornados", name = "Tornadoes (true tile)", description = "Mark the true tile of the Blue Moon's tornadoes", section = moons, position = 4)
	default boolean moonsTornados()
	{
		return true;
	}

	@ConfigItem(keyName = "moonsEclipse", name = "Eclipse true tile", description = "Outline the true tile of the Eclipse Moon and its orb", section = moons, position = 5)
	default boolean moonsEclipse()
	{
		return true;
	}

	@ConfigItem(keyName = "moonsEclipseClones", name = "Eclipse clones: step tile", description = "In the Eclipse Moon's clone phase, mark the tile next to you in the direction the latest one appeared (instead of outlining the bosses)", section = moons, position = 6)
	default boolean moonsEclipseClones()
	{
		return true;
	}

	@net.runelite.client.config.Range(min = 1, max = 5)
	@ConfigItem(keyName = "moonsMoveAt", name = "Say MOVE at (ticks left)", description = "Countdowns count down to this many ticks left, then say MOVE (it hits as the last tick ends)", section = moons, position = 7)
	default int moonsMoveAt()
	{
		return 2;
	}

	@ConfigSection(name = "Debug", description = "For fixing a boss that doesn't work", position = 30, closedByDefault = true)
	String debug = "debug";

	@ConfigItem(keyName = "debugLog", name = "Record fight details", description = "Write nearby NPCs' animations, projectiles and ground effects to the app log (Share log). Turn on, fight, then share the log.", section = debug, position = 31)
	default boolean debugLog()
	{
		return false;
	}

	@ConfigItem(keyName = "prayerBook", name = "Box in prayer book", description = "Box the prayer to use in the prayer tab", section = prayer, position = 1)
	default boolean prayerBook()
	{
		return true;
	}

	@ConfigItem(keyName = "prayerAboveBoss", name = "Icon above boss", description = "Show the prayer's icon above the boss that attacked", section = prayer, position = 0)
	default boolean prayerAboveBoss()
	{
		return true;
	}

	@ConfigItem(keyName = "prayerIcon", name = "Icon over character", description = "Also show the prayer's icon above your character", section = prayer, position = 2)
	default boolean prayerIcon()
	{
		return false;
	}

	@ConfigItem(keyName = "attackWarnings", name = "Attack warnings", description = "Big warning above a boss that announces its next attack with overhead text (Brutus' growl / snort). Learns which text means which attack during the fight.", section = prayer, position = 4)
	default boolean attackWarnings()
	{
		return true;
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

	@ConfigItem(keyName = "abyssalSire", name = "Abyssal Sire", description = "Helper for Abyssal Sire", section = bosses, position = 100)
	default boolean abyssalSire()
	{
		return true;
	}

	@ConfigItem(keyName = "amoxliatl", name = "Amoxliatl", description = "Helper for Amoxliatl", section = bosses, position = 101)
	default boolean amoxliatl()
	{
		return true;
	}

	@ConfigItem(keyName = "brutus", name = "Brutus", description = "Helper for Brutus", section = bosses, position = 102)
	default boolean brutus()
	{
		return true;
	}

	@ConfigItem(keyName = "chaosFanatic", name = "Chaos Fanatic", description = "Helper for Chaos Fanatic", section = bosses, position = 103)
	default boolean chaosFanatic()
	{
		return true;
	}

	@ConfigItem(keyName = "crazyArchaeologist", name = "Crazy Archaeologist", description = "Helper for Crazy Archaeologist", section = bosses, position = 104)
	default boolean crazyArchaeologist()
	{
		return true;
	}

	@ConfigItem(keyName = "dagannothKings", name = "Dagannoth Kings", description = "Helper for Dagannoth Kings", section = bosses, position = 105)
	default boolean dagannothKings()
	{
		return true;
	}

	@ConfigItem(keyName = "derangedArchaeologist", name = "Deranged Archaeologist", description = "Helper for Deranged Archaeologist", section = bosses, position = 106)
	default boolean derangedArchaeologist()
	{
		return true;
	}

	@ConfigItem(keyName = "galvek", name = "Galvek", description = "Helper for Galvek", section = bosses, position = 107)
	default boolean galvek()
	{
		return true;
	}

	@ConfigItem(keyName = "hespori", name = "Hespori", description = "Helper for Hespori", section = bosses, position = 108)
	default boolean hespori()
	{
		return true;
	}

	@ConfigItem(keyName = "hueycoatl", name = "The Hueycoatl", description = "Helper for The Hueycoatl", section = bosses, position = 109)
	default boolean hueycoatl()
	{
		return true;
	}

	@ConfigItem(keyName = "moonsOfPeril", name = "Moons of Peril", description = "Helper for Moons of Peril", section = bosses, position = 110)
	default boolean moonsOfPeril()
	{
		return true;
	}

	@ConfigItem(keyName = "phantomMuspah", name = "Phantom Muspah", description = "Helper for Phantom Muspah", section = bosses, position = 111)
	default boolean phantomMuspah()
	{
		return true;
	}

	@ConfigItem(keyName = "royalTitans", name = "Royal Titans", description = "Helper for Royal Titans", section = bosses, position = 112)
	default boolean royalTitans()
	{
		return true;
	}

	@ConfigItem(keyName = "sarachnis", name = "Sarachnis", description = "Helper for Sarachnis", section = bosses, position = 113)
	default boolean sarachnis()
	{
		return true;
	}

	@ConfigItem(keyName = "scurrius", name = "Scurrius", description = "Helper for Scurrius", section = bosses, position = 114)
	default boolean scurrius()
	{
		return true;
	}

	@ConfigItem(keyName = "jad", name = "TzTok-Jad / JalTok-Jad", description = "Helper for TzTok-Jad / JalTok-Jad", section = bosses, position = 115)
	default boolean jad()
	{
		return true;
	}

	@ConfigItem(keyName = "vorkath", name = "Vorkath", description = "Helper for Vorkath", section = bosses, position = 116)
	default boolean vorkath()
	{
		return true;
	}

	@ConfigItem(keyName = "zulrah", name = "Zulrah", description = "Helper for Zulrah", section = bosses, position = 117)
	default boolean zulrah()
	{
		return true;
	}

}
