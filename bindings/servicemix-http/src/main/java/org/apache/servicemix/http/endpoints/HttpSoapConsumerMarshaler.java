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
package org.apache.servicemix.http.endpoints;

import org.apache.servicemix.soap.api.InterceptorChain;
import org.apache.servicemix.soap.api.InterceptorProvider.Phase;
import org.apache.servicemix.soap.api.Message;
import org.apache.servicemix.soap.api.Policy;
import org.apache.servicemix.soap.api.model.Binding;
import org.apache.servicemix.soap.bindings.http.HttpConstants;
import org.apache.servicemix.soap.bindings.soap.SoapFault;
import org.apache.servicemix.soap.bindings.soap.SoapVersion;
import org.apache.servicemix.soap.interceptors.jbi.JbiConstants;
import org.eclipse.jetty.http.HttpHeaders;

import javax.jbi.component.ComponentContext;
import javax.jbi.messaging.Fault;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * 
 * @author gnodet
 * @since 3.2
 */
public class HttpSoapConsumerMarshaler extends AbstractHttpConsumerMarshaler {

    private Binding<?> binding;
    private boolean useJbiWrapper = true;
    private Policy[] policies;
    private Map<Phase, InterceptorChain> chains = new HashMap<Phase, InterceptorChain>();
    private String soapVersion = "1.2";

    public Binding<?> getBinding() {
        return binding;
    }

    public void setBinding(Binding<?> binding) {
        this.binding = binding;
    }

    public boolean isUseJbiWrapper() {
        return useJbiWrapper;
    }

    public void setUseJbiWrapper(boolean useJbiWrapper) {
        this.useJbiWrapper = useJbiWrapper;
    }

    public Policy[] getPolicies() {
        return policies;
    }

    public void setPolicies(Policy[] policies) {
        this.policies = policies;
    }
    
    public String getSoapVersion() {
        return this.soapVersion;
    }
    
    public void setSoapVersion(String soapVersion) {
        this.soapVersion = soapVersion;
    }

    public MessageExchange createExchange(HttpServletRequest request, ComponentContext context) throws Exception {
        String method = request.getMethod();
        Message msg = binding.createMessage();
        msg.put(ComponentContext.class, context);
        msg.put(JbiConstants.USE_JBI_WRAPPER, useJbiWrapper);
        msg.put(Message.CONTENT_TYPE, request.getContentType());
        Map<String, String> headers = msg.getTransportHeaders();
        for (Enumeration<?> e = request.getHeaderNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            String value = request.getHeader(name);
            headers.put(name, value);
        }
        headers.put(HttpConstants.REQUEST_URI, request.getRequestURL().toString());
        headers.put(HttpConstants.CONTENT_TYPE, request.getContentType());
        headers.put(HttpConstants.REQUEST_METHOD, method);
        if (HttpConstants.METHOD_POST.equals(method) || HttpConstants.METHOD_PUT.equals(method)) {
            msg.setContent(InputStream.class, getRequestEncodingStream(request.getHeader(HttpHeaders.CONTENT_ENCODING), request.getInputStream()));
        }
        request.setAttribute(Message.class.getName(), msg);
        InterceptorChain phase = getChain(Phase.ServerIn);
        phase.doIntercept(msg);
        return msg.getContent(MessageExchange.class);
    }

    public void sendAccepted(MessageExchange exchange, HttpServletRequest request, HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_ACCEPTED);
    }

    public void sendOut(MessageExchange exchange, NormalizedMessage outMsg, HttpServletRequest request, HttpServletResponse response) throws Exception {
        addResponseHeaders(exchange, request, response);
        // the content type is specific to this marshaler, depending of the used SOAP version
        if (soapVersion.equals("1.1")) {
            response.setContentType("text/xml");
        } else {
            response.setContentType("application/soap+xml");
        }
        Message in = (Message) request.getAttribute(Message.class.getName());
        Message msg = binding.createMessage(in);
        OutputStream encodingStream = getResponseEncodingStream(request.getHeader(HttpHeaders.CONTENT_ENCODING), response.getOutputStream());
        msg.setContent(OutputStream.class, encodingStream);
        msg.setContent(MessageExchange.class, exchange);
        msg.setContent(NormalizedMessage.class, outMsg);
        msg.put(SoapVersion.class, in.get(SoapVersion.class));
        msg.put(JbiConstants.USE_JBI_WRAPPER, useJbiWrapper);
        InterceptorChain phase = getChain(Phase.ServerOut);
        phase.doIntercept(msg);
        encodingStream.close();
    }

    public void sendError(MessageExchange exchange, Exception error, HttpServletRequest request, HttpServletResponse response) throws Exception {
        addResponseHeaders(exchange, request, response);
        // the content type is specific to this marshaler, depending of the used SOAP version
        if (soapVersion.equals("1.1")) {
            response.setContentType("text/xml");
        } else {
            response.setContentType("application/soap+xml");
        }
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        Message in = (Message) request.getAttribute(Message.class.getName());
        Message msg = binding.createMessage(in);
        OutputStream encodingStream = getResponseEncodingStream(request.getHeader(HttpHeaders.CONTENT_ENCODING), response.getOutputStream());
        msg.setContent(OutputStream.class, encodingStream);
        msg.setContent(MessageExchange.class, exchange);
        msg.put(SoapVersion.class, in.get(SoapVersion.class));
        msg.put(JbiConstants.USE_JBI_WRAPPER, useJbiWrapper);
        InterceptorChain phase = getChain(Phase.ServerOutFault);
        SoapFault soapFault;
        if (error instanceof SoapFault) {
            soapFault = (SoapFault) error;
        } else {
            soapFault = new SoapFault(error);
        }
        msg.setContent(Exception.class, soapFault);
        phase.doIntercept(msg);
        encodingStream.close();
    }

    public void sendFault(MessageExchange exchange, Fault fault, HttpServletRequest request, HttpServletResponse response) throws Exception {
        addResponseHeaders(exchange, request, response);
        // the content type is specific to this marshaler, depending of the used SOAP version
        if (soapVersion.equals("1.1")) {
            response.setContentType("text/xml");
        } else {
            response.setContentType("application/soap+xml");
        }
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        Message in = (Message) request.getAttribute(Message.class.getName());
        Message msg = binding.createMessage(in);
        OutputStream encodingStream = getResponseEncodingStream(request.getHeader(HttpHeaders.CONTENT_ENCODING), response.getOutputStream());
        msg.setContent(OutputStream.class, encodingStream);
        msg.setContent(MessageExchange.class, exchange);
        msg.setContent(NormalizedMessage.class, fault);
        msg.put(SoapVersion.class, in.get(SoapVersion.class));
        msg.put(JbiConstants.USE_JBI_WRAPPER, useJbiWrapper);
        InterceptorChain phase = getChain(Phase.ServerOutFault);
        QName code = (QName) fault.getProperty("org.apache.servicemix.soap.fault.code");
        String reason = (String) fault.getProperty("org.apache.servicemix.soap.fault.reason");
        SoapFault soapFault = new SoapFault(code, reason, null, null, fault.getContent());
        msg.setContent(Exception.class, soapFault);
        phase.doIntercept(msg);
        encodingStream.close();
    }

    protected InterceptorChain getChain(Phase phase) {
        InterceptorChain chain = chains.get(phase);
        if (chain == null) {
            chain = binding.getInterceptorChain(phase);
            if (policies != null) {
                for (int i = 0; i < policies.length; i++) {
                    chain.add(policies[i].getInterceptors(phase));
                }
            }
            chains.put(phase, chain);
        }
        return chain;
    }

}
