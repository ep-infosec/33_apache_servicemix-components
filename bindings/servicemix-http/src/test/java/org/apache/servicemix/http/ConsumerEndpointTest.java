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

import junit.framework.TestCase;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.log4j.Level;
import org.apache.servicemix.components.http.InvalidStatusResponseException;
import org.apache.servicemix.components.util.EchoComponent;
import org.apache.servicemix.components.util.MockServiceComponent;
import org.apache.servicemix.components.util.TransformComponentSupport;
import org.apache.servicemix.executors.impl.ExecutorFactoryImpl;
import org.apache.servicemix.http.endpoints.HttpConsumerEndpoint;
import org.apache.servicemix.http.endpoints.HttpSoapConsumerEndpoint;
import org.apache.servicemix.jbi.container.JBIContainer;
import org.apache.servicemix.jbi.jaxp.SourceTransformer;
import org.apache.servicemix.jbi.jaxp.StringSource;
import org.apache.servicemix.jbi.messaging.MessageExchangeSupport;
import org.apache.servicemix.jbi.util.DOMUtil;
import org.apache.servicemix.soap.bindings.soap.Soap11;
import org.apache.servicemix.soap.bindings.soap.Soap12;
import org.apache.servicemix.soap.bindings.soap.SoapConstants;
import org.apache.servicemix.soap.interceptors.jbi.JbiConstants;
import org.apache.servicemix.soap.util.DomUtil;
import org.apache.servicemix.tck.ExchangeCompletedListener;
import org.apache.servicemix.tck.ReceiverComponent;
import org.apache.xpath.CachedXPathAPI;
import org.eclipse.jetty.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.traversal.NodeIterator;

