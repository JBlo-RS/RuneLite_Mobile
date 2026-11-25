package com.starhopper;

public final class Suggestion
{
	private final StarReport star;
	private final double estimatedTier;
	private final boolean sameLocation;

	public Suggestion(StarReport star, double estimatedTier, boolean sameLocation)
	{
		this.star = star;
		this.estimatedTier = estimatedTier;
		this.sameLocation = sameLocation;
	}

	public StarReport getStar()
	{
		return star;
	}

	public double getEstimatedTier()
	{
		return estimatedTier;
	}

	/** Estimated tier rounded up, since a star only drops a tier when its layer is fully mined. */
	public int getDisplayTier()
	{
		return Math.max(1, (int) Math.ceil(estimatedTier));
	}

	public boolean isSameLocation()
	{
		return sameLocation;
	}
}
