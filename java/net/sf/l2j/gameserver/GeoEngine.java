/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.l2j.gameserver;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.datatables.DoorTable;
import net.sf.l2j.gameserver.model.L2Object;
import net.sf.l2j.gameserver.model.L2World;
import net.sf.l2j.gameserver.model.Location;
import net.sf.l2j.gameserver.model.actor.instance.L2DoorInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2SiegeGuardInstance;
import net.sf.l2j.util.Point3D;

import net.sf.l2j.gameserver.geoengine.geodata.ABlock;
import net.sf.l2j.gameserver.geoengine.geodata.BlockComplex;
import net.sf.l2j.gameserver.geoengine.geodata.BlockFlat;
import net.sf.l2j.gameserver.geoengine.geodata.BlockMultilayer;
import net.sf.l2j.gameserver.geoengine.geodata.BlockNull;
import net.sf.l2j.gameserver.geoengine.geodata.GeoStructure;

import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author -Nemesiss-
 */
public class GeoEngine extends GeoData
{
	private static Logger _log = Logger.getLogger(GeoData.class.getName());
	private static GeoEngine _instance;
    private final static byte _e = 1;
    private final static byte _w = 2;
    private final static byte _s = 4;
    private final static byte _n = 8;
	private static Map<Short, MappedByteBuffer> _geodata = new ConcurrentHashMap<Short, MappedByteBuffer>();
	private static Map<Short, IntBuffer> _geodataIndex = new ConcurrentHashMap<Short, IntBuffer>();
	private static Map<Short, ABlock[]> _geoBlocks = new ConcurrentHashMap<Short, ABlock[]>();
	private static BufferedOutputStream _geoBugsOut;

	public static GeoEngine getInstance()
    {
        if(_instance == null)
            _instance = new GeoEngine();
        return _instance;
    }
    public GeoEngine()
    {
        nInitGeodata();
    }

