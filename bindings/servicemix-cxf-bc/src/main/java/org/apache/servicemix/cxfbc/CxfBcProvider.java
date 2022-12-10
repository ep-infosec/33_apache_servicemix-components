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

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.MessagingException;
import javax.jbi.messaging.NormalizedMessage;
import javax.wsdl.WSDLException;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;

import org.apache.cxf.phase.PhaseChainCache;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.ibm.wsdl.Constants;

import org.apache.cxf.Bus;

import org.apache.cxf.binding.soap.Soap12;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.SoapVersion;
import org.apache.cxf.binding.soap.interceptor.ReadHeadersInterceptor;
import org.apache.cxf.binding.soap.interceptor.SoapOutInterceptor;
import org.apache.cxf.binding.soap.interceptor.SoapPreProtocolOutInterceptor;
import org.apache.cxf.binding.soap.model.SoapBindingInfo;
import org.apache.cxf.binding.soap.model.SoapBodyInfo;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.catalog.OASISCatalogManager;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.ClientImpl;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.endpoint.EndpointImpl;
import org.apache.cxf.feature.AbstractFeature;
import org.apache.cxf.interceptor.AttachmentOutInterceptor;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.interceptor.StaxInInterceptor;
import org.apache.cxf.interceptor.StaxOutInterceptor;

import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.apache.cxf.phase.PhaseManager;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.model.BindingMessageInfo;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.service.model.OperationInfo;
import org.apache.cxf.service.model.ServiceInfo;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.ConduitInitiator;
import org.apache.cxf.transport.ConduitInitiatorManager;
import org.apache.cxf.wsdl.WSDLManager;
import org.apache.cxf.wsdl11.ServiceWSDLBuilder;
import org.apache.cxf.wsdl11.WSDLServiceBuilder;
import org.apache.cxf.wsdl11.WSDLServiceFactory;
import org.apache.servicemix.common.JbiConstants;
import org.apache.servicemix.common.endpoints.ProviderEndpoint;
import org.apache.servicemix.cxfbc.interceptors.JbiOutInterceptor;
import org.apache.servicemix.cxfbc.interceptors.JbiOutWsdl1Interceptor;
import org.apache.servicemix.cxfbc.interceptors.MtomCheckInterceptor;
import org.apache.servicemix.cxfbc.interceptors.JbiFault;
import org.apache.servicemix.cxfbc.interceptors.SchemaValidationOutInterceptor;
import org.apache.servicemix.cxfbc.interceptors.CxfJbiConstants;
import org.apache.servicemix.jbi.jaxp.SourceTransformer;
import org.apache.servicemix.soap.util.DomUtil;
import org.springframework.core.io.Resource;

/**
 * 
 * @author gnodet
 * @org.apache.xbean.XBean element="provider" description="a provider endpoint that is capable of exposing SOAP/HTTP or SOAP/JMS services"
 */
