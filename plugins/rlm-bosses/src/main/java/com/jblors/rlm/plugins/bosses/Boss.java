package com.jblors.rlm.plugins.bosses;

import static com.jblors.rlm.plugins.bosses.BossDef.npcs;
import static com.jblors.rlm.plugins.bosses.Style.MAGIC;
import static com.jblors.rlm.plugins.bosses.Style.MELEE;
import static com.jblors.rlm.plugins.bosses.Style.RANGED;

/**
 * The bosses. IDs are from RuneLite's game data (gameval names in the comments); the mechanics
 * are from the OSRS wiki as remembered, so each boss is best effort until tested in game.
 */
enum Boss
{
	// ---- group 1
	SCURRIUS("Scurrius", npcs(7221, 7222) // RAT_BOSS_NORMAL, RAT_BOSS_INSTANCE
		.anim(MELEE, 10692, 10693).anim(RANGED, 10694, 10695).anim(MAGIC, 10696, 10697)
		.graphics(2644)), // VFX_RAT_BOSS_FALLING_DEBRIS_01

	JAD("TzTok-Jad / JalTok-Jad", npcs(3127, 7700, 7704, 10623) // TZHAAR_FIGHTCAVE_SWARM_BOSS, INFERNO_JAD(_FINALWAVE), JAD_CHALLENGE_JAD
		// Fight Caves: LORDMAGMUS_ATTACK 2655, _SMASH 2652, _FIRE 2656; Inferno: JALTOKJAD_ATTACK_MELEE/_RANGED/_MAGIC
		.anim(MELEE, 2655, 7590).anim(RANGED, 2652, 7593).anim(MAGIC, 2656, 7592)),

	DAGANNOTH_KINGS("Dagannoth Kings", npcs(2265, 2266, 2267) // DAGCAVE_RANGED/MAGIC/MELEE_BOSS
		.anim(MELEE, 2853).anim(MAGIC, 2854).anim(RANGED, 2855)), // DAGANNOTH_MEGANOTH_ATTACK_*

	PHANTOM_MUSPAH("Phantom Muspah", npcs(12077, 12078, 12079, 12080) // MUSPAH, _MELEE, _SOULSPLIT, _FINAL
		.anim(MAGIC, 9918).anim(MELEE, 9920).anim(RANGED, 9922) // NPC_MUSPAH_ATTACK_*_01
		.projectile(MAGIC, 2327, 4029).projectile(RANGED, 2329) // PROJECTILE_MUSPAH_ATTACK_*
		.form(12077, RANGED).form(12078, MELEE)
		.graphics(2335, 2336)), // VFX_MUSPAH_SPIKE_WARNING_01/_02

	SARACHNIS("Sarachnis", npcs(8713)
		.anim(MELEE, 8147).anim(RANGED, 4410) // HOSDUN_SPIDER_BOSS_ATTACK_*
		.projectile(RANGED, 1686)), // SARACHNIS_RANGEPROJ

	CHAOS_FANATIC("Chaos Fanatic", npcs(6619)
		.form(6619, MAGIC)
		.groundAll()), // its explosives

	CRAZY_ARCHAEOLOGIST("Crazy Archaeologist", npcs(6618)
		.projectile(RANGED, 1259) // CRAZY_ARCHAEOLOGIST_BOOK
		.groundAll()), // the special's books (_BOOK_SPECIAL 1260)

	DERANGED_ARCHAEOLOGIST("Deranged Archaeologist", npcs(7806) // FOSSIL_CRAZY_ARCHAEOLOGIST
		.groundAll()),

	// ---- group 2
	VORKATH("Vorkath", npcs(8061)
		.anim(MELEE, 7951) // DS2_VORKATH_ATTACK_MELEE
		.projectile(RANGED, 1477).projectile(MAGIC, 1479) // VORKATH_RANGED_TRAVEL, _MAGIC_TRAVEL
		.ground(1481, 1482, 2266) // VORKATH_AREA_TRAVEL(_SMALL): the firebomb
		.linger(1483, 25)), // VORKATH_ACID_TRAVEL: acid pools stay for the acid phase

