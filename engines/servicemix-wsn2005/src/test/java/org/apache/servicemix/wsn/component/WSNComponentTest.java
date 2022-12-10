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
package org.apache.servicemix.wsn.component;

import java.io.File;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.util.List;

import javax.jbi.JBIException;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.servicedesc.ServiceEndpoint;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.ws.wsaddressing.W3CEndpointReference;
import javax.xml.bind.util.JAXBSource;
import javax.xml.bind.JAXBElement;
import javax.xml.transform.Source;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import junit.framework.TestCase;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.servicemix.jbi.container.ActivationSpec;
import org.apache.servicemix.jbi.container.JBIContainer;
import org.apache.servicemix.jbi.jaxp.SourceTransformer;
import org.apache.servicemix.tck.Receiver;
import org.apache.servicemix.tck.ReceiverComponent;
import org.apache.servicemix.tck.mock.MockMessageExchange;
import org.apache.servicemix.wsn.client.AbstractWSAClient;
import org.apache.servicemix.wsn.client.CreatePullPoint;
import org.apache.servicemix.wsn.client.NotificationBroker;
import org.apache.servicemix.wsn.client.PullPoint;
import org.apache.servicemix.wsn.client.Subscription;
import org.apache.servicemix.wsn.spring.PublisherComponent;
import org.apache.servicemix.wsn.jbi.JbiNotificationBroker;
import org.apache.servicemix.wsn.AbstractNotificationBroker;
import org.apache.servicemix.client.ServiceMixClient;
import org.apache.servicemix.client.DefaultServiceMixClient;
import org.oasis_open.docs.wsn.b_2.NotificationMessageHolderType;
import org.oasis_open.docs.wsn.b_2.Notify;
import org.oasis_open.docs.wsrf.rp_2.ObjectFactory;

public class WSNComponentTest extends TestCase {

    private static final String BROKER_URL = "tcp://localhost:" + System.getProperty("activemq.port1", "61616");
    public static final QName NOTIFICATION_BROKER =
        new QName("http://servicemix.org/wsnotification", "NotificationBroker");

    private JBIContainer jbi;
    private ServiceMixClient client;
    private BrokerService jmsBroker;
    private NotificationBroker wsnBroker;
    private CreatePullPoint wsnCreatePullPoint;
    private WSNComponent wsnComponent;

    protected void setUp() throws Exception {
        jmsBroker = new BrokerService();
        jmsBroker.setPersistent(false);
        jmsBroker.addConnector(BROKER_URL);
        jmsBroker.start();

        jbi = new JBIContainer();
        jbi.setEmbedded(true);
        jbi.setCreateJmxConnector(false);
        jbi.init();
        jbi.start();

        client = new DefaultServiceMixClient(jbi);

        wsnComponent = new WSNComponent();
        wsnComponent.setConnectionFactory(new ActiveMQConnectionFactory(BROKER_URL));
        ActivationSpec as = new ActivationSpec();
        as.setComponentName("servicemix-wsn2005");
        as.setComponent(wsnComponent);
        jbi.activateComponent(as);

        wsnBroker = new NotificationBroker(client.getContext());
        wsnCreatePullPoint = new CreatePullPoint(client.getContext());
    }

    protected void tearDown() throws Exception {
        if (jbi != null) {
            jbi.shutDown();
        }
        if (jmsBroker != null) {
            jmsBroker.stop();
        }
    }

    public void testWSDL() throws Exception {
        ServiceEndpoint[] ses = jbi.getRegistry().getEndpointsForInterface(
                        new QName("http://docs.oasis-open.org/wsn/brw-2", "NotificationBroker"));
        assertNotNull(ses);
        assertEquals(1, ses.length);
    }

    public void testInvalidSubscription() throws Exception {
        try {
            wsnBroker.subscribe(null, null, null);
            fail("Expected an exception");
        } catch (JBIException e) {
            // ok
        }
    }

    public void testNotify() throws Exception {
        ReceiverComponent receiver = new ReceiverComponent();
        jbi.activateComponent(receiver, "receiver");

        W3CEndpointReference consumer = createEPR(ReceiverComponent.SERVICE, ReceiverComponent.ENDPOINT);
        wsnBroker.subscribe(consumer, "myTopic", null);

        wsnBroker.notify("myTopic", parse("<hello>world</hello>"));
        // Wait for notification
        Thread.sleep(2000);

        receiver.getMessageList().assertMessagesReceived(1);
        NormalizedMessage msg = (NormalizedMessage) receiver.getMessageList().getMessages().get(0);
        Node node = new SourceTransformer().toDOMNode(msg);
        assertEquals("Notify", node.getLocalName());

        // Wait for acks to be processed
        Thread.sleep(5000);
    }

