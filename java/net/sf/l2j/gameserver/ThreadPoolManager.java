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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.network.L2GameClient;

import org.mmocore.network.ReceivablePacket;

/**
 * <p>This class is made to handle all the ThreadPools used in L2j.</p>
 * <p>Scheduled Tasks can either be sent to a {@link #_generalScheduledThreadPool "general"} or {@link #_effectsScheduledThreadPool "effects"} {@link ScheduledThreadPoolExecutor ScheduledThreadPool}:
 * The "effects" one is used for every effects (skills, hp/mp regen ...) while the "general" one is used for
 * everything else that needs to be scheduled.<br>
 * There also is an {@link #_aiScheduledThreadPool "ai"} {@link ScheduledThreadPoolExecutor ScheduledThreadPool} used for AI Tasks.</p>
 * <p>Tasks can be sent to {@link ScheduledThreadPoolExecutor ScheduledThreadPool} either with:
 * <ul>
 * <li>{@link #scheduleEffect(Runnable, long)} : for effects Tasks that needs to be executed only once.</li>
 * <li>{@link #scheduleGeneral(Runnable, long)} : for scheduled Tasks that needs to be executed once.</li>
 * <li>{@link #scheduleAi(Runnable, long)} : for AI Tasks that needs to be executed once</li>
 * </ul>
 * or
 * <ul>
 * <li>{@link #scheduleEffectAtFixedRate(Runnable, long, long)(Runnable, long)} : for effects Tasks that needs to be executed periodicaly.</li>
 * <li>{@link #scheduleGeneralAtFixedRate(Runnable, long, long)(Runnable, long)} : for scheduled Tasks that needs to be executed periodicaly.</li>
 * <li>{@link #scheduleAiAtFixedRate(Runnable, long, long)(Runnable, long)} : for AI Tasks that needs to be executed periodicaly</li>
 * </ul></p>
 *
 * <p>For all Tasks that should be executed with no delay asynchronously in a ThreadPool there also are usual {@link ThreadPoolExecutor ThreadPools}
 * that can grow/shrink according to their load.:
 * <ul>
 * <li>{@link #_generalPacketsThreadPool GeneralPackets} where most packets handler are executed.</li>
 * <li>{@link #_ioPacketsThreadPool I/O Packets} where all the i/o packets are executed.</li>
 * <li>There will be an AI ThreadPool where AI events should be executed</li>
 * <li>A general ThreadPool where everything else that needs to run asynchronously with no delay should be executed ({@link net.sf.l2j.gameserver.model.actor.knownlist KnownList} updates, SQL updates/inserts...)?</li>
 * </ul>
 * </p>
 * <p><b>VT (Virtual Thread) Migration:</b><br>
 * All non-scheduled executors now use {@link java.lang.VirtualThread} via
 * {@link Executors#newVirtualThreadPerTaskExecutor()}. This eliminates
 * platform-thread pooling overhead, reduces memory consumption, and allows
 * massive concurrency without bloating the OS thread count. Tasks that block
 * on I/O (database queries, network writes) automatically yield the carrier
 * thread, dramatically improving scalability under load.</p>
 *
 * @author -Wooden-
 */
public class ThreadPoolManager
{
    protected static final Logger _log = Logger.getLogger(ThreadPoolManager.class.getName());
    
	private static ThreadPoolManager _instance;
	
	private ScheduledThreadPoolExecutor _effectsScheduledThreadPool;
	private ScheduledThreadPoolExecutor _generalScheduledThreadPool;
	
	private ExecutorService _generalPacketsThreadPool;
	private ExecutorService _ioPacketsThreadPool;
	private ExecutorService _aiThreadPool;
	private ExecutorService _generalThreadPool;

	private ScheduledThreadPoolExecutor _aiScheduledThreadPool;

    /** temp workaround for VM issue */
    private static final long MAX_DELAY = Long.MAX_VALUE/1000000/2;
    
	private boolean _shutdown;

	public static ThreadPoolManager getInstance()
	{
		if(_instance == null)
		{
			_instance = new ThreadPoolManager();
		}
		return _instance;
	}

	private ThreadPoolManager()
	{
		// Scheduled pools — kept as platform threads for precise timing
		_effectsScheduledThreadPool = new ScheduledThreadPoolExecutor(Config.THREAD_P_EFFECTS, new PriorityThreadFactory("EffectsSTPool", Thread.NORM_PRIORITY));
		_generalScheduledThreadPool = new ScheduledThreadPoolExecutor(Config.THREAD_P_GENERAL, new PriorityThreadFactory("GerenalSTPool", Thread.NORM_PRIORITY));

		// Async pools — migrated to Virtual Threads
		// Virtual threads are parked (not consuming OS threads) when blocked on I/O,
		// making them ideal for packet handling, DB queries, and network operations.
		_ioPacketsThreadPool = Executors.newThreadPerTaskExecutor(
			Thread.ofVirtual().name("VT-IO-").factory());

		_generalPacketsThreadPool = Executors.newThreadPerTaskExecutor(
			Thread.ofVirtual().name("VT-Packet-").factory());

		_generalThreadPool = Executors.newThreadPerTaskExecutor(
			Thread.ofVirtual().name("VT-General-").factory());

		_aiThreadPool = Executors.newThreadPerTaskExecutor(
			Thread.ofVirtual().name("VT-AI-").factory());

		_aiScheduledThreadPool = new ScheduledThreadPoolExecutor(Config.AI_MAX_THREAD, new PriorityThreadFactory("AISTPool", Thread.NORM_PRIORITY));
	}
    