public class CxfBcProvider extends ProviderEndpoint implements
        CxfBcEndpointWithInterceptor {

   
    List<Interceptor<? extends Message>> in = new CopyOnWriteArrayList<Interceptor<? extends Message>>();

    List<Interceptor<? extends Message>> out = new CopyOnWriteArrayList<Interceptor<? extends Message>>();

    List<Interceptor<? extends Message>> outFault = new CopyOnWriteArrayList<Interceptor<? extends Message>>();

    List<Interceptor<? extends Message>> inFault = new CopyOnWriteArrayList<Interceptor<? extends Message>>();

    private Resource wsdl;

    private String busCfg;

    private Bus bus;
    
    private Bus providedBus;

    private ConduitInitiator conduitInit;
    private Conduit conduit;

    private URI locationURI;

    private EndpointInfo ei;

    private Endpoint ep;

    private Service cxfService;

    private boolean mtomEnabled;

    private boolean useJBIWrapper = true;
    
    private boolean useSOAPEnvelope = true;
    
    private boolean synchronous = true;
  
    private boolean schemaValidationEnabled;
 
    private List<AbstractFeature> features = new CopyOnWriteArrayList<AbstractFeature>();
    
    private Map<String, Object> properties = new ConcurrentHashMap<String, Object>();    

    private List<Interceptor<? extends Message>> outList;

    private PhaseChainCache cache = new PhaseChainCache();


    @Override
    public void activate() throws Exception {
        super.activate();
        outList = new ArrayList<Interceptor<? extends Message>>();
        if (isMtomEnabled()) {
            outList.add(new JbiOutInterceptor());
            outList.add(new MtomCheckInterceptor(true));
            outList.add(new AttachmentOutInterceptor());
        }

        outList.add(new JbiOutInterceptor());
        if (isSchemaValidationEnabled()) {
            outList.add(new SchemaValidationOutInterceptor(isUseJBIWrapper(), isUseSOAPEnvelope()));
        }
        outList.add(new JbiOutWsdl1Interceptor(isUseJBIWrapper(), isUseSOAPEnvelope()));
        outList.add(new SoapPreProtocolOutInterceptor());
        outList.add(new SoapOutInterceptor(getBus()));
        outList.add(new StaxOutInterceptor());
    }

    public void processExchange(MessageExchange exchange) {

    }

    public void process(MessageExchange exchange) throws Exception {

        if (exchange.getStatus() != ExchangeStatus.ACTIVE) {
            return;
        }
        NormalizedMessage nm = exchange.getMessage("in");

        Object newDestinationURI = nm.getProperty(JbiConstants.HTTP_DESTINATION_URI);
        
        
        Message message = ep.getBinding().createMessage();
        if (newDestinationURI != null) {
            ei.setAddress((String) newDestinationURI);
            message.put(Message.ENDPOINT_ADDRESS, newDestinationURI);
        }
        message.put(MessageExchange.class, exchange);
        Exchange cxfExchange = new ExchangeImpl();
        cxfExchange.setConduit(conduit);
        cxfExchange.setSynchronous(isSynchronous());
        cxfExchange.put(MessageExchange.class, exchange);
        
        message.setExchange(cxfExchange);
        cxfExchange.setOutMessage(message);
        cxfExchange.put(CxfBcProvider.class, this);

        QName opeName = exchange.getOperation();
        BindingOperationInfo boi = null;
        if (opeName == null) {
            // if interface only have one operation, may not specify the opeName
            // in MessageExchange
            if (ei.getBinding().getOperations().size() == 1) {
                boi = ei.getBinding().getOperations().iterator().next();
            } else {
                boi = findOperation(nm, message, boi, exchange);
                cxfExchange.put(MessageExchange.class, exchange);
            }
        } else {
            boi = ei.getBinding().getOperation(exchange.getOperation());
        }
        cxfExchange.setOneWay(boi.getOperationInfo().isOneWay());
        cxfExchange.put(BindingOperationInfo.class, boi);
        cxfExchange.put(Endpoint.class, ep);
        cxfExchange.put(Service.class, cxfService);
        cxfExchange.put(Bus.class, getBus());
        PhaseManager pm = getBus().getExtension(PhaseManager.class);

        
        PhaseInterceptorChain outChain = cache.get(pm.getOutPhases(), 
                                                   outList,
                                                   getBus().getOutInterceptors(),
                                                   getBus().getOutFaultInterceptors(),
                                                   getOutInterceptors(),
                                                   getOutFaultInterceptors());

        message.setInterceptorChain(outChain);
        message.setContent(Source.class, nm.getContent());

        conduit.prepare(message);
        
        message.put(org.apache.cxf.message.Message.REQUESTOR_ROLE, true);
        try {
            outChain.doIntercept(message);
            //Check to see if there is a Fault from the outgoing chain
            Exception ex = message.getContent(Exception.class);
            if (ex != null) {
                throw ex;
            }
            ex = message.getExchange().get(Exception.class);
            if (ex != null) {
                throw ex;
            }
            
            conduit.close(message);
        } catch (Exception e) {
            if (!(exchange instanceof InOnly)) {
        	faultProcess(exchange, message, e);
            }
        }
        if (boi.getOperationInfo().isOneWay()) {
            exchange.setStatus(ExchangeStatus.DONE);
            this.getChannel().send(exchange);
        }
    }

    private void faultProcess(MessageExchange exchange, Message message, Exception e) throws MessagingException {
        javax.jbi.messaging.Fault fault = exchange.createFault();
        if (e.getCause() != null) {
            if (isUseJBIWrapper()) {
                handleJBIFault(message, e.getCause().getMessage());
            } else {
                handleSoapFault(message, e.getCause().getMessage(), exchange);
            }
        } else {
            if (isUseJBIWrapper()) {
                handleJBIFault(message, e.getMessage());
            } else {
                handleSoapFault(message, e.getMessage(), exchange);
            }
        }        
        fault.setContent(message.getContent(Source.class));
        exchange.setFault(fault);
        boolean txSync = exchange.getStatus() == ExchangeStatus.ACTIVE
                && exchange.isTransacted()
                && Boolean.TRUE.equals(exchange
                        .getProperty(JbiConstants.SEND_SYNC));
        if (txSync) {
            getContext().getDeliveryChannel().sendSync(exchange);
        } else {
            getContext().getDeliveryChannel().send(exchange);
        }
    }

   
    private void handleSoapFault(Message message, String faultMessage, MessageExchange exchange) {
        Document document = DomUtil.createDocument();
        SoapVersion soapVersion = ((SoapMessage)message).getVersion();
        
        
        if (isUseSOAPEnvelope()) {
            Element soapEnv = DomUtil.createElement(document, new QName(
                soapVersion.getEnvelope().getNamespaceURI(), soapVersion
                        .getEnvelope().getLocalPart(), "soap"));
            Element soapBody = DomUtil.createElement(soapEnv, new QName(
                soapVersion.getBody().getNamespaceURI(), soapVersion
                        .getBody().getLocalPart(), "soap"));
                                  
            soapEnv.appendChild(soapBody);
            Element faultBody = DomUtil.createElement(soapBody, new QName(
                                    soapVersion.getEnvelope().getNamespaceURI(),
                                    "Fault", "soap"));
            Element faultCode = DomUtil.createElement(faultBody, 
                                                      new QName("", "faultcode"));
            faultCode.setTextContent("soap:Client");
            Element faultString = DomUtil.createElement(faultBody, 
                                                      new QName("", "faultstring"));
            faultString.setTextContent(faultMessage);
            Element faultDetail = null;
            if (soapVersion instanceof Soap12) {
                faultDetail = DomUtil.createElement(faultBody, new QName(
                                        soapVersion.getBody().getNamespaceURI(), 
                                        "Detail", "soap"));
            } else {
                faultDetail = DomUtil.createElement(faultBody, new QName(
                                        soapVersion.getBody().getNamespaceURI(), 
                                        "detail"));
            }
            faultDetail.setTextContent(faultMessage);
            /*exchange.setProperty("faultcode", 
                                 new QName(soapVersion.getEnvelope().getNamespaceURI(), "Client"));
            exchange.setProperty("faultstring", faultMessage);*/
                   
        } else {
            Element faultBody = DomUtil.createElement(document, new QName(
                                    soapVersion.getEnvelope().getNamespaceURI(),
                                    "Fault", "soap"));
            Element faultCode = DomUtil.createElement(faultBody, 
                                                      new QName("", "faultcode"));
            faultCode.setTextContent("soap:Client");
            Element faultString = DomUtil.createElement(faultBody, 
                                                      new QName("", "faultstring"));
            faultString.setTextContent(faultMessage);
            Element faultDetail = null;
            if (soapVersion instanceof Soap12) {
                faultDetail = DomUtil.createElement(faultBody, new QName(
                                        soapVersion.getBody().getNamespaceURI(), 
                                        "Detail", "soap"));
            } else {
                faultDetail = DomUtil.createElement(faultBody, new QName(
                                        "", 
                                        "detail"));
            }
            faultDetail.setTextContent(faultMessage);
            /*exchange.setProperty("faultcode", 
                                 new QName(soapVersion.getEnvelope().getNamespaceURI(), "Client"));
            exchange.setProperty("faultstring", faultMessage);*/
        }
        message.setContent(Source.class, new DOMSource(document));
    }

    private void handleJBIFault(Message message, String detail) {
        Document doc = DomUtil.createDocument();
        Element jbiFault = DomUtil.createElement(doc, new QName(
                CxfJbiConstants.WSDL11_WRAPPER_NAMESPACE, JbiFault.JBI_FAULT_ROOT));
        Node jbiFaultDetail = DomUtil.createElement(jbiFault, new QName("", JbiFault.JBI_FAULT_DETAIL));
        jbiFaultDetail.setTextContent(detail);
        jbiFault.appendChild(jbiFaultDetail);
        message.setContent(Source.class, new DOMSource(doc));
        message.put("jbiFault", true);
    }
    
    /**
        * Returns the list of interceptors used to process fault messages being
        * sent back to the consumer.
        *
        * @return a list of <code>Interceptor</code> objects
        * */
    public List<Interceptor<? extends Message>> getOutFaultInterceptors() {
        return outFault;
    }

    /**
        * Returns the list of interceptors used to process fault messages being
        * recieved by the endpoint.
        *
        * @return a list of <code>Interceptor</code> objects
        * */
    public List<Interceptor<? extends Message>> getInFaultInterceptors() {
        return inFault;
    }

    /**
        * Returns the list of interceptors used to process requests being 
        * recieved by the endpoint.
        *
        * @return a list of <code>Interceptor</code> objects
        * */
    public List<Interceptor<? extends Message>> getInInterceptors() {
        return in;
    }

    /**
        * Returns the list of interceptors used to process responses being
        * sent back to the consumer.
        *
        * @return a list of <code>Interceptor</code> objects
        * */
    public List<Interceptor<? extends Message>> getOutInterceptors() {
        return out;
    }

    /**
        * Specifies a list of interceptors used to process requests recieved
        * by the endpoint.
        *
        * @param interceptors   a list of <code>Interceptor</code> objects
        * @org.apache.xbean.Property description="a list of beans configuring interceptors that process incoming requests"
        * */
    public void setInInterceptors(List<Interceptor<? extends Message>> interceptors) {
        in.addAll(interceptors);
    }

    /**
        * Specifies a list of interceptors used to process faults recieved by
         * the endpoint.
        *
        * @param interceptors   a list of <code>Interceptor</code> objects
        * @org.apache.xbean.Property description="a list of beans configuring interceptors that process incoming faults"
        * */
    public void setInFaultInterceptors(List<Interceptor<? extends Message>> interceptors) {
        inFault.addAll(interceptors);
    }

    /**
        * Specifies a list of interceptors used to process responses sent by 
        * the endpoint.
        *
        * @param interceptors   a list of <code>Interceptor</code> objects
        * @org.apache.xbean.Property description="a list of beans configuring interceptors that process responses"
        * */
    public void setOutInterceptors(List<Interceptor<? extends Message>> interceptors) {
        out.addAll(interceptors);
    }

    /**
        * Specifies a list of interceptors used to process faults sent by 
        * the endpoint.
        *
        * @param interceptors   a list of <code>Interceptor</code> objects
        * @org.apache.xbean.Property description="a list of beans configuring interceptors that process fault messages being returned to the consumer"
        * */
    public void setOutFaultInterceptors(List<Interceptor<? extends Message>> interceptors) {
        outFault.addAll(interceptors);
    }

    /**
          * Specifies the location of the WSDL defining the endpoint's interface.
          *
          * @param wsdl the location of the WSDL contract as a <code>Resource</code> object
          * @org.apache.xbean.Property description="the location of the WSDL document defining the endpoint's interface"
          **/
    public void setWsdl(Resource wsdl) {
        this.wsdl = wsdl;
    }

    public Resource getWsdl() {
        return wsdl;
    }

    @Override
    public void validate() throws DeploymentException {
        try {
            if (definition == null) {
                if (wsdl == null) {
                    throw new DeploymentException("wsdl property must be set");
                }
                description = DomUtil.parse(wsdl.getInputStream());
                WSDLFactory wsdlFactory = WSDLFactory.newInstance();
                WSDLReader reader = wsdlFactory.newWSDLReader();
                reader.setFeature(Constants.FEATURE_VERBOSE, false);
                try {
                    //ensure the jax-ws-catalog is loaded
                    OASISCatalogManager.getCatalogManager(getBus()).loadContextCatalogs();
                    // use wsdl manager to parse wsdl or get cached definition
                    definition = getBus().getExtension(WSDLManager.class)
                            .getDefinition(wsdl.getURL());

                } catch (WSDLException ex) {
                    // 
                }
                WSDLServiceFactory factory = new WSDLServiceFactory(getBus(),
                        definition, service);
                cxfService = factory.create();
                ei = cxfService.getServiceInfos().iterator().next()
                        .getEndpoints().iterator().next();

                for (ServiceInfo serviceInfo : cxfService.getServiceInfos()) {
                    if (serviceInfo.getName().equals(service)
                            && getEndpoint() != null
                            && serviceInfo
                                    .getEndpoint(new QName(serviceInfo
                                            .getName().getNamespaceURI(),
                                            getEndpoint())) != null) {
                        ei = serviceInfo.getEndpoint(new QName(serviceInfo
                                .getName().getNamespaceURI(), getEndpoint()));

                    }
                }
                ServiceInfo serInfo = new ServiceInfo();

                Map<String, Element> schemaList = new HashMap<String, Element>();
                SchemaUtil schemaUtil = new SchemaUtil(bus, schemaList);
                schemaUtil.getSchemas(definition, serInfo);

                serInfo = ei.getService();
                List<ServiceInfo> serviceInfos = new ArrayList<ServiceInfo>();
                serviceInfos.add(serInfo);
                
                ServiceWSDLBuilder swBuilder = new ServiceWSDLBuilder(getBus(),
                        serviceInfos);
                
                serInfo.setProperty(WSDLServiceBuilder.WSDL_DEFINITION, null);
                serInfo.getInterface().setProperty(WSDLServiceBuilder.WSDL_PORTTYPE, null);
                for (OperationInfo opInfo : serInfo.getInterface().getOperations()) {
                    opInfo.setProperty(WSDLServiceBuilder.WSDL_OPERATION, null);
                }
                description = WSDLFactory.newInstance().newWSDLWriter()
                        .getDocument(swBuilder.build());
                if (endpoint == null) {
                    endpoint = ei.getName().getLocalPart();
                }
                

                ep = new EndpointImpl(getBus(), cxfService, ei);
                this.ep.putAll(this.getProperties());

                // init transport
                if (locationURI != null) {
                    ei.setAddress(locationURI.toString());
                }

                ConduitInitiatorManager conduitMgr = getBus().getExtension(
                        ConduitInitiatorManager.class);
                conduitInit = conduitMgr.getConduitInitiator(ei
                        .getTransportId());
                conduit = conduitInit.getConduit(ei);
                CxfBcProviderMessageObserver obs = new CxfBcProviderMessageObserver(this);
                conduit.setMessageObserver(obs);
                checkWSRMInterceptors();
                super.validate();
            }
        } catch (DeploymentException e) {
            throw e;
        } catch (Exception e) {
            throw new DeploymentException(e);
        }
    }

    private void checkWSRMInterceptors() {
        //to handle WS-RM requests and responses
        for (Interceptor interceptor : getBus().getOutInterceptors()) {
            if (interceptor.getClass().getName().equals("org.apache.cxf.ws.rm.RMOutInterceptor")) {
                ep.getOutInterceptors().add(new SoapOutInterceptor(getBus()));
                ep.getOutInterceptors().add(new StaxOutInterceptor());
                ep.getInInterceptors().add(new StaxInInterceptor());
                ep.getInInterceptors().add(new ReadHeadersInterceptor(getBus()));
                break;
            }
        }

    }


    private BindingOperationInfo findOperation(NormalizedMessage nm, 
                                               Message message, 
                                               BindingOperationInfo boi, 
                                               MessageExchange exchange) 
        throws TransformerException, ParserConfigurationException, IOException, SAXException {
        //try to figure out the operationName based on the incoming message
        //payload and wsdl if use doc/literal/wrapped
        Element element = new SourceTransformer().toDOMElement(nm.getContent());
        
        if (!useJBIWrapper) {
            SoapVersion soapVersion = ((SoapMessage)message).getVersion();                
            if (element != null) {                                                      
                Element bodyElement = (Element) element.getElementsByTagNameNS(
                        element.getNamespaceURI(),
                        soapVersion.getBody().getLocalPart()).item(0);
                if (bodyElement != null) {
                    element = DomUtil.getFirstChildElement(bodyElement);                           
                } 
            }
        } else {
            element = DomUtil.getFirstChildElement(DomUtil.getFirstChildElement(element));
        }
        
        QName opeName = new QName(element.getNamespaceURI(), element.getLocalName());
        SoapBindingInfo binding = (SoapBindingInfo) ei.getBinding();
        for (BindingOperationInfo op : binding.getOperations()) {
            String style = binding.getStyle(op.getOperationInfo());
            if (style == null) {
                style = binding.getStyle();
            }
            if ("document".equals(style)) {
                BindingMessageInfo msg = op.getInput();
                if (msg.getExtensor(SoapBodyInfo.class)
                            .getParts().get(0).getElementQName().equals(opeName)) {
                    boi = op;
                    exchange.setOperation(new QName(boi.getName().getNamespaceURI(), opeName.getLocalPart()));
                    break;
                }
            } else {
                throw new Fault(new Exception(
                    "Operation must bound on this MessageExchange if use rpc mode"));
            }
        }
        if (boi == null) {
            throw new Fault(new Exception(
                "Operation not bound on this MessageExchange"));
        }
        return boi;
    }

    @Override
    public void start() throws Exception {
        applyFeatures();
        super.start();

    }

    private void applyFeatures() {
        Client client = new ClientImpl(getBus(), ep, conduit);
        if (getFeatures() != null) {
            for (AbstractFeature feature : getFeatures()) {
                feature.initialize(client, getBus());
            }
        } 
    }

   
    
    protected Bus getBus() {
        if (providedBus != null) {
            return providedBus;
        } else if (getBusCfg() != null) {
            if (bus == null) {
                SpringBusFactory bf = new SpringBusFactory();
                bus = bf.createBus(getBusCfg());
            }
            return bus;
        } else {
            return ((CxfBcComponent) getServiceUnit().getComponent()).getBus();
        }
    }

    /**
        * Specifies the location of the CXF configuraiton file used to configure
        * the CXF bus. This allows you to access features like JMS runtime 
        * behavior and WS-RM.
        *
        * @param busCfg a string containing the relative path to the configuration file
        * @org.apache.xbean.Property description="the location of the CXF configuration file used to configure the CXF bus. This allows you to configure features like WS-RM and JMS runtime behavior."
        **/
    public void setBusCfg(String busCfg) {
        this.busCfg = busCfg;
    }

    public String getBusCfg() {
        return busCfg;
    }

    /**
           * Specifies the HTTP address of the exposed service. This value will
           * overide any value specified in the WSDL.
           *
           * @param locationURI a <code>URI</code> object
           * @org.apache.xbean.Property description="the HTTP address of the exposed service. This value will overide any value specified in the WSDL."
           **/
    public void setLocationURI(URI locationURI) {
        this.locationURI = locationURI;
    }

    public URI getLocationURI() {
        return locationURI;
    }

    

    Endpoint getCxfEndpoint() {
        return this.ep;
    }

    EndpointInfo getEndpointInfo() {
        return this.ei;
    }

    /**
          * Specifies if the endpoint can support binnary attachments.
          *
          * @param  mtomEnabled a boolean
          * @org.apache.xbean.Property description="Specifies if MTOM / attachment support is enabled. Default is <code>false</code>."
          **/
    public void setMtomEnabled(boolean mtomEnabled) {
        this.mtomEnabled = mtomEnabled;
    }

    public boolean isMtomEnabled() {
        return mtomEnabled;
    }

    /**
          * Specifies if the endpoint expects messages to use the JBI wrapper 
          * for SOAP messages.
          *
          * @param  useJBIWrapper a boolean
          * @org.apache.xbean.Property description="Specifies if the JBI wrapper is sent in the body of the message. Default is <code>true</code>.
          * 	Ignore the value of useSOAPEnvelope if useJBIWrapper is true"
          **/
    public void setUseJBIWrapper(boolean useJBIWrapper) {
        this.useJBIWrapper = useJBIWrapper;
    }

    public boolean isUseJBIWrapper() {
        return useJBIWrapper;
    }
    
    /**
     * Specifies if the endpoint expects soap messages when useJBIWrapper is false, 
     * if useJBIWrapper is true then ignore useSOAPEnvelope
     *
     * @org.apache.xbean.Property description="Specifies if the endpoint expects soap messages when useJBIWrapper is false, 
     * 				if useJBIWrapper is true then ignore useSOAPEnvelope. The  default is <code>true</code>.
     * */
	public void setUseSOAPEnvelope(boolean useSOAPEnvelope) {
		this.useSOAPEnvelope = useSOAPEnvelope;
	}

	public boolean isUseSOAPEnvelope() {
		return useSOAPEnvelope;
	}

    /**
     * Specifies if the endpoints send message synchronously to external server using underlying 
     * jms/http transport
     *
     *  * @param  synchronous a boolean
     * @org.apache.xbean.Property description="Specifies if the endpoints send message synchronously to external server using underlying 
     * jms/http transport. Default is <code>true</code>."
     **/
	public void setSynchronous(boolean synchronous) {
		this.synchronous = synchronous;
	}

	public boolean isSynchronous() {
		return synchronous;
	}
    
    /**
     * Specifies the cxf features set for this endpoint
     *
     * @param  features a list of <code>AbstractFeature</code> objects
     * @org.apache.xbean.Property description="Specifies the cxf features set for this endpoint"
     **/
    public void setFeatures(List<AbstractFeature> features) {
        this.features = features;
    }


    public List<AbstractFeature> getFeatures() {
        return features;
    }
    
    public boolean isSchemaValidationEnabled() {
        return schemaValidationEnabled;
    }

    /**
     * Specifies if the endpoint use schemavalidation for the incoming/outgoing message.
     * 
     * @param schemaValidationEnabled
     *            a boolean
     * @org.apache.xbean.Property description="Specifies if the endpoint use schemavalidation for the incoming/outgoing message.
     *  Default is <code>false</code>. "
     */

    public void setSchemaValidationEnabled(boolean schemaValidationEnabled) {
        this.schemaValidationEnabled = schemaValidationEnabled;
    }
    
    /**
     * Specifies a preconfigured CXF bus to use.
     *
     * @param providedBus   
     * @org.apache.xbean.Property description="a preconfigured CXF Bus object to use; overrides busCfg"
     * */
     public void setProvidedBus(Bus providedBus) {
         this.providedBus = providedBus;
     }
     
     public Bus getProvidedBus() {
         return this.providedBus;
     }
     
     /**
      * Sets arbitrary properties that are added to the CXF context at
      * the Endpoint level.
      *
      * @param properties
      *             the properties to add
      * @org.apache.xbean.Property description="Sets arbitrary properties that are added to the CXF context at the Endpoint level"             
      */
     public void setProperties(Map<String, Object> properties) {
         this.properties = properties;
     }
     
     public Map<String, Object> getProperties() {
         return this.properties;
     }
}