    //Public Methods
    /**
     * @see net.sf.l2j.gameserver.GeoData#getType(int, int)
     */
    @Override
    public short getType(int x, int y)
    {
        return nGetType((x - L2World.MAP_MIN_X) >> 4, (y - L2World.MAP_MIN_Y) >> 4);
    }
    /**
     * @see net.sf.l2j.gameserver.GeoData#getHeight(int, int, int)
     */
    @Override
    public short getHeight(int x, int y, int z)
    {
        return nGetHeight((x - L2World.MAP_MIN_X) >> 4,(y - L2World.MAP_MIN_Y) >> 4,z);
    }
    /**
     * @see net.sf.l2j.gameserver.GeoData#getSpawnHeight(int, int, int, int, int)
     */
    @Override
    public short getSpawnHeight(int x, int y, int zmin, int zmax, int spawnid)
    {
    	return nGetSpawnHeight((x - L2World.MAP_MIN_X) >> 4,(y - L2World.MAP_MIN_Y) >> 4,zmin,zmax,spawnid);
    }
    /**
     * @see net.sf.l2j.gameserver.GeoData#traceTerrainZ(int, int, int, int, int)
     */
    @Override
    public short traceTerrainZ(int x, int y, int z, int tx, int ty)
    {
    	return nTraceTerrainZ((x - L2World.MAP_MIN_X) >> 4,(y - L2World.MAP_MIN_Y) >> 4,z,(tx - L2World.MAP_MIN_X) >> 4,(ty - L2World.MAP_MIN_Y) >> 4);
    }
    /**
     * @see net.sf.l2j.gameserver.GeoData#geoPosition(int, int)
     */
    @Override
    public String geoPosition(int x, int y)
    {
    	int gx = (x - L2World.MAP_MIN_X) >> 4;
    	int gy = (y - L2World.MAP_MIN_Y) >> 4;
    	return "bx: "+getBlock(gx)+" by: "+getBlock(gy)+" cx: "+getCell(gx)+" cy: "+getCell(gy)+"  region offset: "+getRegionOffset(gx,gy);
    }
    /**
     * @see net.sf.l2j.gameserver.GeoData#canSeeTarget(L2Object, Point3D)
     */
    @Override
    public boolean canSeeTarget(L2Object cha, Point3D target)
    {
    	if (DoorTable.getInstance().checkIfDoorsBetween(cha.getX(),cha.getY(),cha.getZ(),target.getX(),target.getY(),target.getZ()))
    		return false;
    	if(cha.getZ() >= target.getZ())
    		return canSeeTarget(cha.getX(),cha.getY(),cha.getZ(),target.getX(),target.getY(),target.getZ());
    	else
    		return canSeeTarget(target.getX(),target.getY(),target.getZ(), cha.getX(),cha.getY(),cha.getZ());

    }
    /**
     * @see net.sf.l2j.gameserver.GeoData#canSeeTarget(net.sf.l2j.gameserver.model.L2Object, net.sf.l2j.gameserver.model.L2Object)
     */
    @Override
    public boolean canSeeTarget(L2Object cha, L2Object target)
    {
    	// To be able to see over fences and give the player the viewpoint
    	// game client has, all coordinates are lifted 45 from ground.
    	// Because of layer selection in LOS algorithm (it selects -45 there
    	// and some layers can be very close...) do not change this without
    	// changing the LOS code.
    	// Basically the +45 is character height. Raid bosses are naturally higher,
    	// dwarves shorter, but this should work relatively well.
    	// If this is going to be improved, use e.g.
    	// ((L2Character)cha).getTemplate().collisionHeight
    	int z = cha.getZ()+45;
    	if(cha instanceof L2SiegeGuardInstance) z += 30; // well they don't move closer to balcony fence at the moment :(
    	int z2 = target.getZ()+45;
    	if (!(target instanceof L2DoorInstance)
    			&& DoorTable.getInstance().checkIfDoorsBetween(cha.getX(),cha.getY(),z,target.getX(),target.getY(),z2))
    		return false;
    	if(target instanceof L2DoorInstance)
    	{
    		int doorZ = target.getZ() + 45;
    		if(cha.getZ() >= doorZ)
    			return canSeeTarget(cha.getX(), cha.getY(), z, target.getX(), target.getY(), doorZ);
    		else
    			return canSeeTarget(target.getX(), target.getY(), doorZ, cha.getX(), cha.getY(), z);
    	}
    	if(target instanceof L2SiegeGuardInstance) z2 += 30; // well they don't move closer to balcony fence at the moment :(
    	if(cha.getZ() >= target.getZ())
    		return canSeeTarget(cha.getX(),cha.getY(),z,target.getX(),target.getY(),z2);
    	else
    		return canSeeTarget(target.getX(),target.getY(),z2, cha.getX(),cha.getY(),z);
    }
    /**
     * @see net.sf.l2j.gameserver.GeoData#canSeeTargetDebug(net.sf.l2j.gameserver.model.actor.instance.L2PcInstance, net.sf.l2j.gameserver.model.L2Object)
     */
    @Override
    public boolean canSeeTargetDebug(L2PcInstance gm, L2Object target)
    {
    	// comments: see above
    	int z = gm.getZ()+45;
    	int z2 = target.getZ()+45;
    	if(target instanceof L2DoorInstance)
    	{
    		gm.sendMessage("door always true");
    		return true; // door coordinates are hinge coords..
    	}

    	if(gm.getZ() >= target.getZ())
    		return canSeeDebug(gm,(gm.getX() - L2World.MAP_MIN_X) >> 4,(gm.getY() - L2World.MAP_MIN_Y) >> 4,z,(target.getX() - L2World.MAP_MIN_X) >> 4,(target.getY() - L2World.MAP_MIN_Y) >> 4,z2);
    	else
    		return canSeeDebug(gm,(target.getX() - L2World.MAP_MIN_X) >> 4,(target.getY() - L2World.MAP_MIN_Y) >> 4,z2,(gm.getX() - L2World.MAP_MIN_X) >> 4,(gm.getY() - L2World.MAP_MIN_Y) >> 4,z);
    }
    /**
     * @see net.sf.l2j.gameserver.GeoData#getNSWE(int, int, int)
     */
    @Override
    public short getNSWE(int x, int y, int z)
    {
        return nGetNSWE((x - L2World.MAP_MIN_X) >> 4,(y - L2World.MAP_MIN_Y) >> 4,z);
    }
    /**
     * @see net.sf.l2j.gameserver.GeoData#moveCheck(int, int, int, int, int, int)
     */
    @Override
    public Location moveCheck(int x, int y, int z, int tx, int ty, int tz)
    {
    	Location startpoint = new Location(x,y,z);
    	if (DoorTable.getInstance().checkIfDoorsBetween(x,y,z,tx,ty,tz))
    		return startpoint;

    	Location destiny = new Location(tx,ty,tz);
        return moveCheck(startpoint, destiny,(x - L2World.MAP_MIN_X) >> 4,(y - L2World.MAP_MIN_Y) >> 4,z,(tx - L2World.MAP_MIN_X) >> 4,(ty - L2World.MAP_MIN_Y) >> 4,tz);
    }
    /**
     * @see net.sf.l2j.gameserver.GeoData#addGeoDataBug(net.sf.l2j.gameserver.model.actor.instance.L2PcInstance, java.lang.String)
     */
    @Override
    public void addGeoDataBug(L2PcInstance gm, String comment)
    {
    	int gx = (gm.getX() - L2World.MAP_MIN_X) >> 4;
    	int gy = (gm.getY() - L2World.MAP_MIN_Y) >> 4;
    	int bx = getBlock(gx);
    	int by = getBlock(gy);
    	int cx = getCell(gx);
    	int cy = getCell(gy);
    	int rx = (gx >> 11) + 16;
	    int ry = (gy >> 11) + 10;
    	String out = rx+";"+ry+";"+bx+";"+by+";"+cx+";"+cy+";"+gm.getZ()+";"+comment+"\n";
    	try
		{
    		_geoBugsOut.write(out.getBytes());
    		_geoBugsOut.flush();
    		gm.sendMessage("GeoData bug saved!");
		} catch (Exception e) {
			e.printStackTrace();
			gm.sendMessage("GeoData bug save Failed!");
		}
    }

