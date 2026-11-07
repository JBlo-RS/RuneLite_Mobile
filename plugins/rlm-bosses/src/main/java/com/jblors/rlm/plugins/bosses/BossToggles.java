package com.jblors.rlm.plugins.bosses;

/**
 * Whether each boss is turned on. Not a method of BossHelperConfig: RuneLite's config proxy only
 * serves @ConfigItem methods (a default method with a parameter returns null there).
 */
final class BossToggles
{
	private BossToggles()
	{
	}

	static boolean enabled(BossHelperConfig config, Boss boss)
	{
		switch (boss)
		{
			case SCURRIUS:
				return config.scurrius();
			case JAD:
				return config.jad();
			case DAGANNOTH_KINGS:
				return config.dagannothKings();
			case PHANTOM_MUSPAH:
				return config.phantomMuspah();
			case SARACHNIS:
				return config.sarachnis();
			case CHAOS_FANATIC:
				return config.chaosFanatic();
			case CRAZY_ARCHAEOLOGIST:
				return config.crazyArchaeologist();
			case DERANGED_ARCHAEOLOGIST:
				return config.derangedArchaeologist();
			case VORKATH:
				return config.vorkath();
			case ZULRAH:
				return config.zulrah();
			case ABYSSAL_SIRE:
				return config.abyssalSire();
			case ROYAL_TITANS:
				return config.royalTitans();
			case HESPORI:
				return config.hespori();
			case AMOXLIATL:
				return config.amoxliatl();
			case HUEYCOATL:
				return config.hueycoatl();
			case MOONS_OF_PERIL:
				return config.moonsOfPeril();
			case GALVEK:
				return config.galvek();
			case BRUTUS:
				return config.brutus();
			default:
				return false;
		}
	}
}