import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.MessagingException;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;
import javax.wsdl.*;
import javax.wsdl.extensions.ExtensibilityElement;
import javax.wsdl.extensions.soap.SOAPAddress;
import javax.wsdl.extensions.soap.SOAPOperation;
import javax.wsdl.extensions.soap12.SOAP12Address;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.xml.namespace.QName;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ConsumerEndpointTest extends TestCase {

    private final Logger logger = LoggerFactory.getLogger(ConsumerEndpointTest.class);

    String port1 = System.getProperty("http.port1", "61101");
    String port2 = System.getProperty("http.port2", "61102");
    
    protected JBIContainer container;
    protected SourceTransformer transformer = new SourceTransformer();

    static {
        System.setProperty("org.apache.servicemix.preserveContent", "true");

    }

    protected void setUp() throws Exception {
        container = new JBIContainer();
        container.setUseMBeanServer(false);
        container.setCreateMBeanServer(false);
        container.setEmbedded(true);
        ExecutorFactoryImpl factory = new ExecutorFactoryImpl();
        factory.getDefaultConfig().setQueueSize(0);
        container.setExecutorFactory(factory);
        container.init();
        org.apache.log4j.Logger.getRootLogger().setLevel(Level.INFO);
    }

    protected void tearDown() throws Exception {
        if (container != null) {
            container.shutDown();
        }
    }

    protected String textValueOfXPath(Node node, String xpath) throws TransformerException {
        CachedXPathAPI cachedXPathAPI = new CachedXPathAPI();
        NodeIterator iterator = cachedXPathAPI.selectNodeIterator(node, xpath);
        Node root = iterator.nextNode();
        if (root instanceof Element) {
            Element element = (Element) root;
            if (element == null) {
                return "";
            }
            return DOMUtil.getElementText(element);
        } else if (root != null) {
            return root.getNodeValue();
        } else {
            return null;
        }
    }

    public void testHttpInOnly() throws Exception {
        HttpComponent http = new HttpComponent();
        HttpConsumerEndpoint ep = new HttpConsumerEndpoint();
        ep.setService(new QName("urn:test", "svc"));
        ep.setEndpoint("ep");
        ep.setTargetService(new QName("urn:test", "recv"));
        ep.setLocationURI("http://localhost:"+port1+"/ep1");
        ep.setDefaultMep(MessageExchangeSupport.IN_ONLY);
        http.setEndpoints(new HttpEndpointType[] {ep});
        container.activateComponent(http, "http");

        ReceiverComponent recv = new ReceiverComponent();
        recv.setService(new QName("urn:test", "recv"));
        container.activateComponent(recv, "recv");

        container.start();

        PostMethod post = new PostMethod("http://localhost:"+port1+"/ep1/23");
        post.setRequestEntity(new StringRequestEntity("<hello>world</hello>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        logger.info(res);
        assertEquals("", res);
        if (post.getStatusCode() != 202) {
            throw new InvalidStatusResponseException(post.getStatusCode());
        }

        recv.getMessageList().assertMessagesReceived(1);
        post.releaseConnection();
        container.deactivateComponent("recv");
        container.deactivateComponent("http");
    }

    public void testHttpInOutWithTimeout() throws Exception {
        HttpComponent http = new HttpComponent();
        HttpConsumerEndpoint ep = new HttpConsumerEndpoint();
        ep.setService(new QName("urn:test", "svc"));
        ep.setEndpoint("ep");
        ep.setTargetService(new QName("urn:test", "echo"));
        ep.setLocationURI("http://localhost:"+port1+"/ep1/");
        ep.setDefaultMep(MessageExchangeSupport.IN_OUT);
        ep.setTimeout(1000);
        http.setEndpoints(new HttpEndpointType[] {ep});
        container.activateComponent(http, "http");

        EchoComponent echo = new EchoComponent() {
            public void onMessageExchange(MessageExchange exchange) throws MessagingException {
                super.onMessageExchange(exchange);
            }
            protected boolean transform(MessageExchange exchange, NormalizedMessage in, NormalizedMessage out) throws MessagingException {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
                return super.transform(exchange, in, out);
            }
        };
        echo.setService(new QName("urn:test", "echo"));
        echo.setEndpoint("endpoint");
        container.activateComponent(echo, "echo");

        container.start();

        PostMethod post = new PostMethod("http://localhost:"+port1+"/ep1/");
        post.setRequestEntity(new StringRequestEntity("<hello>world</hello>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        logger.info(res);
        
        if (post.getStatusCode() != 500 || !res.contains("HTTP request has timed out")) {
            throw new InvalidStatusResponseException(post.getStatusCode());
        }
        Thread.sleep(1000);
        
        post.releaseConnection();
        container.deactivateComponent("echo");
        container.deactivateComponent("http");
    }

    public void testHttpInOut() throws Exception {
        HttpComponent http = new HttpComponent();
        HttpConsumerEndpoint ep = new HttpConsumerEndpoint();
        ep.setService(new QName("urn:test", "svc"));
        ep.setEndpoint("ep");
        ep.setTargetService(new QName("urn:test", "echo"));
        ep.setLocationURI("http://localhost:"+port1+"/ep1/");
        http.setEndpoints(new HttpEndpointType[] {ep});
        container.activateComponent(http, "http");

        EchoComponent echo = new EchoComponent();
        echo.setService(new QName("urn:test", "echo"));
        echo.setEndpoint("endpoint");
        container.activateComponent(echo, "echo");

        container.start();

        PostMethod post = new PostMethod("http://localhost:"+port1+"/ep1/");
        post.setRequestEntity(new StringRequestEntity("<hello>world</hello>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        logger.info(res);
        Node node = transformer.toDOMNode(new StringSource(res));
        logger.info(transformer.toString(node));
        assertEquals("world", textValueOfXPath(node, "/hello/text()"));
        if (post.getStatusCode() != 200) {
            throw new InvalidStatusResponseException(post.getStatusCode());
        }
        
        post.releaseConnection();
        container.deactivateComponent("echo");
        container.deactivateComponent("http");
    }

    protected void initSoapEndpoints(boolean useJbiWrapper) throws Exception {
    	initSoapEndpoints(useJbiWrapper, true);
    }
    
    protected void initSoapEndpoints(boolean useJbiWrapper, boolean dynamic) throws Exception {
        HttpComponent http = new HttpComponent();
        HttpSoapConsumerEndpoint ep1 = new HttpSoapConsumerEndpoint();
        ep1.setService(new QName("uri:HelloWorld", "HelloService"));
        ep1.setEndpoint("HelloPortSoap11");
        ep1.setTargetService(new QName("urn:test", "echo"));
        ep1.setLocationURI("http://localhost:"+port1+"/ep1/");
        ep1.setWsdl(new ClassPathResource("/org/apache/servicemix/http/HelloWorld-DOC.wsdl"));
        ep1.setValidateWsdl(false); // TODO: Soap 1.2 not handled yet
        ep1.setUseJbiWrapper(useJbiWrapper);
        ep1.setRewriteSoapAddress(dynamic);
        HttpSoapConsumerEndpoint ep2 = new HttpSoapConsumerEndpoint();
        ep2.setService(new QName("uri:HelloWorld", "HelloService"));
        ep2.setEndpoint("HelloPortSoap12");
        ep2.setTargetService(new QName("urn:test", "echo"));
        ep2.setLocationURI("http://localhost:"+port1+"/ep2/");
        ep2.setWsdl(new ClassPathResource("/org/apache/servicemix/http/HelloWorld-DOC.wsdl"));
        ep2.setValidateWsdl(false); // TODO: Soap 1.2 not handled yet
        ep2.setUseJbiWrapper(useJbiWrapper);
        ep2.setRewriteSoapAddress(dynamic);
        http.setEndpoints(new HttpEndpointType[] {ep1, ep2});
        container.activateComponent(http, "http");
        container.start();
    }

    public void testHttpSoap11FaultOnEnvelope() throws Exception {
        initSoapEndpoints(true);

        PostMethod post = new PostMethod("http://localhost:"+port1+"/ep1/");
        post.setRequestEntity(new StringRequestEntity("<hello>world</hello>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        logger.info(res);
        Element elem = transformer.toDOMElement(new StringSource(res));
        assertEquals(Soap11.getInstance().getEnvelope(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap11.getInstance().getBody(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap11.getInstance().getFault(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(SoapConstants.SOAP_11_FAULTCODE, DomUtil.getQName(elem));
        assertEquals(SoapConstants.SOAP_11_CODE_VERSIONMISMATCH, DomUtil.createQName(elem, elem.getTextContent()));
        assertEquals(500, post.getStatusCode());
        
        post.releaseConnection();
        container.deactivateComponent("http");
    }

    public void testHttpSoap12FaultOnEnvelope() throws Exception {
        initSoapEndpoints(true);

        PostMethod post = new PostMethod("http://localhost:"+port1+"/ep2/");
        post.setRequestEntity(new StringRequestEntity("<hello>world</hello>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        logger.info(res);
        Element elem = transformer.toDOMElement(new StringSource(res));
        assertEquals(Soap12.getInstance().getEnvelope(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap12.getInstance().getBody(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap12.getInstance().getFault(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(SoapConstants.SOAP_12_FAULTCODE, DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(SoapConstants.SOAP_12_FAULTVALUE, DomUtil.getQName(elem));
        assertEquals(SoapConstants.SOAP_12_CODE_SENDER, DomUtil.createQName(elem, elem.getTextContent()));
        elem = DomUtil.getNextSiblingElement(elem);
        assertEquals(SoapConstants.SOAP_12_FAULTSUBCODE, DomUtil.getQName(elem));
        assertEquals(SoapConstants.SOAP_12_CODE_VERSIONMISMATCH, DomUtil.createQName(elem, elem.getTextContent()));
        assertEquals(500, post.getStatusCode());
        
        post.releaseConnection();
        container.deactivateComponent("http");
    }

    public void testHttpSoap11UnkownOp() throws Exception {
        initSoapEndpoints(true);

        PostMethod post = new PostMethod("http://localhost:"+port1+"/ep1/");
        post.setRequestEntity(new StringRequestEntity(
                        "<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'>"
                                        + "<s:Body><hello>world</hello></s:Body>" + "</s:Envelope>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        logger.info(res);
        Element elem = transformer.toDOMElement(new StringSource(res));
        assertEquals(Soap11.getInstance().getEnvelope(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap11.getInstance().getBody(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap11.getInstance().getFault(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(SoapConstants.SOAP_11_FAULTCODE, DomUtil.getQName(elem));
        assertEquals(SoapConstants.SOAP_11_CODE_CLIENT, DomUtil.createQName(elem, elem.getTextContent()));
        assertEquals(500, post.getStatusCode());
        
        post.releaseConnection();
        container.deactivateComponent("http");
    }

    /*
     * public void testHttpSoapAttachments() throws Exception { initSoapEndpoints(true);
     * 
     * HttpComponent http = new HttpComponent(); HttpEndpoint ep0 = new HttpEndpoint(); ep0.setService(new
     * QName("urn:test", "s0")); ep0.setEndpoint("ep0"); ep0.setLocationURI("http://localhost:"+port1+"/ep1/");
     * ep0.setRoleAsString("provider"); ep0.setSoapVersion("1.1"); ep0.setSoap(true); http.setEndpoints(new
     * HttpEndpoint[] { ep0 }); container.activateComponent(http, "http2");
     * 
     * MockServiceComponent echo = new MockServiceComponent(); echo.setService(new QName("urn:test", "echo"));
     * echo.setEndpoint("endpoint"); echo.setResponseXml("<jbi:message
     * xmlns:jbi='http://java.sun.com/xml/ns/jbi/wsdl-11-wrapper'><jbi:part><HelloResponse xmlns='uri:HelloWorld' />
     * </jbi:part></jbi:message>");
     * container.activateComponent(echo, "echo");
     * 
     * DefaultServiceMixClient client = new DefaultServiceMixClient(container); Destination d =
     * client.createDestination("service:urn:test:s0"); InOut me = d.createInOutExchange();
     * me.getInMessage().setContent(new StringSource("<HelloRequest xmlns='uri:HelloWorld'/>")); Map<QName,
     * DocumentFragment> headers = new HashMap<QName, DocumentFragment>(); Document doc = DOMUtil.newDocument();
     * DocumentFragment fragment = doc.createDocumentFragment(); DomUtil.createElement(fragment, new
     * QName("uri:HelloWorld", "HelloHeader")); headers.put(new QName("uri:HelloWorld", "HelloHeader"), fragment);
     * me.getInMessage().setProperty(org.apache.servicemix.JbiConstants.SOAP_HEADERS, headers); File f = new
     * File(getClass().getResource("servicemix.jpg").getFile()); ByteArrayOutputStream baos = new
     * ByteArrayOutputStream(); FileUtil.copyInputStream(new FileInputStream(f), baos); DataSource ds = new
     * ByteArrayDataSource(baos.toByteArray(), "image/jpeg"); DataHandler dh = new DataHandler(ds);
     * me.getInMessage().addAttachment("image", dh); client.sendSync(me); assertEquals(ExchangeStatus.ACTIVE,
     * me.getStatus()); assertNull(me.getFault()); assertEquals(1, me.getOutMessage().getAttachmentNames().size());
     * client.done(me); }
     */

    public void testHttpSoap11() throws Exception {
        initSoapEndpoints(true);

        MockServiceComponent echo = new MockServiceComponent();
        echo.setService(new QName("urn:test", "echo"));
        echo.setEndpoint("endpoint");
        echo.setResponseXml("<jbi:message xmlns:jbi='http://java.sun.com/xml/ns/jbi/wsdl-11-wrapper'>"
                        + "<jbi:part><HelloResponse xmlns='uri:HelloWorld' /></jbi:part>" + "</jbi:message>");
        container.activateComponent(echo, "echo");

        PostMethod post = new PostMethod("http://localhost:"+port1+"/ep1/");
        post.setRequestEntity(new StringRequestEntity(
                        "<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'>"
                                        + "<s:Header><HelloHeader xmlns='uri:HelloWorld'/></s:Header>"
                                        + "<s:Body><HelloRequest xmlns='uri:HelloWorld'>world</HelloRequest></s:Body>"
                                        + "</s:Envelope>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        logger.info(res);
        Element elem = transformer.toDOMElement(new StringSource(res));
        assertEquals(Soap11.getInstance().getEnvelope(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap11.getInstance().getBody(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(new QName("uri:HelloWorld", "HelloResponse"), DomUtil.getQName(elem));
        assertEquals(200, post.getStatusCode());
        
        post.releaseConnection();
        container.deactivateComponent("echo");
        container.deactivateComponent("http");
    }

    public void testHttpSoap12() throws Exception {
        initSoapEndpoints(true);

        TransformComponentSupport mock = new TransformComponentSupport() {
            protected boolean transform(MessageExchange exchange, NormalizedMessage in, NormalizedMessage out)
                throws MessagingException {
                Element elem;
                try {
                    elem = transformer.toDOMElement(in.getContent());
                    ConsumerEndpointTest.this.logger.info(transformer.toString(elem));
                } catch (Exception e) {
                    throw new MessagingException(e);
                }
                assertEquals(JbiConstants.WSDL11_WRAPPER_MESSAGE, DomUtil.getQName(elem));
                out.setContent(
                         new StringSource("<jbi:message xmlns:jbi='http://java.sun.com/xml/ns/jbi/wsdl-11-wrapper'>"
                                   + "<jbi:part><HelloResponse xmlns='uri:HelloWorld'>world</HelloResponse></jbi:part>"
                                   + "</jbi:message> "));
                return true;
            }
        };
        mock.setService(new QName("urn:test", "echo"));
        mock.setEndpoint("endpoint");
        container.activateComponent(mock, "mock");

        PostMethod post = new PostMethod("http://localhost:"+port1+"/ep2/");
        post.setRequestEntity(
                        new StringRequestEntity("<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope'>"
                                + "<s:Header><HelloHeader xmlns='uri:HelloWorld'/></s:Header>"
                                + "<s:Body><HelloRequest xmlns='uri:HelloWorld'>world</HelloRequest></s:Body>"
                                + "</s:Envelope>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        logger.info(res);
        Element elem = transformer.toDOMElement(new StringSource(res));
        assertEquals(Soap12.getInstance().getEnvelope(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap12.getInstance().getBody(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(new QName("uri:HelloWorld", "HelloResponse"), DomUtil.getQName(elem));
        assertEquals(200, post.getStatusCode());
        
        post.releaseConnection();
        container.deactivateComponent("mock");
        container.deactivateComponent("http");
    }

    public void testHttpSoap12WithoutJbiWrapper() throws Exception {
        initSoapEndpoints(false);

        TransformComponentSupport mock = new TransformComponentSupport() {
            protected boolean transform(MessageExchange exchange, NormalizedMessage in, NormalizedMessage out)
                throws MessagingException {
                Element elem;
                try {
                    elem = transformer.toDOMElement(in.getContent());
                    ConsumerEndpointTest.this.logger.info(transformer.toString(elem));
                } catch (Exception e) {
                    throw new MessagingException(e);
                }
                assertEquals(new QName("uri:HelloWorld", "HelloRequest"), DomUtil.getQName(elem));
                out.setContent(new StringSource("<HelloResponse xmlns='uri:HelloWorld'>world</HelloResponse>"));
                return true;
            }
        };
        mock.setCopyProperties(false);
        mock.setService(new QName("urn:test", "echo"));
        mock.setEndpoint("endpoint");
        container.activateComponent(mock, "mock");

        PostMethod post = new PostMethod("http://localhost:"+port1+"/ep2/");
        post.setRequestEntity(
                        new StringRequestEntity("<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope'>"
                                + "<s:Header><HelloHeader xmlns='uri:HelloWorld'/></s:Header>"
                                + "<s:Body><HelloRequest xmlns='uri:HelloWorld'>world</HelloRequest></s:Body>"
                                + "</s:Envelope>"));
        new HttpClient().executeMethod(post);
        String res = post.getResponseBodyAsString();
        logger.info(res);
        Element elem = transformer.toDOMElement(new StringSource(res));
        assertEquals(Soap12.getInstance().getEnvelope(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap12.getInstance().getBody(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(new QName("uri:HelloWorld", "HelloResponse"), DomUtil.getQName(elem));
        assertEquals(200, post.getStatusCode());
        
        post.releaseConnection();
        container.deactivateComponent("mock");
        container.deactivateComponent("http");
    }

    public void testGzipEncodingNonSoap() throws Exception {
        HttpComponent http = new HttpComponent();
        HttpConsumerEndpoint ep = new HttpConsumerEndpoint();
        ep.setService(new QName("urn:test", "svc"));
        ep.setEndpoint("ep");
        ep.setTargetService(new QName("urn:test", "echo"));
        ep.setLocationURI("http://localhost:"+port1+"/ep1/");
        http.setEndpoints(new HttpEndpointType[] {ep });
        container.activateComponent(http, "http");

        EchoComponent echo = new EchoComponent();
        echo.setService(new QName("urn:test", "echo"));
        echo.setEndpoint("endpoint");
        container.activateComponent(echo, "echo");

        container.start();

        PostMethod post = new PostMethod("http://localhost:"+port1+"/ep1/");
        post.addRequestHeader(HttpHeaders.CONTENT_ENCODING, "gzip");
        post.addRequestHeader(HttpHeaders.ACCEPT_ENCODING, "gzip");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gos = new GZIPOutputStream(baos);
        gos.write("<hello>world</hello>".getBytes());
        gos.flush();
        gos.close();

        post.setRequestEntity(new ByteArrayRequestEntity(baos.toByteArray()));
        new HttpClient().executeMethod(post);

        GZIPInputStream gis = new GZIPInputStream(post.getResponseBodyAsStream());
        BufferedReader br = new BufferedReader(new InputStreamReader(gis));

        String result = br.readLine();
        logger.info(result);
        Node node = transformer.toDOMNode(new StringSource(result));
        logger.info(transformer.toString(node));
        assertEquals("world", textValueOfXPath(node, "/hello/text()"));
        if (post.getStatusCode() != 200) {
            throw new InvalidStatusResponseException(post.getStatusCode());
        }
        
        post.releaseConnection();
        container.deactivateComponent("echo");
        container.deactivateComponent("http");
    }

    public void testGzipEncodingSoap() throws Exception {
        initSoapEndpoints(true);

        TransformComponentSupport mock = new TransformComponentSupport() {
            protected boolean transform(MessageExchange exchange, NormalizedMessage in, NormalizedMessage out)
                throws MessagingException {
                Element elem;
                try {
                    elem = transformer.toDOMElement(in.getContent());
                    ConsumerEndpointTest.this.logger.info(transformer.toString(elem));
                } catch (Exception e) {
                    throw new MessagingException(e);
                }
                assertEquals(JbiConstants.WSDL11_WRAPPER_MESSAGE, DomUtil.getQName(elem));
                out.setContent(
                    new StringSource("<jbi:message xmlns:jbi='http://java.sun.com/xml/ns/jbi/wsdl-11-wrapper'>"
                        + "<jbi:part><HelloResponse xmlns='uri:HelloWorld'>world</HelloResponse></jbi:part>"
                        + "</jbi:message> "));
                return true;
            }
        };
        mock.setService(new QName("urn:test", "echo"));
        mock.setEndpoint("endpoint");
        container.activateComponent(mock, "mock");

        PostMethod post = new PostMethod("http://localhost:"+port1+"/ep2/");

        post.addRequestHeader(HttpHeaders.CONTENT_ENCODING, "gzip");
        post.addRequestHeader(HttpHeaders.ACCEPT_ENCODING, "gzip");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gos = new GZIPOutputStream(baos);

        gos.write(("<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope'>"
            + "<s:Header><HelloHeader xmlns='uri:HelloWorld'/></s:Header>"
            + "<s:Body><HelloRequest xmlns='uri:HelloWorld'>world</HelloRequest></s:Body>"
            + "</s:Envelope>").getBytes());
        gos.flush();
        gos.close();

        post.setRequestEntity(new ByteArrayRequestEntity(baos.toByteArray()));
        new HttpClient().executeMethod(post);

        GZIPInputStream gis = new GZIPInputStream(post.getResponseBodyAsStream());
        BufferedReader br = new BufferedReader(new InputStreamReader(gis));

        String result = br.readLine();
        logger.info(result);
        Element elem = transformer.toDOMElement(new StringSource(result));
        assertEquals(Soap12.getInstance().getEnvelope(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(Soap12.getInstance().getBody(), DomUtil.getQName(elem));
        elem = DomUtil.getFirstChildElement(elem);
        assertEquals(new QName("uri:HelloWorld", "HelloResponse"), DomUtil.getQName(elem));
        assertEquals(200, post.getStatusCode());
        
        post.releaseConnection();
        container.deactivateComponent("mock");
        container.deactivateComponent("http");
    }

    /*
     * Load testing test
     */
    public void testHttpInOutUnderLoad() throws Exception {
        final int nbThreads = 16;
        final int nbRequests = 8;
        final int endpointTimeout = 100;
        final int echoSleepTime = 120;
        final int soTimeout = 60 * 1000 * 1000;
        final int listenerTimeout = 15000;

        ExchangeCompletedListener listener = new ExchangeCompletedListener(listenerTimeout);
        container.addListener(listener);

        HttpComponent http = new HttpComponent();
        //http.getConfiguration().setJettyConnectorClassName(SocketConnector.class.getName());
        HttpConsumerEndpoint ep = new HttpConsumerEndpoint();
        ep.setService(new QName("urn:test", "svc"));
        ep.setEndpoint("ep");
        ep.setTargetService(new QName("urn:test", "echo"));
        ep.setLocationURI("http://localhost:"+port1+"/ep1/");
        ep.setTimeout(endpointTimeout);
        http.setEndpoints(new HttpEndpointType[] {ep});
        container.activateComponent(http, "http");

        final CountDownLatch latchRecv = new CountDownLatch(nbThreads * nbRequests);
        EchoComponent echo = new EchoComponent() {
            protected boolean transform(MessageExchange exchange, NormalizedMessage in, NormalizedMessage out) throws MessagingException {
                latchRecv.countDown();
                try {
                    Thread.sleep(echoSleepTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
                out.setContent(in.getContent());
                return true;
            }
        };
        echo.setService(new QName("urn:test", "echo"));
        echo.setEndpoint("endpoint");
        container.activateComponent(echo, "echo");
        
        ((ExecutorFactoryImpl) container.getExecutorFactory()).getDefaultConfig().setMaximumPoolSize(16);

        container.start();

        final List<Throwable> throwables = new CopyOnWriteArrayList<Throwable>();
        final CountDownLatch latchSent = new CountDownLatch(nbThreads * nbRequests);
        for (int t = 0; t < nbThreads; t++) {
            new Thread() {
                public void run() {
                    final SourceTransformer transformer = new SourceTransformer(); 
                    final HttpClient client = new HttpClient();
                    client.getParams().setSoTimeout(soTimeout);
                    for (int i = 0; i < nbRequests; i++) {
                    	PostMethod post = null;
                        try {
                            post = new PostMethod("http://localhost:"+port1+"/ep1/");
                            post.setRequestEntity(new StringRequestEntity("<hello>world</hello>"));
                            client.executeMethod(post);
                            if (post.getStatusCode() != 200) {
                                throw new InvalidStatusResponseException(post.getStatusCode());
                            }
                            Node node = transformer.toDOMNode(new StreamSource(post.getResponseBodyAsStream()));
                            logger.info(transformer.toString(node));
                            assertEquals("world", textValueOfXPath(node, "/hello/text()"));
                        } catch (Throwable t) {
                            throwables.add(t);
                        } finally {
                            latchSent.countDown();
                            if (post != null) {
                            	post.releaseConnection();
                            }
//                            System.out.println("[" + System.currentTimeMillis() + "] Request " + latchSent.getCount() + " processed");
                        }
                    }
                }
            }.start();
        }
        latchSent.await();
        latchRecv.await();
        listener.assertExchangeCompleted();
        for (Throwable t : throwables) {
            t.printStackTrace();
        }        
        
        container.deactivateComponent("echo");
        container.deactivateComponent("http");
    }

    public void testProxyWsl() throws Exception {
        final Document wsdl = DomUtil.parse(getClass().getResourceAsStream("/org/apache/servicemix/http/Echo.wsdl"));
        EchoComponent echo = new EchoComponent() {
            @Override
            public Document getServiceDescription(ServiceEndpoint endpoint) {
                return wsdl;
            }
        };
        echo.setService(new QName("http://test", "MyConsumerService"));
        echo.setEndpoint("myConsumer");
        container.activateComponent(echo, "echo");

        HttpComponent http = new HttpComponent();
        HttpSoapConsumerEndpoint ep1 = new HttpSoapConsumerEndpoint();
        ep1.setService(new QName("uri:HelloWorld", "HelloService"));
        ep1.setEndpoint("HelloPortSoap11");
        ep1.setTargetService(new QName("http://test", "MyConsumerService"));
        ep1.setLocationURI("http://localhost:"+port2+"/ep1/");
        ep1.setValidateWsdl(true);
        http.setEndpoints(new HttpEndpointType[] {ep1});
        container.activateComponent(http, "http");
        container.start();

        WSDLFactory factory = WSDLFactory.newInstance();
        WSDLReader reader = factory.newWSDLReader();
        Definition def = reader.readWSDL("http://localhost:"+port2+"/ep1/?wsdl");
        StringWriter writer = new StringWriter();
        factory.newWSDLWriter().writeWSDL(def, writer);
        logger.info(writer.toString());
        Binding b = (Binding) def.getBindings().values().iterator().next();
        BindingOperation bop = (BindingOperation) b.getBindingOperations().iterator().next();
        assertEquals(1, bop.getExtensibilityElements().size());
        ExtensibilityElement ee = (ExtensibilityElement) bop.getExtensibilityElements().iterator().next();
        assertTrue(ee instanceof SOAPOperation);
        assertEquals("", ((SOAPOperation) ee).getSoapActionURI());
        
        container.deactivateComponent("echo");
        container.deactivateComponent("http");
    }
    
    public void testProvidedWsdlWithDynamicAddress() throws Exception {
    	initSoapEndpoints(true);

    	checkAddress("http://127.0.0.1:"+port1+"/ep1/", "http://127.0.0.1:"+port1+"/ep1/?wsdl", 11);
    	checkAddress("http://127.0.0.1:"+port1+"/ep2/", "http://127.0.0.1:"+port1+"/ep2/?wsdl", 12);
    	
        container.deactivateComponent("http");
    }
    
    public void testProvidedWsdlWithStaticAddress() throws Exception {
    	initSoapEndpoints(true, false);

    	checkAddress("http://localhost:"+port1+"/ep1/", "http://127.0.0.1:"+port1+"/ep1/?wsdl", 11);
    	checkAddress("http://localhost:"+port1+"/ep2/", "http://127.0.0.1:"+port1+"/ep2/?wsdl", 12);
    	
        container.deactivateComponent("http");
    }
    
	private void checkAddress(String expected, String wsdlUrl, int soapVersion)
			throws Exception {
		WSDLFactory factory = WSDLFactory.newInstance();
		WSDLReader reader = factory.newWSDLReader();
		Definition def = reader.readWSDL(wsdlUrl);

		Service serv = def.getService(new QName("uri:HelloWorld", "HelloService"));

		ExtensibilityElement ee;

		switch (soapVersion) {
		case 11:
			Port port11 = serv.getPort("HelloPortSoap11");
			ee = (ExtensibilityElement) port11.getExtensibilityElements().iterator().next();
			assertTrue(ee instanceof SOAPAddress);
			assertEquals(expected, ((SOAPAddress) ee).getLocationURI());
			break;
		case 12:
			Port port12 = serv.getPort("HelloPortSoap12");
			ee = (ExtensibilityElement) port12.getExtensibilityElements().iterator().next();
			assertTrue(ee instanceof SOAP12Address);
			assertEquals(expected, ((SOAP12Address) ee).getLocationURI());
		}
	}

}
