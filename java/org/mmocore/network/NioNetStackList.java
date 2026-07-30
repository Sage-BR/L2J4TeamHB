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
package org.mmocore.network;

/**
 * @author Forsaiken
 * @param <E>
 */
public final class NioNetStackList<E>
{
	private final NioNetStackNode _start = new NioNetStackNode();

	private final NioNetStackNodeBuf _buf = new NioNetStackNodeBuf();

	private NioNetStackNode _end = new NioNetStackNode();

	public NioNetStackList()
	{
		clear();
	}

	public final void addLast(final E elem)
	{
		final NioNetStackNode newEndNode = _buf.removeFirst();
		_end._value = elem;
		_end._next = newEndNode;
		_end = newEndNode;
	}

	public final E removeFirst()
	{
		final NioNetStackNode old = _start._next;
		final E value = old._value;
		_start._next = old._next;
		_buf.addLast(old);
		return value;
	}

	public final boolean isEmpty()
	{
		return _start._next == _end;
	}

	public final void clear()
	{
		_start._next = _end;
	}

	protected final class NioNetStackNode
	{
		protected NioNetStackNode _next;

		protected E _value;
	}

	private final class NioNetStackNodeBuf
	{

		private final NioNetStackNode head = new NioNetStackNode(); // or _head,
		                                                            // first,
		                                                            // dummyHead,
		                                                            // etc.

		private NioNetStackNode tail = new NioNetStackNode(); // or _tail

		NioNetStackNodeBuf()
		{
			head._next = tail;
		}

		final void addLast(final NioNetStackNode node)
		{
			node._next = null;
			node._value = null;
			tail._next = node;
			tail = node;
		}

		final NioNetStackNode removeFirst()
		{
			if (head._next == tail)
			{
				return new NioNetStackNode(); // or null / throw exception —
				                              // your choice
			}
			final NioNetStackNode old = head._next;
			head._next = old._next;
			return old;
		}
	}
}