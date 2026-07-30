package net.sf.l2j.gameserver.geoengine.geodata;

/**
 * Singleton for empty / invalid geo blocks. Returns default values
 * (height=worldZ, NSWE=ALL) so movement and LOS are never blocked.
 */
public final class BlockNull extends ABlock
{
	public static final BlockNull INSTANCE = new BlockNull();

	private BlockNull()
	{
	}

	@Override
	public boolean hasGeoPos()
	{
		return false;
	}

	@Override
	public short getHeightNearest(int geoX, int geoY, int worldZ)
	{
		return (short) worldZ;
	}

	@Override
	public byte getNsweNearest(int geoX, int geoY, int worldZ)
	{
		return GeoStructure.CELL_FLAG_ALL;
	}

	@Override
	public short getHeightAbove(int geoX, int geoY, int worldZ)
	{
		return (short) worldZ;
	}
}
