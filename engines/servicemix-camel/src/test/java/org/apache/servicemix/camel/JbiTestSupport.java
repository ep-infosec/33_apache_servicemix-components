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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jbi.JBIException;
import javax.jbi.messaging.MessageExchange;
import javax.xml.namespace.QName;

import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.test.TestSupport;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.servicemix.client.DefaultServiceMixClient;
import org.apache.servicemix.client.ServiceMixClient;
import org.apache.servicemix.common.Registry;
import org.apache.servicemix.jbi.container.ActivationSpec;
import org.apache.servicemix.jbi.container.JBIContainer;
import org.apache.servicemix.jbi.container.SpringJBIContainer;
import org.apache.servicemix.jbi.event.ExchangeEvent;
import org.apache.servicemix.jbi.event.ExchangeListener;
import org.apache.servicemix.tck.ExchangeCompletedListener;

/**
 * @version $Revision: 563665 $
 */
public abstract class JbiTestSupport extends CamelTestSupport {

    protected Exchange receivedExchange;

    protected CamelContext camelContext;

    protected SpringJBIContainer jbiContainer = new SpringJBIContainer();
    
    protected CamelJbiComponent component;

    protected ExchangeCompletedListener exchangeCompletedListener;

    protected CountDownLatch latch = new CountDownLatch(1);

    protected Endpoint endpoint;

    protected String startEndpointUri = "jbi:endpoint:serviceNamespace:serviceA:endpointA";

    protected ProducerTemplate client;

    protected ServiceMixClient servicemixClient;

    /**
     * Sends an exchange to the endpoint
     */
    protected void sendExchange(final Object expectedBody) {
        client.send(endpoint, new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody(expectedBody);
                in.setHeader("cheese", 123);
            }
        });
    }

    /**
     * Sends an exchange to the endpoint
     */
    protected AtomicBoolean sendExchangeAsync(final Object expectedBody) {
        final AtomicBoolean bool = new AtomicBoolean();
        client.asyncCallback(endpoint, new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody(expectedBody);
                in.setHeader("cheese", 123);
            }
        }, new Synchronization() {
            
            public void onFailure(Exchange exchange) {
                // graciously do nothing here    
            }
            
            public void onComplete(Exchange exchange) {
                bool.set(true);
                bool.notify();
            }
        });
        return bool;
    }

    protected Object assertReceivedValidExchange(Class type) throws Exception {
        // lets wait on the message being received
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Did not receive the message!", received);

        assertNotNull(receivedExchange);
        Message receivedMessage = receivedExchange.getIn();

        assertEquals("cheese header", 123, receivedMessage.getHeader("cheese"));
        Object body = receivedMessage.getBody();
        log.debug("Received body: " + body);
        return body;
    }

    @Override
    public void setUp() throws Exception {
        if (camelContext == null) {
            camelContext = createCamelContext();
            client = camelContext.createProducerTemplate();
        }
        
        configureContainer(jbiContainer);
        List<ActivationSpec> activationSpecList = new ArrayList<ActivationSpec>();

        // lets add the Camel endpoint
        component = new CamelJbiComponent();
        activationSpecList.add(createActivationSpec(component, new QName("camel", "camel"), "camelEndpoint"));

        // and provide a callback method for adding more services
        appendJbiActivationSpecs(activationSpecList);
        jbiContainer.setActivationSpecs(activationSpecList.toArray(new ActivationSpec[activationSpecList.size()]));

        jbiContainer.afterPropertiesSet();

        exchangeCompletedListener = new ExchangeCompletedListener(2000);
        jbiContainer.addListener(exchangeCompletedListener);

        // allow for additional configuration of the compenent (e.g. deploying SU)
        configureComponent(component);

        // lets add some routes
        RouteBuilder builder = createRoutes();
        if (builder != null) {
            camelContext.addRoutes(builder);
        }
        endpoint = camelContext.getEndpoint(startEndpointUri);

        camelContext.start();
    }
    
    protected CamelContext createCamelContext() {
        return new DefaultCamelContext();
    }

    protected void configureComponent(CamelJbiComponent camelComponent) throws Exception {
        // add the ServiceMix Camel component to the CamelContext
        camelContext.addComponent("jbi", new JbiComponent(camelComponent));
    }

    protected void configureContainer(final JBIContainer container) throws Exception {
        container.setEmbedded(true);
        container.setForceShutdown(1000);
    }

    public ServiceMixClient getServicemixClient() throws JBIException {
        if (servicemixClient == null) {
            servicemixClient = new DefaultServiceMixClient(jbiContainer);
        }
        return servicemixClient;
    }

    protected ActivationSpec createActivationSpec(Object comp, QName service) {
        return createActivationSpec(comp, service, "endpoint");
    }

    protected ActivationSpec createActivationSpec(Object comp, QName service, String endpointName) {
        ActivationSpec spec = new ActivationSpec(comp);
        spec.setService(service);
        spec.setEndpoint(endpointName);
        return spec;
    }

    @Override
    public void tearDown() throws Exception {
        exchangeCompletedListener.assertExchangeCompleted();

        getServicemixClient().close();
        client.stop();

        assertCamelConsumerEndpointsDone();
        
        camelContext.stop();
        super.tearDown();
    }

    protected MockEndpoint getMockEndpoint(String uri) {
        return (MockEndpoint) camelContext.getEndpoint(uri);
    }

    protected void enableCheckForSerializableExchanges() {
        jbiContainer.addListener(new ExchangeListener() {

            public void exchangeSent(ExchangeEvent exchangeEvent) {
                MessageExchange exchange = exchangeEvent.getExchange();
                try {
                    ObjectOutputStream oos = new ObjectOutputStream(new ByteArrayOutputStream());
                    oos.writeObject(exchange);
                } catch (NotSerializableException e) {
                    fail("Non-serializable MessageExchange found: " + exchange);
                } catch (IOException e) {
                    fail("Error while trying to serialize MessageExchange " + exchange + ":" + e.getMessage());
                }
            }

            public void exchangeAccepted(ExchangeEvent exchangeEvent) {
                // graciously do nothing
            }
        });
    }

    protected void disableExchangeCompletedListener() {
        jbiContainer.removeListener(exchangeCompletedListener);
    }

    /*
     * Assert that all CamelConsumerEndpoint are done:
     * - there should be no more pending ContinuationData instances 
     */
    private void assertCamelConsumerEndpointsDone() throws Exception {
        List<CamelConsumerEndpoint> results = findEndpoints(CamelConsumerEndpoint.class);

        for (CamelConsumerEndpoint endpoint : results) {
            assertEquals("Continuation data map should be empty on endpoint for " + endpoint.getJbiEndpoint().getDestinationUri(),
                         0, endpoint.getContinuationData().size());

        }
    }

    /*
     * Find endpoints in the component registry for the type provided
     */
    private<E> List<E> findEndpoints(Class<E> type) throws NoSuchFieldException, IllegalAccessException {
        List<E> results = new LinkedList<E>();

        // little hack to access the endpoints map in the registry directly
        Field field = Registry.class.getDeclaredField("endpoints");
        field.setAccessible(true);
        Map<String, org.apache.servicemix.common.Endpoint> endpoints = new HashMap();
        endpoints.putAll((Map) field.get(component.getRegistry()));

        for (org.apache.servicemix.common.Endpoint endpoint : endpoints.values()) {
            if (type.isAssignableFrom(endpoint.getClass())) {
                results.add((E) endpoint);
            }
        }

        return results;
    }


    protected abstract void appendJbiActivationSpecs(
            List<ActivationSpec> activationSpecList);

    protected abstract RouteBuilder createRoutes();
}
