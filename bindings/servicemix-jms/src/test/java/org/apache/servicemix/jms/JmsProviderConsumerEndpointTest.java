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
package org.apache.servicemix.jms;

import java.net.URI;
import java.net.URISyntaxException;

import javax.activation.DataHandler;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.InOut;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.MessagingException;
import javax.jbi.messaging.NormalizedMessage;
import javax.jms.ConnectionFactory;
import javax.mail.util.ByteArrayDataSource;
import javax.xml.namespace.QName;
import javax.xml.transform.Source;

import org.apache.activemq.pool.PooledConnectionFactory;
import org.apache.activemq.broker.region.RegionBroker;
import org.apache.servicemix.components.util.EchoComponent;
import org.apache.servicemix.jbi.container.ActivationSpec;
import org.apache.servicemix.jbi.jaxp.SourceTransformer;
import org.apache.servicemix.jbi.jaxp.StringSource;
import org.apache.servicemix.jms.endpoints.DefaultConsumerMarshaler;
import org.apache.servicemix.jms.endpoints.DefaultProviderMarshaler;
import org.apache.servicemix.jms.endpoints.JmsConsumerEndpoint;
import org.apache.servicemix.jms.endpoints.JmsProviderEndpoint;
import org.apache.servicemix.tck.ReceiverComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;

public class JmsProviderConsumerEndpointTest extends AbstractJmsTestSupport {

    private final Logger logger = LoggerFactory.getLogger(JmsProviderConsumerEndpointTest.class);

    public void testProviderConsumerInOut() throws Exception {
        ConnectionFactory connFactory = new PooledConnectionFactory(connectionFactory);
        JmsComponent jmsComponent = new JmsComponent();
        JmsConsumerEndpoint consumerEndpoint = createConsumerEndpoint(connFactory);
        JmsProviderEndpoint providerEndpoint = createProviderEndpoint(connFactory);
        jmsComponent.setEndpoints(new JmsEndpointType[] {consumerEndpoint, providerEndpoint});
        container.activateComponent(jmsComponent, "servicemix-jms");

        // Add an echo component
        EchoComponent echo = new EchoComponent();
        ActivationSpec asEcho = new ActivationSpec("receiver", echo);
        asEcho.setService(new QName("http://jms.servicemix.org/Test", "Echo"));
        container.activateComponent(asEcho);

        InOut inout = null;
        boolean result = false;
        DataHandler dh = null;

        // Test successful return
        Source src = null;
        for (int i = 0; i < 2; i++) {
            inout = client.createInOutExchange();
            inout.setService(new QName("http://jms.servicemix.org/Test", "Provider"));
            inout.getInMessage().setContent(new StringSource("<hello>world</hello>"));
            dh = new DataHandler(new ByteArrayDataSource("myImage", "application/octet-stream"));
            inout.getInMessage().addAttachment("myImage", dh);
            result = client.sendSync(inout);
            assertTrue(result);
            NormalizedMessage out = inout.getOutMessage();
            assertNotNull(out);
            src = out.getContent();
            assertNotNull(src);
            dh = out.getAttachment("myImage");
            assertNotNull(dh);
        }

        // Ensure that only one temporary replyTo queue was created for multiple messages sent
//
        Thread.sleep(2000);
        assertEquals(0, countBrokerTemporaryQueues());

        logger.info(new SourceTransformer().toString(src));

        // Test fault return
        container.deactivateComponent("receiver");
        ReturnFaultComponent fault = new ReturnFaultComponent();
        ActivationSpec asFault = new ActivationSpec("receiver", fault);
        asFault.setService(new QName("http://jms.servicemix.org/Test", "Echo"));
        container.activateComponent(asFault);

        inout = client.createInOutExchange();
        inout.setService(new QName("http://jms.servicemix.org/Test", "Provider"));
        inout.getInMessage().setContent(new StringSource("<hello>world</hello>"));
        result = client.sendSync(inout);
        assertTrue(result);
        assertNotNull(inout.getFault());
        assertTrue(new SourceTransformer().contentToString(inout.getFault()).indexOf("<fault/>") > 0);
        client.done(inout);

        // Test error return
        container.deactivateComponent("receiver");
        ReturnErrorComponent error = new ReturnErrorComponent(new IllegalArgumentException());
        ActivationSpec asError = new ActivationSpec("receiver", error);
        asError.setService(new QName("http://jms.servicemix.org/Test", "Echo"));
        container.activateComponent(asError);

        inout = client.createInOutExchange();
        inout.setService(new QName("http://jms.servicemix.org/Test", "Provider"));
        inout.getInMessage().setContent(new StringSource("<hello>world</hello>"));
        client.sendSync(inout);
        assertEquals(ExchangeStatus.ERROR, inout.getStatus());
        assertTrue("An IllegalArgumentException was expected", inout.getError() instanceof IllegalArgumentException);

    }

