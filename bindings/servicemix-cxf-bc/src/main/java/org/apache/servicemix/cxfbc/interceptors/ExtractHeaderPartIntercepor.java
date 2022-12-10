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
import java.util.List;


import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.model.SoapBindingInfo;
import org.apache.cxf.binding.soap.model.SoapHeaderInfo;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.headers.Header;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.service.model.BindingMessageInfo;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.servicemix.jbi.jaxp.SourceTransformer;
import org.apache.servicemix.soap.interceptors.jbi.JbiConstants;
import org.apache.servicemix.soap.util.DomUtil;
import org.apache.servicemix.soap.util.QNameUtil;


public class ExtractHeaderPartIntercepor extends AbstractPhaseInterceptor<Message> {

    public ExtractHeaderPartIntercepor() {
        super(Phase.PRE_STREAM);
    }

    public void handleMessage(Message message) {
        
        extractHeaderFromMessagePart(message);
    }

    private void extractHeaderFromMessagePart(Message message) {
        Source source = message.getContent(Source.class);
        if (source == null) {
            return;
        }

        Element element;
        try {
            element = new SourceTransformer().toDOMElement(source);
        } catch (Exception e) {
            throw new Fault(e);
        }

        if (!JbiConstants.WSDL11_WRAPPER_NAMESPACE.equals(element
                .getNamespaceURI())
                || !JbiConstants.WSDL11_WRAPPER_MESSAGE_LOCALNAME
                        .equals(element.getLocalName())) {
            message.setContent(Source.class, new DOMSource(element));
            return;
        }

        BindingOperationInfo bop = message.getExchange().get(
                BindingOperationInfo.class);
        if (bop == null) {
            throw new Fault(
                    new Exception("Operation not bound on this message"));
        }
        BindingMessageInfo msg = isRequestor(message) ? bop.getInput() : bop
                .getOutput();

        SoapBindingInfo binding = (SoapBindingInfo) message.getExchange().get(
                Endpoint.class).getEndpointInfo().getBinding();
        String style = binding.getStyle(bop.getOperationInfo());
        if (style == null) {
            style = binding.getStyle();
        }

        Element partWrapper = DomUtil.getFirstChildElement(element);
        while (partWrapper != null) {
            extractHeaderParts((SoapMessage) message, element, partWrapper, msg);
            partWrapper = DomUtil.getNextSiblingElement(partWrapper);
        }
        message.setContent(Source.class, new DOMSource(element));
    }

    private void extractHeaderParts(SoapMessage message,
            Element element, Element partWrapper, BindingMessageInfo msg) {
        List<NodeList> partsContent = new ArrayList<NodeList>();
        if (partWrapper != null) {
            if (!JbiConstants.WSDL11_WRAPPER_NAMESPACE.equals(element
                    .getNamespaceURI())
                    || !JbiConstants.WSDL11_WRAPPER_PART_LOCALNAME
                            .equals(partWrapper.getLocalName())) {
                throw new Fault(new Exception(
                        "Unexpected part wrapper element '"
                                + QNameUtil.toString(element) + "' expected '{"
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
            if (header.getPart().getConcreteName().getNamespaceURI().equals(
                    nl.item(0).getNamespaceURI())
                    && header.getPart().getConcreteName().getLocalPart()
                            .equals(nl.item(0).getLocalName())) {
                headerList.add(new Header(header.getPart().getConcreteName(),
                        nl.item(0)));
                partsContent.remove(0);
            }

        }

    }

}
