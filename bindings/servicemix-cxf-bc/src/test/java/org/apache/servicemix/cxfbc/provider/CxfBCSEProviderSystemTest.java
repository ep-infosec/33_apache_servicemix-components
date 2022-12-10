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
package org.apache.servicemix.cxfbc.provider;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.jbi.messaging.InOut;
import javax.xml.namespace.QName;

import org.apache.cxf.common.logging.LogUtils;

import org.apache.cxf.testutil.common.ServerLauncher;

import org.apache.servicemix.JbiConstants;
import org.apache.servicemix.client.DefaultServiceMixClient;
import org.apache.servicemix.cxfbc.CxfBcSpringTestSupport;
import org.apache.servicemix.cxfbc.EmbededJMSBrokerLauncher;
import org.apache.servicemix.cxfbc.MyJMSServer;
import org.apache.servicemix.jbi.container.SpringJBIContainer;
import org.apache.servicemix.jbi.jaxp.SourceTransformer;
import org.apache.servicemix.jbi.jaxp.StringSource;
import org.springframework.context.support.AbstractXmlApplicationContext;

public class CxfBCSEProviderSystemTest extends CxfBcSpringTestSupport {
    
    private static final Logger LOG = LogUtils.getL7dLogger(CxfBCSEProviderSystemTest.class);
    private static boolean serversStarted;
    private DefaultServiceMixClient client;
    private InOut io;    

    private ServerLauncher sl;
    private ServerLauncher embeddedLauncher;
    private ServerLauncher jmsLauncher;
    
    
    public void startServers() throws Exception {
        if (serversStarted) {
            return;
        }
        Map<String, String> props = new HashMap<String, String>();                
        
        

        if (System.getProperty("activemq.store.dir") != null) {
            props.put("activemq.store.dir", System.getProperty("activemq.store.dir"));
        }
        props.put("java.util.logging.config.file", 
                  System.getProperty("java.util.logging.config.file"));
        
        assertTrue("server did not launch correctly", 
                   launchServer(EmbededJMSBrokerLauncher.class, props, false));
        embeddedLauncher =  sl;
        props = new HashMap<String, String>();                
        if (System.getProperty("javax.xml.transform.TransformerFactory") != null) {
            props.put("javax.xml.transform.TransformerFactory", System.getProperty("javax.xml.transform.TransformerFactory"));
        }
        if (System.getProperty("javax.xml.stream.XMLInputFactory") != null) {
            props.put("javax.xml.stream.XMLInputFactory", System.getProperty("javax.xml.stream.XMLInputFactory"));
        }
        if (System.getProperty("javax.xml.stream.XMLOutputFactory") != null) {
            props.put("javax.xml.stream.XMLOutputFactory", System.getProperty("javax.xml.stream.XMLOutputFactory"));
        }
        assertTrue("server did not launch correctly", 
                launchServer(MyJMSServer.class, props, false));
        jmsLauncher = sl;
        
        assertTrue("server did not launch correctly", 
                launchServer(MyServer.class, props, false));
        
        serversStarted = true;
    }
    
    protected void setUp() throws Exception {
        startServers();
        //super.setUp();
        LOG.info("setUp is invoked");            
    }       
    
    public boolean launchServer(Class<?> clz, Map<String, String> p, boolean inProcess) {
        boolean ok = false;
        try { 
            sl = new ServerLauncher(clz.getName(), p, null, inProcess);
            ok = sl.launchServer();
            assertTrue("server failed to launch", ok);
            
        } catch (IOException ex) {
            ex.printStackTrace();
            fail("failed to launch server " + clz);
        }
        
        return ok;
    }    
               
    public void setUpJBI(String beanFile) throws Exception {
        if (context != null) {
            context.refresh();
        }
        transformer = new SourceTransformer();
        if (beanFile == null) {
            context = createBeanFactory();
        } else {
            context = createBeanFactory(beanFile);
        }
                
        jbi = (SpringJBIContainer) context.getBean("jbi");
        assertNotNull("JBI Container not found in spring!", jbi);
        
    }
    
