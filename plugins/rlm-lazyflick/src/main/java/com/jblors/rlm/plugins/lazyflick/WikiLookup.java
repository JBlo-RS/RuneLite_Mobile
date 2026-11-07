package com.jblors.rlm.plugins.lazyflick;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The OSRS wiki's monster data (its "infobox_monster" bucket) for one NPC ID: name, attack speed
 * and attack styles. One request per NPC, only for NPCs not in the plugin's database yet.
 */
final class WikiLookup
{
	static final String API = "https://oldschool.runescape.wiki/api.php";
	/** The wiki asks for a User-Agent that says who is calling */
	static final String USER_AGENT = "RuneLite Mobile Lazy Flick plugin (github.com/JBlo-RS/RuneLite_Mobile)";

	private WikiLookup()
	{
	}

	static String query(int npcId)
	{
		return "bucket('infobox_monster').select('page_name','id','attack_speed','attack_style')"
			+ ".where('id','" + npcId + "').run()";
	}

	/** What the wiki says about one NPC ID */
	static final class Result
	{
		String name;
		Integer speed;
		List<String> styles = new ArrayList<>();
	}

	/** Reads the API's answer; null when the wiki has no row for this ID. */
	static Result parse(String json, int npcId)
	{
		JsonElement root = new JsonParser().parse(json);
		if (!root.isJsonObject() || !root.getAsJsonObject().has("bucket"))
		{
			return null;
		}
		JsonArray rows = root.getAsJsonObject().getAsJsonArray("bucket");
		JsonObject row = null;
		for (JsonElement r : rows)
		{
			if (r.isJsonObject() && strings(r.getAsJsonObject().get("id")).contains(Integer.toString(npcId)))
			{
				row = r.getAsJsonObject();
				break;
			}
		}
		if (row == null && rows.size() > 0 && rows.get(0).isJsonObject())
		{
			row = rows.get(0).getAsJsonObject();
		}
		if (row == null)
		{
			return null;
		}
		Result res = new Result();
		List<String> names = strings(row.get("page_name"));
		res.name = names.isEmpty() ? null : names.get(0);
		for (String s : strings(row.get("attack_speed")))
		{
			try
			{
				int v = (int) Math.round(Double.parseDouble(s.trim()));
				if (v >= 1 && v <= 20)
				{
					res.speed = v;
					break;
				}
			}
			catch (NumberFormatException ignored)
			{
			}
		}
		res.styles = strings(row.get("attack_style"));
		return res;
	}

	/**
	 * The prayer against an NPC's attack styles: only when they all need the same one (all melee
	 * types, all ranged, or all magic); null for mixed or unusual styles.
	 */
	static Style style(List<String> styles)
	{
		Style found = null;
		for (String raw : styles)
		{
			String s = raw.toLowerCase(Locale.ROOT).trim();
			Style st;
			if (s.equals("stab") || s.equals("slash") || s.equals("crush") || s.equals("melee"))
			{
				st = Style.MELEE;
			}
			else if (s.startsWith("ranged"))
			{
				st = Style.RANGED;
			}
			else if (s.startsWith("magic"))
			{
				st = Style.MAGIC;
			}
			else if (s.isEmpty() || s.equals("none"))
			{
				continue;
			}
			else
			{
				return null; // dragonfire, typeless, ...: no single prayer
			}
			if (found != null && found != st)
			{
				return null;
			}
			found = st;
		}
		return found;
	}

	/** A JSON value as strings: an array's items, or the value itself. */
	private static List<String> strings(JsonElement e)
	{
		List<String> out = new ArrayList<>();
		if (e == null || e.isJsonNull())
		{
			return out;
		}
		if (e.isJsonArray())
		{
			for (JsonElement x : e.getAsJsonArray())
			{
				if (x.isJsonPrimitive())
				{
					out.add(x.getAsString());
				}
			}
		}
		else if (e.isJsonPrimitive())
		{
			out.add(e.getAsString());
		}
		return out;
	}
}
