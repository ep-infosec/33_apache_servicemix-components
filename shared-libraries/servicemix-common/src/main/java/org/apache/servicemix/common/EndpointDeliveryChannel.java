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
package org.apache.servicemix.common;

import javax.jbi.component.ComponentContext;
import javax.jbi.messaging.DeliveryChannel;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.MessageExchangeFactory;
import javax.jbi.messaging.MessagingException;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.servicedesc.ServiceEndpoint;
import javax.xml.namespace.QName;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.InvalidTransactionException;
import javax.transaction.SystemException;

/**
 * <p>
 * This class is a wrapper around an existing DeliveryChannel
 * that will be given to service engine endpoints so that
 * they are able to send messages and to interact with the
 * JBI container.
 * </p>
 * 
 * @author gnodet
 */
public class EndpointDeliveryChannel implements DeliveryChannel {

    private static final ThreadLocal<Endpoint> ENDPOINT_TLS = new ThreadLocal<Endpoint>();

    private final DeliveryChannel channel;
    private final Endpoint endpoint;

    public EndpointDeliveryChannel(Endpoint endpoint) throws MessagingException {
        this.endpoint = endpoint;
        this.channel = endpoint.getServiceUnit().getComponent().getComponentContext().getDeliveryChannel();
    }

    public EndpointDeliveryChannel(ComponentContext context) throws MessagingException {
        this.endpoint = null;
        this.channel = context.getDeliveryChannel();
    }

    public MessageExchange accept() throws MessagingException {
        throw new UnsupportedOperationException();
    }

    public MessageExchange accept(long timeout) throws MessagingException {
        throw new UnsupportedOperationException();
    }

    public void close() throws MessagingException {
        throw new UnsupportedOperationException();
    }

    public MessageExchangeFactory createExchangeFactory() {
        return channel.createExchangeFactory();
    }

    public MessageExchangeFactory createExchangeFactory(QName interfaceName) {
        return channel.createExchangeFactory(interfaceName);
    }

    public MessageExchangeFactory createExchangeFactory(ServiceEndpoint endpoint) {
        return channel.createExchangeFactory(endpoint);
    }

    public MessageExchangeFactory createExchangeFactoryForService(QName serviceName) {
        return channel.createExchangeFactoryForService(serviceName);
    }

    public void send(MessageExchange exchange) throws MessagingException {
        prepareExchange(exchange);
        handleExchange(exchange, exchange.getStatus() == ExchangeStatus.ACTIVE);
        try {
            channel.send(exchange);
        } catch (MessagingException e) {
            handleExchange(exchange, false);
            throw e;
        }
    }

    public boolean sendSync(MessageExchange exchange, long timeout) throws MessagingException {
        boolean processed = false;
        try {
            prepareExchange(exchange);
            handleExchange(exchange, exchange.getStatus() == ExchangeStatus.ACTIVE);
            boolean ret = channel.sendSync(exchange, timeout);
            handleExchange(exchange, exchange.getStatus() == ExchangeStatus.ACTIVE);
            if (ret) {
                resumeTx(exchange);
                processed = true;
            }
            return ret;
        } finally {
            if (!processed) {
                handleExchange(exchange, false);
            }
        }
    }

    public boolean sendSync(MessageExchange exchange) throws MessagingException {
        boolean processed = false;
        try {
            prepareExchange(exchange);
            handleExchange(exchange, exchange.getStatus() == ExchangeStatus.ACTIVE);
            boolean ret = channel.sendSync(exchange);
            handleExchange(exchange, exchange.getStatus() == ExchangeStatus.ACTIVE);
            if (ret) {
                resumeTx(exchange);
                processed = true;
            }
            return ret;
        } finally {
            if (!processed) {
                handleExchange(exchange, false);
            }
        }
    }

    private void resumeTx(MessageExchange exchange) throws MessagingException {
        if (!getEndpoint().getServiceUnit().getComponent().getContainer().handleTransactions()) {
            Transaction tx = (Transaction) exchange.getProperty(MessageExchange.JTA_TRANSACTION_PROPERTY_NAME);
            if (tx != null) {
                TransactionManager txmgr = (TransactionManager) endpoint.getServiceUnit().getComponent().getComponentContext().getTransactionManager();
                try {
                    txmgr.resume(tx);
                } catch (InvalidTransactionException e) {
                    throw new MessagingException(e);
                } catch (SystemException e) {
                    throw new MessagingException(e);
                }
            }
        }
    }

    protected void prepareExchange(MessageExchange exchange) throws MessagingException {
        Endpoint ep = getEndpoint();
        ep.getServiceUnit().getComponent().prepareExchange(exchange, ep);
    }

    protected void handleExchange(MessageExchange exchange, boolean add) throws MessagingException {
        Endpoint ep = getEndpoint();
        ep.getServiceUnit().getComponent().handleExchange(ep, exchange, add);
    }

    protected Endpoint getEndpoint() {
        if (endpoint != null) {
            return endpoint;
        }
        return ENDPOINT_TLS.get();
    }

    public static void setEndpoint(Endpoint endpoint) {
        ENDPOINT_TLS.set(endpoint);
    }
}
