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
package org.apache.servicemix.jms.standard;

import org.apache.servicemix.jms.AbstractJmsProcessor;
import org.apache.servicemix.jms.JmsEndpoint;
import org.apache.servicemix.soap.Context;

import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.MessageExchange;
import javax.jms.*;
import javax.naming.InitialContext;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.IllegalStateException;

public class StandardConsumerProcessor extends AbstractJmsProcessor {

    protected Session session;
    protected Destination destination;
    protected AtomicBoolean running = new AtomicBoolean(false);

    public StandardConsumerProcessor(JmsEndpoint endpoint) throws Exception {
        super(endpoint);
    }

    protected void doInit(InitialContext ctx) throws Exception {
        destination = endpoint.getDestination();
        if (destination == null) {
            if (endpoint.getJndiDestinationName() != null) {
                destination = (Destination) ctx.lookup(endpoint.getJndiDestinationName());
            } else if (endpoint.getJmsProviderDestinationName() == null) {
                throw new IllegalStateException("No destination provided");
            }
        }
    }

    protected void doStart() throws Exception {
        synchronized (running) {
            endpoint.getServiceUnit().getComponent().getExecutor(MessageExchange.Role.CONSUMER).execute(new Runnable() {
                public void run() {
                    StandardConsumerProcessor.this.poll();
                }
            });
            running.wait();
        }
    }

    protected void doStop() throws Exception {
        if (running.get()) {
            synchronized (running) {
                running.set(false);
                running.wait();
            }
        }
    }

    protected void doShutdown() throws Exception {
        if (session != null) {
            session.close();
        }
        session = null;
        destination = null;
    }

    protected void poll() {
        synchronized (running) {
            running.set(true);
            running.notify();
        }
        try {
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            if (destination == null) {
                if (STYLE_QUEUE.equals(endpoint.getDestinationStyle())) {
                    destination = session.createQueue(endpoint.getJmsProviderDestinationName());
                } else {
                    destination = session.createTopic(endpoint.getJmsProviderDestinationName());
                }
            }
            MessageConsumer consumer = session.createConsumer(destination);
            while (running.get()) {
                Message message = consumer.receive(1000L);
                if (message != null) {
                    onMessage(message);
                }
            }
        } catch (Exception e) {
            logger.error("Error during polling.", e);
        } finally {
            synchronized (running) {
                running.notify();
            }
        }
    }

    public void onMessage(final Message message) {
        try {
            logger.debug("Received jms message {}", message);
            Context context = createContext();
            MessageExchange exchange = toNMS(message, context);
            if (!channel.sendSync(exchange)) {
                throw new IllegalStateException("Exchange has been aborted");
            }
            MessageProducer producer = null;
            Message response = null;
            try {
                response = fromNMSResponse(exchange, context, session);
                if (response != null) {
                    producer = session.createProducer(message.getJMSReplyTo());
                    if (endpoint.isUseMsgIdInResponse()) {
                        response.setJMSCorrelationID(message.getJMSMessageID());
                    } else {
                        response.setJMSCorrelationID(message.getJMSCorrelationID());
                    }
                    producer.send(response);
                }
            } finally {
                if (producer != null) {
                    producer.close();
                }
                if (exchange.getStatus() == ExchangeStatus.ACTIVE) {
                    exchange.setStatus(ExchangeStatus.DONE);
                    channel.send(exchange);
                }
            }
        } catch (Throwable e) {
            logger.error("Error while handling jms message", e);
        }
    }

    public void process(MessageExchange exchange) throws Exception {
        throw new IllegalStateException();
    }

}
