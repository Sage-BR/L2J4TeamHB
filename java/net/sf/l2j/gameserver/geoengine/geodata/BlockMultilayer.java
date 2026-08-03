package net.sf.l2j.gameserver.geoengine.geodata;

import java.nio.ByteBuffer;

/**
 * Multilayer block: 64 cells (8x8), each with variable number of layers.
 * Lightweight: reads from the MappedByteBuffer on demand instead of
 * pre-decoding into a byte[] array. Saves hundreds of MB across all regions.
 *
 * Layers are stored from highest to lowest. Buffer layout per block: [byte:
 * type=2] for each cell (0..63): [byte: layerCount] for each layer: [short:
 * (height << 1) | NSWE]
 */
public class BlockMultilayer extends ABlock
{
	private final ByteBuffer _geo;

	/** Absolute position of this block's type byte in the MappedByteBuffer. */
	private final int _blockStart;

	public BlockMultilayer(ByteBuffer geo, int blockStart)
	{
		_geo = geo;
		_blockStart = blockStart;
	}

	/**
	 * @return absolute position of cell (cellX, cellY) data, pointing to the
	 *         layerCount byte.
	 */
	private int findCellStart(int cellX, int cellY)
	{
		int addr = _blockStart + 1; // skip type byte
		int cellsToSkip = cellX * GeoStructure.BLOCK_CELLS_Y + cellY;
		for (int i = 0; i < cellsToSkip; i++)
		{
			int layers = _geo.get(addr++) & 0xFF;
			addr += layers * 2; // each layer = 1 short
		}
		return addr;
	}

	/**
	 * Decode raw short (height << 1 | NSWE) into height with sign fix.
	 */
	private static short decodeHeight(int raw)
	{
		int height = (short) (raw & 0xFFF0);
		height >>= 1;
		return (short) height;
	}

	@Override
	public boolean hasGeoPos()
	{
		return true;
	}

	/**
	 * Sentinel height for "no ground / empty layer" in L2J geodata (0xC000
	 * encoded). Layers at this height are void and must be skipped, otherwise
	 * actors get snapped far below the terrain and disappear from the known
	 * list.
	 */
	private static final short VOID_HEIGHT = -16384;

	@Override
	public short getHeightNearest(int geoX, int geoY, int worldZ)
	{
		int cellX = geoX % GeoStructure.BLOCK_CELLS_X;
		int cellY = geoY % GeoStructure.BLOCK_CELLS_Y;
		int addr = findCellStart(cellX, cellY);
		int layers = _geo.get(addr++) & 0xFF;
		if (layers <= 0)
		{
			return (short) worldZ;
		}

		// Find the nearest non-void layer. Void layers (-16384) are treated as
		// "no ground": if every layer is void, behave like BlockNull.
		int nearestAddr = -1;
		int nearestDist = Integer.MAX_VALUE;

		for (int i = 0; i < layers; i++)
		{
			int layerAddr = addr + i * 2;
			short h = decodeHeight(_geo.getShort(layerAddr) & 0xFFFF);
			if (h == VOID_HEIGHT)
			{
				continue;
			}
			int dist = Math.abs(h - worldZ);
			if (dist < nearestDist)
			{
				nearestDist = dist;
				nearestAddr = layerAddr;
			}
		}

		return (nearestAddr == -1) ? (short) worldZ
		        : decodeHeight(_geo.getShort(nearestAddr) & 0xFFFF);
	}

