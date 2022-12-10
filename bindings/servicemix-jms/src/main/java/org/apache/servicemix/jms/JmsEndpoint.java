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

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.Iterator;
import java.util.Properties;

import javax.jbi.servicedesc.ServiceEndpoint;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.resource.spi.ActivationSpec;
import javax.resource.spi.BootstrapContext;
import javax.resource.spi.ResourceAdapter;
import javax.wsdl.Definition;
import javax.wsdl.Port;
import javax.wsdl.Service;

import org.apache.servicemix.common.ExternalEndpoint;
import org.apache.servicemix.common.security.AuthenticationService;
import org.apache.servicemix.common.security.KeystoreManager;
import org.apache.servicemix.soap.SoapEndpoint;
import org.apache.servicemix.soap.SoapExchangeProcessor;
import org.apache.servicemix.store.Store;
import org.apache.servicemix.store.StoreFactory;

/**
 ** non-Spring JMS endpoint
 * This JMS endpoint can be either a consumer or a provider
 * and is optimized to work with SOAP messages. 
 *
 * @author gnodet
 * @version $Revision$
 * @org.apache.xbean.XBean element="endpoint"
 * 
 */
public class JmsEndpoint extends SoapEndpoint implements JmsEndpointType {
    
    //
    // Jms informations
    //
    protected String initialContextFactory;
    protected String jndiProviderURL;
    protected String destinationStyle;
    protected String jndiConnectionFactoryName;
    protected String jndiDestinationName;
    protected String jmsProviderDestinationName;
    protected String jndiReplyToName;
    protected String jmsProviderReplyToName;

    protected boolean useMsgIdInResponse;
    //
    // Spring configuration
    //
    protected ConnectionFactory connectionFactory;
    protected Destination destination;
    protected String processorName;
    protected JmsMarshaler marshaler;
    //
    // JCA config
    //
    protected ResourceAdapter resourceAdapter;
    protected ActivationSpec activationSpec;
    protected BootstrapContext bootstrapContext;
    protected boolean synchronous;
    protected boolean rollbackOnError;
    
    // Other configuration flags
    protected boolean needJavaIdentifiers;

    /**
     * The store to keep pending exchanges
     */
    protected Store store;
    protected StoreFactory storeFactory;
    
    public JmsEndpoint() {
        super();
        marshaler = new DefaultJmsMarshaler(this);
    }

    public JmsEndpoint(boolean dynamic) {
        super(dynamic);
        this.marshaler = new DefaultJmsMarshaler(this);
    }

    /**
     * The BootstrapContext to use for a JCA consumer endpoint.
     * 
     * @return the bootstrapContext
     */
    public BootstrapContext getBootstrapContext() {
        return bootstrapContext;
    }

    /**
     * @param bootstrapContext the bootstrapContext to set
     */
    public void setBootstrapContext(BootstrapContext bootstrapContext) {
        this.bootstrapContext = bootstrapContext;
    }

    /**
     * Indicates if a JCA consumer endpoint sends the JBI exchange
     * synchronously or asynchronously.
     * This changes the transaction boundary. 
     * 
     * @return the synchronous
     */
    public boolean isSynchronous() {
        return synchronous;
    }

