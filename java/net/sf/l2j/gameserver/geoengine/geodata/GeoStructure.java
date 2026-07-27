package net.sf.l2j.gameserver.geoengine.geodata;

public final class GeoStructure
{
	public static final byte CELL_FLAG_NONE = 0x00;
	public static final byte CELL_FLAG_E = 0x01;
	public static final byte CELL_FLAG_W = 0x02;
	public static final byte CELL_FLAG_S = 0x04;
	public static final byte CELL_FLAG_N = 0x08;
	public static final byte CELL_FLAG_ALL = 0x0F;
	
	public static final int CELL_SIZE = 16;
	public static final int CELL_HEIGHT = 8;
	public static final int CELL_IGNORE_HEIGHT = CELL_HEIGHT * 6;
	
	public static final byte TYPE_FLAT_L2J = 0;
	public static final byte TYPE_COMPLEX_L2J = 1;
	public static final byte TYPE_MULTILAYER_L2J = 2;
	
	public static final int BLOCK_CELLS_X = 8;
	public static final int BLOCK_CELLS_Y = 8;
	public static final int BLOCK_CELLS = BLOCK_CELLS_X * BLOCK_CELLS_Y;
	
	public static final int REGION_BLOCKS_X = 256;
	public static final int REGION_BLOCKS_Y = 256;
	public static final int REGION_BLOCKS = REGION_BLOCKS_X * REGION_BLOCKS_Y;
	
	public static final int REGION_CELLS_X = REGION_BLOCKS_X * BLOCK_CELLS_X;
	public static final int REGION_CELLS_Y = REGION_BLOCKS_Y * BLOCK_CELLS_Y;
	
	private GeoStructure()
	{
	}
}
