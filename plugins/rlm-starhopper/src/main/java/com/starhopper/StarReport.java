package com.starhopper;

/**
 * One star as reported by the Star Miners feed.
 */
public final class StarReport
{
	private final int world;
	private final String location;
	private final int tier;
	private final long reportedAtMillis;

	public StarReport(int world, String location, int tier, long reportedAtMillis)
	{
		this.world = world;
		this.location = location == null ? "Unknown" : location;
		this.tier = tier;
		this.reportedAtMillis = reportedAtMillis;
	}

	public int getWorld()
	{
		return world;
	}

	public String getLocation()
	{
		return location;
	}

	public int getTier()
	{
		return tier;
	}

	public long getReportedAtMillis()
	{
		return reportedAtMillis;
	}

	/**
	 * Reported tier minus the tiers it has probably lost since it was reported.
	 */
	public double estimatedTier(long nowMillis, int minutesPerTier)
	{
		if (reportedAtMillis <= 0 || minutesPerTier <= 0)
		{
			return tier;
		}
		double elapsedMinutes = Math.max(0, nowMillis - reportedAtMillis) / 60000.0;
		return tier - elapsedMinutes / minutesPerTier;
	}

	public String key()
	{
		return world + "|" + location.toLowerCase();
	}
}
