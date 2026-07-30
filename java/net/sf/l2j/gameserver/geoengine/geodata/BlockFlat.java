package net.sf.l2j.gameserver.geoengine.geodata;

/**
 * Flat block: all 64 cells share the same height and NSWE is always ALL. Data
 * is pre-decoded to avoid repeated ByteBuffer reads.
 */
public class BlockFlat extends ABlock
{
	private final short _height;

	public BlockFlat(short height)
	{
		_height = height;
	}

	@Override
	public boolean hasGeoPos()
	{
		return true;
	}

	@Override
	public short getHeightNearest(int geoX, int geoY, int worldZ)
	{
		return _height;
	}

	@Override
	public byte getNsweNearest(int geoX, int geoY, int worldZ)
	{
		return GeoStructure.CELL_FLAG_ALL;
	}

	@Override
	public short getHeightAbove(int geoX, int geoY, int worldZ)
	{
		return (_height > worldZ) ? _height : Short.MAX_VALUE;
	}

	public short getHeight()
	{
		return _height;
	}
}
