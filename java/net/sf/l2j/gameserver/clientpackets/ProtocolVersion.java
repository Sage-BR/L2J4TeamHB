package net.sf.l2j.gameserver.clientpackets;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import Guard.ConfigProtection;
import Guard.hwidmanager.HWIDManager;
import Guard.hwidmanager.HwidSession;
import net.sf.l2j.Config;
import net.sf.l2j.gameserver.serverpackets.KeyPacket;

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

		if (getAvaliableBytes() > 0)
		{
			_extraData = new byte[getAvaliableBytes()];
			readB(_extraData);
		}
	}

	@Override
	protected void runImpl()
	{
		if (_version == -2)
		{
			if (Config.DEBUG) _log.info("Ping received");
			getClient().closeNow();
			return;
		}

		if (_version < Config.MIN_PROTOCOL_REVISION || _version > Config.MAX_PROTOCOL_REVISION)
		{
			_log.info("Client: " + getClient().toString() + " -> Protocol Revision: " + _version + " is invalid.");
			getClient().closeNow();
			return;
		}

		if (Config.DEBUG)
			_log.fine("Client Protocol Revision is ok: " + _version);

		// Extract and validate HWID payload
		if (ConfigProtection.ALLOW_GUARD_SYSTEM)
		{
			final String payload = extractPayloadFromExtra(_extraData);
			if (payload == null || payload.isEmpty())
			{
				_log.warning("HWID payload not found in ProtocolVersion from " + getClient().toString());
				getClient().closeNow();
				return;
			}

			String[] parts = payload.split("\\|");
			if (parts.length < 4)
			{
				_log.warning("Invalid HWID payload format from " + getClient().toString());
				getClient().closeNow();
				return;
			}

			String cpu = parts[0];
			String hdd = parts[1];
			String mac = parts[2];
			String key = parts[3];

			final String selectedHWID = HWIDManager.getInstance().validateClient(getClient(), hdd, mac, cpu, key);
			if (selectedHWID == null)
			{
				_log.warning("HWID INVALID - Connection blocked for " + getClient().toString());
				getClient().closeNow();
				return;
			}

			getClient().setHWID(selectedHWID);
			getClient().setHwidSession(new HwidSession(cpu, hdd, mac));
			getClient().setHwidAuthed(true);

			// Register HWID in DB immediately on validation
			HWIDManager.updateHWIDInfo(getClient());
		}

		KeyPacket pk = new KeyPacket(getClient().enableCrypt());
		getClient().sendPacket(pk);
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
