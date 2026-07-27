package net.sf.l2j.gameserver.geoengine.geodata;

/**
 * Complex block: 64 cells (8x8), each with its own height and NSWE.
 * Data is pre-decoded in the constructor to avoid repeated ByteBuffer reads
 * and to preserve the sign bit for negative Z values.
 */
public class BlockComplex extends ABlock
{
	/** For each cell: 1 byte NSWE + 2 bytes height (little-endian short) = 3 bytes per cell */
	private final byte[] _buffer;
	
	public BlockComplex(java.nio.ByteBuffer bb)
	{
		_buffer = new byte[GeoStructure.BLOCK_CELLS * 3];
		
		for (int i = 0; i < GeoStructure.BLOCK_CELLS; i++)
		{
			// Read raw short (height << 1 | NSWE)
			int raw = bb.getShort() & 0xFFFF;
			
			// NSWE: lowest 4 bits
			byte nswe = (byte)(raw & 0x0F);
			
			// Height: extract bits, cast to short to preserve sign, then shift
			// This is the same pattern used for multilevel blocks to avoid the
			// signed/unsigned bug with negative Z values.
			int height = (short)(raw & 0xFFF0);
			height >>= 1; // arithmetic shift (preserves sign because of the (short) cast above)
			
			_buffer[i * 3] = nswe;
			_buffer[i * 3 + 1] = (byte)(height & 0xFF);       // low byte
			_buffer[i * 3 + 2] = (byte)((height >> 8) & 0xFF); // high byte
		}
	}
	
	/**
	 * Constructor for pre-decoded data (used by dynamic blocks).
	 */
	protected BlockComplex(byte[] buffer)
	{
		_buffer = buffer;
	}
	
	private static int getCellIndex(int cellX, int cellY)
	{
		return (cellX * GeoStructure.BLOCK_CELLS_Y + cellY) * 3;
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
		int index = getCellIndex(cellX, cellY);
		return getShort(index + 1);
	}
	
	@Override
	public byte getNsweNearest(int geoX, int geoY, int worldZ)
	{
		int cellX = geoX % GeoStructure.BLOCK_CELLS_X;
		int cellY = geoY % GeoStructure.BLOCK_CELLS_Y;
		int index = getCellIndex(cellX, cellY);
		return _buffer[index];
	}
	
	@Override
	public short getHeightAbove(int geoX, int geoY, int worldZ)
	{
		int cellX = geoX % GeoStructure.BLOCK_CELLS_X;
		int cellY = geoY % GeoStructure.BLOCK_CELLS_Y;
		int index = getCellIndex(cellX, cellY);
		short height = getShort(index + 1);
		return (height > worldZ) ? height : Short.MAX_VALUE;
	}
	
	public byte getNswe(int cellX, int cellY)
	{
		int index = getCellIndex(cellX, cellY);
		return _buffer[index];
	}
	
	public short getHeight(int cellX, int cellY)
	{
		int index = getCellIndex(cellX, cellY);
		return getShort(index + 1);
	}
}
