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
package org.apache.servicemix.jsr181.packaging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.logging.Logger;

import javax.jbi.JBIException;
import javax.jbi.component.ComponentContext;
import javax.jbi.management.MBeanNames;
import javax.jbi.messaging.DeliveryChannel;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.MessageExchangeFactory;
import javax.jbi.messaging.MessagingException;
import javax.jbi.servicedesc.ServiceEndpoint;
import javax.management.MBeanServer;
import javax.naming.InitialContext;
import javax.xml.namespace.QName;

import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;

import org.apache.servicemix.common.Endpoint;
import org.apache.servicemix.common.packaging.Consumes;
import org.apache.servicemix.common.packaging.Provides;
import org.apache.servicemix.common.xbean.AbstractXBeanServiceUnitAnalyzer;
import org.apache.servicemix.common.xbean.ParentBeanFactoryPostProcessor;
import org.apache.servicemix.jsr181.Jsr181Component;
import org.apache.servicemix.jsr181.Jsr181Endpoint;

public class Jsr181ServiceUnitAnalyzer extends AbstractXBeanServiceUnitAnalyzer {

    protected List<Consumes> getConsumes(Endpoint endpoint) {
        return new ArrayList<Consumes>();
    }

    protected List<Provides> getProvides(Endpoint endpoint) {
        // We need to generate the dummy component to register the services
        Jsr181Endpoint jsr181Endpoint = (Jsr181Endpoint) endpoint;
        try {
            Jsr181Component componentDummy = new Jsr181Component();
            componentDummy.setEndpoints(new Jsr181Endpoint[] {jsr181Endpoint });
            componentDummy.getLifeCycle().init(new DummyComponentContext());
        } catch (Exception e) {
            throw new RuntimeException("Unable to register JSR-181 service, " + e.getMessage(), e);
        }
        return super.getProvides(endpoint);
    }

    protected String getXBeanFile() {
        return "xbean.xml";
    }

    protected boolean isValidEndpoint(Object bean) {
        return bean instanceof Jsr181Endpoint;
    }

    protected List getBeanFactoryPostProcessors(String absolutePath) {
        Map<String, Object> beans = new HashMap<String, Object>();
        beans.put("context", new DummyComponentContext());
        return Collections.singletonList(new ParentBeanFactoryPostProcessor(beans));
    }

    protected class DummyChannel implements DeliveryChannel {

        public MessageExchange accept() throws MessagingException {
            return null;
        }

        public MessageExchange accept(long timeout)
                throws MessagingException {
            return null;
        }

        public void close() throws MessagingException {
            //
        }

        public MessageExchangeFactory createExchangeFactory() {
            return null;
        }

        public MessageExchangeFactory createExchangeFactory(
                QName interfaceName) {
            return null;
        }

        public MessageExchangeFactory createExchangeFactory(
                ServiceEndpoint endpoint) {
            return null;
        }

        public MessageExchangeFactory createExchangeFactoryForService(
                QName serviceName) {
            return null;
        }

        public void send(MessageExchange exchange)
                throws MessagingException {
            //
        }

        public boolean sendSync(MessageExchange exchange)
                throws MessagingException {
            return false;
        }

        public boolean sendSync(MessageExchange exchange, long timeout)
                throws MessagingException {
            return false;
        }
    }
    
    public class DummyComponentContext implements ComponentContext {
        DummyChannel channel = new DummyChannel();        

        public ServiceEndpoint activateEndpoint(QName serviceName, String endpointName) throws JBIException {
            return null;
        }

        public void deactivateEndpoint(ServiceEndpoint endpoint) throws JBIException {
        }

        public void deregisterExternalEndpoint(ServiceEndpoint externalEndpoint) throws JBIException {

        }

        public String getComponentName() {
            return null;
        }

        public DeliveryChannel getDeliveryChannel() throws MessagingException {
            return channel;
        }

        public ServiceEndpoint getEndpoint(QName service, String name) {
            return null;
        }

        public Document getEndpointDescriptor(ServiceEndpoint endpoint) throws JBIException {
            return null;
        }

        public ServiceEndpoint[] getEndpoints(QName interfaceName) {
            return null;
        }

        public ServiceEndpoint[] getEndpointsForService(QName serviceName) {
            return null;
        }

        public ServiceEndpoint[] getExternalEndpoints(QName interfaceName) {
            return null;
        }

        public ServiceEndpoint[] getExternalEndpointsForService(QName serviceName) {
            return null;
        }

        public String getInstallRoot() {
            return null;
        }

        public Logger getLogger(String suffix, String resourceBundleName) throws MissingResourceException, 
                                                                                 JBIException {
            return null;
        }

        public MBeanNames getMBeanNames() {
            return null;
        }

        public MBeanServer getMBeanServer() {
            return null;
        }

        public InitialContext getNamingContext() {
            return null;
        }

        public Object getTransactionManager() {
            return null;
        }

        public String getWorkspaceRoot() {
            return null;
        }

        public void registerExternalEndpoint(ServiceEndpoint externalEndpoint) throws JBIException {
        }

        public ServiceEndpoint resolveEndpointReference(DocumentFragment epr) {
            return null;
        }

    }

}
