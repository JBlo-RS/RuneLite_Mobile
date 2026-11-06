package com.jblors.rlm.plugins.scurrius;

import net.runelite.api.Prayer;
import net.runelite.api.gameval.SpriteID;

/** Scurrius' attack styles and the prayer that blocks each. */
enum AttackStyle
{
	MELEE(Prayer.PROTECT_FROM_MELEE, "Protect from Melee", SpriteID.Prayeron.PROTECT_FROM_MELEE),
	RANGED(Prayer.PROTECT_FROM_MISSILES, "Protect from Missiles", SpriteID.Prayeron.PROTECT_FROM_MISSILES),
	MAGIC(Prayer.PROTECT_FROM_MAGIC, "Protect from Magic", SpriteID.Prayeron.PROTECT_FROM_MAGIC);

	final Prayer prayer;
	/** The prayer's name, as on its button in the prayer book */
	final String name;
	/** The prayer's icon (sprite archive, file 0) */
	final int sprite;

	AttackStyle(Prayer prayer, String name, int sprite)
	{
		this.prayer = prayer;
		this.name = name;
		this.sprite = sprite;
	}

	/** The style of a Scurrius attack animation, or null if it isn't one. */
	static AttackStyle ofAnimation(int animation)
	{
		switch (animation)
		{
			case 10692: // NPC_RAT_BOSS_ATTACK_MELEE_01
			case 10693: // NPC_RAT_BOSS_ATTACK_MELEE_02
				return MELEE;
			case 10694: // NPC_RAT_BOSS_ATTACK_RANGED_01
			case 10695: // NPC_RAT_BOSS_FEEDING_ATTACK_RANGED_01
				return RANGED;
			case 10696: // NPC_RAT_BOSS_ATTACK_MAGIC_01
			case 10697: // NPC_RAT_BOSS_FEEDING_ATTACK_MAGIC_01
				return MAGIC;
			default:
				return null;
		}
	}
}