    public static long validateDelay(long delay)
    {
        if (delay < 0)
        {
            delay = 0;
        }
        else if (delay > MAX_DELAY)
        {
            delay = MAX_DELAY;
        }
        return delay;
    }

	public ScheduledFuture<?> scheduleEffect(Runnable r, long delay)
	{
		try
		{
            delay = ThreadPoolManager.validateDelay(delay);
            return _effectsScheduledThreadPool.schedule(r, delay, TimeUnit.MILLISECONDS);
        }
        catch (RejectedExecutionException e)
        {
            return null;
        }
	}

	public ScheduledFuture<?> scheduleEffectAtFixedRate(Runnable r, long initial, long delay)
	{
		try
        {
            delay = ThreadPoolManager.validateDelay(delay);
            initial = ThreadPoolManager.validateDelay(initial);
            return _effectsScheduledThreadPool.scheduleAtFixedRate(r, initial, delay, TimeUnit.MILLISECONDS);
        }
        catch (RejectedExecutionException e)
        {
            return null;
        }
	}

	public ScheduledFuture<?> scheduleGeneral(Runnable r, long delay)
	{
		try
        {
            delay = ThreadPoolManager.validateDelay(delay);
            return _generalScheduledThreadPool.schedule(r, delay, TimeUnit.MILLISECONDS);
        }
        catch (RejectedExecutionException e)
        {
            return null;
        }
    }

	public ScheduledFuture<?> scheduleGeneralAtFixedRate(Runnable r, long initial, long delay)
    {
        try
        {
            delay = ThreadPoolManager.validateDelay(delay);
            initial = ThreadPoolManager.validateDelay(initial);
            return _generalScheduledThreadPool.scheduleAtFixedRate(r, initial, delay, TimeUnit.MILLISECONDS);
        }
        catch (RejectedExecutionException e)
        {
            return null;
        }
    }

	public ScheduledFuture<?> scheduleAi(Runnable r, long delay)
	{
        try
        {
            delay = ThreadPoolManager.validateDelay(delay);
            return _aiScheduledThreadPool.schedule(r, delay, TimeUnit.MILLISECONDS);
        }
        catch (RejectedExecutionException e)
        {
            return null;
        }
    }

	public ScheduledFuture<?> scheduleAiAtFixedRate(Runnable r, long initial, long delay)
	{
        try
        {
            delay = ThreadPoolManager.validateDelay(delay);
            initial = ThreadPoolManager.validateDelay(initial);
            return _aiScheduledThreadPool.scheduleAtFixedRate(r, initial, delay, TimeUnit.MILLISECONDS);
        }
        catch (RejectedExecutionException e)
        {
            return null;
        }
    }

	public void executePacket(ReceivablePacket<L2GameClient> pkt)
	{
		_generalPacketsThreadPool.execute(pkt);
	}

	public void executeIOPacket(ReceivablePacket<L2GameClient> pkt)
	{
		_ioPacketsThreadPool.execute(pkt);
	}

	public void executeTask(Runnable r)
	{
		_generalThreadPool.execute(r);
	}

	public void executeAi(Runnable r)
	{
		_aiThreadPool.execute(r);
	}