    public void testNotifyWithJbiWrapper() throws Exception {
        wsnBroker.setJbiWrapped(true);

        ReceiverComponent receiver = new ReceiverComponent();
        jbi.activateComponent(receiver, "receiver");

        W3CEndpointReference consumer = createEPR(ReceiverComponent.SERVICE, ReceiverComponent.ENDPOINT);
        wsnBroker.subscribe(consumer, "myTopic", null);

        wsnBroker.notify("myTopic", parse("<hello>world</hello>"));
        // Wait for notification
        Thread.sleep(2000);

        receiver.getMessageList().assertMessagesReceived(1);
        NormalizedMessage msg = (NormalizedMessage) receiver.getMessageList().getMessages().get(0);
        Node node = new SourceTransformer().toDOMNode(msg);
        assertEquals("Notify", node.getLocalName());

        // Wait for acks to be processed
        Thread.sleep(5000);
    }

    public void testRawNotify() throws Exception {
        ReceiverComponent receiver = new ReceiverComponent();
        jbi.activateComponent(receiver, "receiver");

        // START SNIPPET: notify
        W3CEndpointReference consumer = createEPR(ReceiverComponent.SERVICE, ReceiverComponent.ENDPOINT);
        wsnBroker.subscribe(consumer, "myTopic", null, true);

        Element body = parse("<hello>world</hello>");
        wsnBroker.notify("myTopic", body);
        // END SNIPPET: notify

        // Wait for notification
        Thread.sleep(2000);

        receiver.getMessageList().assertMessagesReceived(1);
        NormalizedMessage msg = (NormalizedMessage) receiver.getMessageList().getMessages().get(0);
        Node node = new SourceTransformer().toDOMNode(msg);
        assertEquals("hello", node.getLocalName());

        // Wait for acks to be processed
        Thread.sleep(5000);
    }

    public void testUnsubscribe() throws Exception {
        // START SNIPPET: sub
        PullPoint pullPoint = wsnCreatePullPoint.createPullPoint();
        Subscription subscription = wsnBroker.subscribe(pullPoint.getEndpoint(), "myTopic", null);
        // END SNIPPET: sub

        wsnBroker.notify("myTopic", new Notify());
        // Wait for notification
        Thread.sleep(2000);

        assertEquals(1, pullPoint.getMessages(0).size());

        subscription.unsubscribe();

        wsnBroker.notify("myTopic", new Notify());
        // Wait for notification
        Thread.sleep(2000);

        assertEquals(0, pullPoint.getMessages(0).size());

        // Wait for acks to be processed
        Thread.sleep(5000);
    }

    public void testPauseResume() throws Exception {
        PullPoint pullPoint = wsnCreatePullPoint.createPullPoint();
        Subscription subscription = wsnBroker.subscribe(pullPoint.getEndpoint(), "myTopic", null);

        wsnBroker.notify("myTopic", new Notify());
        // Wait for notification
        Thread.sleep(2000);

        assertEquals(1, pullPoint.getMessages(0).size());

        subscription.pause();

        wsnBroker.notify("myTopic", new Notify());
        // Wait for notification
        Thread.sleep(2000);

        assertEquals(0, pullPoint.getMessages(0).size());

        subscription.resume();

        wsnBroker.notify("myTopic", new Notify());
        // Wait for notification
        Thread.sleep(2000);

        assertEquals(1, pullPoint.getMessages(0).size());

        // Wait for acks to be processed
        Thread.sleep(5000);
    }

    public void testPull() throws Exception {
        PullPoint pullPoint = wsnCreatePullPoint.createPullPoint();
        wsnBroker.subscribe(pullPoint.getEndpoint(), "myTopic", null);

        wsnBroker.notify("myTopic", new Notify());
        // Wait for notification
        Thread.sleep(2000);

        List<NotificationMessageHolderType> msgs = pullPoint.getMessages(0);
        assertNotNull(msgs);
        assertEquals(1, msgs.size());

        // Wait for acks to be processed
        Thread.sleep(5000);
    }

    public void testPullWithFilter() throws Exception {
        PullPoint pullPoint1 = wsnCreatePullPoint.createPullPoint();
        PullPoint pullPoint2 = wsnCreatePullPoint.createPullPoint();
        wsnBroker.subscribe(pullPoint1.getEndpoint(), "myTopic", "@type = 'a'");
        wsnBroker.subscribe(pullPoint2.getEndpoint(), "myTopic", "@type = 'b'");

        wsnBroker.notify("myTopic", parse("<msg type='a'/>"));
        // Wait for notification
        Thread.sleep(2000);

        assertEquals(1, pullPoint1.getMessages(0).size());
        assertEquals(0, pullPoint2.getMessages(0).size());

        wsnBroker.notify("myTopic", parse("<msg type='b'/>"));
        // Wait for notification
        Thread.sleep(5000);

        assertEquals(0, pullPoint1.getMessages(0).size());
        assertEquals(1, pullPoint2.getMessages(0).size());

        wsnBroker.notify("myTopic", parse("<msg type='c'/>"));
        // Wait for notification
        Thread.sleep(2000);

        assertEquals(0, pullPoint1.getMessages(0).size());
        assertEquals(0, pullPoint2.getMessages(0).size());
    }

