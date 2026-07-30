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
package net.sf.l2j.loginserver;

import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.mmocore.network.IAcceptFilter;
import org.mmocore.network.IClientFactory;
import org.mmocore.network.IMMOExecutor;
import org.mmocore.network.MMOConnection;
import org.mmocore.network.ReceivablePacket;

import net.sf.l2j.loginserver.serverpackets.Init;

/**
 * Login Server SelectorHelper — migrado para Virtual Threads. O pool de
 * plataforma foi substitu�do por um executor de virtual threads, eliminando o
 * overhead de pools fixos e permitindo concorr�ncia sob demanda sem consumir
 * threads do SO.
 *
 * @author KenM
 */
public class SelectorHelper implements IMMOExecutor<L2LoginClient>,
        IClientFactory<L2LoginClient>, IAcceptFilter
{
	private final ExecutorService _executor;

	public SelectorHelper()
	{
		_executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("VT-Login-").factory());
	}

	@Override
	public void execute(ReceivablePacket<L2LoginClient> packet)
	{
		_executor.execute(packet);
	}

	@Override
	public L2LoginClient create(MMOConnection<L2LoginClient> con)
	{
		L2LoginClient client = new L2LoginClient(con);
		client.sendPacket(new Init(client));
		return client;
	}

	@Override
	public boolean accept(SocketChannel sc)
	{
		return !LoginController.getInstance().isBannedAddress(sc.socket().getInetAddress());
	}

}
