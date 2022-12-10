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

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.jbi.JBIException;
import javax.jbi.component.ComponentContext;
import javax.jbi.messaging.DeliveryChannel;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.MessageExchangeFactory;
import javax.jbi.messaging.MessagingException;
import javax.jbi.messaging.NormalizedMessage;
import javax.security.auth.Subject;
import javax.xml.namespace.QName;
import javax.xml.transform.Source;

import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.headers.Header;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.ws.addressing.AddressingProperties;
import org.apache.servicemix.cxfbc.WSAUtils;
import org.apache.servicemix.soap.util.QNameUtil;

/**
 * @author <a href="mailto:gnodet [at] gmail.com">Guillaume Nodet</a>
 */
public class JbiInInterceptor extends AbstractPhaseInterceptor<Message> {

    public static final String OPERATION_MEP = "MEP";

    public JbiInInterceptor() {
        super(Phase.PRE_INVOKE);
    }

    public void handleMessage(Message message) {
        try {
            MessageExchange exchange;
            NormalizedMessage nm;
            // Create message
            if (!isRequestor(message)) {
                exchange = createExchange(message);
                nm = exchange.createMessage();
                exchange.setMessage(nm, "in");
                message.setContent(MessageExchange.class, exchange);
            } else {
                exchange = message.getContent(MessageExchange.class);
                if (exchange == null) {
                    throw new IllegalStateException("Content of type "
                            + MessageExchange.class + " not found on message");
                }
                if (message.getContent(Exception.class) == null) {
                    nm = exchange.createMessage();
                    exchange.setMessage(nm, "out");
                } else {
                    exchange.setFault(exchange.createFault());
                    nm = exchange.getFault();
                }
            }
            // Put headers
            toNMSHeaders(nm, message);

            // copy wsa headers if present
            toNMSWSAHeaders(nm, message);

            // Put attachments
            toNMSAttachments(nm, message);
            // Put subject
            nm.setSecuritySubject(message.get(Subject.class));
            // Put main source
            getContent(nm, message);
            // Register new content
            message.setContent(NormalizedMessage.class, nm);
        } catch (JBIException e) {
            throw new Fault(e);
        }
    }

    /**
     * Create the JBI exchange
     */
    private MessageExchange createExchange(Message message) throws JBIException {
        URI mep;
        BindingOperationInfo operation = message.getExchange().get(
                BindingOperationInfo.class);
        if (operation != null) {
            if (operation.getOutput() == null) {
                mep = CxfJbiConstants.IN_ONLY;
            } else {
                mep = CxfJbiConstants.IN_OUT;
            }
        } else {
            mep = (URI) message.get(OPERATION_MEP);
        }
        if (mep == null) {
            throw new NullPointerException("MEP not found");
        }
        MessageExchangeFactory mef = message.getExchange().get(
                MessageExchangeFactory.class);
        if (mef == null) {
            DeliveryChannel dv = message.getExchange().get(
                    DeliveryChannel.class);
            if (dv == null) {
                ComponentContext cc = message.getExchange().get(
                        ComponentContext.class);
                if (cc == null) {
                    throw new NullPointerException(
                            "MessageExchangeFactory or DeliveryChannel or ComponentContext not found");
                }
                dv = cc.getDeliveryChannel();
            }
            mef = dv.createExchangeFactory();
        }
        MessageExchange me = mef.createExchange(mep);
        me.setOperation(operation.getName());
        return me;
    }

    private void toNMSWSAHeaders(NormalizedMessage normalizedMessage,
            Message soapMessage) {
        SoapMessage message = null;
        if (!(soapMessage instanceof SoapMessage)) {
            return;
        } else {
            message = (SoapMessage) soapMessage;
        }

        if (message.get(WSAUtils.WSA_HEADERS_INBOUND) != null) {
            normalizedMessage.setProperty(WSAUtils.WSA_HEADERS_INBOUND,
                    WSAUtils.getAsMap((AddressingProperties) message
                            .get(WSAUtils.WSA_HEADERS_INBOUND)));
        }

    }

    /**
     * Convert SoapMessage headers to NormalizedMessage headers
     */
    private void toNMSHeaders(NormalizedMessage normalizedMessage,
            Message soapMessage) {
        SoapMessage message = null;
        if (!(soapMessage instanceof SoapMessage)) {
            return;
        } else {
            message = (SoapMessage) soapMessage;
        }
        Map<String, Object> headers = new HashMap<String, Object>();
        QName excludeName = new QName(
                "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd",
                "Security");
        for (Header header : message.getHeaders()) {
            if (!header.getName().equals(excludeName)) {
                // We must exclude this security header since in Sun's SAAJ impl, the ElementImpl
                // has a field which doesn't implement Serializable interface, which will cause
                //java.io.NotSerializableException when we try to serialize the JBI message.
                // And this security header isn't necessary inside jbi container since we have already
                // delegate the AA to JAAS at this stage.
                // SM-1696 track this issue
                headers.put(QNameUtil.toString(header.getName()), header
                    .getObject());
            }
        }

        normalizedMessage.setProperty(CxfJbiConstants.PROTOCOL_HEADERS, headers);

    }

    /**
     * Convert SoapMessage attachments to NormalizedMessage attachments
     */
    private void toNMSAttachments(NormalizedMessage normalizedMessage,
            Message soapMessage) throws MessagingException {
        if (soapMessage.getAttachments() != null) {
            for (Attachment att : soapMessage.getAttachments()) {
                normalizedMessage.addAttachment(att.getId(), att
                        .getDataHandler());
            }
        }
    }

    /**
     * Extract the content as a jaxp Source
     */
    private void getContent(NormalizedMessage nm, Message message)
        throws MessagingException {
        Exception e = message.getContent(Exception.class);
        if (e == null) {
            nm.setContent(message.getContent(Source.class));
            /*
             * } else if (e instanceof SoapFault) { SoapFault fault =
             * (SoapFault) e; nm.setContent(fault.getDetails());
             * nm.setProperty(JbiConstants.SOAP_FAULT_CODE, fault.getCode());
             * nm.setProperty(JbiConstants.SOAP_FAULT_NODE, fault.getNode());
             * nm.setProperty(JbiConstants.SOAP_FAULT_REASON,
             * fault.getReason()); nm.setProperty(JbiConstants.SOAP_FAULT_ROLE,
             * fault.getRole()); nm.setProperty(JbiConstants.SOAP_FAULT_SUBCODE,
             * fault.getSubcode());
             */
        }
    }

    protected boolean isRequestor(Message message) {
        return Boolean.TRUE.equals(message.get(Message.REQUESTOR_ROLE));
    }

}
