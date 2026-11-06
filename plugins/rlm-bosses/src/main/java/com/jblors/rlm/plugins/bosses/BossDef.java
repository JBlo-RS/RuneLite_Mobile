package com.jblors.rlm.plugins.bosses;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** What the helper knows about one boss; built with the chained methods below. */
final class BossDef
{
	final Set<Integer> npcIds = new HashSet<>();
	/** Attack animation -> the style it attacks with */
	final Map<Integer, Style> attackAnimations = new HashMap<>();
	/** Projectile (aimed at you) -> style */
	final Map<Integer, Style> projectiles = new HashMap<>();
	/** A form (NPC id) that attacks with one style only */
	final Map<Integer, Style> forms = new HashMap<>();
	/** Spot animations on tiles about to be hit */
	final Set<Integer> markedGraphics = new HashSet<>();
	/** Ground-targeted projectiles to mark (empty = all of the boss's, if groundAll) */
	final Set<Integer> groundProjectiles = new HashSet<>();
	boolean groundAll;
	/** Ground projectile -> ticks its tile stays marked after it lands (e.g. acid pools) */
	final Map<Integer, Integer> linger = new HashMap<>();
	/** Objects on the floor that hurt (venom clouds) */
	final Set<Integer> markedObjects = new HashSet<>();
	/** NPCs that are hazards on the floor (threat circles, area attacks) */
	final Set<Integer> dangerNpcs = new HashSet<>();

	static BossDef npcs(int... ids)
	{
		BossDef d = new BossDef();
		for (int i : ids)
		{
			d.npcIds.add(i);
		}
		return d;
	}

	BossDef anim(Style s, int... ids)
	{
		for (int i : ids)
		{
			attackAnimations.put(i, s);
		}
		return this;
	}

	BossDef projectile(Style s, int... ids)
	{
		for (int i : ids)
		{
			projectiles.put(i, s);
		}
		return this;
	}

	BossDef form(int npcId, Style s)
	{
		forms.put(npcId, s);
		return this;
	}

	BossDef graphics(int... ids)
	{
		for (int i : ids)
		{
			markedGraphics.add(i);
		}
		return this;
	}

	BossDef graphicsRange(int from, int to)
	{
		for (int i = from; i <= to; i++)
		{
			markedGraphics.add(i);
		}
		return this;
	}

	BossDef ground(int... ids)
	{
		for (int i : ids)
		{
			groundProjectiles.add(i);
		}
		return this;
	}

	BossDef groundAll()
	{
		groundAll = true;
		return this;
	}

	BossDef linger(int projectileId, int ticks)
	{
		groundProjectiles.add(projectileId);
		linger.put(projectileId, ticks);
		return this;
	}

	BossDef objects(int... ids)
	{
		for (int i : ids)
		{
			markedObjects.add(i);
		}
		return this;
	}

	BossDef dangerNpcs(int... ids)
	{
		for (int i : ids)
		{
			dangerNpcs.add(i);
		}
		return this;
	}

	boolean marksGround(int projectileId)
	{
		return groundAll || groundProjectiles.contains(projectileId);
	}

	static Set<Integer> unmodifiable(Set<Integer> s)
	{
		return Collections.unmodifiableSet(s);
	}
}
