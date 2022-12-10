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

import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.jbi.messaging.MessageExchange;
import javax.jbi.servicedesc.ServiceEndpoint;

import org.apache.servicemix.common.BaseServiceUnitManager;
import org.apache.servicemix.common.DefaultComponent;
import org.apache.servicemix.common.DefaultServiceUnit;
import org.apache.servicemix.common.Deployer;
import org.apache.servicemix.common.Endpoint;
import org.apache.servicemix.common.util.IntrospectionSupport;
import org.apache.servicemix.common.util.URISupport;
import org.apache.servicemix.common.xbean.BaseXBeanDeployer;
import org.apache.servicemix.jms.endpoints.JmsConsumerEndpoint;
import org.apache.servicemix.jms.endpoints.JmsJcaConsumerEndpoint;
import org.apache.servicemix.jms.endpoints.JmsProviderEndpoint;

/**
 * JMS JBI binding component
 * 
 * @org.apache.xbean.XBean element="component"
 */
public class JmsComponent extends DefaultComponent {

    protected JmsConfiguration configuration = new JmsConfiguration();
    protected JmsEndpointType[] endpoints;
    
    protected List getConfiguredEndpoints() {
        return asList(endpoints);
    }

    protected Class[] getEndpointClasses() {
        return new Class[] {JmsEndpoint.class, 
                            JmsConsumerEndpoint.class, 
                            JmsProviderEndpoint.class,
                            JmsJcaConsumerEndpoint.class};
    }
    
    /**
     * @return Returns the configuration.
     * @org.apache.xbean.Flat
     */
    public JmsConfiguration getConfiguration() {
        return configuration;
    }
    
    /**
    * Specifies the configuration bean used to set up the JMS connections for the endpoints. 
    *
    * @param configuration the configuration object 
          */
    public void setConfiguration(JmsConfiguration configuration) {
        this.configuration = configuration;
    }

    public JmsEndpointType[] getEndpoints() {
        return endpoints;
    }

    /**
          * Specifies the list of endpoints hosted by the component.
          *
          * @param endpoints the list of endpoints
          */
    public void setEndpoints(JmsEndpointType[] endpoints) {
        this.endpoints = endpoints;
    }

    /**
     * @return the keystoreManager
     */
    public Object getKeystoreManager() {
        return configuration.getKeystoreManager();
    }

    /**
     * @param keystoreManager the keystoreManager to set
     */
    public void setKeystoreManager(Object keystoreManager) {
        this.configuration.setKeystoreManager(keystoreManager);
    }

    /**
     * @return the authenticationService
     */
    public Object getAuthenticationService() {
        return configuration.getAuthenticationService();
    }

    /**
     * @param authenticationService the authenticationService to set
     */
    public void setAuthenticationService(Object authenticationService) {
        this.configuration.setAuthenticationService(authenticationService);
    }

    /* (non-Javadoc)
     * @see org.servicemix.common.BaseComponentLifeCycle#getExtensionMBean()
     */
    protected Object getExtensionMBean() throws Exception {
        return configuration;
    }

    /* (non-Javadoc)
     * @see org.apache.servicemix.common.BaseComponent#createServiceUnitManager()
     */
    public BaseServiceUnitManager createServiceUnitManager() {
        Deployer[] deployers = new Deployer[] {new BaseXBeanDeployer(this, getEndpointClasses()), 
                                               new JmsWsdl1Deployer(this)};
        return new BaseServiceUnitManager(this, deployers);
    }

    /* (non-Javadoc)
     * @see org.apache.servicemix.common.DefaultComponent#doInit()
     */
    protected void doInit() throws Exception {
        super.doInit();
        configuration.setRootDir(context.getWorkspaceRoot());
        configuration.setComponentName(this.context.getComponentName());
        configuration.load();
        // Lookup keystoreManager and authenticationService
        if (configuration.getKeystoreManager() == null) {
            try {
                String name = configuration.getKeystoreManagerName();
                Object km =  context.getNamingContext().lookup(name);
                configuration.setKeystoreManager(km); 
            } catch (Exception e) {
                // ignore
            }
        }
        if (configuration.getAuthenticationService() == null) {
            try {
                String name = configuration.getAuthenticationServiceName();
                Object as =  context.getNamingContext().lookup(name);
                configuration.setAuthenticationService(as); 
            } catch (Exception e) {
                try {
                    Class cl = Class.forName("org.apache.servicemix.jbi.security.auth.impl.JAASAuthenticationService");
                    configuration.setAuthenticationService(cl.newInstance());
                } catch (Throwable t) {
                    logger.warn("Unable to retrieve or create the authentication service");
                }
            }
        }
    }
    
    protected Endpoint getResolvedEPR(ServiceEndpoint ep) throws Exception {
        // We receive an exchange for an EPR that has not been used yet.
        // Register a provider endpoint and restart processing.
        JmsEndpoint jmsEp = new JmsEndpoint(true);
        jmsEp.setServiceUnit(new DefaultServiceUnit(component));
        jmsEp.setService(ep.getServiceName());
        jmsEp.setEndpoint(ep.getEndpointName());
        jmsEp.setRole(MessageExchange.Role.PROVIDER);
        URI uri = new URI(ep.getEndpointName());
        Map map = URISupport.parseQuery(uri.getQuery());
        if (IntrospectionSupport.setProperties(jmsEp, map, "jms.")) {
            uri = URISupport.createRemainingURI(uri, map);
        }
        if (uri.getPath() != null) {
            String path = uri.getSchemeSpecificPart();
            while (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.startsWith(AbstractJmsProcessor.STYLE_QUEUE + "/")) {
                jmsEp.setDestinationStyle(AbstractJmsProcessor.STYLE_QUEUE);
                jmsEp.setJmsProviderDestinationName(path.substring(AbstractJmsProcessor.STYLE_QUEUE.length() + 1));
            } else if (path.startsWith(AbstractJmsProcessor.STYLE_TOPIC + "/")) {
                jmsEp.setDestinationStyle(AbstractJmsProcessor.STYLE_TOPIC);
                jmsEp.setJmsProviderDestinationName(path.substring(AbstractJmsProcessor.STYLE_TOPIC.length() + 1));
            }
        }
        return jmsEp;
    }

}
