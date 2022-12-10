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
package org.apache.servicemix.validation;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import javax.activation.FileDataSource;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOut;
import javax.xml.namespace.QName;

import org.apache.servicemix.client.DefaultServiceMixClient;
import org.apache.servicemix.jbi.jaxp.SourceTransformer;
import org.apache.servicemix.jbi.jaxp.StringSource;
import org.apache.servicemix.jbi.util.FileUtil;
import org.apache.servicemix.tck.SpringTestSupport;
import org.apache.xbean.spring.context.ClassPathXmlApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractXmlApplicationContext;

public class ValidationComponentTest extends SpringTestSupport {

    private final Logger logger = LoggerFactory.getLogger(ValidationComponentTest.class);

    private static final String VALID_FILE = "target/test-classes/requestValid.xml";
    private static final String INVALID_FILE = "target/test-classes/requestInvalid.xml";
    
    public void testValidationOKFlow() throws Exception {
        DefaultServiceMixClient client = new DefaultServiceMixClient(jbi);
        InOut me = client.createInOutExchange();
        me.setService(new QName("urn:test", "service"));
        
        String content = getFileContent(VALID_FILE);
        
        me.getInMessage().setContent(new StringSource(content));
        client.sendSync(me);
        if (me.getStatus() == ExchangeStatus.ERROR) {
            if (me.getError() != null) {
                throw me.getError();
            } else {
                fail("Received ERROR status");
            }
        } else if (me.getFault() != null) {
            fail("Received fault: " + new SourceTransformer().toString(me.getFault().getContent()));
        }
        logger.info(new SourceTransformer().toString(me.getOutMessage().getContent()));
        client.done(me);
    }
    
    public void testValidationNotOKFlow() throws Exception {
        DefaultServiceMixClient client = new DefaultServiceMixClient(jbi);
        InOut me = client.createInOutExchange();
        me.setService(new QName("urn:test", "service"));
        
        String content = getFileContent(INVALID_FILE);
        
        me.getInMessage().setContent(new StringSource(content));
        client.sendSync(me);
        
        if (me.getFault() == null) {
            fail("Received fault: " + new SourceTransformer().toString(me.getFault().getContent()));
        }
        logger.info(new SourceTransformer().toString(me.getOutMessage().getContent()));
        client.done(me);
    }
    
    public void testValidationOKJbi() throws Exception {
        DefaultServiceMixClient client = new DefaultServiceMixClient(jbi);
        InOut me = client.createInOutExchange();
        me.setService(new QName("urn:test", "service2"));
        
        String content = getFileContent(VALID_FILE);
        
        me.getInMessage().setContent(new StringSource(content));
        client.sendSync(me);
        if (me.getStatus() == ExchangeStatus.ERROR) {
            if (me.getError() != null) {
                throw me.getError();
            } else {
                fail("Received ERROR status");
            }
        } else if (me.getFault() != null) {
            fail("Received fault: " + new SourceTransformer().toString(me.getFault().getContent()));
        }
        logger.info(new SourceTransformer().toString(me.getOutMessage().getContent()));
        client.done(me);
    }
    
    public void testValidationNotOKJbi() throws Exception {
        DefaultServiceMixClient client = new DefaultServiceMixClient(jbi);
        InOut me = client.createInOutExchange();
        me.setService(new QName("urn:test", "service2"));
        
        String content = getFileContent(INVALID_FILE);
        
        me.getInMessage().setContent(new StringSource(content));
        client.sendSync(me);
        
        if (me.getError() == null) {
            client.done(me);
            fail("Received fault: " + new SourceTransformer().toString(me.getFault().getContent()));
        }
        logger.info(new SourceTransformer().toString(me.getOutMessage().getContent()));
    }
    
    public void testValidationOKFlowCaptures() throws Exception {
        DefaultServiceMixClient client = new DefaultServiceMixClient(jbi);
        InOut me = client.createInOutExchange();
        me.setService(new QName("urn:test", "service3"));
        
        String content = getFileContent(VALID_FILE);
        
        me.getInMessage().setContent(new StringSource(content));
        client.sendSync(me);
        if (me.getStatus() == ExchangeStatus.ERROR) {
            if (me.getError() != null) {
                throw me.getError();
            } else {
                fail("Received ERROR status");
            }
        } else if (me.getFault() != null) {
            fail("Received fault: " + new SourceTransformer().toString(me.getFault().getContent()));
        }
        logger.info(new SourceTransformer().toString(me.getOutMessage().getContent()));
        client.done(me);
    }
    
    public void testValidationNotOKFlowCaptures() throws Exception {
        DefaultServiceMixClient client = new DefaultServiceMixClient(jbi);
        InOut me = client.createInOutExchange();
        me.setService(new QName("urn:test", "service3"));
        
        String content = getFileContent(INVALID_FILE);
        
        me.getInMessage().setContent(new StringSource(content));
        client.sendSync(me);
        
        if (me.getFault() == null) {
            fail("Received fault: " + new SourceTransformer().toString(me.getFault().getContent()));
        }
        logger.info(new SourceTransformer().toString(me.getOutMessage().getContent()));
        client.done(me);
    }
    
    /**
     * reads the file contents to string
     * 
     * @param file  the full path and name of the file
     * @return  the file content as string
     */
    private String getFileContent(String file) {
        String result = "";
        FileDataSource fds = new FileDataSource(new File(file));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            FileUtil.copyInputStream(fds.getInputStream(), baos);
            result = baos.toString();
        } catch (IOException ex) {
            logger.error("Error loading file " + file, ex);
        }
        
        return result;
    }
        
    protected AbstractXmlApplicationContext createBeanFactory() {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(new String[] { "spring.xml" }, false);
        context.setValidating(false);
        context.refresh();
        return context;
    }
    
}
