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
package net.sf.l2j.gameserver.clientpackets;

import java.util.logging.Logger;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.GeoData;
import net.sf.l2j.gameserver.TaskPriority;
import net.sf.l2j.gameserver.geoeditorcon.GeoEditorListener;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.serverpackets.PartyMemberPosition;
import net.sf.l2j.gameserver.serverpackets.ValidateLocation;
import net.sf.l2j.gameserver.serverpackets.ValidateLocationInVehicle;

/**
 * This class ...
 *
 * @version $Revision: 1.13.4.7 $ $Date: 2005/03/27 15:29:30 $
 */
public class ValidatePosition extends L2GameClientPacket
{
    private static Logger _log = Logger.getLogger(ValidatePosition.class.getName());
    private static final String _C__48_VALIDATEPOSITION = "[C] 48 ValidatePosition";

    /** urgent messages, execute immediatly */
    public TaskPriority getPriority() { return TaskPriority.PR_HIGH; }

    private int _x;
    private int _y;
    private int _z;
    private int _heading;
    @SuppressWarnings("unused")
    private int _data;

    @Override
	protected void readImpl()
    {
        _x  = readD();
        _y  = readD();
        _z  = readD();
        _heading  = readD();
        _data  = readD();
    }

    @Override
	protected void runImpl()
    {
		L2PcInstance activeChar = getClient().getActiveChar();
		if (activeChar == null || activeChar.isTeleporting()) return;

		int realX0 = activeChar.getX();
		int realY0 = activeChar.getY();
		int realZ0 = activeChar.getZ();
		if (Config.MOVE_DEBUG)
			_log.info("[MOVE] ValidatePosition IN  client=("+_x+","+_y+","+_z+") server=("+realX0+","+realY0+","+realZ0+") heading="+_heading+" moving="+activeChar.isMoving()+" flying="+activeChar.isFlying());

        // Salva Z original do cliente (antes do Z override) para deteccao de stuck
        final int originalClientZ = _z;
        
        if (Config.GEODATA > 0 
        		&& !activeChar.isFlying()
        		&& GeoData.getInstance().hasGeo(_x, _y))
        {
        	// check Z coordinate sent by client
        	short geoHeight = GeoData.getInstance().getSpawnHeight(_x, _y, activeChar.getZ()-30, activeChar.getZ()+30, activeChar.getObjectId());
        	if (Math.abs(geoHeight - _z) > 15)
        	{
        		if (Config.MOVE_DEBUG)
        			_log.info("[MOVE] Z override by geoHeight: client_z="+_z+" geoHeight="+geoHeight+" diff="+(geoHeight-_z)+" at ("+_x+","+_y+")");
        		// causes mild flashing in the middle of a drop from a castle wall for example
        		_z = geoHeight;
        		// System.out.println("Spawnheight validation diff="+Math.abs(geoHeight - _z));
        	}
        }
        // --- CoordSynchronize unificado (padrão Brproject: threshold 64 + speed 2x, sem broadcast MoveToLocation) ---
        activeChar.setClientX(_x);
        activeChar.setClientY(_y);
        activeChar.setClientZ(_z);
        activeChar.setClientHeading(_heading);
        int realX = activeChar.getX();
        int realY = activeChar.getY();
        int realZ = activeChar.getZ();

        if (_x == 0 && _y == 0) 
        {
        	if (realX != 0) // in this case this seems like a client error
        		return;
        }

        activeChar.setLastClientPosition(_x, _y, _z);
        activeChar.setLastServerPosition(activeChar.getX(), activeChar.getY(), activeChar.getZ());

        // If falling, skip position validation to avoid "jumping" (L2J HorridoJoho pattern).
        if (GeoData.getInstance().hasGeo(realX, realY) && activeChar.isFalling(_z))
        {
            if (Config.MOVE_DEBUG)
                _log.info("[MOVE] ValidatePosition SKIP (isFalling) client=("+_x+","+_y+","+_z+") server=("+realX+","+realY+","+realZ+")");
            return;
        }

        // Sync thresholds aligned with Brproject (ValidatePosition.java).
        // - MAX_DISTANCE_DIFF: absolute cap on 3D divergence between client and server.
        // - MAX_SPEED_CHECK: multiplier over the character's move speed used as per-tick travel cap.
        // Above either limit, the server authoritatively rejects the client position and
        // sends ValidateLocation so the client snaps back. This prevents "fast/jumpy" movement
        // and out-of-range attacks caused by trusting large client predictions.
        final double MAX_DISTANCE_DIFF = 64.0;
        final double MAX_SPEED_CHECK = 2.0;

        double dx = _x - realX;
        double dy = _y - realY;
        double dz = _z - realZ;
        double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);

