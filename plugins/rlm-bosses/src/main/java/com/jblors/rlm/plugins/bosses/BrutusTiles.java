package com.jblors.rlm.plugins.bosses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

/**
 * Brutus' stomp and charge tiles, predicted when the attack's animation starts (his stomp has a
 * 4-tick dodge window per slam, up to 3 slams). The geometry is from the project owner's own
 * Brutus plugin (BrutusPlugin.computeStompTiles / getStompDirection / computeChargePath).
 *
 * <p>Brutus is a 3x3 NPC; getWorldLocation() is his south-west tile. Orientation is in JAU:
 * 0 = south, 512 = west, 1024 = north, 1536 = east.
 */
final class BrutusTiles
{
	static final int ANIM_ATTACK = 13783, ANIM_CHARGE = 13778, ANIM_STOMP = 13785;
	/** Up to three slams, each with a 4-tick dodge window */
	static final int STOMP_SHOW_TICKS = 14;
	static final int CHARGE_SHOW_TICKS = 3;

	private BrutusTiles()
	{
	}

	private static int size(NPC brutus)
	{
		return brutus.getComposition() != null ? Math.max(1, brutus.getComposition().getSize()) : 3;
	}

	/**
	 * Cardinal slam: his front face plus the row just outside it (size x 2 tiles). Diagonal slam:
	 * a 5-tile L hugging the outside of his nearest body corner. The game slams diagonally
	 * whenever his facing rounds to a diagonal, so this follows the direction to the player.
	 */
	static List<WorldPoint> stomp(NPC brutus, Player player)
	{
		if (brutus == null || brutus.getWorldLocation() == null)
		{
			return Collections.emptyList();
		}
		int size = size(brutus);
		WorldPoint b = brutus.getWorldLocation();
		int bx = b.getX(), by = b.getY(), plane = b.getPlane();
		int[] dir = stompDirection(brutus, player, bx, by, size);
		int dx = dir[0], dy = dir[1];
		List<WorldPoint> tiles = new ArrayList<>();
		if (dx == 0 || dy == 0)
		{
			if (dy != 0)
			{
				int faceY = dy > 0 ? by + size - 1 : by;
				for (int x = bx; x < bx + size; x++)
				{
					tiles.add(new WorldPoint(x, faceY, plane));
					tiles.add(new WorldPoint(x, faceY + dy, plane));
				}
			}
			else
			{
				int faceX = dx > 0 ? bx + size - 1 : bx;
				for (int y = by; y < by + size; y++)
				{
					tiles.add(new WorldPoint(faceX, y, plane));
					tiles.add(new WorldPoint(faceX + dx, y, plane));
				}
			}
		}
		else
		{
			int cornerX = dx > 0 ? bx + size - 1 : bx;
			int cornerY = dy > 0 ? by + size - 1 : by;
			int outX = cornerX + dx, outY = cornerY + dy;
			tiles.add(new WorldPoint(outX, outY, plane));
			for (int i = 0; i < 2; i++)
			{
				tiles.add(new WorldPoint(cornerX - dx * i, outY, plane));
				tiles.add(new WorldPoint(outX, cornerY - dy * i, plane));
			}
		}
		return tiles;
	}

	/**
	 * {dx, dy} in -1..1: from Brutus' centre to the player's tile (where he turns to face, so a
	 * mid-turn model doesn't skew it), else his orientation, rounded to one of 8 directions.
	 */
	static int[] stompDirection(NPC brutus, Player player, int bx, int by, int size)
	{
		int orientation = brutus.getOrientation();
		if (player != null && player.getWorldLocation() != null)
		{
			WorldPoint p = player.getWorldLocation();
			int ddx = 2 * p.getX() - (2 * bx + size - 1); // doubled: an even-sized NPC's centre stays whole
			int ddy = 2 * p.getY() - (2 * by + size - 1);
			if (ddx != 0 || ddy != 0)
			{
				orientation = (int) Math.round(Math.atan2(-ddx, -ddy) * 1024 / Math.PI) & 2047;
			}
		}
		switch (((orientation + 128) / 256) % 8) // 0=S 1=SW 2=W 3=NW 4=N 5=NE 6=E 7=SE
		{
			case 0:
				return new int[]{0, -1};
			case 1:
				return new int[]{-1, -1};
			case 2:
				return new int[]{-1, 0};
			case 3:
				return new int[]{-1, 1};
			case 4:
				return new int[]{0, 1};
			case 5:
				return new int[]{1, 1};
			case 6:
				return new int[]{1, 0};
			default:
				return new int[]{1, -1};
		}
	}

	/** The charge: 3 tiles wide, 4 deep, in front of him along his (cardinal) facing. */
	static List<WorldPoint> charge(NPC brutus)
	{
		if (brutus == null || brutus.getWorldLocation() == null)
		{
			return Collections.emptyList();
		}
		int o = brutus.getOrientation();
		WorldPoint pos = brutus.getWorldLocation();
		int dx = 0, dy = 0;
		if (o >= 1280 && o < 1792)
		{
			dx = 1;
		}
		else if (o >= 256 && o < 768)
		{
			dx = -1;
		}
		else if (o >= 768 && o < 1280)
		{
			dy = 1;
		}
		else
		{
			dy = -1;
		}
		int perpX = dy, perpY = -dx;
		int size = size(brutus);
		int startX = pos.getX() + (dy != 0 ? size / 2 : 0);
		int startY = pos.getY() + (dx != 0 ? size / 2 : 0);
		int startI = (dx > 0 || dy > 0) ? size : 1;
		List<WorldPoint> path = new ArrayList<>();
		for (int i = startI; i < startI + 4; i++)
		{
			int cx = startX + dx * i, cy = startY + dy * i;
			path.add(new WorldPoint(cx - perpX, cy - perpY, pos.getPlane()));
			path.add(new WorldPoint(cx, cy, pos.getPlane()));
			path.add(new WorldPoint(cx + perpX, cy + perpY, pos.getPlane()));
		}
		return path;
	}
}
