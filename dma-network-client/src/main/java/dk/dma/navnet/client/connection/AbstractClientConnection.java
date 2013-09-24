/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package dk.dma.navnet.client.connection;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import dk.dma.navnet.client.util.DefaultConnectionFuture;
import dk.dma.navnet.client.util.ThreadManager;
import dk.dma.navnet.messages.ConnectionMessage;
import dk.dma.navnet.messages.s2c.ServerRequestMessage;
import dk.dma.navnet.messages.s2c.ServerResponseMessage;
import dk.dma.navnet.protocol.Connection;

/**
 * 
 * @author Kasper Nielsen
 */
public abstract class AbstractClientConnection extends Connection {

    final ConcurrentHashMap<Long, DefaultConnectionFuture<?>> acks = new ConcurrentHashMap<>();

    final AtomicInteger ai = new AtomicInteger();

    final ThreadManager threadManager;

    final ConcurrentHashMap<String, DefaultConnectionFuture<?>> replies = new ConcurrentHashMap<>();

    /**
     * @param cfs
     */
    public AbstractClientConnection(String id, ThreadManager threadManager) {
        super(id);
        this.threadManager = threadManager;
    }

    public final void onConnectionMessage(ConnectionMessage m) {
        super.onConnectionMessage(m);
        if (m instanceof ServerResponseMessage) {
            ServerResponseMessage am = (ServerResponseMessage) m;
            DefaultConnectionFuture<?> f = acks.remove(am.getMessageAck());
            if (f == null) {
                System.err.println("Orphaned packet with id " + am.getMessageAck() + " registered " + acks.keySet()
                        + ", local " + "" + " p = ");
                // TODO close connection with error
            } else {
                try {
                    handleMessageReply(m, f);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // System.out.println("RELEASING " + am.getMessageAck() + ", remaining " + acks.keySet());
        } else {
            try {
                handleMessage(m);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * @param m
     * @param f
     */
    abstract void handleMessageReply(ConnectionMessage m, DefaultConnectionFuture<?> f);

    /**
     * @param m
     */
    abstract void handleMessage(ConnectionMessage m);

    public final <T> DefaultConnectionFuture<T> sendMessage(ServerRequestMessage<T> m) {
        // we need to send the messages in the same order as they are numbered for now
        synchronized (ai) {
            long id = ai.incrementAndGet();
            DefaultConnectionFuture<T> f = threadManager.create();
            acks.put(id, f);
            m.setReplyTo(id);
            sendConnectionMessage(m);
            return f;
        }
    }
}