    @Override
    public boolean canSeeTarget(int x, int y, int z, int tx, int ty, int tz)
    {
        return canSee((x - L2World.MAP_MIN_X) >> 4,(y - L2World.MAP_MIN_Y) >> 4,z,(tx - L2World.MAP_MIN_X) >> 4,(ty - L2World.MAP_MIN_Y) >> 4,tz);
    }

    public boolean hasGeo(int x, int y)
    {
    	int gx = (x - L2World.MAP_MIN_X) >> 4;
    	int gy = (y - L2World.MAP_MIN_Y) >> 4;
        return getABlock(gx, gy).hasGeoPos();
    }
    
    private static boolean canSee(int x, int y, double z, int tx, int ty, int tz)
    {
    	int dx = (tx - x);
        int dy = (ty - y);
        final double dz = (tz - z);
        final int distance2 = dx*dx+dy*dy;

        if (distance2 > 90000) // (300*300) 300*16 = 4800 in world coord
        {
            //Avoid too long check
            return false;
        }
        // very short checks: 9 => 144 world distance
        // this ensures NLOS function has enough points to calculate,
        // it might not work when distance is small and path vertical
        else if (distance2 < 82)
        {
        	// 150 should be too deep/high.
        	if(dz*dz > 22500)
        	{
        		short region = getRegionOffset(x,y);
        		// geodata is loaded for region and mobs should have correct Z coordinate...
        		// so there would likely be a floor in between the two
        		if (_geoBlocks.get(region) != null)
        			return false;
        	}
        	return true;
        }

        // Increment in Z coordinate when moving along X or Y axis
        // and not straight to the target. This is done because
        // calculation moves either in X or Y direction.
        final int inc_x = sign(dx);
        final int inc_y = sign(dy);
        dx = Math.abs(dx);
        dy = Math.abs(dy);
        final double inc_z_directionx = dz*dx / (distance2);
        final double inc_z_directiony = dz*dy / (distance2);

        // next_* are used in NLOS check from x,y
        int next_x = x;
        int next_y = y;

        // creates path to the target
        // calculation stops when next_* == target
        if (dx >= dy)// dy/dx <= 1
        {
        	int delta_A = 2*dy;
        	int d = delta_A - dx;
            int delta_B = delta_A - 2*dx;

            for (int i = 0; i < dx; i++)
            {
            	x = next_x;
            	y = next_y;
            	if (d > 0)
            	{
            		d += delta_B;
            		next_x += inc_x;
            		z += inc_z_directionx;
            		next_y += inc_y;
            		z += inc_z_directiony;
            		//_log.warning("1: next_x:"+next_x+" next_y"+next_y);
            		if (!nLOS(x,y,(int)z,inc_x,inc_y,inc_z_directionx+inc_z_directiony,tz,false))
            			return false;
            	}
            	else
            	{
            		d += delta_A;
            		next_x += inc_x;
            		//_log.warning("2: next_x:"+next_x+" next_y"+next_y);
            		z += inc_z_directionx;
            		if (!nLOS(x,y,(int)z,inc_x,0,inc_z_directionx,tz,false))
            			return false;
            	}
            }
        }
        else
        {
        	int delta_A = 2*dx;
        	int d = delta_A - dy;
            int delta_B = delta_A - 2*dy;
            for (int i = 0; i < dy; i++)
            {
            	x = next_x;
            	y = next_y;
            	if (d > 0)
            	{
            		d += delta_B;
            		next_y += inc_y;
            		z += inc_z_directiony;
            		next_x += inc_x;
            		z += inc_z_directionx;
            		//_log.warning("3: next_x:"+next_x+" next_y"+next_y);
            		if (!nLOS(x,y,(int)z,inc_x,inc_y,inc_z_directionx+inc_z_directiony,tz,false))
            			return false;
            	}
            	else
            	{
            		d += delta_A;
            		next_y += inc_y;
            		//_log.warning("4: next_x:"+next_x+" next_y"+next_y);
            		z += inc_z_directiony;
            		if (!nLOS(x,y,(int)z,0,inc_y,inc_z_directiony,tz,false))
            			return false;
            	}
            }
        }
        return true;
    }
    /*
     * Debug function for checking if there's a line of sight between
     * two coordinates.
     *
     * Creates points for line of sight check (x,y,z towards target) and
     * in each point, layer and movement checks are made with NLOS function.
     *
     * Coordinates here are geodata x,y but z coordinate is world coordinate
     */
    private static boolean canSeeDebug(L2PcInstance gm, int x, int y, double z, int tx, int ty, int tz)
    {
    	int dx = (tx - x);
        int dy = (ty - y);
        final double dz = (tz - z);
        final int distance2 = dx*dx+dy*dy;

        if (distance2 > 90000) // (300*300) 300*16 = 4800 in world coord
        {
            //Avoid too long check
        	gm.sendMessage("dist > 300");
            return false;
        }
        // very short checks: 9 => 144 world distance
        // this ensures NLOS function has enough points to calculate,
        // it might not work when distance is small and path vertical
        else if (distance2 < 82)
        {
        	// 150 should be too deep/high.
        	if(dz*dz > 22500)
        	{
        		short region = getRegionOffset(x,y);
        		// geodata is loaded for region and mobs should have correct Z coordinate...
        		// so there would likely be a floor in between the two
        		if (_geoBlocks.get(region) != null)
        			return false;
        	}
        	return true;
        }

        // Increment in Z coordinate when moving along X or Y axis
        // and not straight to the target. This is done because
        // calculation moves either in X or Y direction.
        final int inc_x = sign(dx);
        final int inc_y = sign(dy);
        dx = Math.abs(dx);
        dy = Math.abs(dy);
        final double inc_z_directionx = dz*dx / (distance2);
        final double inc_z_directiony = dz*dy / (distance2);

        gm.sendMessage("Los: from X: "+x+ "Y: "+y+ "--->> X: "+tx+" Y: "+ty);

        // next_* are used in NLOS check from x,y
        int next_x = x;
        int next_y = y;

        // creates path to the target
        // calculation stops when next_* == target
        if (dx >= dy)// dy/dx <= 1
        {
        	int delta_A = 2*dy;
        	int d = delta_A - dx;
            int delta_B = delta_A - 2*dx;

            for (int i = 0; i < dx; i++)
            {
            	x = next_x;
            	y = next_y;
            	if (d > 0)
            	{
            		d += delta_B;
            		next_x += inc_x;
            		z += inc_z_directionx;
            		next_y += inc_y;
            		z += inc_z_directiony;
            		//_log.warning("1: next_x:"+next_x+" next_y"+next_y);
            		if (!nLOS(x,y,(int)z,inc_x,inc_y,inc_z_directionx+inc_z_directiony,tz,true))
            			return false;
            	}
            	else
            	{
            		d += delta_A;
            		next_x += inc_x;
            		//_log.warning("2: next_x:"+next_x+" next_y"+next_y);
            		z += inc_z_directionx;
            		if (!nLOS(x,y,(int)z,inc_x,0,inc_z_directionx,tz,true))
            			return false;
            	}
            }
        }
        else
        {
        	int delta_A = 2*dx;
        	int d = delta_A - dy;
            int delta_B = delta_A - 2*dy;
            for (int i = 0; i < dy; i++)
            {
            	x = next_x;
            	y = next_y;
            	if (d > 0)
            	{
            		d += delta_B;
            		next_y += inc_y;
            		z += inc_z_directiony;
            		next_x += inc_x;
            		z += inc_z_directionx;
            		//_log.warning("3: next_x:"+next_x+" next_y"+next_y);
            		if (!nLOS(x,y,(int)z,inc_x,inc_y,inc_z_directionx+inc_z_directiony,tz,true))
            			return false;
            	}
            	else
            	{
            		d += delta_A;
            		next_y += inc_y;
            		//_log.warning("4: next_x:"+next_x+" next_y"+next_y);
            		z += inc_z_directiony;
            		if (!nLOS(x,y,(int)z,0,inc_y,inc_z_directiony,tz,true))
            			return false;
            	}
            }
        }
        return true;
    }
    /*
     *  MoveCheck
     */
    private static Location moveCheck(Location startpoint, Location destiny, int x, int y, double z, int tx, int ty, int tz)
    {
    	int dx = (tx - x);
        int dy = (ty - y);
        final int distance2 = dx*dx+dy*dy;

        if (distance2 == 0)
        	return destiny;
        if (distance2 > 36100) // 190*190*16 = 3040 world coord
        {
            // Avoid too long check
        	// Currently we calculate a middle point
        	// for wyvern users and otherwise for comfort
        	double divider = Math.sqrt((double)30000/distance2);
        	tx = x + (int)(divider * dx);
        	ty = y + (int)(divider * dy);
        	int dz = (tz - startpoint.getZ());
        	tz = startpoint.getZ() + (int)(divider * dz);
        	dx = (tx - x);
        	dy = (ty - y);
            //return startpoint;
        }

        // Increment in Z coordinate when moving along X or Y axis
        // and not straight to the target. This is done because
        // calculation moves either in X or Y direction.
        final int inc_x = sign(dx);
        final int inc_y = sign(dy);
        dx = Math.abs(dx);
        dy = Math.abs(dy);

        //gm.sendMessage("MoveCheck: from X: "+x+ "Y: "+y+ "--->> X: "+tx+" Y: "+ty);

        // next_* are used in NcanMoveNext check from x,y
        int next_x = x;
        int next_y = y;
        double tempz = z;

        // creates path to the target, using only x or y direction
        // calculation stops when next_* == target
        if (dx >= dy)// dy/dx <= 1
        {
        	int delta_A = 2*dy;
        	int d = delta_A - dx;
            int delta_B = delta_A - 2*dx;

            for (int i = 0; i < dx; i++)
            {
            	x = next_x;
            	y = next_y;
            	if (d > 0)
            	{
            		d += delta_B;
            		next_x += inc_x;
            		next_y += inc_y;
            		//_log.warning("2: next_x:"+next_x+" next_y"+next_y);
            		tempz = nCanMoveNext(x,y,(int)z,next_x,next_y,tz);
            		if (tempz == Double.MIN_VALUE)
            			return new Location((x << 4) + L2World.MAP_MIN_X,(y << 4) + L2World.MAP_MIN_Y,(int)z);
            		else z = tempz;
            	}
            	else
            	{
            		d += delta_A;
            		next_x += inc_x;
            		//_log.warning("3: next_x:"+next_x+" next_y"+next_y);
            		tempz = nCanMoveNext(x,y,(int)z,next_x,next_y,tz);
            		if (tempz == Double.MIN_VALUE)
            			return new Location((x << 4) + L2World.MAP_MIN_X,(y << 4) + L2World.MAP_MIN_Y,(int)z);
            		else z = tempz;
            	}
            }
        }
        else
        {
        	int delta_A = 2*dx;
        	int d = delta_A - dy;
            int delta_B = delta_A - 2*dy;
            for (int i = 0; i < dy; i++)
            {
            	x = next_x;
            	y = next_y;
            	if (d > 0)
            	{
            		d += delta_B;
            		next_y += inc_y;
            		next_x += inc_x;
            		//_log.warning("5: next_x:"+next_x+" next_y"+next_y);
            		tempz = nCanMoveNext(x,y,(int)z,next_x,next_y,tz);
            		if (tempz == Double.MIN_VALUE)
            			return new Location((x << 4) + L2World.MAP_MIN_X,(y << 4) + L2World.MAP_MIN_Y,(int)z);
            		else z = tempz;
            	}
            	else
            	{
            		d += delta_A;
            		next_y += inc_y;
            		//_log.warning("6: next_x:"+next_x+" next_y"+next_y);
            		tempz = nCanMoveNext(x,y,(int)z,next_x,next_y,tz);
            		if (tempz == Double.MIN_VALUE)
            			return new Location((x << 4) + L2World.MAP_MIN_X,(y << 4) + L2World.MAP_MIN_Y,(int)z);
            		else z = tempz;
            	}
            }
        }
        if (z == startpoint.getZ()) // geodata hasn't modified Z in any coordinate, i.e. doesn't exist
        	return destiny;
        else
        	return new Location(destiny.getX(),destiny.getY(),(int)z);
    }

