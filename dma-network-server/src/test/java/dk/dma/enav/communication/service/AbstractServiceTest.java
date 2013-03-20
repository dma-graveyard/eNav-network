/*
 * Copyright (c) 2008 Kasper Nielsen.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dk.dma.enav.communication.service;

import java.util.concurrent.TimeUnit;

import test.stubs.HelloService;
import dk.dma.enav.communication.AbstractNetworkTest;
import dk.dma.enav.communication.PersistentConnection;

/**
 * 
 * @author Kasper Nielsen
 */
public class AbstractServiceTest extends AbstractNetworkTest {

    public PersistentConnection registerService(PersistentConnection pnc, String reply) throws Exception {
        pnc.serviceRegister(HelloService.GET_NAME, HelloService.create(reply)).awaitRegistered(5, TimeUnit.SECONDS);
        return pnc;
    }

}
