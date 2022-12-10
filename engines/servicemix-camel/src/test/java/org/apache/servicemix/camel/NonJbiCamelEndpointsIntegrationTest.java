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

import java.io.File;
import java.net.URI;
import java.net.URL;

import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.MessagingException;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;

import junit.framework.TestCase;

import org.apache.servicemix.client.DefaultServiceMixClient;
import org.apache.servicemix.client.ServiceMixClient;
import org.apache.servicemix.jbi.container.JBIContainer;
import org.apache.servicemix.jbi.jaxp.StringSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Revision: 1.1 $
 */
public class NonJbiCamelEndpointsIntegrationTest extends TestCase {
    
    private final static Logger logger = LoggerFactory.getLogger(NonJbiCamelEndpointsIntegrationTest.class);

    protected String suName = "su1";

    protected JBIContainer container = new JBIContainer();

    private File tempRootDir;

    public void testComponentInstallation() throws Exception {
        String serviceUnitConfiguration = suName + "-src/camel-context.xml";

        CamelJbiComponent component = new CamelJbiComponent();
        container.activateComponent(component, "#ServiceMixComponent#");
        URL url = getClass().getResource(serviceUnitConfiguration);
        File path = new File(new URI(url.toString()));
        path = path.getParentFile();
        ServiceMixClient client = new DefaultServiceMixClient(container);

        try {
            for (int i = 0; i < 2; i++) {
                logger.info("Loop counter: {}", i);

                // Deploy and start su
                component.getServiceUnitManager().deploy(suName,
                        path.getAbsolutePath());
                component.getServiceUnitManager().init(suName,
                        path.getAbsolutePath());
                component.getServiceUnitManager().start(suName);

                // Send message
                MessageExchange exchange = createExchange(client);
                configureExchange(client, exchange);
                populateExchange(exchange);
                client.sendSync(exchange);
                checkResult(exchange);
                //assertNotNull(exchange.getMessage("out").getContent());
                if (exchange.getStatus() == ExchangeStatus.ACTIVE) {
                    client.done(exchange);
                }

                // Stop and undeploy
                component.getServiceUnitManager().stop(suName);
                component.getServiceUnitManager().shutDown(suName);
                component.getServiceUnitManager().undeploy(suName,
                        path.getAbsolutePath());

                // Send message
                exchange = createExchange(client);
                try {
                    configureExchange(client, exchange);
                    client.send(exchange);
                    fail("Should have failed to send to a no longer deployed component");
                } catch (Throwable e) {
                    logger.debug("Caught expected exception as the component is undeployed: {}", e, e);
                }
            }
        } catch (Exception e) {
            logger.error("Caught: {}", e, e);
            throw e;
        }
    }

    protected void checkResult(MessageExchange exchange) {
    }

    /*
     * @see TestCase#setUp()
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        container.setCreateMBeanServer(false);
        container.setMonitorInstallationDirectory(false);
        tempRootDir = File.createTempFile("servicemix", "rootDir");
        tempRootDir.delete();
        File tempTemp = new File(tempRootDir.getAbsolutePath() + "/temp");
        if (!tempTemp.mkdirs()) {
            fail("Unable to create temporary working root directory ["
                    + tempTemp.getAbsolutePath() + "]");
        }
        logger.info("Using temporary root directory [{}]", tempRootDir.getAbsolutePath());

        container.setEmbedded(true);
        container.setCreateJmxConnector(false);
        container.setFlowName("st");
        container.init();
        container.start();
    }

    /*
     * @see TestCase#tearDown()
     */
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        container.stop();
        container.shutDown();
        deleteDir(tempRootDir);
    }

    protected MessageExchange createExchange(ServiceMixClient client)
        throws MessagingException {

        return client.createInOutExchange();
    }

    protected void configureExchange(ServiceMixClient client,
            MessageExchange exchange) {
        ServiceEndpoint endpoint = client.getContext().getEndpoint(
                CamelContextEndpoint.SERVICE_NAME, "su1-controlbus");
        assertNotNull("Should have a Camel endpoint exposed in JBI!", endpoint);
        exchange.setEndpoint(endpoint);
    }

    protected void populateExchange(MessageExchange exchange) throws Exception {
        NormalizedMessage msg = exchange.getMessage("in");
        if (msg == null) {
            msg = exchange.createMessage();
            exchange.setMessage(msg, "in");
        }
        msg.setContent(new StringSource("<hello>world</hello>"));
    }

    public static boolean deleteDir(File dir) {
        logger.info("Deleting directory : {}", dir.getAbsolutePath());
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
        // The directory is now empty so delete it
        return dir.delete();
    }
}