    private static byte sign(int x)
    {
    	if (x >= 0)
    		return +1;
        else
        	return -1;
    }

	//GeoEngine
	private static void nInitGeodata()
	{
		int _geoLoadedCount = 0;
		LineNumberReader lnr = null;
		try
		{
			_log.info("Geo Engine: - Loading Geodata...");
			File Data = new File("./data/geodata/geo_index.txt");
			if (!Data.exists())
			{
				_log.warning("Geo Engine: - No geo_index.txt found. Disabling GEODATA for this session.");
				Config.GEODATA = 0;
				return;
			}

			lnr = new LineNumberReader(new BufferedReader(new FileReader(Data)));
		} catch (Exception e) {
			e.printStackTrace();
			throw new Error("Failed to Load geo_index File.");
		}
		String line;
		try
		{
			while ((line = lnr.readLine()) != null) {
				if (line.trim().length() == 0)
					continue;
				StringTokenizer st = new StringTokenizer(line, "_");
				byte rx = Byte.parseByte(st.nextToken());
				byte ry = Byte.parseByte(st.nextToken());
				if (loadGeodataFile(rx,ry))
					_geoLoadedCount++;
			}
			if (_geoLoadedCount == 0)
			{
				_log.warning("Geo Engine: - No geodata files found. Disabling GEODATA for this session.");
				Config.GEODATA = 0;
			}
			else
				_log.info("Geo Engine: - Loaded " + _geoLoadedCount + " geodata archives.");
		} catch (Exception e) {
			e.printStackTrace();
			throw new Error("Failed to Read geo_index File.");
		}
		try
		{
			File geo_bugs = new File("./data/geodata/geo_bugs.txt");

			_geoBugsOut = new BufferedOutputStream(new FileOutputStream(geo_bugs,true));
		} catch (Exception e) {
			e.printStackTrace();
			throw new Error("Failed to Load geo_bugs.txt File.");
		}
	}
	public static void unloadGeodata(byte rx, byte ry)
	{
		short regionoffset = (short)((rx << 5) + ry);
		_geodata.remove(regionoffset);
		_geoBlocks.remove(regionoffset);
	}
	
