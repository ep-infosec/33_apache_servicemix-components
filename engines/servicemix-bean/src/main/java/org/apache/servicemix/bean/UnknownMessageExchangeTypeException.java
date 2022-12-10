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
package org.apache.servicemix.bean;

import javax.jbi.messaging.MessageExchange;

/**
 * An exception thrown if an unknown message exchange is used with a @{link BeanEndpoint}
 *
 * @version $Revision: $
 */
public class UnknownMessageExchangeTypeException extends IllegalArgumentException {

    private final MessageExchange messageExchange;
    private final BeanEndpoint endpoint;

    public UnknownMessageExchangeTypeException(MessageExchange messageExchange, BeanEndpoint endpoint) {
        super("Unknown message exchange: " + messageExchange + " on endpoint: " + endpoint);
        this.messageExchange = messageExchange;
        this.endpoint = endpoint;
    }


    public MessageExchange getMessageExchange() {
        return messageExchange;
    }

    public BeanEndpoint getEndpoint() {
        return endpoint;
    }
}