    public void testProviderInOnlyWithJmsTxRollback() throws Exception {
        ConnectionFactory connFactory = new PooledConnectionFactory(connectionFactory);
        JmsTemplate template = new JmsTemplate(connFactory);
        template.setReceiveTimeout(2000);
        // Make sure there are no messages stuck on queue from previous tests
        template.receive("destination");

        JmsComponent jmsComponent = new JmsComponent();
        JmsConsumerEndpoint consumerEndpoint = createInOnlyConsumerEndpointWithConfiguredRollback(connFactory, true);
        consumerEndpoint.setTransacted("jms");
        JmsProviderEndpoint providerEndpoint = createProviderEndpoint(connFactory);
        jmsComponent.setEndpoints(new JmsEndpointType[] {consumerEndpoint, providerEndpoint});
        container.activateComponent(jmsComponent, "servicemix-jms");

        final int[] receiveCount = new int[]{0};

        ReturnErrorComponent error = new ReturnErrorComponent(new RuntimeException("Error: abort... abort...!!")) {
            public void onMessageExchange(MessageExchange exchange) throws MessagingException {
                receiveCount[0]++;
                super.onMessageExchange(exchange);
            }
        };

        ActivationSpec asError = new ActivationSpec("receiver", error);
        asError.setService(new QName("http://jms.servicemix.org/Test", "Echo"));
        container.activateComponent(asError);

        InOnly exchange = client.createInOnlyExchange();
        exchange.setService(new QName("http://jms.servicemix.org/Test", "Provider"));
        exchange.getInMessage().setContent(new StringSource("<hello>world</hello>"));
        client.sendSync(exchange);

        // Loop and wait for at least one attempt to process the message
        for (int i = 0; i < 5; i++) {
            Thread.sleep(1000);
            if (receiveCount[0] > 0) {
                break;
            }
        }

        assertTrue("The message was never processed by servicemix-jms", receiveCount[0] > 0);

        // Deactivate the JMS component so that it stops
        // trying to get the message from the queue
        container.deactivateComponent("servicemix-jms");

        assertNotNull("Message should still be on the queue", template.receive("destination"));
    }

    public void testProviderInOnlyWithJmsTx() throws Exception {
        ConnectionFactory connFactory = new PooledConnectionFactory(connectionFactory);
        JmsTemplate template = new JmsTemplate(connFactory);
        template.setReceiveTimeout(2000);
        // Make sure there are no messages stuck on queue from previous tests
        template.receive("destination");

        JmsComponent jmsComponent = new JmsComponent();
        JmsConsumerEndpoint consumerEndpoint = createInOnlyConsumerEndpoint(connFactory); //, true);
        consumerEndpoint.setTransacted("jms");
        JmsProviderEndpoint providerEndpoint = createProviderEndpoint(connFactory);
        jmsComponent.setEndpoints(new JmsEndpointType[] {consumerEndpoint, providerEndpoint});
        container.activateComponent(jmsComponent, "servicemix-jms");

        ReceiverComponent rcvr = new ReceiverComponent();
        rcvr.setService(new QName("http://jms.servicemix.org/Test", "Echo"));
        rcvr.setEndpoint("endpoint");
        ActivationSpec asRcvr = new ActivationSpec("receiver", rcvr);
        container.activateComponent(asRcvr);

        InOnly exchange;
        exchange = client.createInOnlyExchange();
        exchange.setService(new QName("http://jms.servicemix.org/Test", "Provider"));
        exchange.getInMessage().setContent(new StringSource("<hello>world</hello>"));
        client.sendSync(exchange);
        rcvr.getMessageList().assertMessagesReceived(1);

        assertNull("Message should not be on the queue", template.receive("destination"));

    }