	@Override
	public byte getNsweNearest(int geoX, int geoY, int worldZ)
	{
		int cellX = geoX % GeoStructure.BLOCK_CELLS_X;
		int cellY = geoY % GeoStructure.BLOCK_CELLS_Y;
		int addr = findCellStart(cellX, cellY);
		int layers = _geo.get(addr++) & 0xFF;
		if (layers <= 0)
		{
			return GeoStructure.CELL_FLAG_ALL;
		}

		int nearestAddr = addr;
		int nearestDist = Math.abs(decodeHeight(_geo.getShort(addr) & 0xFFFF)
		        - worldZ);

		for (int i = 1; i < layers; i++)
		{
			int layerAddr = addr + i * 2;
			int dist = Math.abs(decodeHeight(_geo.getShort(layerAddr) & 0xFFFF)
			        - worldZ);
			if (dist < nearestDist)
			{
				nearestDist = dist;
				nearestAddr = layerAddr;
			}
		}
		int raw = _geo.getShort(nearestAddr) & 0xFFFF;
		return (byte) (raw & 0x0F);
	}

	@Override
	public short getHeightAbove(int geoX, int geoY, int worldZ)
	{
		int cellX = geoX % GeoStructure.BLOCK_CELLS_X;
		int cellY = geoY % GeoStructure.BLOCK_CELLS_Y;
		int addr = findCellStart(cellX, cellY);
		int layers = _geo.get(addr++) & 0xFF;

		// Seek to last layer (highest stored first = at addr + (layers-1)*2)
		addr += (layers - 1) * 2;

		while (layers > 0)
		{
			int raw = _geo.getShort(addr) & 0xFFFF;
			short h = decodeHeight(raw);
			if (h > worldZ)
			{
				return h;
			}
			addr -= 2;
			layers--;
		}
		return Short.MAX_VALUE;
	}

	/**
	 * @return the number of layers at (cellX, cellY)
	 */
	public int getLayerCount(int cellX, int cellY)
	{
		int addr = findCellStart(cellX, cellY);
		return _geo.get(addr) & 0xFF;
	}

	/**
	 * @return height of the layer at index for cell (cellX, cellY) — no array
	 *         allocation.
	 */
	public short getLayerHeight(int cellX, int cellY, int layerIndex)
	{
		int addr = findCellStart(cellX, cellY);
		int layers = _geo.get(addr++) & 0xFF;
		if (layerIndex < 0 || layerIndex >= layers)
		{
			return 0;
		}
		int raw = _geo.getShort(addr + layerIndex * 2) & 0xFFFF;
		return decodeHeight(raw);
	}

	/**
	 * @return NSWE of the layer at index for cell (cellX, cellY) — no array
	 *         allocation.
	 */
	public byte getLayerNSWE(int cellX, int cellY, int layerIndex)
	{
		int addr = findCellStart(cellX, cellY);
		int layers = _geo.get(addr++) & 0xFF;
		if (layerIndex < 0 || layerIndex >= layers)
		{
			return 0;
		}
		int raw = _geo.getShort(addr + layerIndex * 2) & 0xFFFF;
		return (byte) (raw & 0x0F);
	}

	/**
	 * Complete movement check without allocating temporary arrays. Reads raw
	 * shorts from the MappedByteBuffer on demand.
	 *
	 * @return height to move to, or {@link Double#MIN_VALUE} if blocked.
	 */
	public double checkMove(int cellX, int cellY, int z, int tx, int ty)
	{
		int addr = findCellStart(cellX, cellY);
		int layers = _geo.get(addr++) & 0xFF;
		if (layers <= 0)
		{
			return z;
		}

		// Find nearest layer without allocating arrays, reading from buffer
		int nearestAddr = addr;
		int nearestDist = Math.abs(decodeHeight(_geo.getShort(addr) & 0xFFFF)
		        - z);

		for (int i = 1; i < layers; i++)
		{
			int layerAddr = addr + i * 2;
			int h = decodeHeight(_geo.getShort(layerAddr) & 0xFFFF);
			int dist = Math.abs(h - z);
			if (dist < nearestDist)
			{
				nearestDist = dist;
				nearestAddr = layerAddr;
			}
		}

		int raw = _geo.getShort(nearestAddr) & 0xFFFF;
		byte nearestNSWE = (byte) (raw & 0x0F);
		short nearestHeight = decodeHeight(raw);

		int nearestIdx = (nearestAddr - addr) / 2;

		if (checkNSWE((short) (nearestNSWE & 0xFF), tx, ty))
		{
			return nearestHeight;
		}

		// Ramp-up: try layers above (lower index = higher layer stored first)
		for (int i = nearestIdx - 1; i >= 0; i--)
		{
			int layerAddr = addr + i * 2;
			int r = _geo.getShort(layerAddr) & 0xFFFF;
			short h = decodeHeight(r);
			if (h > nearestHeight
			        && checkNSWE((short) ((byte) (r & 0x0F) & 0xFF), tx, ty))
			{
				return h;
			}
		}

		return Double.MIN_VALUE;
	}