	/**
	 * @return true if geodata is loaded for this region.
	 */
	public static boolean hasGeodata(byte rx, byte ry)
	{
		short regionoffset = (short)((rx << 5) + ry);
		return _geoBlocks.containsKey(regionoffset);
	}
	public static boolean loadGeodataFile(byte rx, byte ry)
	{
		String fname = "./data/geodata/"+rx+"_"+ry+".l2j";
		short regionoffset = (short)((rx << 5) + ry);
		if (Config.DEBUG)
			_log.info("Geo Engine: - Loading: "+fname+" -> region offset: "+regionoffset+"X: "+rx+" Y: "+ry);
		File Geo = new File(fname);
		if (!Geo.exists())
			return false;
		int size, index = 0, block = 0, flor = 0;
		try {
	        // Create a read-only memory-mapped file
	        FileChannel roChannel = new RandomAccessFile(Geo, "r").getChannel();
			size = (int)roChannel.size();
			MappedByteBuffer geo;
			if (Config.FORCE_GEODATA) //Force O/S to Loads this buffer's content into physical memory.
				//it is not guarantee, because the underlying operating system may have paged out some of the buffer's data
				geo = roChannel.map(FileChannel.MapMode.READ_ONLY, 0, size).load();
			else
				geo = roChannel.map(FileChannel.MapMode.READ_ONLY, 0, size);
			geo.order(ByteOrder.LITTLE_ENDIAN);

			if (size > 196608)
			{
				// Indexing geo files, so we will know where each block starts
				IntBuffer indexs = IntBuffer.allocate(65536);
				while(block < 65536)
			    {
					byte type = geo.get(index);
			        indexs.put(block,index);
					block++;
					index++;
			        if(type == 0)
			        	index += 2; // 1x short
			        else if(type == 1)
			        	index += 128; // 64 x short
			        else
			        {
			            int b;
			            for(b=0;b<64;b++)
			            {
			                byte layers = geo.get(index);
			                index += (layers << 1) + 1;
			                if (layers > flor)
			                     flor = layers;
			            }
			        }
			    }
			// Build lightweight ABlock array for this region
			ABlock[] blocks = new ABlock[65536];
			for (int bi = 0; bi < 65536; bi++)
			{
				int blockStart = indexs.get(bi);
				byte type = geo.get(blockStart);
				if (type == GeoStructure.TYPE_FLAT_L2J)
				{
					short h = geo.getShort(blockStart + 1);
					blocks[bi] = new BlockFlat(h);
				}
				else if (type == GeoStructure.TYPE_COMPLEX_L2J)
				{
					blocks[bi] = new BlockComplex(geo, blockStart);
				}
				else
				{
					blocks[bi] = new BlockMultilayer(geo, blockStart);
				}
			}
			_geoBlocks.put(regionoffset, blocks);
			// Keep _geodata for lightweight block reads (no pre-decoded byte[] anymore)
		}
		else
		{
			// Small file: all blocks are flat (3 bytes each = 1 type + 2 height)
			ABlock[] blocks = new ABlock[65536];
			geo.position(0);
			for (int bi = 0; bi < 65536; bi++)
			{
				geo.get(); // skip type (should be 0)
				blocks[bi] = new BlockFlat(geo.getShort());
			}
			_geoBlocks.put(regionoffset, blocks);
		}
		_geodata.put(regionoffset, geo);

			if (Config.DEBUG)
				_log.info("Geo Engine: - Max Layers: "+flor+" Size: "+size+" Loaded: "+index);
	    } catch (Exception e)
		{
			e.printStackTrace();
			_log.warning("Failed to Load GeoFile at block: "+block+"\n");
			return false;
	    }
	    return true;
	}

