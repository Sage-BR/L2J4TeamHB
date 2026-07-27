package net.sf.l2j.gameserver.geoengine.geodata;

/**
 * Multilayer block: 64 cells (8x8), each with variable number of layers.
 * Data is pre-decoded in the constructor to avoid repeated ByteBuffer reads
 * and to preserve the sign bit for negative Z values.
 * 
 * Buffer layout per cell:
 *   [byte: layerCount]
 *   for each layer:
 *     [byte: NSWE] [short: height] (3 bytes per layer)
 */
public class BlockMultilayer extends ABlock
{
	private final byte[] _buffer;
	/** Pre-computed start position of each cell in _buffer for O(1) lookup. */
	private final int[] _cellStartPos;
	
	public BlockMultilayer(java.nio.ByteBuffer bb)
	{
		// Temporary buffer to accumulate decoded data
		byte[] temp = new byte[GeoStructure.BLOCK_CELLS * 3 * 126]; // max 125 layers + 1 count byte per cell
		int pos = 0;
		_cellStartPos = new int[GeoStructure.BLOCK_CELLS];
		
		for (int cell = 0; cell < GeoStructure.BLOCK_CELLS; cell++)
		{
			_cellStartPos[cell] = pos;
			int layers = bb.get() & 0xFF;
			
			if (layers <= 0 || layers > 125)
			{
				// Corrupted data: treat as flat with height 0 and ALL NSWE
				temp[pos++] = 1; // layer count
				temp[pos++] = GeoStructure.CELL_FLAG_ALL;
				temp[pos++] = 0;
				temp[pos++] = 0;
				continue;
			}
			
			temp[pos++] = (byte)layers;
			
			for (int layer = 0; layer < layers; layer++)
			{
				int raw = bb.getShort() & 0xFFFF;
				
				// NSWE: lowest 4 bits
				temp[pos++] = (byte)(raw & 0x0F);
				
				// Height: extract bits, cast to short to preserve sign, then shift
				int height = (short)(raw & 0xFFF0);
				height >>= 1; // arithmetic shift preserves sign
				
				temp[pos++] = (byte)(height & 0xFF);
				temp[pos++] = (byte)((height >> 8) & 0xFF);
			}
		}
		
		// Copy to exact-sized buffer
		_buffer = new byte[pos];
		System.arraycopy(temp, 0, _buffer, 0, pos);
	}
	
	/**
	 * @param cellX 0-7
	 * @param cellY 0-7
	 * @return start position of cell data in _buffer (O(1) lookup via _cellStartPos)
	 */
	private int getCellStartPos(int cellX, int cellY)
	{
		return _cellStartPos[cellX * GeoStructure.BLOCK_CELLS_Y + cellY];
	}
	
	/**
	 * Returns the layer index nearest to worldZ for cell (cellX, cellY).
	 */
	private int findNearestLayerIndex(int cellX, int cellY, int worldZ)
	{
		int pos = getCellStartPos(cellX, cellY);
		int layers = _buffer[pos++] & 0xFF;
		int nearest = 0;
		int bestDist = Math.abs((int)getShort(pos + 1) - worldZ);
		
		for (int i = 1; i < layers; i++)
		{
			int layerPos = pos + i * 3;
			int dist = Math.abs((int)getShort(layerPos + 1) - worldZ);
			if (dist < bestDist)
			{
				bestDist = dist;
				nearest = i;
			}
		}
		return nearest;
	}
	
	private short getShort(int index)
	{
		int low = _buffer[index] & 0xFF;
		int high = _buffer[index + 1] & 0xFF;
		return (short)((high << 8) | low);
	}
	
	@Override
	public boolean hasGeoPos()
	{
		return true;
	}
	
	@Override
	public short getHeightNearest(int geoX, int geoY, int worldZ)
	{
		int cellX = geoX % GeoStructure.BLOCK_CELLS_X;
		int cellY = geoY % GeoStructure.BLOCK_CELLS_Y;
		int idx = findNearestLayerIndex(cellX, cellY, worldZ);
		return getLayerHeight(cellX, cellY, idx);
	}
	
	@Override
	public byte getNsweNearest(int geoX, int geoY, int worldZ)
	{
		int cellX = geoX % GeoStructure.BLOCK_CELLS_X;
		int cellY = geoY % GeoStructure.BLOCK_CELLS_Y;
		int idx = findNearestLayerIndex(cellX, cellY, worldZ);
		return getLayerNSWE(cellX, cellY, idx);
	}
	
