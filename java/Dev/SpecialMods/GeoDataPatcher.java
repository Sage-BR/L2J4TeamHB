/*
 * GeoDataPatcher - L2J Geodata Binary Patcher
 *
 * Reads a .l2j geodata file, scans all cells for NSWE flags that block movement
 * to adjacent cells where the height difference is compatible (<= 96 units),
 * and corrects the NSWE to 15 (all directions open).
 *
 * Uses findNearestHeight() for multilayer neighbor comparisons — when checking
 * if a blocked direction leads to compatible terrain, it scans ALL layers of
 * the neighbor cell and picks the height closest to the current cell's layer.
 * This fixes the "can go up but not down" ramp bug even when the corrupt NSWE
 * is on a lower (ground) layer while the neighbor's first layer is a ceiling.
 *
 * Usage: java Dev.SpecialMods.GeoDataPatcher [input.l2j] [-o output.l2j] [-t
 * threshold]
 *
 * Default input: ./data/geodata/16_19.l2j Default output: input_patched.l2j
 * Default threshold: 96 (max height diff to consider a ramp vs wall)
 */
package Dev.SpecialMods;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class GeoDataPatcher
{
	// NSWE flag masks (matching GeoEngine encoding)
	private static final int NSWE_E = 1; // East

	private static final int NSWE_W = 2; // West

	private static final int NSWE_S = 4; // South

	private static final int NSWE_N = 8; // North

	private static final int NSWE_ALL = 15;

	// Block types
	private static final int TYPE_FLAT = 0;

	private static final int TYPE_COMPLEX = 1;

	private static final int TYPE_MULTILAYER = 2;

	// Region dimensions
	private static final int REGION_BLOCKS_X = 256;

	private static final int REGION_BLOCKS_Y = 256;

	private static final int REGION_BLOCKS = REGION_BLOCKS_X * REGION_BLOCKS_Y;

	private static final int BLOCK_CELLS_X = 8;

	private static final int BLOCK_CELLS_Y = 8;

	private static final int REGION_CELLS_X = REGION_BLOCKS_X * BLOCK_CELLS_X;

	private static final int REGION_CELLS_Y = REGION_BLOCKS_Y * BLOCK_CELLS_Y;

	private static int MAX_RAMP_DIFF = 96;

	// The byte data and block index, accessible to helper functions
	private static byte[] _data;

	private static int[] _blockIndex;

	// Pre-extracted cell data (first/highest layer only)
	private static int[][] _cellHeight = new int[REGION_CELLS_X][REGION_CELLS_Y];

	private static byte[][] _cellNSWE = new byte[REGION_CELLS_X][REGION_CELLS_Y];

	// Counters
	private static int _totalCells = 0;

	private static int _scannedComplex = 0;

	private static int _scannedMultilayer = 0;

	private static int _fixedComplex = 0;

	private static int _fixedMultilayer = 0;

	private static int _fixedLayers = 0;

	private static int _scannedLayers = 0;

	// ─── Binary helpers ────────────────────────────────────────────────

	private static int readShort(byte[] d, int pos)
	{
		return (d[pos] & 0xFF) | ((d[pos + 1] & 0xFF) << 8);
	}

	private static void writeShort(byte[] d, int pos, int value)
	{
		d[pos] = (byte) (value & 0xFF);
		d[pos + 1] = (byte) ((value >> 8) & 0xFF);
	}

	/**
	 * Unpack height from L2J format (height << 1 | NSWE).
	 * Format: bits 4-15 = height (signed 12-bit shifted left 1), bits 0-3 = NSWE.
	 */
	private static int unpackHeight(int packed)
	{
		return (short) (packed & 0xFFF0) >> 1;
	}

	private static int unpackNSWE(int packed)
	{
		return packed & 0x0F;
	}

	/**
	 * Pack height and NSWE into L2J format (height << 1 | NSWE).
	 */
	private static int packData(int height, int nswe)
	{
		return ((height << 1) | (nswe & 0x0F)) & 0xFFFF;
	}

	// ─── Multi-layer neighbor height lookup ────────────────────────────

	/**
	 * Finds the height of the layer at (nx, ny) closest to targetZ. For
	 * Flat/Complex cells there is only one layer; for Multilayer cells all
	 * layers are scanned and the closest height (by absolute diff) is returned.
	 */
	private static int findNearestHeight(int nx, int ny, int targetZ)
	{
		if (nx < 0 || nx >= REGION_CELLS_X || ny < 0 || ny >= REGION_CELLS_Y)
		{
			return Integer.MIN_VALUE;
		}

		int bx = nx / BLOCK_CELLS_X;
		int by = ny / BLOCK_CELLS_Y;
		int cx = nx % BLOCK_CELLS_X;
		int cy = ny % BLOCK_CELLS_Y;
		int bi = bx * REGION_BLOCKS_Y + by;
		if (bi < 0 || bi >= REGION_BLOCKS)
		{
			return Integer.MIN_VALUE;
		}

		int blockStart = _blockIndex[bi];
		int type = _data[blockStart] & 0xFF;

		if (type == TYPE_FLAT)
		{
			if (blockStart + 3 > _data.length)
			{
				return Integer.MIN_VALUE;
			}
			return readShort(_data, blockStart + 1);
		}
		else if (type == TYPE_COMPLEX)
		{
			int cellIdx = cy * BLOCK_CELLS_X + cx;
			int cellPos = blockStart + 1 + cellIdx * 2;
			if (cellPos + 2 > _data.length)
			{
				return Integer.MIN_VALUE;
			}
			int packed = readShort(_data, cellPos);
			return unpackHeight(packed);
		}
		else if (type == TYPE_MULTILAYER)
		{
			// Skip to this cell's data
			int cellDataPos = blockStart + 1;
			int cell = cy * BLOCK_CELLS_X + cx;
			for (int i = 0; i < cell; i++)
			{
				if (cellDataPos >= _data.length)
				{
					return Integer.MIN_VALUE;
				}
				int layers = _data[cellDataPos] & 0xFF;
				cellDataPos += 1 + layers * 2;
			}
			if (cellDataPos + 1 > _data.length)
			{
				return Integer.MIN_VALUE;
			}
			int layers = _data[cellDataPos] & 0xFF;
			cellDataPos++;

			if (layers <= 0)
			{
				return Integer.MIN_VALUE;
			}

			// Scan all layers, find closest to targetZ
			int bestH = Integer.MIN_VALUE;
			int bestDist = Integer.MAX_VALUE;
			for (int l = 0; l < layers; l++)
			{
				int layerPos = cellDataPos + l * 2;
				if (layerPos + 2 > _data.length)
				{
					break;
				}
				int packed = readShort(_data, layerPos);
				int h = unpackHeight(packed);
				int dist = Math.abs(h - targetZ);
				if (dist < bestDist)
				{
					bestDist = dist;
					bestH = h;
				}
			}
			return bestH;
		}

		return Integer.MIN_VALUE;
	}

	// ─── Should-fix decision ──────────────────────────────────────────

	/**
	 * Check if cell at (gx, gy) should have its NSWE fixed. Uses
	 * findNearestHeight() for neighbor comparisons so that multilayer cells
	 * find the closest matching layer instead of only the first layer.
	 */
	private static boolean shouldFixCell(int gx, int gy)
	{
		int h = _cellHeight[gx][gy];
		int nswe = _cellNSWE[gx][gy] & 0xFF;

		if (nswe == NSWE_ALL)
		{
			return false;
		}

		// North (gy - 1)
		if ((nswe & NSWE_N) == 0)
		{
			if (gy <= 0)
			{
				return false;
			}
			int nh = findNearestHeight(gx, gy - 1, h);
			if (nh == Integer.MIN_VALUE || Math.abs(nh - h) > MAX_RAMP_DIFF)
			{
				return false;
			}
		}

		// South (gy + 1)
		if ((nswe & NSWE_S) == 0)
		{
			if (gy >= REGION_CELLS_Y - 1)
			{
				return false;
			}
			int nh = findNearestHeight(gx, gy + 1, h);
			if (nh == Integer.MIN_VALUE || Math.abs(nh - h) > MAX_RAMP_DIFF)
			{
				return false;
			}
		}

		// West (gx - 1)
		if ((nswe & NSWE_W) == 0)
		{
			if (gx <= 0)
			{
				return false;
			}
			int nh = findNearestHeight(gx - 1, gy, h);
			if (nh == Integer.MIN_VALUE || Math.abs(nh - h) > MAX_RAMP_DIFF)
			{
				return false;
			}
		}

		// East (gx + 1)
		if ((nswe & NSWE_E) == 0)
		{
			if (gx >= REGION_CELLS_X - 1)
			{
				return false;
			}
			int nh = findNearestHeight(gx + 1, gy, h);
			if (nh == Integer.MIN_VALUE || Math.abs(nh - h) > MAX_RAMP_DIFF)
			{
				return false;
			}
		}

		return true;
	}

	// ─── Main ─────────────────────────────────────────────────────────

	public static void main(String[] args) throws Exception
	{
		String inputPath = "./data/geodata/16_19.l2j";
		String outputPath = null;

		for (int i = 0; i < args.length; i++)
		{
			if (args[i].equals("-o") && i + 1 < args.length)
			{
				outputPath = args[++i];
			}
			else if (args[i].equals("-t") && i + 1 < args.length)
			{
				MAX_RAMP_DIFF = Integer.parseInt(args[++i]);
			}
			else if (!args[i].startsWith("-"))
			{
				inputPath = args[i];
			}
		}

		if (outputPath == null)
		{
			String base = inputPath.replace(".l2j", "");
			outputPath = base + "_patched.l2j";
		}

		File inputFile = new File(inputPath);
		if (!inputFile.exists())
		{
			System.err.println("ERROR: File not found: "
			        + inputFile.getAbsolutePath());
			System.exit(1);
		}

		System.out.println("=== GeoDataPatcher ===");
		System.out.println("Input:  " + inputFile.getAbsolutePath());
		System.out.println("Output: " + new File(outputPath).getAbsolutePath());
		System.out.println("Threshold: " + MAX_RAMP_DIFF + " units");
		System.out.println("File size: " + inputFile.length() + " bytes\n");

		_data = Files.readAllBytes(inputFile.toPath());

		String name = inputFile.getName().replace(".l2j", "");
		String[] parts = name.split("_");
		int rx = Integer.parseInt(parts[0]);
		int ry = Integer.parseInt(parts[1]);
		System.out.println("Region: " + rx + "_" + ry);

		for (int x = 0; x < REGION_CELLS_X; x++)
		{
			for (int y = 0; y < REGION_CELLS_Y; y++)
			{
				_cellHeight[x][y] = Integer.MIN_VALUE;
			}
		}

		// ═════════════════════════════════════════════════════════════════
		// PASS 1: Build block index and extract first-layer cell data
		// ═════════════════════════════════════════════════════════════════
		_blockIndex = new int[REGION_BLOCKS];
		int pos = 0;

		System.out.println("\n[Phase 1] Parsing file structure and extracting cell data...");

		for (int blockIdx = 0; blockIdx < REGION_BLOCKS; blockIdx++)
		{
			if (pos >= _data.length)
			{
				break;
			}
			_blockIndex[blockIdx] = pos;
			int type = _data[pos] & 0xFF;
			pos++;

			int blockX = blockIdx / REGION_BLOCKS_Y;
			int blockY = blockIdx % REGION_BLOCKS_Y;

			if (type == TYPE_FLAT)
			{
				if (pos + 2 > _data.length)
				{
					break;
				}
				int height = readShort(_data, pos);
				pos += 2;
				for (int cy = 0; cy < BLOCK_CELLS_Y; cy++)
				{
					for (int cx = 0; cx < BLOCK_CELLS_X; cx++)
					{
						int gx = blockX * BLOCK_CELLS_X + cx;
						int gy = blockY * BLOCK_CELLS_Y + cy;
						_cellHeight[gx][gy] = height;
						_cellNSWE[gx][gy] = NSWE_ALL;
						_totalCells++;
					}
				}
			}
			else if (type == TYPE_COMPLEX)
			{
				if (pos + 128 > _data.length)
				{
					break;
				}
				for (int cy = 0; cy < BLOCK_CELLS_Y; cy++)
				{
					for (int cx = 0; cx < BLOCK_CELLS_X; cx++)
					{
						int cellIdx = cy * BLOCK_CELLS_X + cx;
						int cellPos = pos + cellIdx * 2;
						int packed = readShort(_data, cellPos);
						int height = unpackHeight(packed);
						int nswe = unpackNSWE(packed);
						int gx = blockX * BLOCK_CELLS_X + cx;
						int gy = blockY * BLOCK_CELLS_Y + cy;
						_cellHeight[gx][gy] = height;
						_cellNSWE[gx][gy] = (byte) nswe;
						_totalCells++;
						_scannedComplex++;
					}
				}
				pos += 128;
			}
			else if (type == TYPE_MULTILAYER)
			{
				for (int cy = 0; cy < BLOCK_CELLS_Y; cy++)
				{
					for (int cx = 0; cx < BLOCK_CELLS_X; cx++)
					{
						if (pos >= _data.length)
						{
							break;
						}
						int layers = _data[pos] & 0xFF;
						pos++;
						int gx = blockX * BLOCK_CELLS_X + cx;
						int gy = blockY * BLOCK_CELLS_Y + cy;
						if (layers > 0)
						{
							if (pos + 2 > _data.length)
							{
								break;
							}
							int packed = readShort(_data, pos);
							_cellHeight[gx][gy] = unpackHeight(packed);
							_cellNSWE[gx][gy] = (byte) unpackNSWE(packed);
						}
						else
						{
							_cellHeight[gx][gy] = Integer.MIN_VALUE;
							_cellNSWE[gx][gy] = NSWE_ALL;
						}
						pos += layers * 2;
						_totalCells++;
						_scannedMultilayer++;
					}
				}
			}
			else
			{
				System.err.println("WARNING: Unknown block type " + type
				        + " at block " + blockIdx);
				break;
			}
		}

		System.out.println("Blocks parsed: " + REGION_BLOCKS);
		System.out.println("Total cells: " + _totalCells);
		System.out.println("Complex cells: " + _scannedComplex);
		System.out.println("Multilayer cells: " + _scannedMultilayer);

		// ═════════════════════════════════════════════════════════════════
		// PASS 2: Fix NSWE
		// ═════════════════════════════════════════════════════════════════
		System.out.println("\n[Phase 2] Analyzing and fixing NSWE flags...");

		pos = 0;
		for (int blockIdx = 0; blockIdx < REGION_BLOCKS; blockIdx++)
		{
			if (pos >= _data.length)
			{
				break;
			}
			int type = _data[pos] & 0xFF;
			pos++;

			int blockX = blockIdx / REGION_BLOCKS_Y;
			int blockY = blockIdx % REGION_BLOCKS_Y;

			if (type == TYPE_FLAT)
			{
				pos += 2;
			}
			else if (type == TYPE_COMPLEX)
			{
				for (int cy = 0; cy < BLOCK_CELLS_Y; cy++)
				{
					for (int cx = 0; cx < BLOCK_CELLS_X; cx++)
					{
						int cellIdx = cy * BLOCK_CELLS_X + cx;
						int cellPos = pos + cellIdx * 2;
						int packed = readShort(_data, cellPos);
						int height = unpackHeight(packed);
						int nswe = unpackNSWE(packed);
						int gx = blockX * BLOCK_CELLS_X + cx;
						int gy = blockY * BLOCK_CELLS_Y + cy;

						if (nswe != NSWE_ALL && shouldFixCell(gx, gy))
						{
							writeShort(_data, cellPos, packData(height, NSWE_ALL));
							_cellNSWE[gx][gy] = NSWE_ALL;
							_fixedComplex++;
							System.out.println("  FIXED complex (" + gx + ","
							        + gy + ") h=" + height + " NSWE=" + nswe
							        + " (" + describeBlockedNSWE(nswe)
							        + ") -> NSWE=15 (ALL)");
						}
					}
				}
				pos += 128;
			}
			else if (type == TYPE_MULTILAYER)
			{
				int cellDataPos = pos;
				for (int cy = 0; cy < BLOCK_CELLS_Y; cy++)
				{
					for (int cx = 0; cx < BLOCK_CELLS_X; cx++)
					{
						if (cellDataPos >= _data.length)
						{
							break;
						}
						int layers = _data[cellDataPos] & 0xFF;
						cellDataPos++;

						int gx = blockX * BLOCK_CELLS_X + cx;
						int gy = blockY * BLOCK_CELLS_Y + cy;

						// Save original first-layer data
						int origHeight = _cellHeight[gx][gy];
						byte origNSWE = _cellNSWE[gx][gy];
						boolean cellFixed = false;

						// Check each layer individually using nearest-height
						// neighbor lookups
						for (int l = 0; l < layers; l++)
						{
							int layerPos = cellDataPos + l * 2;
							if (layerPos + 2 > _data.length)
							{
								break;
							}

							int packed = readShort(_data, layerPos);
							int height = unpackHeight(packed);
							int nswe = unpackNSWE(packed);
							_scannedLayers++;

							if (nswe == NSWE_ALL)
							{
								continue;
							}

							// Temporarily update cell-level data for this layer
							_cellHeight[gx][gy] = height;
							_cellNSWE[gx][gy] = (byte) nswe;

							if (shouldFixCell(gx, gy))
							{
								writeShort(_data, layerPos, packData(height, NSWE_ALL));
								_fixedLayers++;
								if (!cellFixed)
								{
									_fixedMultilayer++;
									cellFixed = true;
									System.out.println("  FIXED multi  (" + gx
									        + "," + gy + ") h=" + height
									        + " NSWE=" + nswe + " ("
									        + describeBlockedNSWE(nswe)
									        + ") layers=" + layers + " l=" + l
									        + " -> NSWE=15 (ALL)");
								}
							}
						}

						// Restore original first-layer data for subsequent
						// neighbor checks
						_cellHeight[gx][gy] = origHeight;
						_cellNSWE[gx][gy] = cellFixed ? NSWE_ALL : origNSWE;

						cellDataPos += layers * 2;
					}
				}
				pos = cellDataPos;
			}
		}

		// ═════════════════════════════════════════════════════════════════
		// RESULTS
		// ═════════════════════════════════════════════════════════════════
		int totalFixed = _fixedComplex + _fixedMultilayer;
		System.out.println("\n===========================================");
		System.out.println("         PATCHING RESULTS");
		System.out.println("===========================================");
		System.out.println("File:         " + inputFile.getName());
		System.out.println("Region:       " + rx + "_" + ry);
		System.out.println("Threshold:    " + MAX_RAMP_DIFF + " units");
		System.out.println("-------------------------------------------");
		System.out.println("Cells scanned:  " + _totalCells);
		System.out.println("  Complex:      " + _scannedComplex);
		System.out.println("  Multilayer:   " + _scannedMultilayer);
		System.out.println("  Layers:       " + _scannedLayers);
		System.out.println("-------------------------------------------");
		System.out.println("Cells FIXED:    " + totalFixed);
		System.out.println("  Complex:      " + _fixedComplex);
		System.out.println("  Multilayer:   " + _fixedMultilayer);
		System.out.println("  Layers fixed: " + _fixedLayers);
		System.out.println("===========================================");

		if (totalFixed > 0)
		{
			Files.write(Paths.get(outputPath), _data);
			System.out.println("\nPatched file written to: "
			        + new File(outputPath).getAbsolutePath());
			System.out.println("\nTo apply: copy \"" + outputPath + "\" \""
			        + inputPath + "\"");
			System.out.println("Then restart the server.");
		}
		else
		{
			System.out.println("\nNo fixes needed - all NSWE data appears correct.");
		}
	}

	private static String describeBlockedNSWE(int nswe)
	{
		StringBuilder sb = new StringBuilder();
		if ((nswe & NSWE_N) == 0)
		{
			sb.append("N");
		}
		if ((nswe & NSWE_S) == 0)
		{
			sb.append("S");
		}
		if ((nswe & NSWE_W) == 0)
		{
			sb.append("W");
		}
		if ((nswe & NSWE_E) == 0)
		{
			sb.append("E");
		}
		return sb.toString();
	}
}
