package net.sf.l2j.gameserver.geoengine.geodata;

import java.nio.ByteBuffer;

/**
 * Complex block: 64 cells (8x8), each with its own height and NSWE.
 * Lightweight: reads from the MappedByteBuffer on demand instead of
 * pre-decoding into a byte[] array. This saves ~200 bytes per block (hundreds
 * of MB total across all regions).
 *
 * The Z-negative sign fix from VERGE SOURCE is applied during reads: (short)
 * cast BEFORE >> to preserve sign bit on negative Z values.
 */
public class BlockComplex extends ABlock
{
	private final ByteBuffer _geo;

	private final int _blockStart; // absolute position of this block's type
	                               // byte

	/**
	 * @param geo
	 *            the MappedByteBuffer for this region (shared across all blocks
	 *            in the region)
	 * @param blockStart
	 *            absolute position of the block's type byte in the buffer
	 */
	public BlockComplex(ByteBuffer geo, int blockStart)
	{
		_geo = geo;
		_blockStart = blockStart;
	}

	/**
	 * @return absolute position of cell (cellX, cellY) short in the buffer
	 */
	private int getCellAddr(int cellX, int cellY)
	{
		// blockStart points to type byte, skip it (+1), then cell index * 2
		// bytes each
		return _blockStart + 1
		        + (cellX * GeoStructure.BLOCK_CELLS_Y + cellY) * 2;
	}

	/**
	 * Decode raw short (height << 1 | NSWE) into height with sign fix.
	 */
	private short decodeHeight(int raw)
	{
		int height = (short) (raw & 0xFFF0);
		height >>= 1; // arithmetic shift preserves sign because of the (short)
		              // cast above
		return (short) height;
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
		int raw = _geo.getShort(getCellAddr(cellX, cellY)) & 0xFFFF;
		return decodeHeight(raw);
	}

	@Override
	public byte getNsweNearest(int geoX, int geoY, int worldZ)
	{
		int cellX = geoX % GeoStructure.BLOCK_CELLS_X;
		int cellY = geoY % GeoStructure.BLOCK_CELLS_Y;
		int raw = _geo.getShort(getCellAddr(cellX, cellY)) & 0xFFFF;
		return (byte) (raw & 0x0F);
	}

	@Override
	public short getHeightAbove(int geoX, int geoY, int worldZ)
	{
		int cellX = geoX % GeoStructure.BLOCK_CELLS_X;
		int cellY = geoY % GeoStructure.BLOCK_CELLS_Y;
		int raw = _geo.getShort(getCellAddr(cellX, cellY)) & 0xFFFF;
		short height = decodeHeight(raw);
		return (height > worldZ) ? height : Short.MAX_VALUE;
	}

	public byte getNswe(int cellX, int cellY)
	{
		int raw = _geo.getShort(getCellAddr(cellX, cellY)) & 0xFFFF;
		return (byte) (raw & 0x0F);
	}

	public short getHeight(int cellX, int cellY)
	{
		int raw = _geo.getShort(getCellAddr(cellX, cellY)) & 0xFFFF;
		return decodeHeight(raw);
	}

	@Override
	public short getHeightBelow(int geoX, int geoY, int worldZ)
	{
		int cellX = geoX % GeoStructure.BLOCK_CELLS_X;
		int cellY = geoY % GeoStructure.BLOCK_CELLS_Y;
		int raw = _geo.getShort(getCellAddr(cellX, cellY)) & 0xFFFF;
		return decodeHeight(raw);
	}

	@Override
	public byte getNsweBelow(int geoX, int geoY, int worldZ)
	{
		int cellX = geoX % GeoStructure.BLOCK_CELLS_X;
		int cellY = geoY % GeoStructure.BLOCK_CELLS_Y;
		int raw = _geo.getShort(getCellAddr(cellX, cellY)) & 0xFFFF;
		return (byte) (raw & 0x0F);
	}
}
