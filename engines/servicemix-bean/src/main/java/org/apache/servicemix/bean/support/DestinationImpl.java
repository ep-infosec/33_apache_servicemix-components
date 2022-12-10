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
package org.apache.servicemix.bean.support;

import java.util.concurrent.Future;

import javax.jbi.messaging.NormalizedMessage;

import org.apache.servicemix.bean.BeanEndpoint;
import org.apache.servicemix.bean.Destination;
import org.apache.servicemix.common.util.MessageUtil;

public class DestinationImpl implements Destination {

    private final BeanEndpoint endpoint;
    private final String uri;
    
    public DestinationImpl(String uri, BeanEndpoint endpoint) {
        this.uri = uri;
        this.endpoint = endpoint;
    }
    
    public NormalizedMessage createMessage() {
        return new MessageUtil.NormalizedMessageImpl();
    }

    public Future<NormalizedMessage> send(NormalizedMessage message) {
        return endpoint.send(uri, message);
    }
    
}