    /**
     * @param synchronous <code>true</code> means the exchange is sent synchronously
     */
    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }

    /**
    * Indicates if the JBI exchange is rolled back if an error is encountered.
     * @return the rollbackOnError
     */
    public boolean isRollbackOnError() {
        return rollbackOnError;
    }

    /**
     * @param rollbackOnError the rollbackOnError to set
     */
    public void setRollbackOnError(boolean rollbackOnError) {
        this.rollbackOnError = rollbackOnError;
    }

    /**
     * The ActivationSpec to use on a JCA consumer endpoint.
     * 
     * @return the activationSpec
     */
    public ActivationSpec getActivationSpec() {
        return activationSpec;
    }

    /**
     * @param activationSpec the activationSpec to set
     */
    public void setActivationSpec(ActivationSpec activationSpec) {
        this.activationSpec = activationSpec;
    }

    /**
     * The ResourceAdapter to use on a JCA consumer endpoint.
     * 
     * @return the resourceAdapter
     */
    public ResourceAdapter getResourceAdapter() {
        return resourceAdapter;
    }

    /**
     * @param resourceAdapter the resourceAdapter to set
     */
    public void setResourceAdapter(ResourceAdapter resourceAdapter) {
        this.resourceAdapter = resourceAdapter;
    }

    /**
     * The class name of the JNDI InitialContextFactory to use.
     * 
     * @return Returns the initialContextFactory.
     */
    public String getInitialContextFactory() {
        return initialContextFactory;
    }

    /**
     * @param initialContextFactory The initialContextFactory to set.
     */
    public void setInitialContextFactory(String initialContextFactory) {
        this.initialContextFactory = initialContextFactory;
    }

    /**
     * The name of the destination created by a call to 
     * <code>Session.createQueue</code> or <code>Session.createTopic</code>.
     * This property is used when <code>destination</code> and 
     * <code>jndiDestinationName</code> are
     * both <code>null</code>.
     * 
     * @return Returns the jmsProviderDestinationName.
     */
    public String getJmsProviderDestinationName() {
        return jmsProviderDestinationName;
    }

    /**
     * @param jmsProviderDestinationName The jmsProviderDestinationName to set.
     */
    public void setJmsProviderDestinationName(String jmsProviderDestinationName) {
        this.jmsProviderDestinationName = jmsProviderDestinationName;
    }

    /**
     * The name of the JMS Reply-to destination to lookup in JNDI.
     * If this property is not set a temporary replyTo queue is used.
     *
     * @return Returns the jndiReplyToName.
     */
    public String getJndiReplyToName() {
        return jndiReplyToName;
    }

    /**
     * @param jndiReplyToName the JNDI name of the replyTo queue
     */
    public void setJndiReplyToName(String jndiReplyToName) {
        this.jndiReplyToName = jndiReplyToName;
    }
    /**
     * The name of the reply destination created by a call to 
     * <code>Session.createQueue</code> or <code>Session.createTopic</code>.
     * This property is used when <code>jndiReplyToName</code> is
     * <code>null</code>.  A temporary queue will be used
     * if a replyTo is not provided.
     * 
     * @return Returns the jmsProviderReplyToName.
     */
    public String getJmsProviderReplyToName() {
        return jmsProviderReplyToName;
    }

    /**
     * @param jmsProviderReplyToName the name of the replyTo destination
     */
    public void setJmsProviderReplyToName(String jmsProviderReplyToName) {
        this.jmsProviderReplyToName = jmsProviderReplyToName;
    }

    /**
     * The name of the JMS ConnectionFactory to lookup in JNDI.
     * Used if <code>connectionFactory</code> is <code>null</code>
     * 
     * @return Returns the jndiConnectionFactoryName.
     */
    public String getJndiConnectionFactoryName() {
        return jndiConnectionFactoryName;
    }

    /**
     * @param jndiConnectionFactoryName the JNDI name of the connection factory
     */
    public void setJndiConnectionFactoryName(String jndiConnectionFactoryName) {
        this.jndiConnectionFactoryName = jndiConnectionFactoryName;
    }

    /**
     * The name of the JMS Destination to lookup in JNDI.
     * Used if <code>destination</code> is <code>null</code>.
     * 
     * @return Returns the destination's JNDI name.
     */
    public String getJndiDestinationName() {
        return jndiDestinationName;
    }

    /**
     * @param jndiDestinationName the JNDI name of the JMS destination
     */
    public void setJndiDestinationName(String jndiDestinationName) {
        this.jndiDestinationName = jndiDestinationName;
    }

    /**
     * The provider URL used to create the JNDI context. 
     * 
     * @return Returns the jndiProviderURL.
     */
    public String getJndiProviderURL() {
        return jndiProviderURL;
    }

    /**
     * @param jndiProviderURL The JNDI provider URL
     */
    public void setJndiProviderURL(String jndiProviderURL) {
        this.jndiProviderURL = jndiProviderURL;
    }

    /**
     * Specifies the destination type used with the jmsProviderDestinationName.
     * Can be <code>queue</code> or <code>topic</code>.
     * 
     * @return Returns the destinationStyle.
     */
    public String getDestinationStyle() {
        return destinationStyle;
    }

    /**
     * @param destinationStyle <code>queue</code> or <code>topic</code>
     */
    public void setDestinationStyle(String destinationStyle) {
        this.destinationStyle = destinationStyle;
    }

    /**
     * A configured ConnectionFactory to use on this endpoint.
     * 
     * @return Returns the connectionFactory.
     */
    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    /**
     * @param connectionFactory a configured JMS ConnectionFactory
     */
    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }
    
    /**
     * A configured Destination to use on this endpoint.
     * 
     * @return Returns the destination.
     */
    public Destination getDestination() {
        return destination;
    }

    /**
     * @param destination a configured JMS destination
     */
    public void setDestination(Destination destination) {
        this.destination = destination;
    }
    
    /**
    * Indicates if the JMS properties used by the endpoint need to be spec compliant.
    *
     * @return if jms properties should be spec compliant
     */
    public boolean isNeedJavaIdentifiers() {
        return needJavaIdentifiers;
    }
    
    /**
     * @param needJavaIdentifiers if jms properties should be spec compliant
     */
    public void setNeedJavaIdentifiers(boolean needJavaIdentifiers) {
        this.needJavaIdentifiers = needJavaIdentifiers;
    }

    /**
     * Specifies the role of this endpoint. Endpoints can be 
     * <code>consumer</code> or <code>provider</code>.
     * 
     * @org.apache.xbean.Property alias="role"
     * @param role
     * @param role the role of the endpoint
     */
    public void setRoleAsString(String role) {
        super.setRoleAsString(role);
    }

    /**
     * @return Returns the store.
     */
    public Store getStore() {
        return store;
    }
    /**
    * Specifies a persistent data store to hold pending exchanges for the 
    * endpoint.
    *
     * @param store The store to set.
     */
    public void setStore(Store store) {
        this.store = store;
    }
    /**
    
     * @return Returns the storeFactory.
     */
    public StoreFactory getStoreFactory() {
        return storeFactory;
    }
    /**
    * Specifies the factory used to create presistent data stores for this endpoint.
    *
     * @param storeFactory the factory used to create persistent stores
     */
    public void setStoreFactory(StoreFactory storeFactory) {
        this.storeFactory = storeFactory;
    }

    protected SoapExchangeProcessor createProviderProcessor() {
        return createProcessor("provider");
    }

    protected SoapExchangeProcessor createConsumerProcessor() {
        return createProcessor("consumer");
    }
    
    protected SoapExchangeProcessor createProcessor(String type) {
        try {
            String procName = processorName;
            if (processorName == null) {
                procName = getConfiguration().getProcessorName();
            }
            String uri = "META-INF/services/org/apache/servicemix/jms/" + procName;
            InputStream in = loadResource(uri);
            Properties props = new Properties();
            props.load(in);
            String className = props.getProperty(type);
            Class cl = loadClass(className);
            Constructor cns = cl.getConstructor(new Class[] {getClass()});
            return (SoapExchangeProcessor) cns.newInstance(new Object[] {this});
        } catch (Exception e) {
            throw new RuntimeException("Could not create processor of type " + type + " and name " + processorName, e);
        }
    }

    /**
     * Attempts to load the class on the current thread context class loader or
     * the class loader which loaded us
     */
    protected Class loadClass(String name) throws ClassNotFoundException {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            try {
                return contextClassLoader.loadClass(name);
            } catch (ClassNotFoundException e) {
                //thread context class loader didn't work out
            }
        }
        return getClass().getClassLoader().loadClass(name);
    }

    /**
     * Loads the resource from the given URI
     */
    protected InputStream loadResource(String uri) {
        // lets try the thread context class loader first
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(uri);
        if (in == null) {
            in = getClass().getClassLoader().getResourceAsStream(uri);
            if (in == null) {
                logger.debug("Could not find resource: {}", uri);
            }
        }
        return in;
    }

    protected ServiceEndpoint createExternalEndpoint() {
        return new ExternalEndpoint(getServiceUnit().getComponent().getEPRElementName(),
                                    getLocationURI(),
                                    getService(),
                                    getEndpoint(),
                                    getInterfaceName());
    }
    
    protected String getLocationURI() {
        // Need to return a real URI
        return getService() + "#" + getEndpoint();
    }

    protected void overrideDefinition(Definition def) {
        Service svc = null;
        Port port = null;
        if (targetService != null && targetEndpoint != null) {
            svc = def.getService(targetService);
            port = (svc != null) ? svc.getPort(targetEndpoint) : null;
        } else if (targetService != null) {
            svc = def.getService(targetService);
            if (svc != null) {
                Iterator it = svc.getPorts().values().iterator();
                port = (it.hasNext()) ? (Port) it.next() : null;
            }
        } else if (targetInterfaceName != null) {
            Iterator it = def.getServices().values().iterator();
            svc = it .hasNext() ? (Service) it.next() : null;
            if (svc != null) {
                it = svc.getPorts().values().iterator();
                port = (it.hasNext()) ? (Port) it.next() : null;
            }
        } else {
            svc = def.getService(service);
            port = (svc != null) ? svc.getPort(endpoint) : null;
        }
        if (port != null) {
            port.getExtensibilityElements().clear();
            /*
            if (isSoap()) {
                SOAPAddress address = new SOAPAddressImpl();
                address.setLocationURI(getLocationURI());
                port.addExtensibilityElement(address);
                def.addNamespace("soap", "http://schemas.xmlsoap.org/wsdl/soap/");
            } else {
                HTTPAddress address = new HTTPAddressImpl();
                address.setLocationURI(getLocationURI());
                port.addExtensibilityElement(address);
                def.addNamespace("http", "http://schemas.xmlsoap.org/wsdl/http/");
            }
            */
            // TODO: add binding information
            svc.getPorts().clear();
            svc.addPort(port);
            definition = def;
        }
    }

    public String toString() {
        return "JMSEndpoint[service: " + service + ", " 
                + "endpoint: " + endpoint + ", " 
                + "address: " + jndiDestinationName + "(" + destinationStyle + "), " 
                + "soap: " + soap + "]";
    }

    /**
     * Specifies the processor family to use for this endpoint.
     * Can be:
     * <ul>
     *   <li><code>multiplexing</code> (default)</li>
     *   <li><code>standard</code></li>
     *   <li><code>jca</code></li>
     * </ul>
     * 
     * @return Returns the processorName.
     */
    public String getProcessorName() {
        return processorName;
    }

    /**
     * @param processorName The processorName to set.
     */
    public void setProcessorName(String processorName) {
        this.processorName = processorName;
    }
    
    public JmsConfiguration getConfiguration() {
        JmsComponent component = (JmsComponent) getServiceUnit().getComponent();
        return component.getConfiguration();
    }

    public AuthenticationService getAuthenticationService() {
        JmsComponent comp = (JmsComponent) getServiceUnit().getComponent();
        return AuthenticationService.Proxy.create(comp.getAuthenticationService());
    }

    public KeystoreManager getKeystoreManager() {
        JmsComponent comp = (JmsComponent) getServiceUnit().getComponent();
        return KeystoreManager.Proxy.create(comp.getKeystoreManager());
    }

    /**
     * Indicates whether  the message id of the request message
     * should be used as the correlation id in the response or the correlation id of the request.
     * @return
     */
    public boolean isUseMsgIdInResponse() {
        return useMsgIdInResponse;
    }

    public void setUseMsgIdInResponse(boolean useMsgIdInResponse) {
        this.useMsgIdInResponse = useMsgIdInResponse;
    }

    public JmsMarshaler getMarshaler() {
        return marshaler;
    }

    /**
    * Specifies the class implementing the logic for marshaling and 
    * unmarshaling messages between the JMS destination and the endpoint.
    * Defaults to <code>DefaultJmsMarshaler</code>.
    *
    * @param marshaler the marshaling class
    */
    public void setMarshaler(JmsMarshaler marshaler) {
        this.marshaler = marshaler;
    }  

}
