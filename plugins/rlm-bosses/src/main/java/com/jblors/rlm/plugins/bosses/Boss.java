package com.jblors.rlm.plugins.bosses;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * What the helper knows about each boss. IDs are from RuneLite's game data (gameval names in
 * the comments); the mechanics are from the OSRS wiki as remembered, so each boss is
 * "best effort" until tested in game.
 *
 * <ul>
 *   <li>attack animations: when the boss starts an attack, the prayer against it is shown</li>
 *   <li>projectiles aimed at you: the same, for attacks without a telling animation</li>
 *   <li>forms: a boss form that attacks with one style only (shown while in that form)</li>
 *   <li>marked graphics: spot animations on tiles about to be hit (shadows, warnings)</li>
 *   <li>ground projectiles: the boss's projectiles aimed at a tile rather than at a player
 *       (bombs, books); their landing tile is marked until they land</li>
 * </ul>
 */
enum Boss
{
	SCURRIUS("Scurrius", ids(7221, 7222), // NpcID.RAT_BOSS_NORMAL, RAT_BOSS_INSTANCE
		anims(Style.MELEE, 10692, 10693, Style.RANGED, 10694, 10695, Style.MAGIC, 10696, 10697),
		none(), none(),
		ids(2644), // SpotanimID.VFX_RAT_BOSS_FALLING_DEBRIS_01
		false),

	JAD("TzTok-Jad / JalTok-Jad", ids(3127, 7700, 7704, 10623), // TZHAAR_FIGHTCAVE_SWARM_BOSS, INFERNO_JAD(_FINALWAVE), JAD_CHALLENGE_JAD
		// Fight Caves: LORDMAGMUS_ATTACK (melee) 2655, _SMASH (ranged) 2652, _FIRE (magic) 2656
		// Inferno / challenge: JALTOKJAD_ATTACK_MELEE 7590, _RANGED 7593, _MAGIC 7592
		anims(Style.MELEE, 2655, 7590, Style.RANGED, 2652, 7593, Style.MAGIC, 2656, 7592),
		none(), none(), ids(), false),

	DAGANNOTH_KINGS("Dagannoth Kings", ids(2265, 2266, 2267), // DAGCAVE_RANGED_BOSS (Supreme), _MAGIC_BOSS (Prime), _MELEE_BOSS (Rex)
		// DAGANNOTH_MEGANOTH_ATTACK_MELEE / _MAGE / _RANGE
		anims(Style.MELEE, 2853, Style.MAGIC, 2854, Style.RANGED, 2855),
		none(), none(), ids(), false),

	PHANTOM_MUSPAH("Phantom Muspah", ids(12077, 12078, 12079, 12080), // MUSPAH, _MELEE, _SOULSPLIT (shielded), _FINAL
		// NPC_MUSPAH_ATTACK_MAGIC_01 9918, _MELEE_01 9920, _RANGED_01 9922
		anims(Style.MAGIC, 9918, Style.MELEE, 9920, Style.RANGED, 9922),
		// PROJECTILE_MUSPAH_ATTACK_MAGIC_01 2327 (and _SMALL 4029), _RANGED_01 2329
		anims(Style.MAGIC, 2327, 4029, Style.RANGED, 2329),
		forms(12077, Style.RANGED, 12078, Style.MELEE),
		ids(2335, 2336), // VFX_MUSPAH_SPIKE_WARNING_01 / _02
		false),

	SARACHNIS("Sarachnis", ids(8713),
		// HOSDUN_SPIDER_BOSS_ATTACK_MELEE 8147, _RANGED 4410; SARACHNIS_RANGEPROJ 1686
		anims(Style.MELEE, 8147, Style.RANGED, 4410),
		anims(Style.RANGED, 1686), none(), ids(), false),

	CHAOS_FANATIC("Chaos Fanatic", ids(6619),
		none(), none(),
		forms(6619, Style.MAGIC), // attacks with magic; its explosives land on tiles
		ids(), true),

	CRAZY_ARCHAEOLOGIST("Crazy Archaeologist", ids(6618),
		none(),
		anims(Style.RANGED, 1259), // CRAZY_ARCHAEOLOGIST_BOOK (the special, _SPECIAL 1260, lands on tiles)
		none(), ids(), true),

	DERANGED_ARCHAEOLOGIST("Deranged Archaeologist", ids(7806), // FOSSIL_CRAZY_ARCHAEOLOGIST
		none(), none(), none(), ids(), true);

	final String displayName;
	final Set<Integer> npcIds;
	final Map<Integer, Style> attackAnimations;
	final Map<Integer, Style> projectiles;
	final Map<Integer, Style> forms;
	final Set<Integer> markedGraphics;
	final boolean markGroundProjectiles;

	Boss(String displayName, Set<Integer> npcIds, Map<Integer, Style> attackAnimations, Map<Integer, Style> projectiles,
		Map<Integer, Style> forms, Set<Integer> markedGraphics, boolean markGroundProjectiles)
	{
		this.displayName = displayName;
		this.npcIds = npcIds;
		this.attackAnimations = attackAnimations;
		this.projectiles = projectiles;
		this.forms = forms;
		this.markedGraphics = markedGraphics;
		this.markGroundProjectiles = markGroundProjectiles;
	}

	static Boss of(int npcId)
	{
		for (Boss b : values())
		{
			if (b.npcIds.contains(npcId))
			{
				return b;
			}
		}
		return null;
	}

	private static Set<Integer> ids(int... ids)
	{
		Set<Integer> s = new HashSet<>();
		for (int i : ids)
		{
			s.add(i);
		}
		return Collections.unmodifiableSet(s);
	}

	private static Map<Integer, Style> none()
	{
		return Collections.emptyMap();
	}

	/** (Style, id, id, ..., Style, id, ...) */
	private static Map<Integer, Style> anims(Object... spec)
	{
		Map<Integer, Style> m = new HashMap<>();
		Style current = null;
		for (Object o : spec)
		{
			if (o instanceof Style)
			{
				current = (Style) o;
			}
			else
			{
				m.put((Integer) o, current);
			}
		}
		return Collections.unmodifiableMap(m);
	}

	/** (npcId, Style, npcId, Style, ...) */
	private static Map<Integer, Style> forms(Object... spec)
	{
		Map<Integer, Style> m = new HashMap<>();
		for (int i = 0; i < spec.length; i += 2)
		{
			m.put((Integer) spec[i], (Style) spec[i + 1]);
		}
		return Collections.unmodifiableMap(m);
	}
}
