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
package org.apache.servicemix.script;

import javax.jbi.component.ComponentContext;
import javax.jbi.messaging.DeliveryChannel;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.MessageExchangeFactory;
import javax.jbi.messaging.MessagingException;

/**
 * This helper object can be injected into your scripts to allow to quick access
 * to basic JBI operations
 * 
 * @org.apache.xbean.XBean element="exchangeHelper" description="ServiceMix
 *                         Scripting Helper"
 */
public class ScriptExchangeHelper implements ScriptHelper {

    protected ScriptExchangeProcessorEndpoint endpoint;

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.servicemix.script.ScriptHelper#setScriptExchangeProcessorEndpoint(
     *          org.apache.servicemix.script.ScriptExchangeProcessorEndpoint)
     */
    public void setScriptExchangeProcessorEndpoint(ScriptExchangeProcessorEndpoint ep) {
        this.endpoint = ep;
    }

    public void doneExchange(MessageExchange exchange) throws MessagingException {
        endpoint.done(exchange);
    }

    public void failExchange(MessageExchange exchange, Exception exception) throws MessagingException {
        endpoint.fail(exchange, exception);
    }

    public void sendExchange(MessageExchange exchange) throws MessagingException {
        endpoint.send(exchange);
    }

    public void sendSyncExchange(MessageExchange exchange) throws MessagingException {
        endpoint.sendSync(exchange);
    }

    public DeliveryChannel getChannel() {
        return endpoint.getChannel();
    }

    public ComponentContext getContext() {
        return endpoint.getContext();
    }

    public MessageExchangeFactory getExchangeFactory() {
        return endpoint.getExchangeFactory();
    }
}