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
	
	public BlockMultilayer(java.nio.ByteBuffer bb)
	{
		// Temporary buffer to accumulate decoded data
		byte[] temp = new byte[GeoStructure.BLOCK_CELLS * 3 * 126]; // max 125 layers + 1 count byte per cell
		int pos = 0;
		
		for (int cell = 0; cell < GeoStructure.BLOCK_CELLS; cell++)
		{
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
	 * Returns the index of the nearest layer to worldZ for cell (cellX, cellY).
	 */
	private int findIndexNearest(int cellX, int cellY, int worldZ)
	{
		int pos = 0;
		int cellsToSkip = cellX * GeoStructure.BLOCK_CELLS_Y + cellY;
		
		// Skip preceding cells
		for (int i = 0; i < cellsToSkip; i++)
		{
			int layers = _buffer[pos] & 0xFF;
			pos += 1 + layers * 3;
		}
		
		int layers = _buffer[pos++] & 0xFF;
		int closest = pos;
		int bestDist = Integer.MAX_VALUE;
		
		for (int i = 0; i < layers; i++)
		{
			int height = getShort(pos + 1);
			int dist = Math.abs(height - worldZ);
			if (dist < bestDist)
			{
				bestDist = dist;
				closest = pos;
			}
			pos += 3;
		}
		return closest;
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
		int index = findIndexNearest(cellX, cellY, worldZ);
		return getShort(index + 1);
	}
	
	@Override
	public byte getNsweNearest(int geoX, int geoY, int worldZ)
	{
		int cellX = geoX % GeoStructure.BLOCK_CELLS_X;
		int cellY = geoY % GeoStructure.BLOCK_CELLS_Y;
		int index = findIndexNearest(cellX, cellY, worldZ);
		return _buffer[index];
	}
	
	@Override
	public short getHeightAbove(int geoX, int geoY, int worldZ)
	{
		int cellX = geoX % GeoStructure.BLOCK_CELLS_X;
		int cellY = geoY % GeoStructure.BLOCK_CELLS_Y;
		
		// Find the layer just above worldZ
		// Layers are stored from highest to lowest
		int pos = 0;
		int cellsToSkip = cellX * GeoStructure.BLOCK_CELLS_Y + cellY;
		for (int i = 0; i < cellsToSkip; i++)
		{
			int layers = _buffer[pos] & 0xFF;
			pos += 1 + layers * 3;
		}
		
		int layers = _buffer[pos++] & 0xFF;
		// Start from highest layer (first in buffer)
		pos += (layers - 1) * 3; // seek to last layer (highest stored first)
		
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
		int pos = 0;
		int cellsToSkip = cellX * GeoStructure.BLOCK_CELLS_Y + cellY;
		for (int i = 0; i < cellsToSkip; i++)
		{
			int layers = _buffer[pos] & 0xFF;
			pos += 1 + layers * 3;
		}
		return _buffer[pos] & 0xFF;
	}
	
	/**
	 * @return array of heights for all layers at (cellX, cellY)
	 */
	public short[] getHeights(int cellX, int cellY)
	{
		int pos = 0;
		int cellsToSkip = cellX * GeoStructure.BLOCK_CELLS_Y + cellY;
		for (int i = 0; i < cellsToSkip; i++)
		{
			int layers = _buffer[pos] & 0xFF;
			pos += 1 + layers * 3;
		}
		int layers = _buffer[pos++] & 0xFF;
		short[] heights = new short[layers];
		for (int i = 0; i < layers; i++)
		{
			heights[i] = getShort(pos + 1);
			pos += 3;
		}
		return heights;
	}
	
	/**
	 * @return array of NSWE values for all layers at (cellX, cellY)
	 */
	public byte[] getNSWEs(int cellX, int cellY)
	{
		int pos = 0;
		int cellsToSkip = cellX * GeoStructure.BLOCK_CELLS_Y + cellY;
		for (int i = 0; i < cellsToSkip; i++)
		{
			int layers = _buffer[pos] & 0xFF;
			pos += 1 + layers * 3;
		}
		int layers = _buffer[pos++] & 0xFF;
		byte[] nswes = new byte[layers];
		for (int i = 0; i < layers; i++)
		{
			nswes[i] = _buffer[pos];
			pos += 3;
		}
		return nswes;
	}
}
