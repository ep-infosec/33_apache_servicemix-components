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
package org.apache.servicemix.cxfbc.interceptors;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.SoapVersion;
import org.apache.cxf.binding.soap.interceptor.AbstractSoapInterceptor;
import org.apache.cxf.binding.soap.model.SoapBindingInfo;
import org.apache.cxf.binding.soap.model.SoapHeaderInfo;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.headers.Header;
import org.apache.cxf.helpers.NSStack;
import org.apache.cxf.helpers.XMLUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.service.model.BindingMessageInfo;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.service.model.MessagePartInfo;
import org.apache.cxf.staxutils.StaxUtils;
import org.apache.servicemix.jbi.jaxp.SourceTransformer;
import org.apache.servicemix.soap.interceptors.jbi.JbiConstants;
import org.apache.servicemix.soap.util.DomUtil;
import org.apache.servicemix.soap.util.QNameUtil;

/**
 * @author <a href="mailto:gnodet [at] gmail.com">Guillaume Nodet</a>
 */
public class JbiOutWsdl1Interceptor extends AbstractSoapInterceptor {
    
    private boolean useJBIWrapper = true;
    private boolean useSOAPWrapper = true;
    
    public JbiOutWsdl1Interceptor(boolean useJBIWrapper, boolean useSOAPWrapper) {
        super(Phase.MARSHAL);
        this.useJBIWrapper = useJBIWrapper;
        this.useSOAPWrapper = useSOAPWrapper;
    }

    public void handleMessage(SoapMessage message) {
        try {
            Source source = message.getContent(Source.class);
            if (source == null) {
                return;
            }
            
            Element element = new SourceTransformer().toDOMElement(source);
            XMLStreamWriter xmlWriter = message
                .getContent(XMLStreamWriter.class);
            
            if (!useJBIWrapper) {
                SoapVersion soapVersion = message.getVersion();                
                if (element != null) {                                                      
                    // if this message is coming from the CxfBCConsumer
                    Element bodyElement = null;
                    if (useSOAPWrapper) {
                    	bodyElement = (Element) element.getElementsByTagNameNS(
                            element.getNamespaceURI(),
                            soapVersion.getBody().getLocalPart()).item(0);
                    }
                    if (bodyElement != null) {
                        StaxUtils.writeElement(DomUtil.getFirstChildElement(bodyElement), xmlWriter, true);                           
                    } else {
                        // if this message is coming from the CxfBCProvider 
                        StaxUtils.writeElement(element, xmlWriter, true);
                    }
                }
                return;
            }
            
            if (!JbiConstants.WSDL11_WRAPPER_NAMESPACE.equals(element
                    .getNamespaceURI())
                    || !JbiConstants.WSDL11_WRAPPER_MESSAGE_LOCALNAME
                            .equals(element.getLocalName())) {
                throw new Fault(new Exception("Message wrapper element is '"
                        + QNameUtil.toString(element) + "' but expected '{"
                        + JbiConstants.WSDL11_WRAPPER_NAMESPACE + "}message'"));
            }
            BindingOperationInfo bop = message.getExchange().get(
                    BindingOperationInfo.class);
            if (bop == null) {
                throw new Fault(
                        new Exception("Operation not bound on this message"));
            }
            BindingMessageInfo msg = isRequestor(message) ? bop.getInput()
                    : bop.getOutput();

            
            SoapBindingInfo binding = (SoapBindingInfo) message.getExchange()
                    .get(Endpoint.class).getEndpointInfo().getBinding();
            String style = binding.getStyle(bop.getOperationInfo());
            if (style == null) {
                style = binding.getStyle();
            }

            if ("rpc".equals(style)) {
                addOperationNode(message, xmlWriter);
                getRPCPartWrapper(msg, element, message, xmlWriter);
            } else {
                Element partWrapper = DomUtil.getFirstChildElement(element);
                while (partWrapper != null) {
                    List<NodeList> partsContent = getPartsContent(message, element, partWrapper, msg); 
                    for (NodeList nl : partsContent) {
                        for (int i = 0; i < nl.getLength(); i++) {
                            Node n = nl.item(i);                            
                            StaxUtils.writeNode(n, xmlWriter, true);
                        }
                    }
                    partWrapper = DomUtil.getNextSiblingElement(partWrapper);
                }
            }

            if ("rpc".equals(style)) {
                xmlWriter.writeEndElement();
            }
        } catch (Fault e) {
            throw e;
        } catch (Exception e) {
            throw new Fault(e);
        }
    }

    
    private void getRPCPartWrapper(BindingMessageInfo msg, 
                                   Element element,
                                   SoapMessage message, 
                                   XMLStreamWriter xmlWriter) {
        try {
            List<MessagePartInfo> parts = msg.getMessageParts();
            Iterator iter = parts.iterator();
            Element partWrapper = DomUtil.getFirstChildElement(element);
            while (partWrapper != null) {
                MessagePartInfo msgPart = (MessagePartInfo) iter.next();
                String prefix = msgPart.getName().getPrefix();
                String name = msgPart.getName().getLocalPart();
                StaxUtils.writeStartElement(xmlWriter, prefix, name, "");
                List<NodeList> partsContent = getPartsContent(message, element,
                                                              partWrapper, msg);
                for (NodeList nl : partsContent) {
                    for (int i = 0; i < nl.getLength(); i++) {
                        Node n = nl.item(i);
                        StaxUtils.writeNode(n, xmlWriter, true);
                    }
                }
                xmlWriter.writeEndElement();
                partWrapper = DomUtil.getNextSiblingElement(partWrapper);
            }
        } catch (Fault e) {
            throw e;
        } catch (Exception e) {
            throw new Fault(e);
        }
    }
    
