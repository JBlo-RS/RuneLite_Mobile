package com.jblors.rlm.plugins.bosses;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.api.NPC;
import net.runelite.api.TileObject;

/**
 * Perilous Moons hazards, from the game's own spawn/despawn events (like Brutus and Scurrius):
 * Blood Moon's blood pools and blood rain, Blue Moon's icicles and tornadoes, and the Eclipse
 * Moon's orb. No prayers there: protection prayers don't work at the Moons.
 *
 * <p>Countdowns: each hazard's lifetime (spawn to despawn, in game ticks) is measured as they go
 * and used for the next ones; until then a default of 4 ticks. The tile shows ticks left, and
 * says MOVE from {@code moveAt} ticks left (on screen 4 ticks: 4, 3, 2 = MOVE, 1, gone).
 */
final class MoonsTracker
{
	// ObjectID: PMOON_BOSS_BLOOD_POOL, _BLOOD_POOL_HARD, _BLOOD_RAIN
	static final int BLOOD_POOL = 51046, BLOOD_POOL_HARD = 52991, BLOOD_RAIN = 51045;
	// NpcID: PMOON_BOSS_ICICLE_1-3, _ICICLE_UNCRACKED, _ICICLE_CRACKED, _WINTER_STORM, _ECLIPSE_MOON, _ECLIPSE_MOON_SHIELD
	static final int ICICLE_1 = 13022, ICICLE_2 = 13023, ICICLE_3 = 13024, ICICLE_UNCRACKED = 13025, ICICLE_CRACKED = 13026;
	static final int WINTER_STORM = 13027, ECLIPSE_MOON = 13018, ECLIPSE_SHIELD = 13020;
	static final int DEFAULT_LIFETIME = 4;

	enum Kind
	{
		BLOOD_POOL, BLOOD_RAIN, ICICLE, TORNADO, ECLIPSE
	}

	static Kind kindOfObject(int id)
	{
		switch (id)
		{
			case BLOOD_POOL:
			case BLOOD_POOL_HARD:
				return Kind.BLOOD_POOL;
			case BLOOD_RAIN:
				return Kind.BLOOD_RAIN;
			default:
				return null;
		}
	}

	static Kind kindOfNpc(int id)
	{
		switch (id)
		{
			case ICICLE_1:
			case ICICLE_2:
			case ICICLE_3:
			case ICICLE_UNCRACKED:
			case ICICLE_CRACKED:
				return Kind.ICICLE;
			case WINTER_STORM:
				return Kind.TORNADO;
			case ECLIPSE_MOON:
			case ECLIPSE_SHIELD:
				return Kind.ECLIPSE;
			default:
				return null;
		}
	}

	/** Hazards here now -> the game tick they appeared */
	final Map<TileObject, Integer> objects = new ConcurrentHashMap<>();
	final Map<NPC, Integer> npcs = new ConcurrentHashMap<>();
	/** Measured lifetime (ticks) per object / NPC id */
	private final Map<Integer, Integer> lifetime = new ConcurrentHashMap<>();

	void objectSpawned(TileObject o, int tick)
	{
		if (kindOfObject(o.getId()) != null)
		{
			objects.put(o, tick);
		}
	}

	void objectDespawned(TileObject o, int tick)
	{
		Integer t0 = objects.remove(o);
		learn(o.getId(), t0, tick);
	}

	void npcSpawned(NPC n, int tick)
	{
		if (kindOfNpc(n.getId()) != null)
		{
			npcs.putIfAbsent(n, tick);
		}
	}

	void npcDespawned(NPC n, int tick)
	{
		Integer t0 = npcs.remove(n);
		learn(n.getId(), t0, tick);
	}

	private void learn(int id, Integer spawnTick, int tick)
	{
		if (spawnTick != null && tick - spawnTick >= 1 && tick - spawnTick <= 30)
		{
			lifetime.put(id, tick - spawnTick);
		}
	}

	/** Ticks left (lifetime counted down; at least 1 while it's still there). */
	int ticksLeft(int id, int spawnTick, int now)
	{
		int life = lifetime.getOrDefault(id, DEFAULT_LIFETIME);
		return Math.max(1, life - (now - spawnTick));
	}

	void clear()
	{
		objects.clear();
		npcs.clear();
	}
}