	public String[] getStats()
	{
		return new String[] {
		                     "STP (Platform Threads):",
		                     " + Effects:",
		                     " |- ActiveThreads:   "+_effectsScheduledThreadPool.getActiveCount(),
		                     " |- getCorePoolSize: "+_effectsScheduledThreadPool.getCorePoolSize(),
		                     " |- PoolSize:        "+_effectsScheduledThreadPool.getPoolSize(),
		                     " |- MaximumPoolSize: "+_effectsScheduledThreadPool.getMaximumPoolSize(),
		                     " |- CompletedTasks:  "+_effectsScheduledThreadPool.getCompletedTaskCount(),
		                     " |- ScheduledTasks:  "+(_effectsScheduledThreadPool.getTaskCount() - _effectsScheduledThreadPool.getCompletedTaskCount()),
		                     " | -------",
		                     " + General:",
		                     " |- ActiveThreads:   "+_generalScheduledThreadPool.getActiveCount(),
		                     " |- getCorePoolSize: "+_generalScheduledThreadPool.getCorePoolSize(),
		                     " |- PoolSize:        "+_generalScheduledThreadPool.getPoolSize(),
		                     " |- MaximumPoolSize: "+_generalScheduledThreadPool.getMaximumPoolSize(),
		                     " |- CompletedTasks:  "+_generalScheduledThreadPool.getCompletedTaskCount(),
		                     " |- ScheduledTasks:  "+(_generalScheduledThreadPool.getTaskCount() - _generalScheduledThreadPool.getCompletedTaskCount()),
		                     " | -------",
		                     " + AI:",
		                     " |- ActiveThreads:   "+_aiScheduledThreadPool.getActiveCount(),
		                     " |- getCorePoolSize: "+_aiScheduledThreadPool.getCorePoolSize(),
		                     " |- PoolSize:        "+_aiScheduledThreadPool.getPoolSize(),
		                     " |- MaximumPoolSize: "+_aiScheduledThreadPool.getMaximumPoolSize(),
		                     " |- CompletedTasks:  "+_aiScheduledThreadPool.getCompletedTaskCount(),
		                     " |- ScheduledTasks:  "+(_aiScheduledThreadPool.getTaskCount() - _aiScheduledThreadPool.getCompletedTaskCount()),
		                     "VT (Virtual Threads — unbounded, no pool overhead):",
		                     " + Packets (generalPacketsThreadPool):",
		                     "   Mode: Virtual Thread per task",
		                     "   Pool type: Executors.newVirtualThreadPerTaskExecutor()",
		                     " + I/O Packets (ioPacketsThreadPool):",
		                     "   Mode: Virtual Thread per task",
		                     "   Pool type: Executors.newVirtualThreadPerTaskExecutor()",
		                     " + General Tasks (generalThreadPool):",
		                     "   Mode: Virtual Thread per task",
		                     "   Pool type: Executors.newVirtualThreadPerTaskExecutor()",
		                     " + AI Tasks (aiThreadPool):",
		                     "   Mode: Virtual Thread per task",
		                     "   Pool type: Executors.newVirtualThreadPerTaskExecutor()",
		};
	}

    private class PriorityThreadFactory implements ThreadFactory
    {
    	private int _prio;
		private String _name;
		private java.util.concurrent.atomic.AtomicInteger _threadNumber = new java.util.concurrent.atomic.AtomicInteger(1);
		private ThreadGroup _group;

		public PriorityThreadFactory(String name, int prio)
    	{
    		_prio = prio;
    		_name = name;
    		_group = new ThreadGroup(_name);
    	}

		public Thread newThread(Runnable r)
		{
			Thread t = new Thread(_group, r);
			t.setName(_name + "-" + _threadNumber.getAndIncrement());
			t.setPriority(_prio);
			return t;
		}

		public ThreadGroup getGroup()
		{
			return _group;
		}
    }

	public void shutdown()
	{
		_shutdown = true;
		try
		{
			_effectsScheduledThreadPool.shutdown();
			_effectsScheduledThreadPool.awaitTermination(1, TimeUnit.SECONDS);
			_generalScheduledThreadPool.shutdown();
			_generalScheduledThreadPool.awaitTermination(1, TimeUnit.SECONDS);
			_generalPacketsThreadPool.shutdown();
			_ioPacketsThreadPool.shutdown();
			_generalThreadPool.shutdown();
			_aiThreadPool.shutdown();
			_aiScheduledThreadPool.shutdown();
			_aiScheduledThreadPool.awaitTermination(1, TimeUnit.SECONDS);
			_log.info("All ThreadPools are now stoped");
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
	}

	public boolean isShutdown()
	{
		return _shutdown;
	}

	public void purge()
	{
		_effectsScheduledThreadPool.purge();
		_generalScheduledThreadPool.purge();
		_aiScheduledThreadPool.purge();
	}

	public String getPacketStats()
	{
		StringBuilder tb = new StringBuilder();
		tb.append("General Packet Thread Pool [Virtual Threads]:\r\n");
		tb.append("ExecutorService: Executors.newVirtualThreadPerTaskExecutor()\r\n");
		tb.append("Tasks are executed on demand, no queue backlog.\r\n");
		return tb.toString();
	}

	public String getIOPacketStats()
	{
		StringBuilder tb = new StringBuilder();
		tb.append("I/O Packet Thread Pool [Virtual Threads]:\r\n");
		tb.append("ExecutorService: Executors.newVirtualThreadPerTaskExecutor()\r\n");
		tb.append("Tasks are executed on demand, no queue backlog.\r\n");
		return tb.toString();
	}

	public String getGeneralStats()
	{
		StringBuilder tb = new StringBuilder();
		tb.append("General Thread Pool [Virtual Threads]:\r\n");
		tb.append("ExecutorService: Executors.newVirtualThreadPerTaskExecutor()\r\n");
		tb.append("Tasks are executed on demand, no queue backlog.\r\n");
		return tb.toString();
	}
}