    // Get each parts content
    private List<NodeList> getPartsContent(SoapMessage message,
                                           Element element,
                                           Element partWrapper, 
                                           BindingMessageInfo msg) {
        List<NodeList> partsContent = new ArrayList<NodeList>();        
        if (partWrapper != null) {
            if (!JbiConstants.WSDL11_WRAPPER_NAMESPACE.equals(element.getNamespaceURI())
                    || !JbiConstants.WSDL11_WRAPPER_PART_LOCALNAME
                            .equals(partWrapper.getLocalName())) {
                throw new Fault(new Exception(
                        "Unexpected part wrapper element '"
                                + QNameUtil.toString(element)
                                + "' expected '{"
                                + JbiConstants.WSDL11_WRAPPER_NAMESPACE
                                + "}part'"));
            }
            NodeList nodes = partWrapper.getChildNodes();
            partsContent.add(nodes);
        }
            
        List<Header> headerList = message.getHeaders();
        List<SoapHeaderInfo> headers = msg.getExtensors(SoapHeaderInfo.class);
        for (SoapHeaderInfo header : headers) {
            if (partsContent.size() == 0) {
                break;
            }

            NodeList nl = partsContent.get(0);
            if (header.getPart().getConcreteName().getNamespaceURI().equals(nl.item(0).getNamespaceURI())
                    && header.getPart().getConcreteName().getLocalPart().equals(nl.item(0).getLocalName())) {
                headerList.add(new Header(header.getPart().getConcreteName(),
                        nl.item(0)));
                partsContent.remove(0);
            }
                       
        }
        
        return partsContent;
    }
    

    protected String addOperationNode(SoapMessage message, XMLStreamWriter xmlWriter)
        throws XMLStreamException {
        String responseSuffix = !isRequestor(message) ? "Response" : "";
        BindingOperationInfo boi = message.getExchange().get(
                BindingOperationInfo.class);
        String ns = boi.getName().getNamespaceURI();
        NSStack nsStack = new NSStack();
        nsStack.push();
        nsStack.add(ns);
        String prefix = nsStack.getPrefix(ns);
        StaxUtils.writeStartElement(xmlWriter, prefix, boi.getName()
                .getLocalPart()
                + responseSuffix, ns);
        return ns;
    }

}
