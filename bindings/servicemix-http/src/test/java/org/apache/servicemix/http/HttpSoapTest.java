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

import com.ibm.wsdl.util.xml.DOMUtils;
import junit.framework.TestCase;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.servicemix.client.DefaultServiceMixClient;
import org.apache.servicemix.client.ServiceMixClient;
import org.apache.servicemix.components.util.EchoComponent;
import org.apache.servicemix.components.util.TransformComponentSupport;
import org.apache.servicemix.jbi.FaultException;
import org.apache.servicemix.jbi.api.Destination;
import org.apache.servicemix.jbi.container.JBIContainer;
import org.apache.servicemix.jbi.jaxp.SourceTransformer;
import org.apache.servicemix.jbi.jaxp.StringSource;
import org.apache.servicemix.jbi.resolver.ServiceNameEndpointResolver;
import org.apache.servicemix.jbi.util.ByteArrayDataSource;
import org.apache.servicemix.jbi.util.DOMUtil;
import org.apache.servicemix.jbi.util.FileUtil;
import org.apache.servicemix.soap.marshalers.JBIMarshaler;
import org.apache.servicemix.soap.marshalers.SoapMarshaler;
import org.apache.servicemix.tck.ReceiverComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.jbi.messaging.*;
import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.List;

public class HttpSoapTest extends TestCase {

    private final Logger logger = LoggerFactory.getLogger(HttpSoapTest.class);

    protected JBIContainer container;

    String port1 = System.getProperty("http.port1","61101");
    String port2 = System.getProperty("http.port2", "61102");
    String port3 = System.getProperty("http.port3", "61103");

    protected void setUp() throws Exception {
        container = new JBIContainer();
        container.setUseMBeanServer(false);
        container.setCreateMBeanServer(false);
        container.setEmbedded(true);
        container.init();
    }

    protected void tearDown() throws Exception {
        if (container != null) {
            container.shutDown();
        }
    }