	ZULRAH("Zulrah", npcs(2042, 2043, 2044) // SNAKEBOSS_BOSS_RANGED, _MELEE, _MAGIC
		.form(2042, RANGED).form(2043, MELEE).form(2044, MAGIC)
		.projectile(RANGED, 1044).projectile(MAGIC, 1046) // SNAKEBOSS_ORB, SNAKEBOSS_FIREBALL
		.ground(1045) // SNAKEBOSS_DOUBLE_ORB: venom clouds on their way
		.objects(11700)), // SNAKEBOSS_POISONCLOUD

	ABYSSAL_SIRE("Abyssal Sire", npcs(5886, 5887, 5888, 5890, 5891, 5908) // ABYSSALSIRE_SIRE_*
		.anim(MELEE, 5366, 5369, 5751, 5755) // SIRE_RIGHT_HOOK, _ATTACK_RIGHT_WHIP, _DOUBLE_HOOK, _DOUBLE_WHIPS
		.graphics(1275).ground(1275)), // ABYSSAL_MIASMA_SPOTANIM

	ROYAL_TITANS("Royal Titans", npcs(12596, 14147) // RT_FIRE_QUEEN, RT_ICE_KING
		.dangerNpcs(14152, 14153) // RS_LOC_ATTACK_FIRE_HITBOX, _ICE_HITBOX
		.graphics(3208, 3225)), // ROYAL_TITANS_AREA_ATTACK_ICE, SPOTANIM_PLACEHOLDER_FIRE_QUEEN_LOC_ATTACK

	HESPORI("Hespori", npcs(8583)
		.anim(RANGED, 8224) // HESPORI_ATTACK_RANGED
		.projectile(RANGED, 1639).projectile(MAGIC, 1640)), // HESPORI_RANGE_PROJ, _MAGIC_PROJ

	AMOXLIATL("Amoxliatl", npcs(13685)
		.graphics(2941) // VFX_AMOXLIATL_ICE_FLOOR_ATTACK_01
		.groundAll()),

	HUEYCOATL("The Hueycoatl", npcs(14009, 14013) // HUEY_HEAD, HUEY_HEAD_ENRAGED
		.anim(MELEE, 11669, 11673).anim(RANGED, 11670, 11674).anim(MAGIC, 11671, 11675) // HUEY_(SHIELD_)ATTACK_*
		.projectile(MELEE, 2969).projectile(RANGED, 2972).projectile(MAGIC, 2975) // VFX_HUEY_ATTACK_*_PROJANIM_01
		.graphicsRange(2977, 2992)), // VFX_HUEY_TAIL_SLAM_SHOCKWAVE_*

	MOONS_OF_PERIL("Moons of Peril", npcs(13016, 13017, 13018, 13019) // PMOON_BOSS_BLOOD/BLUE/ECLIPSE_MOON, _ECLIPSE_CLONE
		.form(13016, MELEE).form(13017, MELEE).form(13018, MELEE).form(13019, MELEE)
		.dangerNpcs(13014, 13015, 13027)), // PMOON_BOSS_(SMALL_)THREAT_CIRCLE, PMOON_BOSS_WINTER_STORM

	GALVEK("Galvek", npcs(8094, 8095, 8096, 8097, 8098) // GALVEK_WAVES/FIRE/WATER/WIND/EARTH
		.projectile(RANGED, 1489).projectile(MAGIC, 1490) // GALVEK_RANGED_TRAVEL, _MAGIC_TRAVEL
		.groundAll()), // GALVEK_AREA_TRAVEL and the elemental specials

	BRUTUS("Brutus", npcs(15626) // COWBOSS
		.anim(MELEE, 13783) // COW_BOSS_ATTACK
		.projectile(RANGED, 3591).projectile(MELEE, 3592).projectile(MAGIC, 3593)); // VFX_COWBOSS_HM_*_01

	final String displayName;
	final BossDef def;

	Boss(String displayName, BossDef def)
	{
		this.displayName = displayName;
		this.def = def;
	}

	static Boss of(int npcId)
	{
		for (Boss b : values())
		{
			if (b.def.npcIds.contains(npcId))
			{
				return b;
			}
		}
		return null;
	}
}
