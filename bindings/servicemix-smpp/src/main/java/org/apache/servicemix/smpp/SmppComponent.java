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
package org.apache.servicemix.smpp;

import org.apache.servicemix.common.DefaultComponent;

import java.util.List;

/**
 * @org.apache.xbean.XBean element="component" description="Smpp Component" SMPP
 * ServiceMix Component allowing to use SMPP protocol.
 * It provides interfaces to communicate with Message
 * Center or ESME (External Short Message Entity). SMPP
 * is stand for Short Message Peer to Peer. It is a
 * standard protocol for exchanging SMS messages between
 * SMS entities over TCP/IP or X.25 connections.
 *
 * @author lhein
 * @author jbonofre
 */
public class SmppComponent extends DefaultComponent {

    // the list of SMPP endpoints
    private SmppEndpointType[] endpoints;

    /**
     * Getter on the component endpoints
     *
     * @return the SMPP endpoints list
     */
    public SmppEndpointType[] getEndpoints() {
        return this.endpoints;
    }

    /**
     * Setter on the components endpoints
     *
     * @param endpoints the SMPP endpoints list
     */
    public void setEndpoints(SmppEndpointType[] endpoints) {
        this.endpoints = endpoints;
    }

    /**
     * @see org.apache.servicemix.common.DefaultComponent#getConfiguredEndpoints()
     */
    protected List getConfiguredEndpoints() {
        return asList(this.getEndpoints());
    }

    /**
     * @see org.apache.servicemix.common.DefaultComponent#getEndpointClasses()
     */
    protected Class[] getEndpointClasses() {
        return new Class[]{SmppConsumerEndpoint.class, SmppProviderEndpoint.class};
    }
}
