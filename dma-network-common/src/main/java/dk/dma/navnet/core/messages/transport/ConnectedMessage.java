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
package dk.dma.navnet.core.messages.transport;

import static java.util.Objects.requireNonNull;

import java.io.IOException;

import dk.dma.navnet.core.messages.TransportMessage;
import dk.dma.navnet.core.messages.MessageType;
import dk.dma.navnet.core.messages.util.TextMessageReader;
import dk.dma.navnet.core.messages.util.TextMessageWriter;

/**
 * 
 * @author Kasper Nielsen
 */
public class ConnectedMessage extends TransportMessage {

    private final String connectionId;

    public ConnectedMessage(TextMessageReader pr) throws IOException {
        this(pr.takeString());
    }

    /**
     * @param messageType
     */
    public ConnectedMessage(String connectionId) {
        super(MessageType.CONNECTED);
        this.connectionId = requireNonNull(connectionId);
    }

    public String getConnectionId() {
        return connectionId;
    }

    /** {@inheritDoc} */
    @Override
    public void write(TextMessageWriter w) {
        w.writeString(connectionId);
    }
}
