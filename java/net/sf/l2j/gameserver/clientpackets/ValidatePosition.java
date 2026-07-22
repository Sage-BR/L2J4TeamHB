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
import net.sf.l2j.gameserver.Universe;
import net.sf.l2j.gameserver.geoeditorcon.GeoEditorListener;
import net.sf.l2j.gameserver.model.L2Character;
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

        if (Config.GEODATA > 0 
        		&& (activeChar.isInOlympiadMode() || activeChar.isInsideZone(L2Character.ZONE_SIEGE))
        		&& !activeChar.isFlying()
        		&& GeoData.getInstance().hasGeo(_x, _y))
        {
        	// check Z coordinate sent by client
        	short geoHeight = GeoData.getInstance().getSpawnHeight(_x, _y, activeChar.getZ()-30, activeChar.getZ()+30, activeChar.getObjectId());
        	if (Math.abs(geoHeight - _z) > 15)
        	{
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
                if (activeChar.isInBoat())
                    sendPacket(new ValidateLocationInVehicle(activeChar));
                else
                    activeChar.sendPacket(new ValidateLocation(activeChar));
            }
            else
            {
                // Within tolerance: trust client
                activeChar.setXYZ(_x, _y, _z);
            }
        }

        activeChar.setLastClientPosition(_x, _y, _z);
        activeChar.setLastServerPosition(activeChar.getX(), activeChar.getY(), activeChar.getZ());
        // --- fim sincronização ---
        
		if(activeChar.getParty() != null)
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
