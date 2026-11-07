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
	// NpcID: PMOON_BOSS_ICICLE_1-3 (13025 _ICICLE_UNCRACKED is "Frozen weapons" and 13026 "Cracked
	// ice": not hazards), _WINTER_STORM; the Eclipse Moon as seen in a fight log (_ECLIPSE_MOON_VIS
	// 13012, "Enraged Eclipse Moon" PMOON_ECLIPSE_BOSS_INVIS 13487) and _ECLIPSE_MOON 13018, and
	// its orb, "Moon Shield" _ECLIPSE_MOON_SHIELD
	static final int ICICLE_1 = 13022, ICICLE_2 = 13023, ICICLE_3 = 13024;
	static final int WINTER_STORM = 13027, ECLIPSE_SHIELD = 13020;
	static final int ECLIPSE_MOON_VIS = 13012, ECLIPSE_MOON = 13018, ECLIPSE_ENRAGED = 13487;
	/** NpcID.PMOON_BOSS_ECLIPSE_CLONE */
	static final int ECLIPSE_CLONE = 13019;
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
				return Kind.ICICLE;
			case WINTER_STORM:
				return Kind.TORNADO;
			case ECLIPSE_MOON_VIS:
			case ECLIPSE_MOON:
			case ECLIPSE_ENRAGED:
			case ECLIPSE_CLONE:
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

	/** The Eclipse Moon or clone that appeared last (for the clone phase), and when */
	volatile NPC lastEclipse;
	volatile int lastEclipseTick;

	void npcSpawned(NPC n, int tick)
	{
		if (kindOfNpc(n.getId()) != null)
		{
			if (npcs.putIfAbsent(n, tick) == null && isEclipseBody(n.getId()))
			{
				lastEclipse = n;
				lastEclipseTick = tick;
			}
		}
	}

	/** The moon itself or a clone of it (not its Moon Shield orb). */
	static boolean isEclipseBody(int id)
	{
		return id == ECLIPSE_MOON_VIS || id == ECLIPSE_MOON || id == ECLIPSE_ENRAGED || id == ECLIPSE_CLONE;
	}

	/** The Eclipse Moon or clone that appeared last and is still here, else null. */
	NPC latestEclipse()
	{
		NPC last = lastEclipse;
		if (last != null && npcs.containsKey(last))
		{
			return last;
		}
		NPC best = null;
		int bestTick = Integer.MIN_VALUE;
		for (Map.Entry<NPC, Integer> e : npcs.entrySet())
		{
			if (isEclipseBody(e.getKey().getId()) && e.getValue() > bestTick)
			{
				best = e.getKey();
				bestTick = e.getValue();
			}
		}
		return best;
	}

	/** The clone phase: a clone is here, or more than one Eclipse Moon. */
	boolean clonePhase()
	{
		int bodies = 0;
		for (NPC n : npcs.keySet())
		{
			if (n.getId() == ECLIPSE_CLONE)
			{
				return true;
			}
			if (isEclipseBody(n.getId()))
			{
				bodies++;
			}
		}
		return bodies > 1;
	}

	/**
	 * The direction {dx, dy} (each -1..1) from the player's tile to an NPC's centre, rounded to
	 * one of 8 directions.
	 */
	static int[] direction(int px, int py, int nx, int ny, int size)
	{
		// doubled so the centre of an even-sized NPC stays whole
		int vx = 2 * nx + size - 1 - 2 * px, vy = 2 * ny + size - 1 - 2 * py;
		if (vx == 0 && vy == 0)
		{
			return new int[]{0, 0};
		}
		double a = Math.atan2(vy, vx); // 0 = east, counter-clockwise
		int sector = (int) Math.round(a / (Math.PI / 4)); // -4..4
		int[][] dirs = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
		return dirs[((sector % 8) + 8) % 8];
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
