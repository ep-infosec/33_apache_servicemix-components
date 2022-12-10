/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.camel;

import java.io.ByteArrayInputStream;
import java.util.List;

import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.Fault;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.MessagingException;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.messaging.RobustInOnly;
import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.Exchange;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.servicemix.MessageExchangeListener;
import org.apache.servicemix.client.ServiceMixClient;
import org.apache.servicemix.components.util.ComponentSupport;
import org.apache.servicemix.jbi.container.ActivationSpec;
import org.apache.servicemix.jbi.helper.MessageUtil;
import org.apache.servicemix.jbi.jaxp.SourceTransformer;
import org.apache.servicemix.tck.ReceiverComponent;
import org.junit.Test;
import org.springframework.util.Assert;

/**
 * Tests on handling fault messages with the Camel Exception handler
 */
public class JbiInOnlyWithFaultHandledTrueSpringDSLTest extends SpringJbiTestSupport {

    private static final Level LOG_LEVEL = Logger.getLogger("org.apache.servicemix").getEffectiveLevel();
    private static final String MESSAGE = "<just><a>test</a></just>";
    private static final QName TEST_SERVICE = new QName("urn:test", "fault-handled-true");

    private ReceiverComponent receiver;
    private ReceiverComponent deadLetter;
    
    @Override
    public void setUp() throws Exception {
        receiver = new ReceiverComponent() {
            public void onMessageExchange(MessageExchange exchange) throws MessagingException {
                NormalizedMessage inMessage = getInMessage(exchange);
                try {
                    Assert.notNull(exchange.getProperty(Exchange.EXCEPTION_CAUGHT), Exchange.EXCEPTION_CAUGHT + " property not set");
                    MessageUtil.enableContentRereadability(inMessage);
                    String message = new SourceTransformer().contentToString(inMessage);
                    Assert.isTrue(message.contains(MESSAGE));
                } catch (Exception e) {
                    throw new MessagingException(e);
                }
                
                super.onMessageExchange(exchange);
            }
        };
        deadLetter = new ReceiverComponent();

        super.setUp();

        // change the log level to avoid the conversion to DOMSource 
        Logger.getLogger("org.apache.servicemix").setLevel(Level.ERROR);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        // restore the original log level
        Logger.getLogger("org.apache.servicemix").setLevel(LOG_LEVEL);
    }

    @Test
    public void testInOnlyWithFaultHandledByExceptionClause() throws Exception {
        ServiceMixClient smxClient = getServicemixClient();
        InOnly exchange = smxClient.createInOnlyExchange();
        exchange.setEndpoint(jbiContainer.getRegistry().getEndpointsForService(TEST_SERVICE)[0]);
        Source content = new StreamSource(new ByteArrayInputStream(MESSAGE.getBytes()));
        exchange.getMessage("in").setContent(content);

        smxClient.send(exchange);

        exchange = (InOnly)smxClient.receive();
        assertEquals(ExchangeStatus.DONE, exchange.getStatus());
        assertNotNull(exchange.getProperty(Exchange.EXCEPTION_CAUGHT));

        deadLetter.getMessageList().assertMessagesReceived(0);
        receiver.getMessageList().assertMessagesReceived(1);
    }

    @Test
    public void testRobustInOnlyWithFaultHandledByExceptionClause() throws Exception {
        ServiceMixClient smxClient = getServicemixClient();
        RobustInOnly exchange = smxClient.createRobustInOnlyExchange();
        exchange.setEndpoint(jbiContainer.getRegistry().getEndpointsForService(TEST_SERVICE)[0]);
        Source content = new StreamSource(new ByteArrayInputStream(MESSAGE.getBytes()));
        exchange.getMessage("in").setContent(content);

        smxClient.send(exchange);

        exchange = (RobustInOnly) smxClient.receive();
        assertEquals(ExchangeStatus.DONE, exchange.getStatus());
        assertNotNull(exchange.getProperty(Exchange.EXCEPTION_CAUGHT));

        deadLetter.getMessageList().assertMessagesReceived(0);
        receiver.getMessageList().assertMessagesReceived(1);
    }

    @Override
    protected String getServiceUnitName() {
        return "su9";
    }

    @Override
    protected void appendJbiActivationSpecs(List<ActivationSpec> activationSpecList) {
        activationSpecList.add(createActivationSpec(new ReturnFaultComponent(), new QName("urn:test", "faulty-service")));

        activationSpecList.add(createActivationSpec(receiver, new QName("urn:test", "receiver-service")));
        activationSpecList.add(createActivationSpec(deadLetter, new QName("urn:test", "deadLetter-service")));
    }

    protected static class ReturnFaultComponent extends ComponentSupport implements MessageExchangeListener {
        public void onMessageExchange(MessageExchange exchange) throws MessagingException {
            if (exchange.getStatus() == ExchangeStatus.ACTIVE) {
                // read the in message content before returning to ensure that the 
                // Camel DeadLetterChannel caches the stream correctly prior to re-delivery
                try {
                    new SourceTransformer().contentToString(exchange.getMessage("in"));
                } catch (Exception e) {
                    throw new MessagingException(e);
                }

                Fault fault = exchange.createFault();
                fault.setContent(new StreamSource(new ByteArrayInputStream("<fault/>".getBytes())));
                fail(exchange, fault);
            }
        }
    }
}