	//Geodata Methods
	/**
	 * @param x
	 * @param y
	 * @return Region Offset
	 */
	private static short getRegionOffset(int x, int y)
	{
	    int rx = x >> 11; // =/(256 * 8)
	    int ry = y >> 11;
	    return (short)(((rx+16) << 5) + (ry+10));
	}

	/**
	 * @param pos
	 * @return Block Index: 0-255
	 */	private static int getBlock(int geo_pos)
	{
	    return (geo_pos >> 3) % 256;
	}

	/**
	 * @param pos
	 * @return Cell Index: 0-7
	 */
	private static int getCell(int geo_pos)
	{
	    return geo_pos % 8;
	}

	/**
	 * @param geoX
	 * @param geoY
	 * @return ABlock for the given geodata coordinates (cached / pre-decoded).
	 */
	private static ABlock getABlock(int geoX, int geoY)
	{
		short region = getRegionOffset(geoX, geoY);
		int blockX = getBlock(geoX);
		int blockY = getBlock(geoY);
		ABlock[] blocks = _geoBlocks.get(region);
		if (blocks == null)
			return BlockNull.INSTANCE;
		return blocks[(blockX << 8) + blockY];
	}

	//Geodata Functions

	/**
	 * @param x
	 * @param y
	 * @return Type of geo_block: 0-2
	 */
	private static short nGetType(int x, int y)
	{
		ABlock block = getABlock(x, y);
		if (block instanceof BlockFlat)
			return 0;
		else if (block instanceof BlockComplex)
			return 1;
		else if (block instanceof BlockMultilayer)
			return 2;
		else
			return 0;
	}
	/**
	 * @param x
	 * @param y
	 * @param z
	 * @return Nearest Z
	 */
	private static short nGetHeight(int geox, int geoy, int z)
	{
	    ABlock block = getABlock(geox, geoy);
	    if (!block.hasGeoPos())
	    	return (short)z;
	    return block.getHeightNearest(geox, geoy, z);
	}
	/**
	 * @param x
	 * @param y
	 * @param z
	 * @return One layer higher Z than parameter Z
	 */
	private static short nGetUpperHeight(int geox, int geoy, int z)
	{
	    ABlock block = getABlock(geox, geoy);
	    if (!block.hasGeoPos())
	    	return (short)z;
	    return block.getHeightAbove(geox, geoy, z);
	}
	
