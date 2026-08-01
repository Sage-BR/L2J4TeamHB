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
import net.sf.l2j.gameserver.TaskPriority;
import net.sf.l2j.gameserver.geoeditorcon.GeoEditorListener;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.serverpackets.PartyMemberPosition;
import net.sf.l2j.gameserver.serverpackets.ValidateLocation;
import net.sf.l2j.gameserver.serverpackets.ValidateLocationInVehicle;

/**
 * Aligned with VERGE SOURCE 2.2 pattern: simple desync check vs speed.
 * No Z override, no terrain snap, no geometry stuck recovery.
 * The geodata movement system (GeoEngine) handles height/NSWE validation.
 */
public class ValidatePosition extends L2GameClientPacket
{
	private static Logger _log = Logger.getLogger(ValidatePosition.class.getName());

	private static final String _C__48_VALIDATEPOSITION = "[C] 48 ValidatePosition";

	/** urgent messages, execute immediatly */
	public TaskPriority getPriority()
	{
		return TaskPriority.PR_HIGH;
	}

	private int _x;

	private int _y;

	private int _z;

	private int _heading;

	@SuppressWarnings("unused")
	private int _data;

	@Override
	protected void readImpl()
	{
		_x = readD();
		_y = readD();
		_z = readD();
		_heading = readD();
		_data = readD();
	}