    public void testFaultOnParse() throws Exception {
        HttpComponent http = new HttpComponent();
        HttpEndpoint ep = new HttpEndpoint();
        ep.setService(new QName("urn:test", "echo"));
        ep.setEndpoint("echo");
        ep.setLocationURI("http://localhost:"+port1+"/ep1/");
        ep.setRoleAsString("consumer");
        ep.setDefaultMep(URI.create("http://www.w3.org/2004/08/wsdl/in-out"));
        ep.setSoap(true);
        http.setEndpoints(new HttpEndpoint[] {ep});
        container.activateComponent(http, "http");
        container.start();

        PostMethod method = new PostMethod("http://localhost:"+port1+"/ep1/");
        method.setRequestEntity(new StringRequestEntity("<hello>world</hello>"));
        int state = new HttpClient().executeMethod(method);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, state);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FileUtil.copyInputStream(method.getResponseBodyAsStream(), baos);
        logger.info("{}", baos);
    }

    public void testSoap() throws Exception {
        EchoComponent echo = new EchoComponent();
        echo.setService(new QName("urn:test", "echo"));
        echo.setEndpoint("echo");
        container.activateComponent(echo, "echo");

        HttpComponent http = new HttpComponent();

        HttpEndpoint ep1 = new HttpEndpoint();
        ep1.setService(new QName("urn:test", "echo"));
        ep1.setEndpoint("echo");
        ep1.setLocationURI("http://localhost:"+port1+"/ep1/");
        ep1.setRoleAsString("consumer");
        ep1.setDefaultMep(URI.create("http://www.w3.org/2004/08/wsdl/in-out"));
        ep1.setSoap(true);

        HttpEndpoint ep2 = new HttpEndpoint();
        ep2.setService(new QName("urn:test", "s2"));
        ep2.setEndpoint("ep2");
        ep2.setLocationURI("http://localhost:"+port1+"/ep1/");
        ep2.setRoleAsString("provider");
        ep2.setSoap(true);

        http.setEndpoints(new HttpEndpoint[] {ep1, ep2});

        container.activateComponent(http, "http");

        container.start();

        ServiceMixClient client = new DefaultServiceMixClient(container);
        client.request(new ServiceNameEndpointResolver(new QName("urn:test", "s2")), null, null, new StreamSource(
                        getClass().getResourceAsStream("soap-request.xml")));

    }

    public void testSoapRoundtripConsumerProvider() throws Exception {
        EchoComponent echo = new EchoComponent();
        echo.setService(new QName("urn:test", "echo"));
        echo.setEndpoint("echo");
        container.activateComponent(echo, "echo");

        HttpComponent http = new HttpComponent();

        HttpEndpoint ep1 = new HttpEndpoint();
        ep1.setService(new QName("urn:test", "s1"));
        ep1.setEndpoint("ep1");
        ep1.setTargetService(new QName("urn:test", "echo"));
        ep1.setLocationURI("http://localhost:"+port1+"/ep1/");
        ep1.setRoleAsString("consumer");
        ep1.setDefaultMep(URI.create("http://www.w3.org/2004/08/wsdl/in-out"));
        ep1.setSoap(true);

        HttpEndpoint ep2 = new HttpEndpoint();
        ep2.setService(new QName("urn:test", "s2"));
        ep2.setEndpoint("ep2");
        ep2.setLocationURI("http://localhost:"+port1+"/ep1/");
        ep2.setRoleAsString("provider");
        ep2.setSoap(true);

        http.setEndpoints(new HttpEndpoint[] {ep1, ep2});

        container.activateComponent(http, "http");

        container.start();

        ServiceMixClient client = new DefaultServiceMixClient(container);
        Destination dest = client.createDestination("service:urn:test:s2");
        InOut me = dest.createInOutExchange();
        me.getInMessage().setContent(new StringSource("<hello>world</hello>"));
        client.sendSync(me);
        assertEquals(ExchangeStatus.ACTIVE, me.getStatus());
        String str = new SourceTransformer().contentToString(me.getOutMessage());
        client.done(me);
        logger.info(str);
    }

    public void testSoapRoundtripProviderConsumerProvider() throws Exception {
        EchoComponent echo = new EchoComponent();
        echo.setService(new QName("urn:test", "echo"));
        echo.setEndpoint("echo");
        container.activateComponent(echo, "echo");

        HttpComponent http = new HttpComponent();

        HttpEndpoint ep1 = new HttpEndpoint();
        ep1.setService(new QName("urn:test", "s1"));
        ep1.setEndpoint("ep1");
        ep1.setTargetService(new QName("urn:test", "s2"));
        ep1.setLocationURI("http://localhost:"+port1+"/ep1/");
        ep1.setRoleAsString("consumer");
        ep1.setDefaultMep(URI.create("http://www.w3.org/2004/08/wsdl/in-out"));
        ep1.setSoap(true);

        HttpEndpoint ep2 = new HttpEndpoint();
        ep2.setService(new QName("urn:test", "s2"));
        ep2.setEndpoint("ep2");
        ep2.setLocationURI("http://localhost:"+port1+"/ep3/");
        ep2.setRoleAsString("provider");
        ep2.setSoap(true);

        HttpEndpoint ep3 = new HttpEndpoint();
        ep3.setService(new QName("urn:test", "s3"));
        ep3.setEndpoint("ep3");
        ep3.setTargetService(new QName("urn:test", "echo"));
        ep3.setLocationURI("http://localhost:"+port1+"/ep3/");
        ep3.setRoleAsString("consumer");
        ep3.setDefaultMep(URI.create("http://www.w3.org/2004/08/wsdl/in-out"));
        ep3.setSoap(true);

        http.setEndpoints(new HttpEndpoint[] {ep1, ep2, ep3});

        container.activateComponent(http, "http");

        container.start();

        PostMethod method = new PostMethod("http://localhost:"+port1+"/ep1/");
        method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new HttpMethodRetryHandler() {
            public boolean retryMethod(HttpMethod method, IOException exception, int executionCount) {
                return false;
            }
        });
        method.setRequestEntity(new StringRequestEntity(
                        "<env:Envelope xmlns:env='http://www.w3.org/2003/05/soap-envelope'>"
                        + "<env:Body><hello>world</hello></env:body>" + "</env:Envelope>"));
        int state = new HttpClient().executeMethod(method);
        assertEquals(HttpServletResponse.SC_OK, state);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FileUtil.copyInputStream(method.getResponseBodyAsStream(), baos);
        logger.info("{}", baos);
    }

    public void testSoapFault12() throws Exception {
        TransformComponentSupport echo = new TransformComponentSupport() {
            protected boolean transform(MessageExchange exchange, NormalizedMessage in, NormalizedMessage out)
                throws MessagingException {
                Fault f = exchange.createFault();
                f.setContent(new StringSource("<hello xmlns='myuri'>this is a fault</hello>"));
                f.setProperty(JBIMarshaler.SOAP_FAULT_REASON, "My reason");
                throw new FaultException(null, exchange, f);
            }
        };
        echo.setService(new QName("urn:test", "echo"));
        echo.setEndpoint("echo");
        container.activateComponent(echo, "echo");

        HttpEndpoint ep1 = createInOutEndpoint("ep1");
        ep1.setTargetService(new QName("urn:test", "echo"));
        ep1.setTargetEndpoint("echo");
        ep1.setLocationURI("http://localhost:"+port2+"/ep1/");
        ep1.setRoleAsString("consumer");
        ep1.setSoap(true);

        HttpEndpoint ep2 = createInOutEndpoint("ep2");
        ep2.setTargetService(new QName("urn:test", "http"));
        ep2.setTargetEndpoint("ep3");
        ep2.setLocationURI("http://localhost:"+port2+"/ep2/");
        ep2.setRoleAsString("consumer");
        ep2.setSoap(true);

        HttpEndpoint ep3 = createInOutEndpoint("ep3");
        ep3.setLocationURI("http://localhost:"+port2+"/ep1/");
        ep3.setRoleAsString("provider");
        ep3.setDefaultMep(URI.create("http://www.w3.org/2004/08/wsdl/in-out"));
        ep3.setSoap(true);

        HttpComponent http = new HttpComponent();
        http.setEndpoints(new HttpEndpoint[] {ep1, ep2, ep3});
        container.activateComponent(http, "http1");

        container.start();

        PostMethod method = new PostMethod("http://localhost:"+port2+"/ep2/");
        method.setRequestEntity(new InputStreamRequestEntity(getClass().getResourceAsStream("soap-request-12.xml")));
        int state = new HttpClient().executeMethod(method);
        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, state);
        SourceTransformer st = new SourceTransformer();
        Node node = st.toDOMNode(new StreamSource(method.getResponseBodyAsStream()));
        logger.info(st.toString(node));

        Element e = ((Document) node).getDocumentElement();
        assertEquals(new QName(SoapMarshaler.SOAP_12_URI, SoapMarshaler.ENVELOPE), DOMUtil.getQName(e));
        e = DOMUtil.getFirstChildElement(e);
        assertEquals(new QName(SoapMarshaler.SOAP_12_URI, SoapMarshaler.BODY), DOMUtil.getQName(e));
        e = DOMUtil.getFirstChildElement(e);
        assertEquals(new QName(SoapMarshaler.SOAP_12_URI, SoapMarshaler.FAULT), DOMUtil.getQName(e));

        method = new PostMethod("http://localhost:"+port2+"/ep2/");
        method.setRequestBody("hello");
        state = new HttpClient().executeMethod(method);
        String str = method.getResponseBodyAsString();
        logger.info(str);
        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, state);
        node = st.toDOMNode(new StringSource(str));
        e = ((Document) node).getDocumentElement();
        assertEquals(new QName(SoapMarshaler.SOAP_12_URI, SoapMarshaler.ENVELOPE), DOMUtil.getQName(e));
        e = DOMUtil.getFirstChildElement(e);
        assertEquals(new QName(SoapMarshaler.SOAP_12_URI, SoapMarshaler.BODY), DOMUtil.getQName(e));
        e = DOMUtil.getFirstChildElement(e);
        assertEquals(new QName(SoapMarshaler.SOAP_12_URI, SoapMarshaler.FAULT), DOMUtil.getQName(e));

        method = new PostMethod("http://localhost:"+port2+"/ep2/");
        method.setRequestBody("<hello/>");
        state = new HttpClient().executeMethod(method);
        str = method.getResponseBodyAsString();
        logger.info(str);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, state);
        node = st.toDOMNode(new StringSource(str));
        e = ((Document) node).getDocumentElement();
        assertEquals(new QName(SoapMarshaler.SOAP_12_URI, SoapMarshaler.ENVELOPE), DOMUtil.getQName(e));
        e = DOMUtils.getFirstChildElement(e);
        assertEquals(new QName(SoapMarshaler.SOAP_12_URI, SoapMarshaler.BODY), DOMUtil.getQName(e));
        e = DOMUtils.getFirstChildElement(e);
        assertEquals(new QName(SoapMarshaler.SOAP_12_URI, SoapMarshaler.FAULT), DOMUtil.getQName(e));
    }

    public void testSoapFault11() throws Exception {
        TransformComponentSupport echo = new TransformComponentSupport() {
            protected boolean transform(MessageExchange exchange, NormalizedMessage in, NormalizedMessage out)
                throws MessagingException {
                Fault f = exchange.createFault();
                f.setContent(new StringSource("<hello xmlns='myuri'>this is a fault</hello>"));
                f.setProperty(JBIMarshaler.SOAP_FAULT_REASON, "My reason");
                throw new FaultException(null, exchange, f);
            }
        };
        echo.setService(new QName("urn:test", "echo"));
        echo.setEndpoint("echo");
        container.activateComponent(echo, "echo");

        HttpEndpoint ep1 = createInOutEndpoint("ep1");
        ep1.setTargetService(new QName("urn:test", "echo"));
        ep1.setTargetEndpoint("echo");
        ep1.setLocationURI("http://localhost:"+port3+"/ep1/");
        ep1.setRoleAsString("consumer");
        ep1.setSoap(true);
        ep1.setSoapVersion("1.1");

        HttpEndpoint ep2 = createInOutEndpoint("ep2");
        ep2.setTargetService(new QName("urn:test", "http"));
        ep2.setTargetEndpoint("ep3");
        ep2.setLocationURI("http://localhost:"+port3+"/ep2/");
        ep2.setRoleAsString("consumer");
        ep2.setSoap(true);
        ep2.setSoapVersion("1.1");

        HttpEndpoint ep3 = createInOutEndpoint("ep3");
        ep3.setLocationURI("http://localhost:"+port3+"/ep1/");
        ep3.setRoleAsString("provider");
        ep3.setSoap(true);
        ep3.setSoapVersion("1.1");

        HttpComponent http = new HttpComponent();
        http.setEndpoints(new HttpEndpoint[] {ep1, ep2, ep3});
        container.activateComponent(http, "http1");

        container.start();

        PostMethod method = new PostMethod("http://localhost:"+port3+"/ep2/");
        method.setRequestEntity(new InputStreamRequestEntity(getClass().getResourceAsStream("soap-request.xml")));
        int state = new HttpClient().executeMethod(method);
        String str = method.getResponseBodyAsString();
        logger.info(str);
        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, state);
        SourceTransformer st = new SourceTransformer();
        Node node = st.toDOMNode(new StringSource(str));

        Element e = ((Document) node).getDocumentElement();
        assertEquals(new QName(SoapMarshaler.SOAP_11_URI, SoapMarshaler.ENVELOPE), DOMUtil.getQName(e));
        e = DOMUtils.getFirstChildElement(e);
        assertEquals(new QName(SoapMarshaler.SOAP_11_URI, SoapMarshaler.BODY), DOMUtil.getQName(e));
        e = DOMUtils.getFirstChildElement(e);
        assertEquals(new QName(SoapMarshaler.SOAP_11_URI, SoapMarshaler.FAULT), DOMUtil.getQName(e));

        method = new PostMethod("http://localhost:"+port3+"/ep2/");
        method.setRequestBody("hello");
        state = new HttpClient().executeMethod(method);
        str = method.getResponseBodyAsString();
        logger.info(str);
        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, state);
        node = st.toDOMNode(new StringSource(str));
        e = ((Document) node).getDocumentElement();
        assertEquals(new QName(SoapMarshaler.SOAP_11_URI, SoapMarshaler.ENVELOPE), DOMUtil.getQName(e));
        e = DOMUtils.getFirstChildElement(e);
        assertEquals(new QName(SoapMarshaler.SOAP_11_URI, SoapMarshaler.BODY), DOMUtil.getQName(e));
        e = DOMUtils.getFirstChildElement(e);
        assertEquals(new QName(SoapMarshaler.SOAP_11_URI, SoapMarshaler.FAULT), DOMUtil.getQName(e));

        method = new PostMethod("http://localhost:"+port3+"/ep2/");
        method.setRequestBody("<hello/>");
        state = new HttpClient().executeMethod(method);
        str = method.getResponseBodyAsString();
        logger.info(str);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, state);
        node = st.toDOMNode(new StringSource(str));
        e = ((Document) node).getDocumentElement();
        assertEquals(new QName(SoapMarshaler.SOAP_11_URI, SoapMarshaler.ENVELOPE), DOMUtil.getQName(e));
        e = DOMUtils.getFirstChildElement(e);
        assertEquals(new QName(SoapMarshaler.SOAP_11_URI, SoapMarshaler.BODY), DOMUtil.getQName(e));
        e = DOMUtils.getFirstChildElement(e);
        assertEquals(new QName(SoapMarshaler.SOAP_11_URI, SoapMarshaler.FAULT), DOMUtil.getQName(e));
    }

    public void testSoapXml() throws Exception {
        ReceiverComponent echo = new ReceiverComponent();
        echo.setService(new QName("urn:test", "echo"));
        echo.setEndpoint("echo");
        container.activateComponent(echo, "echo");

        HttpEndpoint ep1 = createInOnlyEndpoint("ep1");
        ep1.setTargetService(new QName("urn:test", "echo"));
        ep1.setTargetEndpoint("echo");
        ep1.setLocationURI("http://localhost:"+port2+"/ep1/");
        ep1.setRoleAsString("consumer");
        ep1.setSoap(false);

        HttpEndpoint ep2 = createInOnlyEndpoint("ep2");
        ep2.setTargetService(new QName("urn:test", "http"));
        ep2.setTargetEndpoint("ep3");
        ep2.setLocationURI("http://localhost:"+port2+"/ep2/");
        ep2.setRoleAsString("consumer");
        ep2.setSoap(true);

        HttpEndpoint ep3 = createInOnlyEndpoint("ep3");
        ep3.setLocationURI("http://localhost:"+port2+"/ep1/");
        ep3.setRoleAsString("provider");
        ep3.setSoap(true);

        HttpComponent http = new HttpComponent();
        http.setEndpoints(new HttpEndpoint[] {ep1, ep2, ep3});
        container.activateComponent(http, "http1");

        container.start();

        PostMethod method = new PostMethod("http://localhost:"+port2+"/ep2/");
        method.setRequestEntity(new InputStreamRequestEntity(getClass().getResourceAsStream("request.xml")));
        new HttpClient().executeMethod(method);

        echo.getMessageList().assertMessagesReceived(1);
        List msgs = echo.getMessageList().flushMessages();
        NormalizedMessage msg = (NormalizedMessage) msgs.get(0);
        SourceTransformer st = new SourceTransformer();
        Element e = st.toDOMElement(msg);
        String strMsg = DOMUtil.asXML(e);
        logger.info(strMsg);

        assertEquals(new QName(SoapMarshaler.SOAP_12_URI, SoapMarshaler.ENVELOPE), DOMUtil.getQName(e));
        e = DOMUtil.getFirstChildElement(e);
        assertEquals(new QName(SoapMarshaler.SOAP_12_URI, SoapMarshaler.BODY), DOMUtil.getQName(e));
        e = DOMUtil.getFirstChildElement(e);
        assertEquals(new QName("http://ws.location.services.cardinal.com/", "listAllProvider"), DOMUtil.getQName(e));
        e = DOMUtil.getFirstChildElement(e);
        assertEquals(new QName("", "clientSessionGuid"), DOMUtil.getQName(e));
    }

    public void testAttachments() throws Exception {
        EchoComponent echo = new EchoComponent();
        echo.setService(new QName("urn:test", "echo"));
        echo.setEndpoint("echo");
        container.activateComponent(echo, "echo");

        HttpComponent http = new HttpComponent();

        HttpEndpoint ep0 = new HttpEndpoint();
        ep0.setService(new QName("urn:test", "s0"));
        ep0.setEndpoint("ep0");
        ep0.setLocationURI("http://localhost:"+port1+"/ep1/");
        ep0.setRoleAsString("provider");
        ep0.setSoap(true);

        HttpEndpoint ep1 = new HttpEndpoint();
        ep1.setService(new QName("urn:test", "s1"));
        ep1.setEndpoint("ep1");
        ep1.setTargetService(new QName("urn:test", "s2"));
        ep1.setLocationURI("http://localhost:"+port1+"/ep1/");
        ep1.setRoleAsString("consumer");
        ep1.setDefaultMep(URI.create("http://www.w3.org/2004/08/wsdl/in-out"));
        ep1.setSoap(true);

        HttpEndpoint ep2 = new HttpEndpoint();
        ep2.setService(new QName("urn:test", "s2"));
        ep2.setEndpoint("ep2");
        ep2.setLocationURI("http://localhost:"+port1+"/ep3/");
        ep2.setRoleAsString("provider");
        ep2.setSoap(true);

        HttpEndpoint ep3 = new HttpEndpoint();
        ep3.setService(new QName("urn:test", "s3"));
        ep3.setEndpoint("ep3");
        ep3.setTargetService(new QName("urn:test", "echo"));
        ep3.setLocationURI("http://localhost:"+port1+"/ep3/");
        ep3.setRoleAsString("consumer");
        ep3.setDefaultMep(URI.create("http://www.w3.org/2004/08/wsdl/in-out"));
        ep3.setSoap(true);

        http.setEndpoints(new HttpEndpoint[] {ep0, ep1, ep2, ep3});

        container.activateComponent(http, "http");

        container.start();

        DefaultServiceMixClient client = new DefaultServiceMixClient(container);
        Destination d = client.createDestination("service:urn:test:s0");
        InOut me = d.createInOutExchange();
        me.getInMessage().setContent(new StringSource("<hello>world</hello>"));
        File f = new File(getClass().getResource("servicemix.jpg").getFile());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FileUtil.copyInputStream(new FileInputStream(f), baos);
        DataSource ds = new ByteArrayDataSource(baos.toByteArray(), "image/jpeg");
        DataHandler dh = new DataHandler(ds);
        me.getInMessage().addAttachment("image", dh);
        client.sendSync(me);
        assertEquals(ExchangeStatus.ACTIVE, me.getStatus());
        assertEquals(1, me.getOutMessage().getAttachmentNames().size());
        client.done(me);
    }

    private HttpEndpoint createInOnlyEndpoint(String name) {
        HttpEndpoint ep = createHttpEndpoint(name);
        ep.setDefaultMep(URI.create("http://www.w3.org/2004/08/wsdl/in-only"));
        return ep;
    }

    private HttpEndpoint createInOutEndpoint(String name) {
        HttpEndpoint ep = createHttpEndpoint(name);
        ep.setDefaultMep(URI.create("http://www.w3.org/2004/08/wsdl/in-out"));
        return ep;
    }

    private HttpEndpoint createHttpEndpoint(String name) {
        HttpEndpoint ep = new HttpEndpoint();
        ep.setService(new QName("urn:test", "http"));
        ep.setEndpoint(name);
        return ep;
    }
}
