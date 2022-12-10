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

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.commons.httpclient.util.EncodingUtil;
import org.apache.servicemix.client.DefaultServiceMixClient;
import org.apache.servicemix.jbi.jaxp.SourceTransformer;
import org.apache.servicemix.jbi.jaxp.StringSource;
import org.apache.servicemix.tck.SpringTestSupport;
import org.apache.xbean.spring.context.ClassPathXmlApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractXmlApplicationContext;

import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOut;
import javax.xml.namespace.QName;
import java.io.ByteArrayOutputStream;
import java.io.File;

public class HttpSpringTest extends SpringTestSupport {

    private final Logger logger = LoggerFactory.getLogger(HttpSpringTest.class);
    
    String port9 = System.getProperty("http.port9", "61109");

    protected void setUp() throws Exception {
        String str = "Basic " + EncodingUtil.getAsciiString(
                Base64.encodeBase64(EncodingUtil.getBytes("smx:smx", "UTF-8")));
        System.err.println(str);
        System.setProperty("javax.net.debug", "all");
        super.setUp();
    }

    public void test() throws Exception {
        DefaultServiceMixClient client = new DefaultServiceMixClient(jbi);
        InOut me = client.createInOutExchange();
        me.setService(new QName("http://test", "MyProviderService"));
        me.getInMessage().setContent(new StringSource("<echo xmlns='http://test'><echoin0>world</echoin0></echo>"));
        client.sendSync(me);
        if (me.getStatus() == ExchangeStatus.ERROR) {
            if (me.getFault() != null) {
                fail("Received fault: " + new SourceTransformer().toString(me.getFault().getContent()));
            } else if (me.getError() != null) {
                throw me.getError();
            } else {
                fail("Received ERROR status");
            }
        } else {
            logger.info(new SourceTransformer().toString(me.getOutMessage().getContent()));
        }
    }

    public void testSsl() throws Exception {
        DefaultServiceMixClient client = new DefaultServiceMixClient(jbi);
        InOut me = client.createInOutExchange();
        me.setService(new QName("http://test/ssl", "MyProviderService"));
        me.getInMessage().setContent(new StringSource("<echo xmlns='http://test'><echoin0>world</echoin0></echo>"));
        client.sendSync(me);
        if (me.getStatus() == ExchangeStatus.ERROR) {
            if (me.getFault() != null) {
                fail("Received fault: " + new SourceTransformer().toString(me.getFault().getContent()));
            } else if (me.getError() != null) {
                throw me.getError();
            } else {
                fail("Received ERROR status");
            }
        } else {
            logger.info(new SourceTransformer().toString(me.getOutMessage().getContent()));
        }
    }

    public void testMimeWithHttpClient() throws Exception {
        File f = new File(getClass().getResource("servicemix.jpg").getFile());
        PostMethod filePost = new PostMethod("http://localhost:"+port9+"/Service/");
        Part[] parts = {new StringPart("request", "<dummy/>"), new FilePart(f.getName(), f)};
        RequestEntity entity = new MultipartRequestEntity(parts, filePost.getParams());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        entity.writeRequest(baos);
        logger.info("{}", baos);
        filePost.setRequestEntity(entity);
        HttpClient client = new HttpClient();
        int status = client.executeMethod(filePost);
        assertEquals(200, status);
        filePost.releaseConnection();
    }

    protected AbstractXmlApplicationContext createBeanFactory() {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(new String[] { "org/apache/servicemix/http/spring.xml" }, false);
        context.setValidating(false);
        context.refresh();
        return context;
    }

}
