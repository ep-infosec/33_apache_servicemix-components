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

import org.apache.commons.httpclient.HttpStatus;
import org.apache.servicemix.expression.Expression;
import org.apache.servicemix.http.jetty.SmxHttpExchange;
import org.apache.servicemix.jbi.jaxp.SourceTransformer;
import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.http.HttpMethods;
import org.eclipse.jetty.io.ByteArrayBuffer;

import javax.jbi.messaging.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Map;

/**
 * Default marshaler used for non-soap provider endpoints.
 *
 * @author gnodet
 * @since 3.2
 */
public class DefaultHttpProviderMarshaler extends AbstractHttpProviderMarshaler implements HttpProviderMarshaler {

    private SourceTransformer transformer = new SourceTransformer();
    private String locationURI;
    private Expression locationURIExpression;
    private String method;
    private Expression methodExpression;
    private String contentType = "text/xml";
    private Expression contentTypeExpression;
    private Map<String, String> headers;

    public String getLocationURI() {
        return locationURI;
    }

    public void setLocationURI(String locationUri) {
        this.locationURI = locationUri;
    }

    public Expression getLocationURIExpression() {
        return locationURIExpression;
    }

    public void setLocationURIExpression(Expression locationUriExpression) {
        this.locationURIExpression = locationUriExpression;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Expression getMethodExpression() {
        return methodExpression;
    }

    public void setMethodExpression(Expression methodExpression) {
        this.methodExpression = methodExpression;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Expression getContentTypeExpression() {
        return contentTypeExpression;
    }

    public void setContentTypeExpression(Expression contentTypeExpression) {
        this.contentTypeExpression = contentTypeExpression;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    protected String getLocationUri(MessageExchange exchange, NormalizedMessage inMsg) throws Exception {
        String uri = null;
        if (locationURIExpression != null) {
            Object o = locationURIExpression.evaluate(exchange, inMsg);
            uri = (o != null) ? o.toString() : null;
        }
        if (uri == null) {
            uri = locationURI;
        }
        if (uri == null) {
            throw new IllegalStateException("Unable to find URI for exchange");
        }
        return uri;
    }

    protected String getMethod(MessageExchange exchange, NormalizedMessage inMsg) throws Exception {
        String mth = null;
        if (methodExpression != null) {
            Object o = methodExpression.evaluate(exchange, inMsg);
            mth = (o != null) ? o.toString() : null;
        }
        if (mth == null) {
            mth = method;
        }
        if (mth == null) {
            if (inMsg.getContent() == null) {
                mth = HttpMethods.GET;
            } else {
                mth = HttpMethods.POST;
            }
        }
        return mth;
    }

    protected String getContentType(MessageExchange exchange, NormalizedMessage inMsg) throws Exception {
        String content = null;
        if (contentTypeExpression != null) {
            Object o = contentTypeExpression.evaluate(exchange, inMsg);
            content = (o != null) ? o.toString() : null;
        }
        if (content == null) {
            content = contentType;
        }
        if (content == null) {
            throw new IllegalStateException("ContentType must not be null");
        }
        return content;
    }

    public void createRequest(final MessageExchange exchange,
                              final NormalizedMessage inMsg,
                              final SmxHttpExchange httpExchange) throws Exception {
        httpExchange.setURL(getLocationUri(exchange, inMsg));

        // Temporary fix for bug in jetty-client 6.1.5
        // http://fisheye.codehaus.org/browse/jetty-contrib/jetty/trunk/contrib/client/src/main/java/org/mortbay/jetty/client/HttpConnection.java?r1=374&r2=378
        httpExchange.addRequestHeader(HttpHeaders.HOST_BUFFER, new ByteArrayBuffer(new URI(getLocationUri(exchange, inMsg)).getHost()));

        httpExchange.setMethod(getMethod(exchange, inMsg));
        httpExchange.setRequestHeader(HttpHeaders.CONTENT_TYPE, getContentType(exchange, inMsg));

        if (getContentEncoding() != null) {
            httpExchange.setRequestHeader(HttpHeaders.CONTENT_ENCODING, getContentEncoding());
        }
        if (getAcceptEncoding() != null) {
            httpExchange.setRequestHeader(HttpHeaders.ACCEPT_ENCODING, getAcceptEncoding());
        }

        if (getHeaders() != null) {
            for (Map.Entry<String, String> e : getHeaders().entrySet()) {
                httpExchange.setRequestHeader(e.getKey(), e.getValue());
            }
        }
        if (inMsg.getContent() != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStream encodingStream = getRequestEncodingStream(getContentEncoding(), baos);
            transformer.toResult(inMsg.getContent(), new StreamResult(encodingStream));
            encodingStream.close();
            httpExchange.setRequestContent(new ByteArrayBuffer(baos.toByteArray()));
        }
    }

    public void handleResponse(MessageExchange exchange, SmxHttpExchange httpExchange) throws Exception {
        int response = httpExchange.getResponseStatus();
        if (response != HttpStatus.SC_OK && response != HttpStatus.SC_ACCEPTED) {
            if (!(exchange instanceof InOnly)) {
                Fault fault = exchange.createFault();
                fault.setContent(new StreamSource(getResponseEncodingStream(
                    httpExchange.getResponseFields().getStringField(HttpHeaders.CONTENT_ENCODING),
                    httpExchange.getResponseStream())));
                exchange.setFault(fault);
            } else {
                throw new Exception("Invalid status response: " + response);
            }
        } else if (exchange instanceof InOut) {
            NormalizedMessage msg = exchange.createMessage();
            msg.setContent(new StreamSource(getResponseEncodingStream(
                httpExchange.getResponseFields().getStringField(HttpHeaders.CONTENT_ENCODING),
                httpExchange.getResponseStream())));
            exchange.setMessage(msg, "out");
        } else if (exchange instanceof InOptionalOut) {
            InputStream is = httpExchange.getResponseStream();
            if (is != null) {
                NormalizedMessage msg = exchange.createMessage();
                msg.setContent(new StreamSource(getResponseEncodingStream(
                    httpExchange.getResponseFields().getStringField(HttpHeaders.CONTENT_ENCODING),
                    is)));
                exchange.setMessage(msg, "out");
            } else {
                exchange.setStatus(ExchangeStatus.DONE);
            }
        } else {
            exchange.setStatus(ExchangeStatus.DONE);

        }
    }

    public void handleException(MessageExchange exchange, SmxHttpExchange httpExchange, Throwable ex) {
        exchange.setError((Exception)ex);
    }

}