	@Override
	protected void runImpl()
	{
		L2PcInstance activeChar = getClient().getActiveChar();
		if (activeChar == null || activeChar.isTeleporting())
		{
			return;
		}

		// Disable validation during fall to avoid "jumping" (VERGE pattern).
		if (activeChar.isFalling(_z))
		{
			return;
		}

		// Store original server position for reference.
		int realX0 = activeChar.getX();
		int realY0 = activeChar.getY();
		int realZ0 = activeChar.getZ();

		if (Config.MOVE_DEBUG)
		{
			_log.info("[MOVE] ValidatePosition IN  client=(" + _x + "," + _y
			        + "," + _z + ") server=(" + realX0 + "," + realY0 + ","
			        + realZ0 + ") heading=" + _heading + " moving="
			        + activeChar.isMoving() + " flying="
			        + activeChar.isFlying());
		}

		// Store client-reported position for desync calculation.
		activeChar.setClientX(_x);
		activeChar.setClientY(_y);
		activeChar.setClientZ(_z);
		activeChar.setClientHeading(_heading);

		// Save last positions for stuck detection and other systems.
		activeChar.setLastClientPosition(_x, _y, _z);
		activeChar.setLastServerPosition(realX0, realY0, realZ0);

		// VERGE pattern: simple speed-based desync check.
		// For boats: send back if desync > 500.
		// For regular movement: send back if desync > actualSpeed.
		// This is the ONLY validation needed — geodata movement handles
		// height/NSWE at the GeoEngine level.
		double actualSpeed;
		double dist;

		boolean isInBoat = activeChar.isInBoat();
		if (isInBoat)
		{
			actualSpeed = 500;
			dist = Math.sqrt(Math.pow(_x - realX0, 2) + Math.pow(_y - realY0, 2));

			if (dist > actualSpeed)
			{
				sendPacket(new ValidateLocationInVehicle(activeChar));
				return;
			}
			dist = 0; // skip ground logic below
		}
		else
		{
			actualSpeed = activeChar.getStat().getMoveSpeed();
			if (actualSpeed <= 0)
			{
				// Cannot move (overloaded etc), skip validation.
				return;
			}

			// For ground movement use 2D distance; for flying use 3D.
			if (activeChar.isFlying())
			{
				dist = Math.sqrt(Math.pow(_x - realX0, 2) + Math.pow(_y - realY0, 2)
			        + Math.pow(_z - realZ0, 2));
			}
			else
			{
				dist = Math.sqrt(Math.pow(_x - realX0, 2) + Math.pow(_y - realY0, 2));
			}

			// Latency-tolerant desync check (time-based).
			// The client reports its position roughly once per second, so a
			// legitimate client's reported position can be up to
			// speed * (reportInterval + networkLatency) behind the server.
			// Use the elapsed time since the last report as the interval
			// proxy plus a 1-second latency buffer, capped at 3 seconds of
			// movement so sustained speedhacks are still caught.
			long now = System.currentTimeMillis();
			// Math.max guards against clock adjustments (NTP) making the
			// elapsed negative, which would shrink allowedDist below
			// actualSpeed and cause a spurious ROLLBACK.
			long elapsed = Math.max(0L, now - activeChar.getLastValidateTime());
			activeChar.setLastValidateTime(now);

			double allowedDist = actualSpeed * (1.0 + Math.min(elapsed, 2000L) / 1000.0);
			if (allowedDist > actualSpeed * 3.0)
			{
				allowedDist = actualSpeed * 3.0;
			}

			// Anti-cheat: the time-based tolerance stands whenever the client is
			// at-or-behind the server along the authoritative movement
			// direction — a stale report is never ahead of the server. Only a
			// client strictly AHEAD of the server by more than one second of
			// movement (the unambiguous speedhack signature) falls back to the
			// strict speed threshold. The > actualSpeed threshold (instead of
			// > 0) avoids spurious ROLLBACKs at pathfinding corners: at a
			// sharp (>90°) turn a legit client behind along the old segment
			// projects positive onto the new segment direction, but by less
			// than a second of movement.
			if (activeChar.isMoving())
			{
				final double toDestX = activeChar.getXdestination() - realX0;
				final double toDestY = activeChar.getYdestination() - realY0;
				final double distToDest = Math.sqrt(toDestX * toDestX + toDestY * toDestY);
				if (distToDest > 0)
				{
					final double clientAhead = ((_x - realX0) * toDestX + (_y - realY0) * toDestY) / distToDest;
					if (clientAhead > actualSpeed)
					{
						allowedDist = actualSpeed;
					}
				}
				else
				{
					// Moving but no usable direction (arriving) — strict only.
					allowedDist = actualSpeed;
				}
			}
			else
			{
				// Not moving: a large offset is a teleport, not latency.
				allowedDist = actualSpeed;
			}

			if (dist > allowedDist)
			{
				// Desync too large — send ValidateLocation to snap client back.
				if (Config.MOVE_DEBUG)
				{
					_log.info("[MOVE] ROLLBACK (desync>" + (int) allowedDist
					        + "): client=(" + _x + "," + _y + "," + _z
					        + ") server=(" + realX0 + "," + realY0 + ","
					        + realZ0 + ") distance=" + dist);
				}
				activeChar.sendPacket(new ValidateLocation(activeChar));
			}
			else
			{
				// Trust client position — setXYZ directly (VERGE pattern).
				if (Config.MOVE_DEBUG)
				{
					_log.info("[MOVE] ValidatePosition ACCEPTED: client=(" + _x
					        + "," + _y + "," + _z + ") server=(" + realX0 + ","
					        + realY0 + "," + realZ0 + ") distance=" + dist
					        + " speed=" + actualSpeed);
				}
				activeChar.setXYZ(_x, _y, _z);
			}
		}

		// Broadcast party member position (standard L2J).
		if (activeChar.getParty() != null)
		{
			activeChar.getParty().broadcastToPartyMembers(activeChar, new PartyMemberPosition(activeChar));
		}

		if (Config.ACCEPT_GEOEDITOR_CONN)
		{
			if (GeoEditorListener.getInstance().getThread() != null
			        && GeoEditorListener.getInstance().getThread().isWorking()
			        && GeoEditorListener.getInstance().getThread().isSend(activeChar))
			{
				GeoEditorListener.getInstance().getThread().sendGmPosition(_x, _y, (short) _z);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 *
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
		return _x == pos._x && _y == pos._y && _z == pos._z
		        && _heading == pos._heading;
	}
}