    public void testProviderInOnlyWithNoneTx() throws Exception {
        ConnectionFactory connFactory = new PooledConnectionFactory(connectionFactory);
        JmsTemplate template = new JmsTemplate(connFactory);
        template.setReceiveTimeout(2000);
        // Make sure there are no messages stuck on queue from previous tests
        template.receive("destination");

        JmsComponent jmsComponent = new JmsComponent();
        JmsConsumerEndpoint consumerEndpoint = createInOnlyConsumerEndpoint(connFactory);
        consumerEndpoint.setTransacted("none");
        JmsProviderEndpoint providerEndpoint = createProviderEndpoint(connFactory);
        jmsComponent.setEndpoints(new JmsEndpointType[] {consumerEndpoint, providerEndpoint});
        container.activateComponent(jmsComponent, "servicemix-jms");

        final int[] receiveCount = new int[]{0};

        ReturnErrorComponent error = new ReturnErrorComponent(new RuntimeException("Error: abort... abort...!!")) {
            public void onMessageExchange(MessageExchange exchange) throws MessagingException {
                receiveCount[0]++;
                super.onMessageExchange(exchange);
            }
        };

        ActivationSpec asError = new ActivationSpec("receiver", error);
        asError.setService(new QName("http://jms.servicemix.org/Test", "Echo"));
        container.activateComponent(asError);

        InOnly exchange = client.createInOnlyExchange();
        exchange.setService(new QName("http://jms.servicemix.org/Test", "Provider"));
        exchange.getInMessage().setContent(new StringSource("<hello>world</hello>"));
        client.sendSync(exchange);

        // Loop and wait for at least one attempt to process the message
        for (int i = 0; i < 5; i++) {
            Thread.sleep(1000);
            if (receiveCount[0] > 0) {
                break;
            }
        }

        assertTrue("The message was never processed by servicemix-jms", receiveCount[0] > 0);

        assertNull("Message should not be on the queue", template.receive("destination"));
    }



    private JmsConsumerEndpoint createInOnlyConsumerEndpoint(ConnectionFactory connFactory) throws URISyntaxException {
        JmsConsumerEndpoint endpoint = new JmsConsumerEndpoint();
        endpoint.setService(new QName("http://jms.servicemix.org/Test", "Consumer"));
        endpoint.setEndpoint("endpoint");
        DefaultConsumerMarshaler marshaler = new DefaultConsumerMarshaler();
        marshaler.setMep(new URI("http://www.w3.org/2004/08/wsdl/in-only"));
        endpoint.setMarshaler(marshaler);
        endpoint.setListenerType("simple");
        endpoint.setConnectionFactory(connFactory);
        endpoint.setDestinationName("destination");
        endpoint.setRecoveryInterval(10000);
        endpoint.setConcurrentConsumers(1);
        endpoint.setTargetService(new QName("http://jms.servicemix.org/Test", "Echo"));
        return endpoint;
    }

    private JmsConsumerEndpoint createInOnlyConsumerEndpointWithConfiguredRollback(ConnectionFactory connFactory,
                                                                                   boolean rollbackOnError) throws URISyntaxException {
        JmsConsumerEndpoint endpoint = new JmsConsumerEndpoint();
        endpoint.setService(new QName("http://jms.servicemix.org/Test", "Consumer"));
        endpoint.setEndpoint("endpoint");
        DefaultConsumerMarshaler marshaler = new DefaultConsumerMarshaler();
        marshaler.setMep(new URI("http://www.w3.org/2004/08/wsdl/in-only"));
        marshaler.setRollbackOnError(rollbackOnError);
        endpoint.setMarshaler(marshaler);
        endpoint.setListenerType("simple");
        endpoint.setListenerType("simple");
        endpoint.setConnectionFactory(connFactory);
        endpoint.setDestinationName("destination");
        endpoint.setRecoveryInterval(10000);
        endpoint.setConcurrentConsumers(1);
        endpoint.setTargetService(new QName("http://jms.servicemix.org/Test", "Echo"));
        return endpoint;
    }

    private JmsConsumerEndpoint createConsumerEndpoint(ConnectionFactory connFactory) throws URISyntaxException {
        JmsConsumerEndpoint endpoint = new JmsConsumerEndpoint();
        endpoint.setService(new QName("http://jms.servicemix.org/Test", "Consumer"));
        endpoint.setEndpoint("endpoint");
        DefaultConsumerMarshaler marshaler = new DefaultConsumerMarshaler();
        marshaler.setMep(new URI("http://www.w3.org/2004/08/wsdl/in-out"));
        endpoint.setMarshaler(marshaler);
        endpoint.setListenerType("simple");
        endpoint.setConnectionFactory(connFactory);
        endpoint.setDestinationName("destination");
        endpoint.setTargetService(new QName("http://jms.servicemix.org/Test", "Echo"));
        return endpoint;
    }

    private JmsProviderEndpoint createProviderEndpoint(ConnectionFactory connFactory) {
        JmsProviderEndpoint endpoint = new JmsProviderEndpoint();
        endpoint.setService(new QName("http://jms.servicemix.org/Test", "Provider"));
        endpoint.setEndpoint("endpoint");
        DefaultProviderMarshaler marshaler = new DefaultProviderMarshaler();
        endpoint.setMarshaler(marshaler);
        endpoint.setConnectionFactory(connFactory);
        endpoint.setDestinationName("destination");
        return endpoint;
    }

    private int countBrokerTemporaryQueues() throws Exception {
        return ((RegionBroker) broker.getRegionBroker()).getTempQueueRegion().getDestinationMap().size();
    }
}
