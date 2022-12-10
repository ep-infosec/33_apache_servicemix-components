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
package org.apache.servicemix.cxfbc.ws.policy;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.namespace.QName;

import junit.framework.TestCase;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.endpoint.ClientImpl;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.greeter_control.BasicGreeterService;
import org.apache.cxf.greeter_control.Greeter;
import org.apache.cxf.greeter_control.PingMeFault;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.testutil.common.ServerLauncher;


public class CxfBCPolicyTest extends TestCase {

    private static final Logger LOG = LogUtils.getL7dLogger(CxfBCPolicyTest.class);
    protected static boolean serversStarted;
    private ServerLauncher sl;
    
    public void startJBIContainers() throws Exception {
        if (serversStarted) {
            return;
        }
        Map<String, String> props = new HashMap<String, String>();                
        if (System.getProperty("javax.xml.transform.TransformerFactory") != null) {
            props.put("javax.xml.transform.TransformerFactory", System.getProperty("javax.xml.transform.TransformerFactory"));
        }
        if (System.getProperty("javax.xml.stream.XMLInputFactory") != null) {
            props.put("javax.xml.stream.XMLInputFactory", System.getProperty("javax.xml.stream.XMLInputFactory"));
        }
        if (System.getProperty("javax.xml.stream.XMLOutputFactory") != null) {
            props.put("javax.xml.stream.XMLOutputFactory", System.getProperty("javax.xml.stream.XMLOutputFactory"));
        }
        
        assertTrue("JBIContainers did not launch correctly", 
                launchServer(JBIServer.class, props, false));
       
        
        serversStarted = true;
    }
    
    protected void setUp() throws Exception {
        startJBIContainers();
    }
    
    protected void tearDown() throws Exception {
        super.tearDown();
        try {
            sl.stopServer();         
        } catch (IOException ex) {
            ex.printStackTrace();
            fail("failed to stop jbi container " + sl.getClass());
        } 
        serversStarted = false;
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
    
    public void testUsingAddressing() throws Exception {
        SpringBusFactory bf = new SpringBusFactory();
        Bus bus = bf
                .createBus("/org/apache/servicemix/cxfbc/ws/policy/addr.xml");
        BusFactory.setDefaultBus(bus);
        LoggingInInterceptor in = new LoggingInInterceptor();
        bus.getInInterceptors().add(in);
        bus.getInFaultInterceptors().add(in);
        LoggingOutInterceptor out = new LoggingOutInterceptor();
        bus.getOutInterceptors().add(out);
        bus.getOutFaultInterceptors().add(out);
        URL wsdl = getClass().getResource("/wsdl/greeter_control.wsdl");
        QName serviceName = new QName("http://cxf.apache.org/greeter_control",
                                      "BasicGreeterService");
        BasicGreeterService gs = new BasicGreeterService(wsdl, serviceName);
        final Greeter greeter = gs.getGreeterPort();
        LOG.info("Created greeter client.");
        ConnectionHelper.setKeepAliveConnection(greeter, true);
        //set timeout to 100 secs to avoid intermitly failed
        ((ClientImpl)ClientProxy.getClient(greeter)).setSynchronousTimeout(100000);
        
        
        assertEquals("CXF", greeter.greetMe("cxf"));

        // exception

        try {
            greeter.pingMe();
        } catch (PingMeFault ex) {
            fail("First invocation should have succeeded.");
        }

        try {
            greeter.pingMe();
            fail("Expected PingMeFault not thrown.");
        } catch (PingMeFault ex) {
            assertEquals(2, (int) ex.getFaultInfo().getMajor());
            assertEquals(1, (int) ex.getFaultInfo().getMinor());
        }
        // oneway
        greeter.greetMeOneWay("CXF");
    }

    
}
