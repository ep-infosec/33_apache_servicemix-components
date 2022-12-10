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
package org.apache.servicemix.http;

import org.apache.servicemix.client.DefaultServiceMixClient;
import org.apache.servicemix.jbi.jaxp.SourceTransformer;
import org.apache.servicemix.jbi.util.FileUtil;
import org.apache.servicemix.tck.SpringTestSupport;
import org.apache.xbean.spring.context.ClassPathXmlApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.w3c.dom.Node;

import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOut;
import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

public class HttpAddressingTest extends SpringTestSupport {

    private final Logger logger = LoggerFactory.getLogger(HttpAddressingTest.class);
    
    String port1 = System.getProperty("http.port1", "61101");

    public void testOk() throws Exception {
        DefaultServiceMixClient client = new DefaultServiceMixClient(jbi);
        InOut me = client.createInOutExchange();
        me.setService(new QName("http://test", "MyProviderService"));
        InputStream fis = getClass().getResourceAsStream("addressing-request.xml");
        me.getInMessage().setContent(new StreamSource(fis));
        client.sendSync(me);
        if (me.getStatus() == ExchangeStatus.ERROR) {
            if (me.getError() != null) {
                throw me.getError();
            } else {
                fail("Received ERROR status");
            }
        } else if (me.getFault() != null) {
            String txt = new SourceTransformer().toString(me.getFault().getContent());
            client.done(me);
            fail("Received fault: " + txt);
        } else {
            Node node = new SourceTransformer().toDOMNode(me.getOutMessage());
            client.done(me);
            logger.info(new SourceTransformer().toString(node));
            assertEquals("myid", textValueOfXPath(node, "//*[local-name()='RelatesTo']"));
            assertNotNull(textValueOfXPath(node, "//*[local-name()='MessageID']"));
        }
    }

    public void testOkFromUrl() throws Exception {
        URLConnection connection = new URL("http://localhost:"+port1+"/Service/").openConnection();
        connection.setDoOutput(true);
        connection.setDoInput(true);
        OutputStream os = connection.getOutputStream();
        // Post the request file.
        InputStream fis = getClass().getResourceAsStream("addressing-request.xml");
        FileUtil.copyInputStream(fis, os);
        // Read the response.
        InputStream is = connection.getInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FileUtil.copyInputStream(is, baos);
        logger.info(baos.toString());
    }

    public void testBad() throws Exception {
        /*
         * Disable this test until http://jira.codehaus.org/browse/JETTY-99 or
         * http://issues.apache.org/activemq/browse/SM-541
         *  // This test is bit weird, because the http consumer is not soap 
         *  // so it will just forward the HTTP error // //
         * TODO: note that WSA based faults are not created // DefaultServiceMixClient client = new
         * DefaultServiceMixClient(jbi); InOut me = client.createInOutExchange(); me.setService(new QName("http://test",
         * "MyProviderService")); InputStream fis = getClass().getResourceAsStream("bad-addressing-request.xml");
         * me.getInMessage().setContent(new StreamSource(fis)); client.sendSync(me); assertEquals(ExchangeStatus.ACTIVE,
         * me.getStatus()); assertNotNull(me.getFault()); logger.info(new
         * SourceTransformer().toString(me.getFault().getContent()));
         */
    }

    protected AbstractXmlApplicationContext createBeanFactory() {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(new String[] { "org/apache/servicemix/http/addressing.xml" }, false);
        context.setValidating(false);
        context.refresh();
        return context;
    }

}
