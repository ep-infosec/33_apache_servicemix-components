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
package org.apache.servicemix.cxfbc;

import java.net.URL;
import java.util.logging.Logger;

import javax.xml.namespace.QName;
import javax.xml.ws.soap.SOAPBinding;
import org.apache.cxf.calculator.CalculatorPortType;
import org.apache.cxf.calculator.CalculatorService;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;

import org.apache.servicemix.jbi.container.SpringJBIContainer;
import org.apache.servicemix.jbi.jaxp.SourceTransformer;
import org.springframework.context.support.AbstractXmlApplicationContext;

public class CxfBCConsumerAsynTest extends CxfBcSpringTestSupport {
    
    private static final Logger LOG = LogUtils.getL7dLogger(CxfBCConsumerAsynTest.class);
    public void setUp() throws Exception {
        //override super setup
        LOG.info("setUp is invoked");
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
    }

    public void testMultipleClientWithAsyn() throws Exception {
        setUpJBI("org/apache/servicemix/cxfbc/xbean_asyn.xml");
        multiClientTestBase();
    }

        
    private void multiClientTestBase() throws Exception {
        URL wsdl = getClass().getResource("/wsdl/calculator.wsdl");
        assertNotNull(wsdl);
        CalculatorService service = new CalculatorService(wsdl, new QName(
                "http://apache.org/cxf/calculator", "CalculatorService"));
        QName endpoint = new QName("http://apache.org/cxf/calculator", "CalculatorPort");
        service.addPort(endpoint, 
                SOAPBinding.SOAP12HTTP_BINDING, "http://localhost:19000/CalculatorService/SoapPort");
        CalculatorPortType port = service.getPort(endpoint, CalculatorPortType.class);
        ClientProxy.getClient(port).getInInterceptors().add(new LoggingInInterceptor());
        ClientProxy.getClient(port).getOutInterceptors().add(new LoggingOutInterceptor());
        MultiClientThread[] clients = new MultiClientThread[3];
        for (int i = 0; i < clients.length; i++) {
            clients[i] = new MultiClientThread(port, i);
        }
        
        for (int i = 0; i < clients.length; i++) {
            clients[i].start();
            Thread.sleep(2000);
        }
        
        for (int i = 0; i < clients.length; i++) {
            clients[i].join();
            //ensure the second invocation return first since it's less time consuming
            assertEquals(clients[i].getResult(), "420");
        }
    }
    
    
    @Override
    protected AbstractXmlApplicationContext createBeanFactory() {
        // load cxf se and bc from spring config file
        return new ClassPathXmlApplicationContext(
                "org/apache/servicemix/cxfbc/xbean.xml");
    }
    
    protected AbstractXmlApplicationContext createBeanFactory(String beanFile) {
        //load cxf se and bc from specified spring config file
        return new ClassPathXmlApplicationContext(
            beanFile);
    }

}

class MultiClientThread extends Thread {
    private CalculatorPortType port;
    static String result = "";
    private int index;
    
    public MultiClientThread(CalculatorPortType port, int index) {
        this.port = port;
        this.index = index;
    }
    
    public void run() {
        try {
        	int ret = port.add(index, index);
        	if (ret == 2 * index) {
        		result = result + ret;
        	}
        } catch (Exception ex) {
        	result = "invocation failed " + ex.getMessage();
        }
    }
    
    public String getResult() {
    	return result;
    }
    
}