	@Override
	public short getHeightAbove(int geoX, int geoY, int worldZ)
	{
		int cellX = geoX % GeoStructure.BLOCK_CELLS_X;
		int cellY = geoY % GeoStructure.BLOCK_CELLS_Y;
		int pos = getCellStartPos(cellX, cellY);
		int layers = _buffer[pos++] & 0xFF;
		
		// Layers are stored from highest to lowest.
		// Seek to the last layer (highest stored first).
		pos += (layers - 1) * 3;
		
		while (layers > 0)
		{
			short height = getShort(pos + 1);
			if (height > worldZ)
				return height;
			pos -= 3;
			layers--;
		}
		return Short.MAX_VALUE;
	}
	
	/**
	 * @return the number of layers at (cellX, cellY)
	 */
	public int getLayerCount(int cellX, int cellY)
	{
		int pos = getCellStartPos(cellX, cellY);
		return _buffer[pos] & 0xFF;
	}
	
	/**
	 * @return height of the layer at index for cell (cellX, cellY) — no array allocation.
	 */
	public short getLayerHeight(int cellX, int cellY, int layerIndex)
	{
		int pos = getCellStartPos(cellX, cellY);
		int layers = _buffer[pos++] & 0xFF;
		if (layerIndex < 0 || layerIndex >= layers)
			return 0;
		return getShort(pos + 1 + layerIndex * 3);
	}
	
	/**
	 * @return NSWE of the layer at index for cell (cellX, cellY) — no array allocation.
	 */
	public byte getLayerNSWE(int cellX, int cellY, int layerIndex)
	{
		int pos = getCellStartPos(cellX, cellY);
		int layers = _buffer[pos++] & 0xFF;
		if (layerIndex < 0 || layerIndex >= layers)
			return 0;
		return _buffer[pos + layerIndex * 3];
	}
	
	/**
	 * Complete movement check without allocating temporary arrays.
	 * @return height to move to, or {@link Double#MIN_VALUE} if blocked.
	 */
	public double checkMove(int cellX, int cellY, int z, int tx, int ty)
	{
		int pos = getCellStartPos(cellX, cellY);
		int layers = _buffer[pos++] & 0xFF;
		if (layers <= 0)
			return z;
		
		// Find nearest layer without allocating arrays
		int nearestPos = pos;
		int nearestDist = Math.abs((int)getShort(pos + 1) - z);
		
		for (int i = 1; i < layers; i++)
		{
			int layerPos = pos + i * 3;
			int dist = Math.abs((int)getShort(layerPos + 1) - z);
			if (dist < nearestDist)
			{
				nearestDist = dist;
				nearestPos = layerPos;
			}
		}
		
		byte nearestNSWE = _buffer[nearestPos];
		short nearestHeight = getShort(nearestPos + 1);
		
		// Determine direction from cell position to target for NSWE check
		int nearestIdx = (nearestPos - pos) / 3;
		
		if (checkNSWE((short)(nearestNSWE & 0xFF), tx, ty))
			return nearestHeight;
		
		// Ramp-up: try layers above (lower index = higher layer stored first)
		for (int i = nearestIdx - 1; i >= 0; i--)
		{
			int layerPos = pos + i * 3;
			short h = getShort(layerPos + 1);
			if (h > nearestHeight && checkNSWE((short)(_buffer[layerPos] & 0xFF), tx, ty))
				return h;
		}
		
		return Double.MIN_VALUE;
	}
	
	/**
	 * Simplified NSWE check using only target direction (no geoX needed).
	 */
	private static boolean checkNSWE(short NSWE, int tx, int ty)
	{
		if (NSWE == 15)
			return true;
		// Direction: tx/ty are already absolute differences from current cell
		// We check: if tx > 0, need E; if tx < 0, need W; if ty > 0, need S; if ty < 0, need N
		if (tx > 0 && (NSWE & 1) == 0) return false;
		if (tx < 0 && (NSWE & 2) == 0) return false;
		if (ty > 0 && (NSWE & 4) == 0) return false;
		if (ty < 0 && (NSWE & 8) == 0) return false;
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
			heights[i] = getLayerHeight(cellX, cellY, i);
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
			nswes[i] = getLayerNSWE(cellX, cellY, i);
		return nswes;
	}
}