	/**
	 * Simplified NSWE check using only direction deltas.
	 */
	private static boolean checkNSWE(short NSWE, int tx, int ty)
	{
		if (NSWE == 15)
		{
			return true;
		}
		if ((tx > 0 && (NSWE & 1) == 0) || (tx < 0 && (NSWE & 2) == 0))
		{
			return false;
		}
		if ((ty > 0 && (NSWE & 4) == 0) || (ty < 0 && (NSWE & 8) == 0))
		{
			return false;
		}
		return true;
	}

	/**
	 * @return array of heights for all layers at (cellX, cellY)
	 */
	public short[] getHeights(int cellX, int cellY)
	{
		int layers = getLayerCount(cellX, cellY);
		short[] heights = new short[layers];
		for (int i = 0; i < layers; i++)
		{
			heights[i] = getLayerHeight(cellX, cellY, i);
		}
		return heights;
	}

	/**
	 * @return array of NSWE values for all layers at (cellX, cellY)
	 */
	public byte[] getNSWEs(int cellX, int cellY)
	{
		int layers = getLayerCount(cellX, cellY);
		byte[] nswes = new byte[layers];
		for (int i = 0; i < layers; i++)
		{
			nswes[i] = getLayerNSWE(cellX, cellY, i);
		}
		return nswes;
	}

	@Override
	public short getHeightBelow(int geoX, int geoY, int worldZ)
	{
		int cellX = geoX % GeoStructure.BLOCK_CELLS_X;
		int cellY = geoY % GeoStructure.BLOCK_CELLS_Y;
		int addr = findCellStart(cellX, cellY);
		int layers = _geo.get(addr++) & 0xFF;
		if (layers <= 0)
		{
			return (short) worldZ;
		}

		// Layers stored highest to lowest; find highest layer <= worldZ
		for (int i = 0; i < layers; i++)
		{
			int layerAddr = addr + i * 2;
			short h = decodeHeight(_geo.getShort(layerAddr) & 0xFFFF);
			if (h <= worldZ)
			{
				return h;
			}
		}
		return (short) worldZ;
	}

	@Override
	public byte getNsweBelow(int geoX, int geoY, int worldZ)
	{
		int cellX = geoX % GeoStructure.BLOCK_CELLS_X;
		int cellY = geoY % GeoStructure.BLOCK_CELLS_Y;
		int addr = findCellStart(cellX, cellY);
		int layers = _geo.get(addr++) & 0xFF;
		if (layers <= 0)
		{
			return GeoStructure.CELL_FLAG_ALL;
		}

		// Same logic as getHeightBelow — returns NSWE of the SAME layer.
		// This guarantees that getNsweBelow and getHeightBelow agree.
		for (int i = 0; i < layers; i++)
		{
			int layerAddr = addr + i * 2;
			short h = decodeHeight(_geo.getShort(layerAddr) & 0xFFFF);
			if (h <= worldZ)
			{
				int raw = _geo.getShort(layerAddr) & 0xFFFF;
				return (byte) (raw & 0x0F);
			}
		}
		return GeoStructure.CELL_FLAG_ALL;
	}
}
