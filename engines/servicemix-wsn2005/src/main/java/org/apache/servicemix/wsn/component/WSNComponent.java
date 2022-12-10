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
package org.apache.servicemix.wsn.component;

import com.ibm.wsdl.Constants;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.servicemix.common.*;
import org.apache.servicemix.common.tools.wsdl.WSDLFlattener;
import org.apache.servicemix.wsn.EndpointManager;
import org.apache.servicemix.wsn.EndpointRegistrationException;
import org.apache.servicemix.wsn.jbi.JbiNotificationBroker;
import org.apache.servicemix.wsn.jms.JmsCreatePullPoint;
import org.springframework.core.io.Resource;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.jbi.messaging.MessageExchange;
import javax.jbi.servicedesc.ServiceEndpoint;
import javax.jms.ConnectionFactory;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.wsdl.Definition;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLLocator;
import javax.wsdl.xml.WSDLReader;
import javax.xml.namespace.QName;
import java.net.URL;
import java.util.*;

public class WSNComponent extends DefaultComponent {

    private static final String EMBEDDED_AMQ_BROKER_NAME = "smx_embedded";

    private WSDLFlattener flattener;

    private Map<QName, Document> descriptions;

    private JbiNotificationBroker notificationBroker;

    private JmsCreatePullPoint createPullPoint;

    private WSNConfiguration configuration;

    private ConnectionFactory connectionFactory;

    private Resource[] resources;

    private Object[] requests;

    private List<Endpoint> endpoints;

    private WSNDeployer deployer;

    private BrokerService brokerService;
    
    private String brokerUsername;
    
    private String brokerPassword;


    public WSNComponent() {
        configuration = new WSNConfiguration();
        serviceUnit = new DefaultServiceUnit(this);
    }

    public JbiNotificationBroker getNotificationBroker() {
        return notificationBroker;
    }

    public JmsCreatePullPoint getCreatePullPoint() {
        return createPullPoint;
    }

    protected Object getExtensionMBean() throws Exception {
        return configuration;
    }

    @Override
    public BaseServiceUnitManager createServiceUnitManager() {
        deployer = new WSNDeployer(this);
        return new BaseServiceUnitManager(this, new Deployer[] {deployer });
    }

    public ConnectionFactory getConnectionFactory() {
        return this.connectionFactory;
    }

    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    protected List getConfiguredEndpoints() {
        return endpoints;
    }

    protected Class[] getEndpointClasses() {
        return new Class[] {
            WSNEndpoint.class,
            WSNDeployableEndpoint.class,
        };
    }

    /**
     * @return Returns the endpoints.
     */
    public Resource[] getResources() {
        return resources;
    }

    /**
     * @param resources The resources to set.
     */
    public void setResources(Resource[] resources) {
        this.resources = resources;
    }

    /**
     * @return Returns the requests.
     */
    public Object[] getRequests() {
        return requests;
    }

    /**
     * @param requests The requests to set.
     */
    public void setRequests(Object[] requests) {
        this.requests = requests;
    }

    @Override
    protected void doInit() throws Exception {
        configuration.setRootDir(context.getWorkspaceRoot());
        configuration.load();
        // Notification Broker
        notificationBroker = new JbiNotificationBroker(configuration.getBrokerName());
        notificationBroker.setManager(new WSNEndpointManager());
        if (connectionFactory == null) {
            try {
                connectionFactory = lookupConnectionFactory();
            } catch (Throwable t) {
                // create some embedded cf
                this.brokerService = new BrokerService();
                this.brokerService.setUseJmx(false);
                this.brokerService.setPersistent(false);
                this.brokerService.setBrokerName(EMBEDDED_AMQ_BROKER_NAME);
                this.brokerService.start();
                this.connectionFactory = new ActiveMQConnectionFactory("vm://" + EMBEDDED_AMQ_BROKER_NAME);
            }
        }
        if (connectionFactory != null) {
            notificationBroker.setConnectionFactory(connectionFactory);
            notificationBroker.init(brokerUsername, brokerPassword);
        }
        // Create PullPoint
        createPullPoint = new JmsCreatePullPoint(configuration.getBrokerName());
        createPullPoint.setManager(new WSNEndpointManager());
        createPullPoint.setConnectionFactory(connectionFactory);
        createPullPoint.init(brokerUsername, brokerPassword);
        // Create endpoints
        endpoints = new ArrayList<Endpoint>();
        if (resources != null) {
            for (int i = 0; i < resources.length; i++) {
                Endpoint ep = deployer.createEndpoint(resources[i].getURL());
                endpoints.add(ep);
            }
        }
        if (requests != null) {
            for (int i = 0; i < requests.length; i++) {
                Endpoint ep = deployer.createEndpoint(requests[i]);
                endpoints.add(ep);
            }
        }
        super.doInit();
    }

