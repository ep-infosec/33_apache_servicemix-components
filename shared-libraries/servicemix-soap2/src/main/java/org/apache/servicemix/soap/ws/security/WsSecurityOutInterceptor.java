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
package org.apache.servicemix.soap.ws.security;

import java.net.URI;
import java.util.Collection;

import javax.xml.namespace.QName;

import org.apache.servicemix.soap.api.Message;
import org.apache.servicemix.soap.bindings.soap.SoapInterceptor;
import org.apache.servicemix.soap.core.AbstractInterceptor;

public class WsSecurityOutInterceptor extends AbstractInterceptor implements SoapInterceptor {

    private final WsSecurityPolicy policy;
    
    public WsSecurityOutInterceptor(WsSecurityPolicy policy) {
        this.policy = policy;
    }

    public void handleMessage(Message message) {
        boolean required = policy.isRequired();
        // TODO
    }

    public Collection<URI> getRoles() {
        // TODO Auto-generated method stub
        return null;
    }

    public Collection<QName> getUnderstoodHeaders() {
        // TODO Auto-generated method stub
        return null;
    }

}
