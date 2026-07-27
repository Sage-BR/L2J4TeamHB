package net.sf.l2j.gameserver.geoengine.geodata;

public abstract class ABlock
{
	/**
	 * @return true if this block contains valid geodata.
	 */
	public abstract boolean hasGeoPos();
	
	/**
	 * @param geoX
	 * @param geoY
	 * @param worldZ
	 * @return height of the nearest layer to worldZ at (geoX, geoY)
	 */
	public abstract short getHeightNearest(int geoX, int geoY, int worldZ);
	
	/**
	 * @param geoX
	 * @param geoY
	 * @param worldZ
	 * @return NSWE of the nearest layer to worldZ at (geoX, geoY)
	 */
	public abstract byte getNsweNearest(int geoX, int geoY, int worldZ);
	
	/**
	 * @param geoX
	 * @param geoY
	 * @param worldZ
	 * @return height of the layer just above worldZ, or Short.MAX_VALUE if none
	 */
	public abstract short getHeightAbove(int geoX, int geoY, int worldZ);
}