    public void testDemandBasedPublisher() throws Exception {
        PublisherComponent publisherComponent = new PublisherComponent();
        publisherComponent.setService(new QName("http://servicemix.org/example", "publisher"));
        publisherComponent.setEndpoint("publisher");
        publisherComponent.setTopic("myTopic");
        publisherComponent.setDemand(true);
        jbi.activateComponent(publisherComponent, "publisher");

        Thread.sleep(5000);
        assertNull(publisherComponent.getSubscription());

        PullPoint pullPoint = wsnCreatePullPoint.createPullPoint();
        Subscription subscription = wsnBroker.subscribe(pullPoint.getEndpoint(), "myTopic", null);

        Thread.sleep(1500);
        assertNotNull(publisherComponent.getSubscription());

        subscription.unsubscribe();

        Thread.sleep(1500);
        assertNull(publisherComponent.getSubscription());

        Thread.sleep(1500);
    }

    public void testDeployPullPoint() throws Exception {
        URL url = getClass().getClassLoader().getResource("pullpoint/pullpoint.xml");
        File path = new File(new URI(url.toString()));
        path = path.getParentFile();
        wsnComponent.getServiceUnitManager().deploy("pullpoint", path.getAbsolutePath());
        wsnComponent.getServiceUnitManager().init("pullpoint", path.getAbsolutePath());
        wsnComponent.getServiceUnitManager().start("pullpoint");

        wsnBroker.notify("myTopic", parse("<hello>world</hello>"));
        PullPoint pullPoint = new PullPoint(
                        client.getContext(),
                        AbstractWSAClient.createWSA("http://www.consumer.org/service/endpoint"));
        Thread.sleep(2000);
        assertEquals(1, pullPoint.getMessages(0).size());
    }

    public void testDeploySubscription() throws Exception {
        URL url = getClass().getClassLoader().getResource("subscription/subscribe.xml");
        File path = new File(new URI(url.toString()));
        path = path.getParentFile();
        wsnComponent.getServiceUnitManager().deploy("subscription", path.getAbsolutePath());

        ActivationSpec consumer = new ActivationSpec();
        consumer.setService(new QName("http://www.consumer.org", "service"));
        consumer.setEndpoint("endpoint");
        Receiver receiver = new ReceiverComponent();
        consumer.setComponent(receiver);
        jbi.activateComponent(consumer);

        wsnComponent.getServiceUnitManager().init("subscription", path.getAbsolutePath());
        wsnComponent.getServiceUnitManager().start("subscription");

        wsnBroker.notify("myTopic", parse("<hello>world</hello>"));
        // Wait for notification
        Thread.sleep(2000);
        receiver.getMessageList().assertMessagesReceived(1);
        receiver.getMessageList().flushMessages();

        wsnComponent.getServiceUnitManager().stop("subscription");
        wsnComponent.getServiceUnitManager().shutDown("subscription");

        wsnBroker.notify("myTopic", parse("<hello>world</hello>"));
        // Wait for notification
        Thread.sleep(2000);
        assertEquals(0, receiver.getMessageList().flushMessages().size());

        wsnComponent.getServiceUnitManager().init("subscription", path.getAbsolutePath());
        wsnComponent.getServiceUnitManager().start("subscription");

        wsnBroker.notify("myTopic", parse("<hello>world</hello>"));
        // Wait for notification
        Thread.sleep(2000);
        receiver.getMessageList().assertMessagesReceived(1);
        receiver.getMessageList().flushMessages();
    }

    public void testWsRfRp() throws Exception {
        List<Object> props = wsnBroker.getResourceProperty(AbstractNotificationBroker.TOPIC_EXPRESSION_DIALECT_QNAME);
        for (Object o : props) {
            if (o instanceof JAXBElement) {
                System.err.println(((JAXBElement) o).getValue());
            } else {
                System.err.println(o);
            }
        }
    }

    protected Element parse(String txt) throws Exception {
        DocumentBuilder builder = new SourceTransformer().createDocumentBuilder();
        InputSource is = new InputSource(new StringReader(txt));
        Document doc = builder.parse(is);
        return doc.getDocumentElement();
    }

    protected W3CEndpointReference createEPR(QName service, String endpoint) {
        return AbstractWSAClient.createWSA(service.getNamespaceURI() + "/" + service.getLocalPart() + "/" + endpoint);
    }

}
