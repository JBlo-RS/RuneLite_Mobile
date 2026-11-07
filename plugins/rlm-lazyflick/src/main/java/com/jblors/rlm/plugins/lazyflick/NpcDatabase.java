package com.jblors.rlm.plugins.lazyflick;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The plugin's own database of NPCs, built up as you fight and from the wiki: one entry per NPC
 * ID, kept as JSON in RuneLite's folder (~/.runelite/rlm-lazyflick-npcs.json).
 */
final class NpcDatabase
{
	static final class Entry
	{
		/** Name, from the wiki */
		String name;
		/** Ticks between attacks, and where that came from: "seen" (timed in fights) or "wiki" */
		Integer speed;
		String speedFrom;
		/** The wiki's attack style when it has only one (MELEE/RANGED/MAGIC), else null */
		String wikiStyle;
		/** The wiki was asked (whatever it answered): it isn't asked again */
		boolean wikiChecked;
		/** Animations seen as attacks */
		Set<Integer> attacks = ConcurrentHashMap.newKeySet();
		/** The protection prayer that was on when an attack landed, per attack animation, and the latest */
		Map<Integer, String> prayed = new ConcurrentHashMap<>();
		String prayedLast;
	}

	private final Map<Integer, Entry> entries = new ConcurrentHashMap<>();
	private final File file;
	private final Gson gson;
	private volatile boolean dirty;

	NpcDatabase(File file, Gson gson)
	{
		this.file = file;
		this.gson = gson;
	}

	Entry get(int id)
	{
		return entries.get(id);
	}

	Entry entry(int id)
	{
		return entries.computeIfAbsent(id, k -> new Entry());
	}

	int size()
	{
		return entries.size();
	}

	void changed()
	{
		dirty = true;
	}

	void clear()
	{
		entries.clear();
		dirty = true;
	}

	void load()
	{
		if (file == null || !file.exists())
		{
			return;
		}
		try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
		{
			Map<Integer, Entry> m = gson.fromJson(r, new TypeToken<HashMap<Integer, Entry>>()
			{
			}.getType());
			if (m != null)
			{
				for (Map.Entry<Integer, Entry> e : m.entrySet())
				{
					Entry v = e.getValue();
					if (v == null)
					{
						continue;
					}
					// Gson makes plain collections (or null): make them safe to share between threads
					Set<Integer> attacks = ConcurrentHashMap.newKeySet();
					if (v.attacks != null)
					{
						attacks.addAll(v.attacks);
					}
					v.attacks = attacks;
					v.prayed = v.prayed == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(v.prayed);
					entries.put(e.getKey(), v);
				}
			}
		}
		catch (IOException | RuntimeException ex)
		{
			System.out.println("rlm-lazyflick: couldn't read " + file + ": " + ex);
		}
	}

	/** Writes the file if anything changed (to a temporary file first, so it's never half written). */
	void saveIfChanged()
	{
		if (!dirty || file == null)
		{
			return;
		}
		dirty = false;
		try
		{
			File tmp = new File(file.getPath() + ".tmp");
			try (Writer w = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8))
			{
				gson.toJson(new HashMap<>(entries), w);
			}
			Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException | RuntimeException ex)
		{
			System.out.println("rlm-lazyflick: couldn't save " + file + ": " + ex);
		}
	}
}
