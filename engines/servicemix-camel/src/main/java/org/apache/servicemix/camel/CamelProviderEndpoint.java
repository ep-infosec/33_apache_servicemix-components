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

import java.util.concurrent.Callable;

import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.Fault;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.MessagingException;
import javax.jbi.messaging.RobustInOnly;
import javax.xml.namespace.QName;

import org.apache.camel.*;
import org.apache.camel.spi.Synchronization;

import org.apache.servicemix.common.JbiConstants;
import org.apache.servicemix.common.ServiceUnit;
import org.apache.servicemix.common.endpoints.ProviderEndpoint;
import org.apache.servicemix.jbi.exception.FaultException;

/**
 * A JBI endpoint which when invoked will delegate to a Camel endpoint
 *
 * @version $Revision: 426415 $
 */
public class CamelProviderEndpoint extends ProviderEndpoint implements Synchronization {

    public static final QName SERVICE_NAME = new QName("http://camel.apache.org/schema/jbi", "provider");

    private final JbiBinding binding;

    private Endpoint camelEndpoint;

    private AsyncProcessor camelProcessor;
    
    public CamelProviderEndpoint(ServiceUnit serviceUnit, QName service, String endpoint, JbiBinding binding, AsyncProcessor camelProcessor) {
        super(serviceUnit, service, endpoint);
        this.camelProcessor = camelProcessor;
        this.binding = binding;
    }

    public CamelProviderEndpoint(ServiceUnit serviceUnit, Endpoint camelEndpoint, JbiBinding binding, AsyncProcessor camelProcessor) {
        this(serviceUnit, SERVICE_NAME, camelEndpoint.getEndpointUri(), binding, camelProcessor);
    }

    /**
     * Set the Camel JbiEndpoint instance that corresponds with this JBI endpoint
     *
     * @param camelEndpoint the Camel JbiEndpoint instance
     */
    public void setCamelEndpoint(Endpoint camelEndpoint) {
        this.camelEndpoint = camelEndpoint;
    }

    @Override
    public void process(MessageExchange exchange) throws Exception {
        // The component acts as a provider, this means that another component has requested our service
        // As this exchange is active, this is either an in or a fault (out are sent by this component)
        
        if (exchange.getRole() == MessageExchange.Role.PROVIDER) {
            // Exchange is finished
            if (exchange.getStatus() == ExchangeStatus.DONE) {
                return;
            // Exchange has been aborted with an exception
            } else if (exchange.getStatus() == ExchangeStatus.ERROR) {
                return;
            // Exchange is active
            } else {
                handleActiveProviderExchange(exchange);

            }
        // Unsupported role: this should never happen has we never create exchanges
        } else {
            throw new IllegalStateException("Unsupported role: " + exchange.getRole());
        }
    }

    protected void handleActiveProviderExchange(MessageExchange exchange) throws Exception {
        // Fault message
        if (exchange.getFault() != null) {
            done(exchange);
        // In message
        } else if (exchange.getMessage("in") != null) {
            logger.debug("Received exchange: {}", exchange);
            final Exchange camelExchange = binding.createExchange(exchange);
            camelExchange.setFromEndpoint(camelEndpoint);
            camelExchange.addOnCompletion(this);

            binding.runWithCamelContextClassLoader(new Callable<Object>() {
                public Object call() throws Exception {
                    camelProcessor.process(camelExchange, new AsyncCallback() {
                        public void done(boolean doneSync) {
                            // result processing done by onFailure/onSuccess methods
                        }
                    });
                    return null;
                }
            });

        } else {
            // This is not complaint with the default MEPs
            throw new IllegalStateException("Provider exchange is ACTIVE, but no in or fault is provided");
        }
    }

    /*
     * Check if the exchange is capable of conveying fault messages
     */
    private boolean isFaultCapable(MessageExchange exchange) {
        return !(exchange instanceof InOnly);
    }
    
    /*
     * Send back the response, taking care to use sendSync when necessary
     */
    private void doSend(MessageExchange exchange) throws MessagingException {
        boolean txSync = exchange.isTransacted() && Boolean.TRUE.equals(exchange.getProperty(JbiConstants.SEND_SYNC));
        if (txSync && ExchangeStatus.ACTIVE.equals(exchange.getStatus())) {
            sendSync(exchange);
        } else {
            send(exchange);
        }        
    }

    public void onComplete(Exchange exchange) {
        MessageExchange me = JbiBinding.getMessageExchange(exchange);
        try {
            binding.copyFromCamelToJbi(exchange, me);
            if (me instanceof InOnly || me instanceof RobustInOnly) {
                done(me);
            } else {
                doSend(me);
            }
        } catch (MessagingException e) {
            logger.warn("Unable to send JBI MessageExchange after successful Camel route invocation: {}", me, e);
        }
    }

    public void onFailure(Exchange exchange) {
        MessageExchange me = JbiBinding.getMessageExchange(exchange);
        try {
            if (exchange.hasOut() && exchange.getOut().isFault()) {
                Fault fault = me.createFault();
                binding.copyFromCamelToJbi(exchange.getOut(), fault);
                if (isFaultCapable(me)) {
                    me.setFault(fault);
                    doSend(me);
                } else {
                    // MessageExchange is not capable of conveying faults -- sending the information as an error instead
                    fail(me, new FaultException("Fault occured for " + exchange.getPattern() + " exchange", me, fault));
                }
            } else {
                fail(me, binding.extractException(exchange));
            }
        } catch (MessagingException e) {
            logger.warn("Unable to send JBI MessageExchange after successful Camel route invocation: {}", me, e);
        } 
    }
}
