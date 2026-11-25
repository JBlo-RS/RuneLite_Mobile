package com.starhopper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fetches the star list from the Star Miners crowdsource endpoint.
 *
 * The exact JSON schema isn't published, so parsing is deliberately tolerant:
 * it accepts a top-level array or an object wrapping one, and several common
 * field names for world, location, tier and time. Turn on "Log raw response"
 * if stars don't show up, and adjust the key lists below to match.
 */
@Singleton
public class StarMinersClient
{
	private static final Logger log = LoggerFactory.getLogger(StarMinersClient.class);

	private static final String[] WORLD_KEYS = {"world", "worldId", "world_id", "w"};
	private static final String[] LOCATION_KEYS = {"location", "locationName", "location_name", "loc", "area", "region", "locationKey"};
	private static final String[] TIER_KEYS = {"tier", "size", "starTier", "star_tier", "t"};
	private static final String[] TIME_KEYS = {"calledAt", "called_at", "updatedAt", "updated_at", "time", "timestamp",
		"foundAt", "found_at", "reportedAt", "lastUpdated", "minTime"};
	private static final String[] ARRAY_KEYS = {"stars", "data", "results", "items", "crowdsource"};

	private final OkHttpClient http;
	private final Gson gson;

	@Inject
	StarMinersClient(OkHttpClient http, Gson gson)
	{
		this.http = http;
		this.gson = gson;
	}

	public List<StarReport> fetch(String endpoint, String key, boolean debug) throws IOException
	{
		Request.Builder rb = new Request.Builder()
			.url(endpoint.trim())
			.header("Accept", "application/json")
			.get();
		if (key != null && !key.trim().isEmpty())
		{
			rb.header("Authorization", key.trim());
		}

		try (Response response = http.newCall(rb.build()).execute())
		{
			if (!response.isSuccessful())
			{
				if (response.code() == 401 || response.code() == 403)
				{
					throw new IOException("Key rejected (HTTP " + response.code() + "). Check your Star Miners key.");
				}
				throw new IOException("Star Miners server returned HTTP " + response.code());
			}
			ResponseBody body = response.body();
			String text = body == null ? "" : body.string();
			if (debug)
			{
				log.info("Star Hopper raw response ({} chars): {}", text.length(),
					text.length() > 1000 ? text.substring(0, 1000) + "..." : text);
			}
			return parse(text);
		}
	}

	List<StarReport> parse(String text)
	{
		if (text == null || text.trim().isEmpty())
		{
			return Collections.emptyList();
		}
		JsonElement root = gson.fromJson(text, JsonElement.class);
		JsonArray array = findArray(root);
		List<StarReport> out = new ArrayList<>();
		if (array == null)
		{
			return out;
		}
		for (JsonElement el : array)
		{
			if (!el.isJsonObject())
			{
				continue;
			}
			JsonObject o = el.getAsJsonObject();
			int world = parseInt(first(o, WORLD_KEYS));
			if (world <= 0)
			{
				continue;
			}
			String location = parseLocation(first(o, LOCATION_KEYS));
			int tier = parseInt(first(o, TIER_KEYS));
			long time = parseTime(first(o, TIME_KEYS));
			out.add(new StarReport(world, location, tier, time));
		}
		return out;
	}

	private static JsonArray findArray(JsonElement root)
	{
		if (root == null || root.isJsonNull())
		{
			return null;
		}
		if (root.isJsonArray())
		{
			return root.getAsJsonArray();
		}
		if (root.isJsonObject())
		{
			JsonObject o = root.getAsJsonObject();
			for (String k : ARRAY_KEYS)
			{
				if (o.has(k) && o.get(k).isJsonArray())
				{
					return o.getAsJsonArray(k);
				}
			}
			for (Map.Entry<String, JsonElement> e : o.entrySet())
			{
				if (e.getValue().isJsonArray())
				{
					return e.getValue().getAsJsonArray();
				}
			}
		}
		return null;
	}

	private static JsonElement first(JsonObject o, String[] keys)
	{
		for (String k : keys)
		{
			if (o.has(k) && !o.get(k).isJsonNull())
			{
				return o.get(k);
			}
		}
		return null;
	}

	private static String parseLocation(JsonElement e)
	{
		if (e == null)
		{
			return "Unknown";
		}
		if (e.isJsonObject())
		{
			JsonObject o = e.getAsJsonObject();
			for (String k : new String[]{"name", "location", "description", "shortName"})
			{
				if (o.has(k) && o.get(k).isJsonPrimitive())
				{
					return o.get(k).getAsString();
				}
			}
			return o.toString();
		}
		return e.isJsonPrimitive() ? e.getAsString() : e.toString();
	}

	private static int parseInt(JsonElement e)
	{
		if (e == null || !e.isJsonPrimitive())
		{
			return 0;
		}
		JsonPrimitive p = e.getAsJsonPrimitive();
		if (p.isNumber())
		{
			return p.getAsInt();
		}
		String digits = p.getAsString().replaceAll("[^0-9]", "");
		if (digits.isEmpty())
		{
			return 0;
		}
		try
		{
			return Integer.parseInt(digits);
		}
		catch (NumberFormatException ex)
		{
			return 0;
		}
	}

	private static long parseTime(JsonElement e)
	{
		if (e == null || !e.isJsonPrimitive())
		{
			return 0;
		}
		JsonPrimitive p = e.getAsJsonPrimitive();
		if (p.isNumber())
		{
			return toMillis(p.getAsLong());
		}
		String s = p.getAsString().trim();
		try
		{
			return toMillis(Long.parseLong(s));
		}
		catch (NumberFormatException ignored)
		{
		}
		try
		{
			return Instant.parse(s).toEpochMilli();
		}
		catch (Exception ignored)
		{
		}
		try
		{
			return OffsetDateTime.parse(s).toInstant().toEpochMilli();
		}
		catch (Exception ignored)
		{
		}
		return 0;
	}

	/** Treat small numbers as epoch seconds, large ones as epoch millis. */
	private static long toMillis(long v)
	{
		return v < 100_000_000_000L ? v * 1000L : v;
	}
}
