package com.starhopper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Where the Star Miners key comes from: the key saved in the settings (Star Hopper's panel has a
 * box for it), else a key built into the jar as the resource com/starhopper/starminers.key. That
 * file is in .gitignore so a key never goes into the repository; put one there to build a
 * personal jar that works without typing the key.
 */
final class StarMinersKey
{
	static final String BUILT_IN = load();

	private StarMinersKey()
	{
	}

	private static String load()
	{
		try (InputStream in = StarMinersKey.class.getResourceAsStream("starminers.key"))
		{
			return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
		}
		catch (Exception e)
		{
			return "";
		}
	}

	static boolean set(String s)
	{
		return s != null && !s.trim().isEmpty();
	}

	/** The settings' key if one is set, else the built-in one ("" if neither). */
	static String effective(String fromSettings)
	{
		return set(fromSettings) ? fromSettings.trim() : BUILT_IN;
	}

	/** Where the key in use came from, for the panel and the log */
	static String source(String fromSettings)
	{
		return set(fromSettings) ? "saved" : set(BUILT_IN) ? "built in" : "none";
	}
}