	/**
	 * @param x
	 * @param y
	 * @param zmin
	 * @param zmax
	 * @return Z betwen zmin and zmax
	 */
	private static short nGetSpawnHeight(int geox, int geoy, int zmin, int zmax, int spawnid)
	{
		ABlock block = getABlock(geox, geoy);
		if (!block.hasGeoPos())
			return (short)zmin;
		
		int cellX = getCell(geox);
		int cellY = getCell(geoy);
		short temph;
		
		if (block instanceof BlockFlat)
		{
			temph = ((BlockFlat)block).getHeight();
		}
		else if (block instanceof BlockComplex)
		{
			temph = ((BlockComplex)block).getHeight(cellX, cellY);
		}
		else if (block instanceof BlockMultilayer)
		{
			BlockMultilayer bm = (BlockMultilayer)block;
			int layers = bm.getLayerCount(cellX, cellY);
			int refZ = (zmin + zmax) / 2;
			temph = bm.getLayerHeight(cellX, cellY, 0);
			for (int i = 1; i < layers; i++)
			{
				short h = bm.getLayerHeight(cellX, cellY, i);
				if ((refZ - temph) * (refZ - temph) > (refZ - h) * (refZ - h))
					temph = h;
			}
			if (temph > zmax + 200 || temph < zmin - 200)
			{
				if(Config.DEBUG)
					_log.warning("SpawnHeight Error - Couldnt find correct layer to spawn NPC - GeoData or Spawnlist Bug!: zmin: "+zmin+" zmax: "+zmax+" value: "+temph+" SpawnId: "+spawnid+" at: "+geox+" : "+geoy);
				return (short)zmin;
			}
			if (temph > zmax + 1000 || temph < zmin - 1000)
			{
				if(Config.DEBUG)
					_log.warning("SpawnHeight Error - Spawnlist z value is wrong or GeoData error: zmin: "+zmin+" zmax: "+zmax+" value: "+temph+" SpawnId: "+spawnid+" at: "+geox+" : "+geoy);
				return (short)zmin;
			}
			return temph;
		}
		else
		{
			return (short)zmin;
		}
		
		if (temph > zmax + 1000 || temph < zmin - 1000)
		{
			if(Config.DEBUG)
				_log.warning("SpawnHeight Error - Spawnlist z value is wrong or GeoData error: zmin: "+zmin+" zmax: "+zmax+" value: "+temph+" SpawnId: "+spawnid+" at: "+geox+" : "+geoy);
			return (short)zmin;
		}
		return temph;
	}
	/**
	 * @return Terrain Z at (tx,ty) stepping cell by cell from (x,y,z).
	 */
	private static short nTraceTerrainZ(int geox, int geoy, int z, int tgeox, int tgeoy)
	{
		int dx = tgeox - geox;
		int dy = tgeoy - geoy;
		int steps = Math.max(Math.abs(dx), Math.abs(dy));
		short terrainZ = (short)z;
		if (steps == 0)
			return nGetHeight(geox, geoy, z);
		for (int i = 1; i <= steps; i++)
		{
			int cx = geox + (dx * i) / steps;
			int cy = geoy + (dy * i) / steps;
			terrainZ = nGetHeight(cx, cy, terrainZ);
		}
		return terrainZ;
	}

