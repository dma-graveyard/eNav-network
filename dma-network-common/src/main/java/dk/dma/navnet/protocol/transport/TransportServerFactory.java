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
package dk.dma.navnet.protocol.transport;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.eclipse.jetty.websocket.api.UpgradeResponse;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.dma.enav.util.function.Supplier;

/**
 * A factory used to create transports from connections by remote clients.
 * 
 * @author Kasper Nielsen
 */
public final class TransportServerFactory {

    /** The logger. */
    static final Logger LOG = LoggerFactory.getLogger(TransportServerFactory.class);

    /** The actual WebSocket server */
    private final Server server;

    private TransportServerFactory(InetSocketAddress sa) {
        this.server = new Server(sa);
        // Sets the sockets reuse address to true
        ServerConnector connector = (ServerConnector) server.getConnectors()[0];
        connector.setReuseAddress(true);
    }

    /**
     * Invoked whenever a client has connected.
     * 
     * @param supplier
     *            a supplier used for creating new transports
     * @throws IOException
     */
    public void startAccept(final Supplier<Transport> supplier) throws IOException {
        requireNonNull(supplier);
        // Creates the web socket handler that accept incoming requests
        WebSocketHandler wsHandler = new WebSocketHandler() {
            public void configure(WebSocketServletFactory factory) {
                factory.setCreator(new WebSocketCreator() {
                    public Object createWebSocket(UpgradeRequest req, UpgradeResponse resp) {
                        return new TransportWebSocketListener(supplier.get());
                    }
                });
            }
        };

        server.setHandler(wsHandler);
        try {
            server.start();
            LOG.info("System is ready accept client connections");
        } catch (Exception e) {
            try {
                server.stop();
            } catch (Exception e1) {
                // We want to rethrow the original exception, so just log this one
                LOG.info("System failed to stop", e);
            }
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw new IOException(e);
        }
    }

    /**
     * Stops accepting any more connections
     * 
     * @throws IOException
     */
    public void shutdown() throws IOException {
        try {
            server.stop();
        } catch (Exception e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw new IOException(e);
        }
    }

    /**
     * Creates a new transport server factory.
     * 
     * @param port
     *            the port to listen on
     * @return a new transport server factory
     */
    public static TransportServerFactory createServer(int port) {
        return createServer(new InetSocketAddress(port));
    }

    /**
     * Creates a new transport server factory.
     * 
     * @param port
     *            the address to bind to
     * @return a new transport server factory
     */
    public static TransportServerFactory createServer(InetSocketAddress address) {
        return new TransportServerFactory(address);
    }
}