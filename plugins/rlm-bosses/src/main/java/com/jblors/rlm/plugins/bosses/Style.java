package com.jblors.rlm.plugins.bosses;

import net.runelite.api.Prayer;
import net.runelite.api.gameval.SpriteID;

/** Attack styles and the protection prayer for each. */
enum Style
{
	MELEE(Prayer.PROTECT_FROM_MELEE, "Protect from Melee", SpriteID.Prayeron.PROTECT_FROM_MELEE),
	RANGED(Prayer.PROTECT_FROM_MISSILES, "Protect from Missiles", SpriteID.Prayeron.PROTECT_FROM_MISSILES),
	MAGIC(Prayer.PROTECT_FROM_MAGIC, "Protect from Magic", SpriteID.Prayeron.PROTECT_FROM_MAGIC);

	final Prayer prayer;
	final String prayerName;
	final int sprite;

	Style(Prayer prayer, String prayerName, int sprite)
	{
		this.prayer = prayer;
		this.prayerName = prayerName;
		this.sprite = sprite;
	}
}