	/**
	 * @param x
	 * @param y
	 * @param z
	 * @param tx
	 * @param ty
	 * @param tz
	 * @return True if char can move to (tx,ty,tz)
	 */
	private static double nCanMoveNext(int x, int y, int z, int tx, int ty, int tz)
	{
		ABlock block = getABlock(x, y);
		if (!block.hasGeoPos())
			return z;
		
		int cellX = getCell(x);
		int cellY = getCell(y);
		
		if (block instanceof BlockFlat)
		{
			return ((BlockFlat)block).getHeight();
		}
		else if (block instanceof BlockComplex)
		{
			BlockComplex bc = (BlockComplex)block;
			short height = bc.getHeight(cellX, cellY);
			byte nswe = bc.getNswe(cellX, cellY);
			if (!checkNSWE((short)(nswe & 0xFF), x, y, tx, ty))
			{
				if(Config.MOVE_DEBUG)
					_log.info("[GEO] nCanMoveNext BLOCKED (complex): from=("+x+","+y+","+z+") to=("+tx+","+ty+") NSWE="+nswe+" type=complex height="+height);
				return Double.MIN_VALUE;
			}
			return height;
		}
		else if (block instanceof BlockMultilayer)
		{
			BlockMultilayer bm = (BlockMultilayer)block;
			double result = bm.checkMove(cellX, cellY, z, tx - x, ty - y);
			if (result == Double.MIN_VALUE && Config.MOVE_DEBUG)
				_log.info("[GEO] nCanMoveNext BLOCKED (multilevel): from=("+x+","+y+","+z+") to=("+tx+","+ty+")");
			return result;
		}
		return z;
	}
	/**
	 * @param x
	 * @param y
	 * @param z
	 * @param inc_x
	 * @param inc_y
	 * @param tz
	 * @return True if Char can see target
	 */
	private static boolean nLOS(int x, int y, int z, int inc_x, int inc_y, double inc_z, int tz, boolean debug)
	{
		ABlock block = getABlock(x, y);
		if (!block.hasGeoPos())
			return true;
		
		int cellX = getCell(x);
		int cellY = getCell(y);
		
		if (block instanceof BlockFlat)
		{
			if (debug)
				_log.warning("flatheight:"+((BlockFlat)block).getHeight());
			return true;
		}
		else if (block instanceof BlockComplex)
		{
			BlockComplex bc = (BlockComplex)block;
			short height = bc.getHeight(cellX, cellY);
			short nswe = (short)(bc.getNswe(cellX, cellY) & 0xFF);
			if (!checkNSWE(nswe, x, y, x + inc_x, y + inc_y))
			{
				if (debug) _log.warning("height:"+height+" z"+z);
				if (z < nGetUpperHeight(x + inc_x, y + inc_y, height))
					return false;
				return true;
			}
			return true;
		}
		else if (block instanceof BlockMultilayer)
		{
			BlockMultilayer bm = (BlockMultilayer)block;
			int layers = bm.getLayerCount(cellX, cellY);
			
			if (layers <= 0 || layers > 125)
				return false;
			
			// Find which layer we're on and above/below
			short upperHeight = Short.MAX_VALUE;
			short lowerHeight = Short.MIN_VALUE;
			byte currentNSWE = 0;
			boolean highestlayer = true;
			
			// Layers stored highest to lowest
			for (int i = 0; i < layers; i++)
			{
				short h = bm.getLayerHeight(cellX, cellY, i);
				if (z > h)
				{
					lowerHeight = h;
					currentNSWE = bm.getLayerNSWE(cellX, cellY, i);
					highestlayer = false;
					break;
				}
				else
				{
					upperHeight = h;
				}
			}
			
			if (debug) _log.warning("z:"+z+" x: "+cellX+" y:"+cellY+" la "+layers+" lo:"+lowerHeight+" up:"+upperHeight);
			// Check if LOS goes under a layer/floor
			if ((z - upperHeight) < -10 && (z - upperHeight) > inc_z - 10 && (z - lowerHeight) > 40)
			{
				if (debug) _log.warning("false, incz"+inc_z);
				return false;
			}
			// or there's a fence/wall ahead when we're not on highest layer
			if (!highestlayer)
			{
				if (!checkNSWE((short)(currentNSWE & 0xFF), x, y, x + inc_x, y + inc_y))
				{
					if (debug) _log.warning("block and next in x"+inc_x+" y"+inc_y+" is:"+nGetUpperHeight(x + inc_x, y + inc_y, lowerHeight));
					if (z < nGetUpperHeight(x + inc_x, y + inc_y, lowerHeight))
						return false;
					return true;
				}
				return true;
			}
			if (!checkNSWE((short)(currentNSWE & 0xFF), x, y, x + inc_x, y + inc_y))
			{
				if (z < nGetUpperHeight(x + inc_x, y + inc_y, lowerHeight))
					return false;
				return true;
			}
			return true;
		}
		return true;
	}
	/**
	 * @param x
	 * @param y
	 * @param z
	 * @return NSWE: 0-15
	 */
	private short nGetNSWE(int x, int y, int z)
	{
		ABlock block = getABlock(x, y);
		if (!block.hasGeoPos())
			return 15;
		return (short)(block.getNsweNearest(x, y, z) & 0xFF);
	}

	/**
	 * @param NSWE
	 * @param x
	 * @param y
	 * @param tx
	 * @param ty
	 * @return True if NSWE dont block given direction
	 */
	private static boolean checkNSWE(short NSWE, int x, int y, int tx, int ty)
    {
        //Check NSWE
	    if(NSWE == 15)
	       return true;

	    boolean canX = true;
	    boolean canY = true;

	    if(tx > x)
	    	canX = (NSWE & _e) != 0;
	    else if (tx < x)
	    	canX = (NSWE & _w) != 0;

	    if (ty > y)
	    	canY = (NSWE & _s) != 0;
	    else if (ty < y)
	    	canY = (NSWE & _n) != 0;

	    if (!canX || !canY)
	    	return false;

	    if (tx != x && ty != y)
	    {
	    	if (ty > y)
	    	{
	    		if (tx > x)
	    			return checkNSWE(NSWE, x, y, tx, y) && checkNSWE(NSWE, x, y, x, ty);
	    		else
	    			return checkNSWE(NSWE, x, y, tx, y) && checkNSWE(NSWE, x, y, x, ty);
	    	}
	    	else
	    	{
	    		if (tx > x)
	    			return checkNSWE(NSWE, x, y, tx, y) && checkNSWE(NSWE, x, y, x, ty);
	    		else
	    			return checkNSWE(NSWE, x, y, tx, y) && checkNSWE(NSWE, x, y, x, ty);
	    	}
	    }
	    return true;
    }
}