    @Override
    protected void doShutDown() throws Exception {
        notificationBroker.destroy();
        createPullPoint.destroy();
        if (this.brokerService != null) {
            this.brokerService.stop();
            this.brokerService = null;
        }
        super.doShutDown();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.servicemix.common.BaseComponent#getServiceDescription(javax.jbi.servicedesc.ServiceEndpoint)
     */
    @Override
    public Document getServiceDescription(ServiceEndpoint endpoint) {
        logger.debug("Querying service description for {}", endpoint);
        String key = EndpointSupport.getKey(endpoint);
        Endpoint ep = this.registry.getEndpoint(key);
        if (ep != null) {
            QName interfaceName = ep.getInterfaceName();
            if (interfaceName == null) {
                logger.debug("Could not retrieve description for endpoint {} (no interface defined)", key);
                return null;
            }
            return getDescription(interfaceName);
        } else {
            logger.debug("No endpoint found for {}", key);
            return null;
        }
    }

    private synchronized Document getDescription(QName interfaceName) {
        try {
            if (descriptions == null) {
                descriptions = new HashMap<QName, Document>();
            }
            Document doc = descriptions.get(interfaceName);
            if (doc == null) {
                if (flattener == null) {
                    final URL resource = getClass().getClassLoader().getResource("org/apache/servicemix/wsn/wsn.wsdl");
                    WSDLReader reader = WSDLFactory.newInstance().newWSDLReader();
                    reader.setFeature(Constants.FEATURE_VERBOSE, false);
                    // Do not let wsdl4j read imports, as it uses the URL getContent() which fails because wsdl
                    // is not a known extension in OSGi
                    Definition definition = reader.readWSDL(new WSDLLocator() {
                        private String last = null;
                        public InputSource getBaseInputSource() {
                            return new InputSource(resource.toString());
                        }
                        public InputSource getImportInputSource(String parentLocation, String importLocation) {
                            int idx = parentLocation.lastIndexOf('/');
                            last = parentLocation.substring(0, idx + 1) + importLocation;
                            return new InputSource(last);
                        }
                        public String getBaseURI() {
                            return resource.toString();
                        }
                        public String getLatestImportURI() {
                            return last;
                        }
                        public void close() {
                        }
                    });
                    flattener = new WSDLFlattener(definition);
                }
                Definition flatDef = flattener.getDefinition(interfaceName);
                doc = WSDLFactory.newInstance().newWSDLWriter().getDocument(flatDef);
                descriptions.put(interfaceName, doc);
            }
            return doc;
        } catch (Exception e) {
            logger.debug("Error retrieving endpoint description", e);
            return null;
        }
    }

    protected ConnectionFactory lookupConnectionFactory() throws NamingException {
        Properties props = new Properties();
        if (configuration.getInitialContextFactory() != null && configuration.getJndiProviderURL() != null) {
            props.put(Context.INITIAL_CONTEXT_FACTORY, configuration.getInitialContextFactory());
            props.put(Context.PROVIDER_URL, configuration.getJndiProviderURL());
        }
        InitialContext ctx = new InitialContext(props);
        return (ConnectionFactory) ctx.lookup(configuration.getJndiConnectionFactoryName());
    }

    public String getBrokerUsername() {
        return brokerUsername;
    }

    public void setBrokerUsername(String brokerUsername) {
        this.brokerUsername = brokerUsername;
    }

    public String getBrokerPassword() {
        return brokerPassword;
    }

    public void setBrokerPassword(String brokerPassword) {
        this.brokerPassword = brokerPassword;
    }

    public class WSNEndpointManager implements EndpointManager {

        public Object register(String address, Object service) throws EndpointRegistrationException {
            try {
                WSNEndpoint endpoint = new WSNEndpoint(address, service);
                WSNComponent.this.addEndpoint(endpoint);
                return endpoint;
            } catch (Exception e) {
                throw new EndpointRegistrationException("Unable to activate endpoint", e);
            }
        }

        public void unregister(final Object endpoint) throws EndpointRegistrationException {
            WSNComponent.this.getExecutor(MessageExchange.Role.CONSUMER).execute(new Runnable() {
                public void run() {
                    try {
                        Endpoint ep = (Endpoint) endpoint;
                        // let's wait for pending exchanges to be done before actually removing the endpoint
                        WSNComponent.this.prepareShutdown(ep);
                        WSNComponent.this.removeEndpoint(ep);
                    } catch (Exception e) {
                        logger.error("Unable to deactivate endpoint", e);
                    }
                }
            });
        }

    }

}
