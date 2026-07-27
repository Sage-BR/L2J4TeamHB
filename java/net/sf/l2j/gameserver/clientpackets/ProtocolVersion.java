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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.serverpackets.KeyPacket;
import net.sf.l2j.protection.hwid.HwidManager;

public final class ProtocolVersion extends L2GameClientPacket
{
	private static final String _C__00_PROTOCOLVERSION = "[C] 00 ProtocolVersion";
	static Logger _log = Logger.getLogger(ProtocolVersion.class.getName());

	private static final byte[] HWID_MAGIC = { 'B', 'H', 'W', 'D' };

    private int _version;
    private byte[] _extraData;

	@Override
	protected void readImpl()
	{
		_version = readD();

		if (_buf.remaining() > 0)
		{
			_extraData = new byte[_buf.remaining()];
			readB(_extraData);
		}
	}

	@Override
	protected void runImpl()
	{
		// this packet is never encrypted
		if (_version == -2)
		{
            if (Config.DEBUG) _log.info("Ping received");
			// this is just a ping attempt from the new C2 client
            getClient().closeNow();
		}
        else if (_version < Config.MIN_PROTOCOL_REVISION || _version > Config.MAX_PROTOCOL_REVISION)
        {
            _log.info("Client: "+getClient().toString()+" -> Protocol Revision: " + _version + " is invalid. Minimum is "+Config.MIN_PROTOCOL_REVISION+" and Maximum is "+Config.MAX_PROTOCOL_REVISION+" are supported. Closing connection.");
            _log.warning("Wrong Protocol Version "+_version);
            getClient().closeNow();
        }
        else
        {
        	if (Config.DEBUG)
        		_log.fine("Client Protocol Revision is ok: "+_version);

        	final String payload = extractPayloadFromExtra(_extraData);
        	if (payload == null || payload.isEmpty())
        	{
                StringBuilder sb = new StringBuilder();
                for (byte b : _extraData) sb.append(String.format("%02X ", b));
        		_log.warning("HWID payload nao encontrado no ProtocolVersion. ExtraData (hex): [" + sb.toString() + "]");
        		getClient().closeNow();
        		return;
        	}

        	String[] parts = payload.split("\\|");

        	String cpu = parts[0];
        	String hdd = parts[1];
        	String mac = parts[2];
        	String key = parts[3];

        	final boolean ok = HwidManager.getInstance().validateClient(getClient(), hdd, mac, cpu, key);

        	if (!ok)
        	{
        		_log.warning("HWID INVALIDO - CONEXAO BLOQUEADA");
        		getClient().closeNow();
        		return;
        	}

        	getClient().setHwidAuthed(true);
        	KeyPacket pk = new KeyPacket(getClient().enableCrypt());
        	getClient().sendPacket(pk);
        }
	}

	private static String extractPayloadFromExtra(byte[] extra)
	{
		if (extra == null || extra.length == 0)
			return null;

		final int start = indexOf(extra, HWID_MAGIC);
		if (start < 0)
			return null;

		final int lenPos = start + 4;
		if (extra.length < lenPos + 4)
			return null;

		final ByteBuffer lenBuffer = ByteBuffer.wrap(extra, lenPos, 4).order(ByteOrder.LITTLE_ENDIAN);
		final int payloadLen = lenBuffer.getInt();

		if (payloadLen <= 0)
			return null;

		final int payloadStart = lenPos + 4;
		if (payloadStart + payloadLen > extra.length)
			return null;

		int realLen = payloadLen;
		if (extra[payloadStart + payloadLen - 1] == 0)
			realLen--;

		if (realLen <= 0)
			return null;

		return new String(extra, payloadStart, realLen, StandardCharsets.US_ASCII).trim();
	}

	private static int indexOf(byte[] data, byte[] pattern)
	{
		if (data == null || pattern == null || pattern.length == 0 || data.length < pattern.length)
			return -1;

		outer:
		for (int i = 0; i <= data.length - pattern.length; i++)
		{
			for (int j = 0; j < pattern.length; j++)
			{
				if (data[i + j] != pattern[j])
					continue outer;
			}
			return i;
		}
		return -1;
	}

	@Override
	public String getType()
	{
		return _C__00_PROTOCOLVERSION;
	}
}
