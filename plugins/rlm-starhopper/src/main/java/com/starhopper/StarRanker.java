package com.starhopper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldType;

/**
 * Turns the raw star list into an ordered list of suggestions.
 *
 * Order: if you were on a star that just depleted (or you're at a known location)
 * and "Prefer same location" is on, stars at that same location on other worlds come
 * first. After that, the highest estimated tier wins, newest report breaking ties.
 */
public final class StarRanker
{
	private static final Set<String> SPECIAL_WORLD_TYPES = new HashSet<>(Arrays.asList(
		"DEADMAN", "SEASONAL", "LAST_MAN_STANDING", "TOURNAMENT", "BETA_WORLD", "FRESH_START_WORLD",
		"QUEST_SPEEDRUNNING", "NOSAVE_MODE", "PVP_ARENA", "BOUNTY", "TOURNAMENT_WORLD"));

	private StarRanker()
	{
	}

	public static List<Suggestion> rank(
		Collection<StarReport> stars,
		Map<Integer, World> worlds,
		Map<String, Long> depleted,
		StarHopperConfig config,
		int miningLevel,
		int currentWorld,
		String currentLocation,
		long now)
	{
		List<String> include = tokens(config.locations());
		List<String> exclude = tokens(config.excludeLocations());
		int maxTier = config.limitToMiningLevel() && miningLevel > 0 ? Math.min(9, miningLevel / 10) : 9;
		long maxAgeMillis = config.maxReportAge() * 60_000L;
		boolean sameFirst = config.preferSameLocation() && currentLocation != null;

		List<Suggestion> out = new ArrayList<>();
		for (StarReport s : stars)
		{
			if (s.getWorld() == currentWorld)
			{
				continue;
			}
			World w = worlds.get(s.getWorld());
			// If the world list hasn't loaded yet, don't filter on world type
			if (!worlds.isEmpty() && (w == null || !worldAllowed(w, config)))
			{
				continue;
			}
			if (s.getReportedAtMillis() > 0 && now - s.getReportedAtMillis() > maxAgeMillis)
			{
				continue;
			}
			Long depletedAt = depleted.get(s.key());
			if (depletedAt != null && depletedAt >= s.getReportedAtMillis())
			{
				continue;
			}
			String loc = s.getLocation().toLowerCase();
			if (!include.isEmpty() && include.stream().noneMatch(loc::contains))
			{
				continue;
			}
			if (exclude.stream().anyMatch(loc::contains))
			{
				continue;
			}

			double est = s.getTier() > 0 ? s.estimatedTier(now, config.minutesPerTier()) : 0;
			if (s.getTier() > 0)
			{
				if (est <= 0)
				{
					continue; // probably gone by now
				}
				int displayTier = (int) Math.ceil(est);
				if (displayTier < config.minTier() || displayTier > maxTier)
				{
					continue;
				}
			}

			boolean same = sameFirst && s.getLocation().equalsIgnoreCase(currentLocation);
			out.add(new Suggestion(s, est, same));
		}

		out.sort(Comparator
			.comparing((Suggestion x) -> !x.isSameLocation())
			.thenComparing(Suggestion::getEstimatedTier, Comparator.reverseOrder())
			.thenComparing(x -> x.getStar().getReportedAtMillis(), Comparator.reverseOrder()));
		return out;
	}

	static boolean worldAllowed(World w, StarHopperConfig c)
	{
		EnumSet<WorldType> types = w.getTypes();
		Set<String> t = new HashSet<>();
		if (types != null)
		{
			for (WorldType type : types)
			{
				t.add(type.name());
			}
		}

		boolean members = t.contains("MEMBERS");
		if (members && !c.membersWorlds())
		{
			return false;
		}
		if (!members && !c.f2pWorlds())
		{
			return false;
		}
		if (t.contains("PVP") && !c.allowPvp())
		{
			return false;
		}
		if (t.contains("HIGH_RISK") && !c.allowHighRisk())
		{
			return false;
		}
		if (t.contains("SKILL_TOTAL") && !c.allowSkillTotal())
		{
			return false;
		}
		if (!c.allowSpecial() && !Collections.disjoint(t, SPECIAL_WORLD_TYPES))
		{
			return false;
		}
		return true;
	}

	private static List<String> tokens(String csv)
	{
		List<String> out = new ArrayList<>();
		if (csv == null)
		{
			return out;
		}
		for (String part : csv.split(","))
		{
			String p = part.trim().toLowerCase();
			if (!p.isEmpty())
			{
				out.add(p);
			}
		}
		return out;
	}
}
