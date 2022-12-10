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

import java.util.Map;
import java.util.Properties;

import javax.jbi.component.ComponentContext;
import javax.jbi.messaging.DeliveryChannel;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.Fault;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.servicemix.common.EndpointComponentContext;
import org.apache.servicemix.common.JbiConstants;
import org.apache.servicemix.soap.Context;
import org.apache.servicemix.soap.SoapFault;
import org.apache.servicemix.soap.SoapHelper;
import org.apache.servicemix.soap.SoapExchangeProcessor;
import org.apache.servicemix.soap.marshalers.SoapMessage;
import org.apache.servicemix.store.Store;
import org.apache.servicemix.store.memory.MemoryStoreFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractJmsProcessor implements SoapExchangeProcessor {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    public static final String STYLE_QUEUE = "queue";
    public static final String STYLE_TOPIC = "topic";
    
    public static final String CONTENT_TYPE = "MimeContentType";

    protected JmsEndpoint endpoint;
    protected Connection connection;
    protected SoapHelper soapHelper;
    protected ComponentContext context;
    protected DeliveryChannel channel;
    protected Session session;
    protected Destination destination;
    protected Destination replyToDestination;

    protected Store store;

    public AbstractJmsProcessor(JmsEndpoint endpoint) throws Exception {
        this.endpoint = endpoint;
        this.soapHelper = new SoapHelper(endpoint);
        this.context = new EndpointComponentContext(endpoint);
        this.channel = context.getDeliveryChannel();
    }

    protected void commonDoStartTasks(InitialContext ctx) throws Exception {
        channel = endpoint.getServiceUnit().getComponent().getComponentContext().getDeliveryChannel();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        destination = endpoint.getDestination();
        if (destination == null) {
            if (endpoint.getJndiDestinationName() != null) {
                destination = (Destination) ctx.lookup(endpoint.getJndiDestinationName());
            } else if (endpoint.getJmsProviderDestinationName() != null) {
                if (STYLE_QUEUE.equals(endpoint.getDestinationStyle())) {
                    destination = session.createQueue(endpoint.getJmsProviderDestinationName());
                } else {
                    destination = session.createTopic(endpoint.getJmsProviderDestinationName());
                }
            } else {
                throw new IllegalStateException("No destination provided");
            }
        }
        if (endpoint.getJndiReplyToName() != null) {
            replyToDestination = (Destination) ctx.lookup(endpoint.getJndiReplyToName());
        } else if (endpoint.getJmsProviderReplyToName() != null) {
            if (destination instanceof Queue) {
                replyToDestination = session.createQueue(endpoint.getJmsProviderReplyToName());
            } else {
                replyToDestination = session.createTopic(endpoint.getJmsProviderReplyToName());
            }
        }        
    }
    
    protected ConnectionFactory getConnectionFactory(InitialContext ctx) throws NamingException {
        // First check configured connectionFactory on the endpoint
        ConnectionFactory connectionFactory = endpoint.getConnectionFactory();
        // Then, check for jndi connection factory name on the endpoint
        if (connectionFactory == null && endpoint.getJndiConnectionFactoryName() != null) {
            connectionFactory = (ConnectionFactory) ctx.lookup(endpoint.getJndiConnectionFactoryName());
        }
        // Check for a configured connectionFactory on the configuration
        if (connectionFactory == null && endpoint.getConfiguration().getConnectionFactory() != null) {
            connectionFactory = endpoint.getConfiguration().getConnectionFactory();
        }
        // Check for jndi connection factory name on the configuration
        if (connectionFactory == null && endpoint.getConfiguration().getJndiConnectionFactoryName() != null) {
            connectionFactory = (ConnectionFactory) ctx.lookup(endpoint.getConfiguration().getJndiConnectionFactoryName());
        }
        return connectionFactory;
    }

    protected InitialContext getInitialContext() throws NamingException {
        Properties props = new Properties();
        if (endpoint.getInitialContextFactory() != null && endpoint.getJndiProviderURL() != null) {
            props.put(InitialContext.INITIAL_CONTEXT_FACTORY, endpoint.getInitialContextFactory());
            props.put(InitialContext.PROVIDER_URL, endpoint.getJndiProviderURL());
            return new InitialContext(props);
        } else if (endpoint.getConfiguration().getJndiInitialContextFactory() != null 
                   && endpoint.getConfiguration().getJndiProviderUrl() != null) {
            props.put(InitialContext.INITIAL_CONTEXT_FACTORY, endpoint.getConfiguration().getJndiInitialContextFactory());
            props.put(InitialContext.PROVIDER_URL, endpoint.getConfiguration().getJndiProviderUrl());
            return new InitialContext(props);
        } else {
            return endpoint.getServiceUnit().getComponent().getComponentContext().getNamingContext();
        }
    }

    protected Store getStore() {
        return store;
    }

    public void init() throws Exception {
        try {
            InitialContext ctx = getInitialContext();
            ConnectionFactory connectionFactory = null;
            connectionFactory = getConnectionFactory(ctx);
            connection = connectionFactory.createConnection();
            connection.start();

            // set up the Store
            if (endpoint.store != null) {
                store = endpoint.store;
            } else if (endpoint.storeFactory != null) {
                store = endpoint.storeFactory.open(endpoint.getService().toString() + endpoint.getEndpoint());
            } else {
                store = new MemoryStoreFactory().open(endpoint.getService().toString() + endpoint.getEndpoint());
            }

            doInit(ctx);
        } catch (Exception e) {
            shutdown();
        }
    }

    protected void doInit(InitialContext ctx) throws Exception {
    }

    public void start() throws Exception {
        try {
            doStart();
        } catch (Exception e) {
            try {
                stop();
            } catch (Exception inner) {
                // TODO: logger
            }
            throw e;
        }
    }

    protected void doStart() throws Exception {
    }

    public void stop() throws Exception {
        try {
            doStop();
        } finally {
        }
    }

    protected void doStop() throws Exception {
    }

    public void shutdown() throws Exception {
        try {
            doShutdown();
            if (connection != null) {
                connection.close();
            }
        } finally {
            connection = null;
        }
    }

    protected void doShutdown() throws Exception {
    }

    protected Context createContext() {
        return soapHelper.createContext();
    }
    
    protected Message fromNMS(NormalizedMessage nm, Session session) throws Exception {
        SoapMessage soap = new SoapMessage();
        soapHelper.getJBIMarshaler().fromNMS(soap, nm);
        Map headers = (Map) nm.getProperty(JbiConstants.PROTOCOL_HEADERS);
        return endpoint.getMarshaler().toJMS(soap, headers, session);
    }
    
    protected MessageExchange toNMS(Message message, Context ctx) throws Exception {
        SoapMessage soap = endpoint.getMarshaler().toSOAP(message);
        ctx.setInMessage(soap);
        ctx.setProperty(Message.class.getName(), message);
        MessageExchange exchange = soapHelper.onReceive(ctx);
        // TODO: copy protocol messages
        //inMessage.setProperty(JbiConstants.PROTOCOL_HEADERS, getHeaders(message));
        return exchange;
    }
    
    protected Message fromNMSResponse(MessageExchange exchange, Context ctx, Session session) throws Exception {
        Message response = null;
        if (exchange.getStatus() == ExchangeStatus.ERROR) {
            // marshal error
            Exception e = exchange.getError();
            if (e == null) {
                e = new Exception("Unkown error");
            }
            response = endpoint.getMarshaler().toJMS(e, session);
        } else if (exchange.getStatus() == ExchangeStatus.ACTIVE) {
            // check for fault
            Fault jbiFault = exchange.getFault(); 
            if (jbiFault != null) {
                // convert fault to SOAP message
                SoapFault fault = new SoapFault(SoapFault.RECEIVER, null, null, null, jbiFault.getContent());
                SoapMessage soapFault = soapHelper.onFault(ctx, fault);
                Map headers = (Map) jbiFault.getProperty(JbiConstants.PROTOCOL_HEADERS);
                response = endpoint.getMarshaler().toJMS(soapFault, headers, session);
            } else {
                NormalizedMessage outMsg = exchange.getMessage("out");
                if (outMsg != null) {
                    SoapMessage out = soapHelper.onReply(ctx, outMsg);
                    Map headers = (Map) outMsg.getProperty(JbiConstants.PROTOCOL_HEADERS);
                    response = endpoint.getMarshaler().toJMS(out, headers, session);
                }
            }
        }
        return response;
    }
    
    protected Message createMessageFromExchange(Session session,
            MessageExchange exchange) throws Exception {
//        TextMessage msg = session.createTextMessage();
        NormalizedMessage nm = exchange.getMessage("in");
        Message msg = fromNMS(nm, session);

        // Build the SoapAction from <interface namespace>/<interface
        // name>/<operation name>
        String soapAction = "";
        if (exchange.getOperation() != null) {
            String interFaceName = exchange.getInterfaceName() == null ? ""
                    : exchange.getInterfaceName().getNamespaceURI() + "/"
                            + exchange.getInterfaceName().getLocalPart();
            soapAction = interFaceName + "/" + exchange.getOperation();
        }
        msg.setStringProperty("SoapAction", soapAction);
        msg.setStringProperty("SOAPJMS_soapAction", soapAction);
        return msg;
    }

}