        if (distance > MAX_DISTANCE_DIFF)
        {
            // Emergency: too far — force server authoritative position
            if (Config.MOVE_DEBUG)
                _log.info("[MOVE] ROLLBACK (distance>"+MAX_DISTANCE_DIFF+"): client=("+_x+","+_y+","+_z+") server=("+realX+","+realY+","+realZ+") distance="+distance);
            if (activeChar.isInBoat())
                sendPacket(new ValidateLocationInVehicle(activeChar));
            else
                activeChar.sendPacket(new ValidateLocation(activeChar));
        }
        else
        {
            double moveSpeed = activeChar.getStat().getMoveSpeed();
            double maxMovePerTick = moveSpeed * MAX_SPEED_CHECK;
            double planarMove = Math.sqrt(dx*dx + dy*dy);
            if (planarMove > maxMovePerTick)
            {
                // Speed check failed — reject client prediction (no setXYZ)
                if (Config.MOVE_DEBUG)
                    _log.info("[MOVE] ROLLBACK (speed check): planarMove="+planarMove+" > max="+maxMovePerTick+" (speed="+moveSpeed+" x"+MAX_SPEED_CHECK+") client=("+_x+","+_y+","+_z+") server=("+realX+","+realY+","+realZ+")");
                if (activeChar.isInBoat())
                    sendPacket(new ValidateLocationInVehicle(activeChar));
                else
                    activeChar.sendPacket(new ValidateLocation(activeChar));
            }
            else
            {
                // Trust client position (Brproject pattern — no geo-collision check per tick)
                if (Config.MOVE_DEBUG)
                    _log.info("[MOVE] ValidatePosition ACCEPTED: client=("+_x+","+_y+","+_z+") server=("+realX+","+realY+","+realZ+") distance="+distance+" planarMove="+planarMove+"/"+maxMovePerTick);
                activeChar.setXYZ(_x, _y, _z);
            }
        }

        // --- fim sincronização ---

        // Brproject: terrain height snap — if client Z is slightly below walkable terrain
        // after all validations, correct it. Prevents "below ground" artifacts when routing
        // through geodata gaps (columns, bridges, ramps, etc).
        if (Config.GEODATA > 0 && !activeChar.isFlying())
        {
            // Use a wider scan window to ensure we find the ramp layer even when
            // the client is several units offset from the geo surface.
            int terrainZ = GeoData.getInstance().getSpawnHeight(_x, _y, _z - 80, _z + 80, activeChar.getObjectId());
            int heightDiff = terrainZ - _z;
            if (heightDiff > 5 && heightDiff < 80)
            {
                if (Config.MOVE_DEBUG)
                    _log.info("[MOVE] TERRAIN SNAP: client_z="+_z+" terrainZ="+terrainZ+" diff="+heightDiff+" at ("+_x+","+_y+")");
                activeChar.setXYZ(_x, _y, terrainZ);
                activeChar.sendPacket(new ValidateLocation(activeChar));
            }
            else if (Config.MOVE_DEBUG && heightDiff != 0)
            {
                _log.info("[MOVE] TERRAIN no-snap: heightDiff="+heightDiff+" (outside 5-80 range) client_z="+_z+" terrainZ="+terrainZ+" at ("+_x+","+_y+")");
            }
            
            // --- GeomStuck: safety net for characters inside walls/ramps/floors ---
            // Usa o Z original do cliente (antes do override) para detectar quando o cliente
            // esta reportando uma posicao muito diferente do terreno. Isso pega casos onde
            // o personagem spawnou dentro de geometria (rampa/parede) mesmo depois do Z override.
            // Nota: diferencas de 30+ indicam que o cliente esta flutuando sobre o terreno
            // (colision height + geo gap). Com threshold 30, diffs como 39 sao detectados.
            int checkZ = (Math.abs(originalClientZ - realZ) <= 30) ? originalClientZ : realZ;
            int serverTerrainZ = GeoData.getInstance().getSpawnHeight(realX, realY, checkZ - 500, checkZ + 500, activeChar.getObjectId());
            if (Math.abs(checkZ - serverTerrainZ) > 30 && !activeChar.isFalling(originalClientZ))
            {
                if (activeChar.checkGeometryStuck() && Config.MOVE_DEBUG)
                    _log.info("[MOVE] GEOMETRY STUCK RECOVERY: player=("+realX+","+realY+","+realZ+") clientZ="+originalClientZ+" terrainZ="+serverTerrainZ);
            }
        }

		if(activeChar.getParty() != null)
			activeChar.getParty().broadcastToPartyMembers(activeChar,new PartyMemberPosition(activeChar));

		if (Config.ACCEPT_GEOEDITOR_CONN)
            if (GeoEditorListener.getInstance().getThread() != null  && GeoEditorListener.getInstance().getThread().isWorking()  && GeoEditorListener.getInstance().getThread().isSend(activeChar))
            	GeoEditorListener.getInstance().getThread().sendGmPosition(_x,_y,(short)_z);
    }

    /* (non-Javadoc)
     * @see net.sf.l2j.gameserver.clientpackets.ClientBasePacket#getType()
     */
    @Override
	public String getType()
    {
        return _C__48_VALIDATEPOSITION;
    }

    @Deprecated
    public boolean equal(ValidatePosition pos)
    {
        return _x == pos._x && _y == pos._y && _z == pos._z && _heading == pos._heading;
    }
}