    public void tearDown() throws Exception {
        if (context != null) {
            context.destroy();
            context = null;
        }
        if (jbi != null) {
            jbi.shutDown();
            jbi.destroy();
            jbi = null;
        }
        
        try {
            embeddedLauncher.stopServer();         
        } catch (IOException ex) {
            ex.printStackTrace();
            fail("failed to stop server " + embeddedLauncher.getClass());
        }
        try {
            jmsLauncher.stopServer();         
        } catch (IOException ex) {
            ex.printStackTrace();
            fail("failed to stop server " + jmsLauncher.getClass());
        } 
        
        try {
            sl.stopServer();         
        } catch (IOException ex) {
            ex.printStackTrace();
            fail("failed to stop server " + sl.getClass());
        }
        serversStarted = false;
    }

    public void testGreetMeProviderWithJBIWrapperWithoutOperationName() throws Exception {
        setUpJBI("org/apache/servicemix/cxfbc/provider/xbean_provider_with_jbi_wrapper.xml");
        greetMeProviderWithoutOperationNameTestBase();
    }    

    public void testGreetMeProviderWithOutJBIWrapper() throws Exception {
        setUpJBI("org/apache/servicemix/cxfbc/provider/xbean_provider_without_jbi_wrapper.xml");
        greetMeProviderTestBase(false);
    }
    
    public void testGreetMeProviderWithDynamicUri() throws Exception {
        setUpJBI("org/apache/servicemix/cxfbc/provider/xbean_provider_without_jbi_wrapper.xml");
        greetMeProviderTestBase(true);
    }
    
    public void testGreetMeProviderWithJmSTransportSync() throws Exception {
        setUpJBI("org/apache/servicemix/cxfbc/provider/xbean_provider_without_jbi_wrapper.xml");
        greetMeProviderJmsTestBase(true, "Edell");
    }

    public void testGreetMeProviderWithJmSTransportAsync() throws Exception {
        setUpJBI("org/apache/servicemix/cxfbc/provider/xbean_provider_without_jbi_wrapper.xml");
        greetMeProviderJmsTestBase(false, "Edell");
    }

    public void testGreetMeProviderWithJmSTransportSyncTimeOut() throws Exception {
        setUpJBI("org/apache/servicemix/cxfbc/provider/xbean_provider_without_jbi_wrapper.xml");
        greetMeProviderJmsTestBase(true, "ffang");
    }

    public void testGreetMeProviderWithJmSTransportAsyncTimeOut() throws Exception {
        setUpJBI("org/apache/servicemix/cxfbc/provider/xbean_provider_without_jbi_wrapper.xml");
        greetMeProviderJmsTestBase(false, "ffang");
    }
        
    public void testGreetMeProviderWithBusLoggerFeature() throws Exception {
        setUpJBI("org/apache/servicemix/cxfbc/provider/xbean_provider_with_bus_logger_feature.xml");
        greetMeProviderTestBase(true);
    }
    
