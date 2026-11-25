package com.starhopper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(StarHopperConfig.GROUP)
public interface StarHopperConfig extends Config
{
	String GROUP = "starhopper";

	@ConfigSection(name = "Star Miners feed", description = "Where star data comes from", position = 0)
	String feedSection = "feed";

	@ConfigSection(name = "Filters", description = "Which stars and worlds to suggest", position = 1)
	String filterSection = "filters";

	@ConfigSection(name = "Behaviour", description = "Suggestions, notifications and overlay", position = 2)
	String behaviourSection = "behaviour";

	// ---------------- Feed ----------------

	@ConfigItem(
		keyName = "endpoint",
		name = "Endpoint",
		description = "Star Miners crowdsource URL",
		section = feedSection,
		position = 0
	)
	default String endpoint()
	{
		return "https://public.starminers.site/crowdsource";
	}

	@ConfigItem(
		keyName = "authKey",
		name = "Authorization key",
		description = "Your personal key from /pluginkey in the Star Miners Discord. Never share it.",
		secret = true,
		section = feedSection,
		position = 1
	)
	default String authKey()
	{
		return "";
	}

	@Range(min = 15, max = 600)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "pollSeconds",
		name = "Refresh every",
		description = "How often to fetch the star list. Please be kind to the Star Miners server.",
		section = feedSection,
		position = 2
	)
	default int pollSeconds()
	{
		return 60;
	}

	@ConfigItem(
		keyName = "debugLogResponse",
		name = "Log raw response",
		description = "Write the start of each server response to the client log (for troubleshooting)",
		section = feedSection,
		position = 3
	)
	default boolean debugLogResponse()
	{
		return false;
	}

	// ---------------- Filters ----------------

	@ConfigItem(
		keyName = "locations",
		name = "Locations",
		description = "Comma-separated words. A star matches if its location contains any of them, "
			+ "e.g. \"lumbridge, varrock, crafting guild\". Empty = all locations.",
		section = filterSection,
		position = 0
	)
	default String locations()
	{
		return "";
	}

	@ConfigItem(
		keyName = "excludeLocations",
		name = "Exclude locations",
		description = "Comma-separated words. Stars whose location contains any of them are skipped.",
		section = filterSection,
		position = 1
	)
	default String excludeLocations()
	{
		return "wilderness";
	}

	@ConfigItem(keyName = "membersWorlds", name = "Members worlds", description = "Suggest members worlds",
		section = filterSection, position = 2)
	default boolean membersWorlds()
	{
		return true;
	}

	@ConfigItem(keyName = "f2pWorlds", name = "Free-to-play worlds", description = "Suggest F2P worlds",
		section = filterSection, position = 3)
	default boolean f2pWorlds()
	{
		return false;
	}

	@ConfigItem(keyName = "allowPvp", name = "PvP worlds", description = "Allow PvP worlds",
		section = filterSection, position = 4)
	default boolean allowPvp()
	{
		return false;
	}

	@ConfigItem(keyName = "allowHighRisk", name = "High-risk worlds", description = "Allow high-risk worlds",
		section = filterSection, position = 5)
	default boolean allowHighRisk()
	{
		return false;
	}

	@ConfigItem(keyName = "allowSkillTotal", name = "Skill-total worlds",
		description = "Allow skill-total worlds (you must meet the total yourself)",
		section = filterSection, position = 6)
	default boolean allowSkillTotal()
	{
		return false;
	}

	@ConfigItem(keyName = "allowSpecial", name = "Special worlds",
		description = "Allow Leagues, Deadman, speedrun, LMS, tournament, beta and similar worlds",
		section = filterSection, position = 7)
	default boolean allowSpecial()
	{
		return false;
	}

	@Range(min = 1, max = 9)
	@ConfigItem(keyName = "minTier", name = "Minimum tier", description = "Ignore stars estimated below this tier",
		section = filterSection, position = 8)
	default int minTier()
	{
		return 1;
	}

	@ConfigItem(keyName = "limitToMiningLevel", name = "Limit to my Mining level",
		description = "Skip stars whose estimated tier needs a higher Mining level than yours",
		section = filterSection, position = 9)
	default boolean limitToMiningLevel()
	{
		return true;
	}

	@Range(min = 5, max = 240)
	@Units(Units.MINUTES)
	@ConfigItem(keyName = "maxReportAge", name = "Max report age",
		description = "Ignore reports older than this",
		section = filterSection, position = 10)
	default int maxReportAge()
	{
		return 60;
	}

	// ---------------- Behaviour ----------------

	@ConfigItem(keyName = "preferSameLocation", name = "Prefer same location",
		description = "When your star depletes, suggest the same location on another world first",
		section = behaviourSection, position = 0)
	default boolean preferSameLocation()
	{
		return true;
	}

	@Range(min = 1, max = 30)
	@Units(Units.MINUTES)
	@ConfigItem(keyName = "minutesPerTier", name = "Minutes per tier",
		description = "Rough time a star takes to drop one tier; used to age old reports",
		section = behaviourSection, position = 1)
	default int minutesPerTier()
	{
		return 7;
	}

	@ConfigItem(keyName = "notifyOnDeplete", name = "Notify on depletion",
		description = "Send a notification with the next suggestion when your star depletes",
		section = behaviourSection, position = 2)
	default boolean notifyOnDeplete()
	{
		return true;
	}

	@ConfigItem(keyName = "showOverlay", name = "Show overlay",
		description = "Show the next suggestion on screen. Right-click / long-press it to hop.",
		section = behaviourSection, position = 3)
	default boolean showOverlay()
	{
		return true;
	}
}
