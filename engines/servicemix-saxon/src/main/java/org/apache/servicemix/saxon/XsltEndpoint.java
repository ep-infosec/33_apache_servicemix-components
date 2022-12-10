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
package org.apache.servicemix.saxon;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Map;

import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import net.sf.saxon.TransformerFactoryImpl;
import org.apache.servicemix.jbi.jaxp.BytesSource;
import org.apache.servicemix.jbi.jaxp.StringSource;
import org.springframework.core.io.Resource;

/**
 * @org.apache.xbean.XBean element="xslt"
 */
public class XsltEndpoint extends SaxonEndpoint {
    
    private TransformerFactory transformerFactory;
    private Source xsltSource;
    private Templates templates;
    private boolean useDomSourceForXslt = true;
    private Boolean useDomSourceForContent;

    public TransformerFactory getTransformerFactory() {
        if (transformerFactory == null) {
            transformerFactory = createTransformerFactory();
        }
        return transformerFactory;
    }

    /**
     * Set a transform factory, e.g. for injecting a custom transformer configuration or implementation.
     *
     * @param transformerFactory
     */
    public void setTransformerFactory(TransformerFactory transformerFactory) {
        this.transformerFactory = transformerFactory;
    }

    public boolean isUseDomSourceForXslt() {
        return useDomSourceForXslt;
    }

    /**
     * Convert the XSL-T stylesheet Source into a DOMSource.  Defaults to <code>true</true>.
     *
     * @param useDomSourceForXslt
     */
    public void setUseDomSourceForXslt(boolean useDomSourceForXslt) {
        this.useDomSourceForXslt = useDomSourceForXslt;
    }

    public Boolean getUseDomSourceForContent() {
        return useDomSourceForContent;
    }

    /**
     * Convert the message body Source into a DOMSource.  Defaults to <code>false</true>.
     *
     * @param useDomSourceForContent
     */
    public void setUseDomSourceForContent(Boolean useDomSourceForContent) {
        this.useDomSourceForContent = useDomSourceForContent;
    }

    public void validate() throws DeploymentException {
        if (xsltSource == null && getResource() == null && getExpression() == null) {
            throw new DeploymentException("xsltSource, resource or expression must be specified");
        }
    }
    
    // Implementation methods
    // -------------------------------------------------------------------------

    protected void transform(MessageExchange exchange, NormalizedMessage in, NormalizedMessage out) throws Exception {
        Transformer transformer = createTransformer(exchange, in);
        configureTransformer(transformer, exchange, in);
        transformContent(transformer, exchange, in, out);
    }
    
    protected void transformContent(Transformer transformer, MessageExchange exchange, 
            NormalizedMessage in, NormalizedMessage out) throws Exception {
        Source src = in.getContent();
        if (useDomSourceForContent != null && useDomSourceForContent.booleanValue()) {
            src = new DOMSource(getSourceTransformer().toDOMDocument(src));
        } else if (useDomSourceForContent != null && !useDomSourceForContent.booleanValue()) {
            src = getSourceTransformer().toStreamSource(src);
        } else {
            if (src instanceof DOMSource) {
                src = new DOMSource(getSourceTransformer().toDOMDocument(src));
            }
        }
        if (RESULT_BYTES.equalsIgnoreCase(getResult())) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            Result result = new StreamResult(buffer);
            transformer.transform(src, result);
            out.setContent(new BytesSource(buffer.toByteArray()));
        } else if (RESULT_STRING.equalsIgnoreCase(getResult())) {
            StringWriter buffer = new StringWriter();
            Result result = new StreamResult(buffer);
            transformer.transform(src, result);
            out.setContent(new StringSource(buffer.toString()));
        } else {
            DOMResult result = new DOMResult();
            transformer.transform(src, result);
            out.setContent(new DOMSource(result.getNode()));
        }
    }

    protected Source getXsltSource() throws Exception {
        if (xsltSource == null) {
            xsltSource = createXsltSource(getResource());
        }
        return xsltSource;
    }

    protected Source createXsltSource(Resource res) throws Exception {
        String url = null;
        try {
            url = res.getURL().toURI().toString();
        } catch (Exception e) {
            // Ignore
        }
        if (useDomSourceForXslt) {
            return new DOMSource(parse(res), url);
        } else {
            return new StreamSource(res.getInputStream(), url);
        }
    }

    public synchronized Templates getTemplates() throws Exception {
        if (templates == null) {
            templates = createTemplates();
        }
        return templates;
    }

    /**
     * Factory method to create a new transformer instance
     */
    protected Templates createTemplates() throws Exception {
        Source source = getXsltSource();
        return getTransformerFactory().newTemplates(source);
    }

    /**
     * Factory method to create a new transformer instance
     */
    protected Transformer createTransformer(MessageExchange exchange, NormalizedMessage in) throws Exception {
        // Use dynamic stylesheet selection
        if (getExpression() != null) {
            Resource r = getDynamicResource(exchange, in);
            if (r == null) {
                return getTransformerFactory().newTransformer();
            } else {
                Source source = createXsltSource(r);
                return getTransformerFactory().newTransformer(source);
            }
        // Use static stylesheet
        } else {
            if (isReload()) {
                Source source = createXsltSource(getResource());
                return getTransformerFactory().newTransformer(source);
            } else {
                return getTemplates().newTransformer();
            }
        }
    }
    
    protected TransformerFactory createTransformerFactory() {
        if (getConfiguration() != null) {
            return new TransformerFactoryImpl(getConfiguration());
        } else {
            return new TransformerFactoryImpl();
        }
    }

    /**
     * A hook to allow the transformer to be configured from the current
     * exchange and inbound message
     */
    protected void configureTransformer(Transformer transformer, MessageExchange exchange, NormalizedMessage in) {
        for (Iterator iter = exchange.getPropertyNames().iterator(); iter.hasNext();) {
            String name = (String) iter.next();
            Object value = exchange.getProperty(name);
            transformer.setParameter(name, value);
        }
        for (Iterator iter = in.getPropertyNames().iterator(); iter.hasNext();) {
            String name = (String) iter.next();
            Object value = in.getProperty(name);
            transformer.setParameter(name, value);
        }
        Map parameters = getParameters();
        if (parameters != null) {
            for (Iterator iter = parameters.keySet().iterator(); iter.hasNext();) {
                String name = (String) iter.next();
                Object value = parameters.get(name);
                transformer.setParameter(name, value);
            }
        }
        transformer.setParameter("exchange", exchange);
        transformer.setParameter("in", in);
        transformer.setParameter("component", this);
    }

}