    private void greetMeProviderTestBase(boolean useDynamicUri) throws Exception {

        client = new DefaultServiceMixClient(jbi);
        io = client.createInOutExchange();
        io.setService(new QName("http://apache.org/hello_world_soap_http_provider", "SOAPService"));
        io.setInterfaceName(new QName("http://apache.org/hello_world_soap_http_provider", "Greeter"));
        //send message to proxy
        io.getInMessage().setContent(new StringSource(
              "<greetMe xmlns='http://apache.org/hello_world_soap_http_provider/types'><requestType>"
              + "Edell"
              + "</requestType></greetMe>"));
        if (useDynamicUri) {
            io.getInMessage().setProperty(JbiConstants.HTTP_DESTINATION_URI, "http://localhost:9002/dynamicuritest");
        }
        client.sendSync(io);
        String txt = new SourceTransformer().contentToString(io.getOutMessage());
        client.done(io);
        assertTrue(txt.indexOf("Hello Edell") >= 0);
        
        if (useDynamicUri) {
            //the second round use dummy uri to verify the dynamic uri could be overriden for each message
            io = client.createInOutExchange();
            io.setService(new QName("http://apache.org/hello_world_soap_http_provider", "SOAPService"));
            io.setInterfaceName(new QName("http://apache.org/hello_world_soap_http_provider", "Greeter"));
            io.getInMessage()
                    .setContent(
                            new StringSource(
                                    "<greetMe xmlns='http://apache.org/hello_world_soap_http_provider/types'><requestType>"
                                            + "ffang"
                                            + "</requestType></greetMe>"));
            if (useDynamicUri) {
                io.getInMessage().setProperty(
                        JbiConstants.HTTP_DESTINATION_URI,
                        "http://localhost:9003/dynamicuritest");
            }
            client.sendSync(io);
            // the out message should be null as the server not exist at all
            assertNull(io.getOutMessage());
        }
        
    }

    private void greetMeProviderWithoutOperationNameTestBase() throws Exception {
        client = new DefaultServiceMixClient(jbi);
        io = client.createInOutExchange();
        io.setService(new QName("http://apache.org/hello_world_soap_http_provider", "SOAPService"));
        io.setInterfaceName(new QName("http://apache.org/hello_world_soap_http_provider", "Greeter"));
        //send message to proxy
        io.getInMessage().setContent(new StringSource(
                "<message xmlns='http://java.sun.com/xml/ns/jbi/wsdl-11-wrapper'>"
                + "<part> "
              + "<greetMe xmlns='http://apache.org/hello_world_soap_http_provider/types'><requestType>"
              + "ffang"
              + "</requestType></greetMe>"
              + "</part>"
              + "</message>"));
        
        client.sendSync(io);
        
        assertTrue(new SourceTransformer().contentToString(
                io.getOutMessage()).indexOf("Hello ffang") >= 0);
    }
    
    private void greetMeProviderJmsTestBase(boolean sync, String name) throws Exception {

        client = new DefaultServiceMixClient(jbi);
        
        io = client.createInOutExchange();
        io.setService(new QName("http://apache.org/hello_world_soap_http", "HelloWorldService"));
        io.setInterfaceName(new QName("http://apache.org/hello_world_soap_http", "Greeter"));
        io.setOperation(new QName("http://apache.org/hello_world_soap_http", "greetMe"));
        //send message to proxy
        io.getInMessage().setContent(new StringSource(
              "<greetMe xmlns='http://apache.org/hello_world_soap_http/types'><requestType>"
              + name
              + "</requestType></greetMe>"));
        
        if (sync) {
            client.sendSync(io);
        } else {
            client.send(io);
            client.receive(5000);
        }
        String txt = new SourceTransformer().contentToString(io.getFault() != null ? io.getFault() : io.getOutMessage());
        client.done(io);
        if ("ffang".equals(name)) {
            //in this case, the server is intended to sleep 3 sec,
            //which will cause time out both for sync and async invoke
            assertTrue(txt.indexOf("Timeout receiving message") >= 0);
        } else {
            //in this case, both sync and async invocation shouldn't see the timeout problem
            assertTrue(txt.indexOf("Hello " + name) >= 0);
        }
    }

    @Override
    protected AbstractXmlApplicationContext createBeanFactory() {
        // load cxf se and bc from spring config file
        return new ClassPathXmlApplicationContext(
                "org/apache/servicemix/cxfbc/provider/xbean_provider.xml");
    }
    
    protected AbstractXmlApplicationContext createBeanFactory(String beanFile) {
        //load cxf se and bc from specified spring config file
        return new ClassPathXmlApplicationContext(
            beanFile);
    }

}
