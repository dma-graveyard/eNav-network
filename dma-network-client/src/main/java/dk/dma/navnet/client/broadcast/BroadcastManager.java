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
package dk.dma.navnet.client.broadcast;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

import jsr166e.ConcurrentHashMapV8;

import org.picocontainer.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.MapMaker;

import dk.dma.enav.communication.MaritimeNetworkClientConfiguration;
import dk.dma.enav.communication.broadcast.BroadcastFuture;
import dk.dma.enav.communication.broadcast.BroadcastListener;
import dk.dma.enav.communication.broadcast.BroadcastMessage;
import dk.dma.enav.communication.broadcast.BroadcastMessageHeader;
import dk.dma.enav.communication.broadcast.BroadcastOptions;
import dk.dma.enav.communication.broadcast.BroadcastSubscription;
import dk.dma.enav.model.MaritimeId;
import dk.dma.enav.model.geometry.PositionTime;
import dk.dma.enav.util.function.BiConsumer;
import dk.dma.enav.util.function.Consumer;
import dk.dma.navnet.client.InternalClient;
import dk.dma.navnet.client.connection.ConnectionMessageBus;
import dk.dma.navnet.client.service.PositionManager;
import dk.dma.navnet.client.util.DefaultConnectionFuture;
import dk.dma.navnet.client.util.ThreadManager;
import dk.dma.navnet.messages.c2c.broadcast.BroadcastAck;
import dk.dma.navnet.messages.c2c.broadcast.BroadcastDeliver;
import dk.dma.navnet.messages.c2c.broadcast.BroadcastSend;
import dk.dma.navnet.messages.c2c.broadcast.BroadcastSendAck;

/**
 * Manages sending and receiving of broadcasts.
 * 
 * @author Kasper Nielsen
 */
public class BroadcastManager implements Startable {

    /** The logger. */
    private static final Logger LOG = LoggerFactory.getLogger(BroadcastManager.class);

    /** The network */
    private final ConnectionMessageBus connection;

    /** A map of listeners. ChannelName -> List of listeners. */
    final ConcurrentHashMapV8<String, CopyOnWriteArraySet<BroadcastMessageSubscription>> listeners = new ConcurrentHashMapV8<>();

    private final PositionManager positionManager;

    /** Thread manager takes care of asynchronous processing. */
    private final ThreadManager threadManager;

    private final InternalClient client;

    private final BroadcastOptions defaultOptions;

    private final ConcurrentMap<Long, DefaultBroadcastFuture> broadcasts = new MapMaker().weakValues().makeMap();

    /**
     * Creates a new instance of this class.
     * 
     * @param network
     *            the network
     */
    public BroadcastManager(PositionManager positionManager, ThreadManager threadManager, InternalClient client,
            ConnectionMessageBus connection, MaritimeNetworkClientConfiguration configuration) {
        this.connection = requireNonNull(connection);
        this.positionManager = requireNonNull(positionManager);
        this.threadManager = requireNonNull(threadManager);
        this.client = requireNonNull(client);
        this.defaultOptions = configuration.getDefaultBroadcastOptions().immutable();
    }

    /**
     * Sets up listeners for incoming broadcast messages.
     * 
     * @param messageType
     *            the type of message to receive
     * @param listener
     *            the callback listener
     * @return a subscription
     */
    public <T extends BroadcastMessage> BroadcastSubscription listenFor(Class<T> messageType,
            BroadcastListener<T> listener) {
        BroadcastMessageSubscription sub = new BroadcastMessageSubscription(this, getChannelName(messageType), listener);
        listeners.computeIfAbsent(messageType.getCanonicalName(),
                new ConcurrentHashMapV8.Fun<String, CopyOnWriteArraySet<BroadcastMessageSubscription>>() {
                    public CopyOnWriteArraySet<BroadcastMessageSubscription> apply(String t) {
                        return new CopyOnWriteArraySet<>();
                    }
                }).add(sub);
        return sub;
    }

    void onBroadcastAck(BroadcastAck ack) {
        DefaultBroadcastFuture f = broadcasts.get(ack.getBroadcastId());
        if (f != null) {
            final PositionTime pt = ack.getPositionTime();
            final MaritimeId mid = ack.getId();
            f.onMessage(new BroadcastMessage.Ack() {
                public PositionTime getPosition() {
                    return pt;
                }

                public MaritimeId getId() {
                    return mid;
                }
            });
        }
    }

    /**
     * Invoked whenever a broadcast message was received.
     * 
     * @param broadcast
     *            the broadcast that was received
     */
    void onBroadcastMessage(BroadcastDeliver broadcast) {
        CopyOnWriteArraySet<BroadcastMessageSubscription> set = listeners.get(broadcast.getChannel());
        if (set != null && !set.isEmpty()) {
            BroadcastMessage bm = null;
            try {
                bm = broadcast.tryRead();
            } catch (Exception e) {
                LOG.error("Exception while trying to deserialize an incoming broadcast message ", e);
                LOG.error(broadcast.toJSON());
            }

            final BroadcastMessage bmm = bm;
            final BroadcastMessageHeader bp = new BroadcastMessageHeader(broadcast.getId(), broadcast.getPositionTime());

            // Deliver to each listener
            for (final BroadcastMessageSubscription s : set) {
                threadManager.execute(new Runnable() {
                    public void run() {
                        s.deliver(bp, bmm);
                    }
                });
            }
        }
    }

    /**
     * Sends a broadcast.
     * 
     * @param broadcast
     *            the broadcast to send
     */
    public BroadcastFuture sendBroadcastMessage(BroadcastMessage broadcast) {
        return sendBroadcastMessage(broadcast, defaultOptions);
    }

    public BroadcastFuture sendBroadcastMessage(BroadcastMessage broadcast, BroadcastOptions options) {
        requireNonNull(broadcast, "broadcast is null");
        requireNonNull(options, "options is null");
        options = options.immutable();
        BroadcastSend b = BroadcastSend.create(client.getLocalId(), positionManager.getPositionTime(), broadcast,
                options);

        DefaultConnectionFuture<BroadcastSendAck> response = connection.sendMessage(b);

        final DefaultBroadcastFuture dbf = new DefaultBroadcastFuture(threadManager, options);
        broadcasts.put(b.getReplyTo(), dbf);

        response.handle(new BiConsumer<BroadcastSendAck, Throwable>() {
            public void accept(BroadcastSendAck ack, Throwable cause) {
                if (ack != null) {
                    dbf.receivedOnServer.complete(null);
                } else {
                    dbf.receivedOnServer.completeExceptionally(cause);
                    // remove from broadcasts??
                }
            }
        });
        return dbf;
    }

    /** Translates a class to a channel name. */
    private static String getChannelName(Class<?> c) {
        return c.getCanonicalName();
    }

    /** {@inheritDoc} */
    @Override
    public void start() {
        connection.subscribe(BroadcastDeliver.class, new Consumer<BroadcastDeliver>() {
            public void accept(BroadcastDeliver t) {
                onBroadcastMessage(t);
            }
        });

        connection.subscribe(BroadcastAck.class, new Consumer<BroadcastAck>() {
            public void accept(BroadcastAck t) {
                onBroadcastAck(t);
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public void stop() {}
}
