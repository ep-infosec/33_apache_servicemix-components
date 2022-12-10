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

import org.apache.servicemix.common.DefaultComponent;
import org.apache.servicemix.common.JbiConstants;
import org.apache.servicemix.common.ServiceUnit;
import org.apache.servicemix.common.endpoints.ProviderEndpoint;
import org.apache.servicemix.common.security.KeystoreManager;
import org.apache.servicemix.http.HttpComponent;
import org.apache.servicemix.http.HttpConfiguration;
import org.apache.servicemix.http.HttpEndpointType;
import org.apache.servicemix.http.SslParameters;
import org.apache.servicemix.http.jetty.SmxHttpExchange;
import org.eclipse.jetty.client.Address;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.security.ProxyAuthorization;
import org.eclipse.jetty.client.security.Realm;
import org.eclipse.jetty.client.security.SimpleRealmResolver;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;
import javax.net.ssl.*;
import javax.xml.namespace.QName;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;

/**
 * A plain HTTP provider. This type of endpoint can be used to send non-SOAP requests to HTTP endpoints.
 * 
 * @author gnodet
 * @since 3.2
 * @org.apache.xbean.XBean element="provider"
 */
public class HttpProviderEndpoint extends ProviderEndpoint implements HttpEndpointType {

    private HttpProviderMarshaler marshaler;
    private HttpProviderListener listener;
    private String locationURI;
    private int clientConnectTimeout = 75000;
    private int providerExpirationTime = 300000;
    private int maxConnectionsPerAddress = 32;
    private HttpClient jettyClient;
    private boolean ownClient = false;
    private String principal;
    private String credentials;

    private String proxyHost;
    private int proxyPort = 80;
    private String proxyUsername;
    private String proxyPassword;

    private SslParameters ssl;

    private boolean expectGzippedResponse;
    private boolean gzipRequest;


    public HttpProviderEndpoint() {
        super();
    }

    public HttpProviderEndpoint(DefaultComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }

    public HttpProviderEndpoint(ServiceUnit serviceUnit, QName service, String endpoint) {
        super(serviceUnit, service, endpoint);
    }

    /**
     * Returns the URI to which the endpoint sends requests.
     * 
     * @return a string representing the URI to which requests are sent
     */
    public String getLocationURI() {
        return locationURI;
    }

    /**
     * Sets the URI to which an endpoint sends requests.
     * 
     * @param locationURI a string representing the URI
     * @org.apache.xbean.Property description="the URI to which the endpoint sends requests"
     */
    public void setLocationURI(String locationURI) {
        this.locationURI = locationURI;
    }

    /**
     * @return the marshaler
     */
    public HttpProviderMarshaler getMarshaler() {
        return marshaler;
    }

    /**
     * Sets the class used to marshal messages.
     * 
     * @param marshaler the marshaler to set
     * @org.apache.xbean.Property description="the bean used to marshal HTTP messages. The default is a
     *                            <code>DefaultHttpProviderMarshaler</code>."
     */
    public void setMarshaler(HttpProviderMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    public HttpProviderListener getListener() {
        return listener;
    }

    /**
     * Sets the class used to marshal messages.
     *
     * @param listener the marshaler to set
     * @org.apache.xbean.Property description="the bean used monitor Jetty Client instance and to handle some Jetty Client events"
     */
    public void setListener(HttpProviderListener listener) {
        this.listener = listener;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * Sets the host name of the HTTP proxy used
     *
     * @param proxyHost the host name of the HTTP proxy
     * @org.apache.xbean.Property description="the host name of the HTTP proxy"
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    /**
     * Sets the host port of the HTTP proxy used (defaults to 80)
     *
     * @param proxyPort the host name of the HTTP proxy
     * @org.apache.xbean.Property description="the host port of the HTTP proxy (defaults to 80)"
     */
    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getProxyUsername() {
        return proxyUsername;
    }

    /**
     * Sets the user name for the HTTP proxy authentication
     *
     * @param proxyUsername the user name for the HTTP proxy authentication
     * @org.apache.xbean.Property description="the user name for the HTTP proxy authentication"
     */
    public void setProxyUsername(String proxyUsername) {
        this.proxyUsername = proxyUsername;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    /**
     * Sets the password for the HTTP proxy authentication
     *
     * @param proxyPassword the password for the HTTP proxy authentication
     * @org.apache.xbean.Property description="the password for the HTTP proxy authentication"
     */
    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    public SslParameters getSsl() {
        return ssl;
    }

    /**
     * Sets the SSL parameters
     *
     * @param ssl the SSL parameters
     * @org.apache.xbean.Property description="the SSL parameters"
     */
    public void setSsl(SslParameters ssl) {
        this.ssl = ssl;
    }

    public boolean isExpectGzippedResponse() {
        return expectGzippedResponse;
    }

    /**
     * If true, the accept-encoding http header will be set to gzip and the response will be un-gzipped.
     *
     * @param expectGzippedResponse if the response should be unzipped
     */
    public void setExpectGzippedResponse(boolean expectGzippedResponse) {
        this.expectGzippedResponse = expectGzippedResponse;
    }

    public boolean isGzipRequest() {
        return gzipRequest;
    }

    /**
     * If true, the request content will be gzipped and sent over the wire. The content-encoding http header will
     * also be set to gzip.
     *
     * @param gzipRequest if the request should be compressed using gzip
     */
    public void setGzipRequest(boolean gzipRequest) {
        this.gzipRequest = gzipRequest;
    }
    
    public String getPrincipal() {
        return principal;
    }
    
    /**
     * <p>
     * Principal used by authentication realm.
     * </p>
     * 
     * @param principal the principal used by authentication realm.
     * @org.apache.xbean.Property description="The authentication principal" 
     */
    public void setPrincipal(String principal) {
        this.principal = principal;
    }
    
    public String getCredentials() {
        return this.credentials;
    }
    
    /**
     * <p>
     * Credentials used by authentication realm.
     * </p>
     * 
     * @param credentials the credentials used by authentication realm.
     * @org.apache.xbean.Property description="The authentication credentials"
     */
    public void setCredentials(String credentials) {
        this.credentials = credentials;
    }

    public void process(MessageExchange exchange) throws Exception {
        if (exchange.getStatus() == ExchangeStatus.ACTIVE) {
            NormalizedMessage nm = exchange.getMessage("in");
            if (nm == null) {
                throw new IllegalStateException("Exchange has no input message");
            }
            SmxHttpExchange httpEx = new Exchange(exchange);
            try {
                marshaler.createRequest(exchange, nm, httpEx);
                jettyClient.send(httpEx);
            } catch (Exception e){
                handleException(httpEx, exchange,  e);
            }
        }
    }
    
    @Override
    public synchronized void start() throws Exception {
    	getConnectionPool();
    }

    @Override
    public synchronized void stop() throws Exception {
        if (listener != null){
            listener.stopJettyClientMonitoring(jettyClient);
        }
        if (ownClient && jettyClient != null) {
            jettyClient.stop();
            jettyClient = null;
        }
        super.stop();
    }

    protected void handle(SmxHttpExchange httpExchange, MessageExchange exchange) throws IOException {
        try {
            marshaler.handleResponse(exchange, httpExchange);
        } catch (Exception e) {
            exchange.setError(e);
        }
        try {
            boolean txSync = exchange.getStatus() == ExchangeStatus.ACTIVE && exchange.isTransacted()
                             && Boolean.TRUE.equals(exchange.getProperty(JbiConstants.SEND_SYNC));
            if (txSync) {
                sendSync(exchange);
            } else {
                send(exchange);
            }
        } catch (Exception e) {
            throw (IOException) new IOException(e.getMessage()).initCause(e);
        }
    }

    protected void handleException(SmxHttpExchange httpExchange, MessageExchange exchange, Throwable ex)  {
        try {
            marshaler.handleException(exchange, httpExchange, ex);
            boolean txSync = exchange.getStatus() == ExchangeStatus.ACTIVE
                             && exchange.isTransacted()
                            && Boolean.TRUE.equals(exchange.getProperty(JbiConstants.SEND_SYNC));
            if (txSync) {
                sendSync(exchange);
            } else {
                send(exchange);
            }
        } catch (Exception e) {
            logger.warn("Unable to send back exchange in error", e);
        }
    }

    protected org.eclipse.jetty.client.HttpClient getConnectionPool() throws Exception {
        if (jettyClient == null) {
            HttpComponent comp = (HttpComponent) getServiceUnit().getComponent();
            if (comp.getConfiguration().isJettyClientPerProvider() || proxyHost != null || ssl != null) {
                ownClient = true;
                jettyClient = new SSLManagedHttpClient();
                jettyClient.setThreadPool(new QueuedThreadPool(getConfiguration().getJettyClientThreadPoolSize()));
                jettyClient.setConnectorType(HttpClient.CONNECTOR_SELECT_CHANNEL);
                if (proxyHost != null) {
                    jettyClient.setProxy(new Address(proxyHost, proxyPort));
                    if (proxyUsername != null) {
                        jettyClient.setProxyAuthentication(new ProxyAuthorization(proxyUsername, proxyPassword));
                    }
                }
                jettyClient.setConnectTimeout(getClientConnectTimeout());
                jettyClient.setTimeout(getProviderExpirationTime());
                jettyClient.setMaxConnectionsPerAddress(getMaxConnectionsPerAddress());
                if (principal != null && credentials != null) {
                    jettyClient.setRealmResolver(new SimpleRealmResolver(new Realm() {
                        public String getPrincipal() {
                            return principal;
                        }
                        public String getCredentials() {
                            return credentials;
                        }
                        public String getId() {
                            return null;
                        }
                    }));
                }
                jettyClient.start();
            } else {
                ownClient = false;
                // return shared client
                jettyClient = comp.getConnectionPool();
            }
        }
        if (listener != null){
            listener.startJettyClientMonitoring(jettyClient);
        }
        return jettyClient;
    }

    public int getClientConnectTimeout() {
        return clientConnectTimeout;
    }

    /**
     * Sets the number of milliseconds the endpoint will block while attempting to read a request. The default value is 60000.
     * Setting this to 0 specifies that the endpoint will never timeout.
     * 
     * @param clientConnectTimeout an int specifying the number of milliseconds the socket will block while attempting to read a request
     * @org.apache.xbean.Property description="the number of milliseconds the endpoint will block while attempting to read a request. The default value is 60000. Setting this to 0 specifies that the endpoint will never timeout."
     */
    public void setClientConnectTimeout(int clientConnectTimeout) {
        this.clientConnectTimeout = clientConnectTimeout;
    }

    public int getProviderExpirationTime() {
        return providerExpirationTime;
    }

    /***
     * Sets the number of milliseconds the endpoint will wait to read the response. The default value is 300000.
     *
     * @param providerExpirationTime an int specifying the number of milliseconds to wait for a response before expiring.
     * @org.apache.xbean.Property description="the number of milliseconds to wait for a response before expiring."
     */
    public void setProviderExpirationTime(int providerExpirationTime) {
        this.providerExpirationTime = providerExpirationTime;
    }
    
    public int getMaxConnectionsPerAddress() {
        return maxConnectionsPerAddress;
    }

    /**
     * Sets the number of the maximum connections per address that JettyClient creates for each destination.
     * The default default value for Jetty is 32.
     * 
     * @param maxConnectionsPerAddress the maxConnectionsPerAddress to set
     * @org.apache.xbean.Property description="the number of the maximum connections per address that JettyClient creates for each destination. The default is 32."
     * 
     */
    public void setMaxConnectionsPerAddress(int maxConnectionsPerAddress) {
        this.maxConnectionsPerAddress = maxConnectionsPerAddress;
    }

    public void validate() throws DeploymentException {
        super.validate();
        if (marshaler == null) {
            marshaler = new DefaultHttpProviderMarshaler();
        }
        if (marshaler instanceof DefaultHttpProviderMarshaler && locationURI != null) {
            ((DefaultHttpProviderMarshaler) marshaler).setLocationURI(locationURI);
        }
        if (marshaler instanceof AbstractHttpProviderMarshaler) {
            if (isGzipRequest()) {
                ((AbstractHttpProviderMarshaler) marshaler).setContentEncoding("gzip");
            }
            if (isExpectGzippedResponse()) {
                ((AbstractHttpProviderMarshaler) marshaler).setAcceptEncoding("gzip");
            }
        }
    }

    private HttpConfiguration getConfiguration() {
        return ((HttpComponent) getServiceUnit().getComponent()).getConfiguration();
    }

    protected class Exchange extends SmxHttpExchange {
        MessageExchange jbiExchange;

        public Exchange(MessageExchange jbiExchange) {
            this.jbiExchange = jbiExchange;
        }

        protected void onResponseComplete() throws IOException {
            handle(this, jbiExchange);
        }

        protected void onConnectionFailed(Throwable throwable) {
            handleException(this, jbiExchange, throwable);
        }

        protected void onException(Throwable throwable) {
            handleException(this, jbiExchange, throwable);
        }
        
        protected void onExpire() {
            handleException(this, jbiExchange, new Exception("Http request expired."));
        }


        @Override
        protected void onRequestCommitted() throws IOException {
            if (listener != null){
                listener.onRequestCommited(jbiExchange, this);
            }
        }

        @Override
        protected void onRequestComplete() throws IOException {
            if (listener != null){
                listener.onRequestComplete(jbiExchange, this);
            }
        }
    }

    protected class SSLManagedHttpClient extends HttpClient {

        protected SSLContext getSSLContext() {
            if (ssl != null && ssl.getKeyStore() != null) {
                return getStrictSSLContext();
            } else {
                return super.getSSLContext();
            }
        }

        protected SSLContext getStrictSSLContext() {
            try {
                if (ssl.isManaged()) {
                    KeystoreManager keystoreMgr = KeystoreManager.Proxy.create(getConfiguration().getKeystoreManager());
                    return keystoreMgr.createSSLContext(ssl.getProvider(), ssl.getProtocol(),
                                                        ssl.getKeyManagerFactoryAlgorithm(), ssl.getKeyStore(),
                                                        ssl.getKeyAlias(), ssl.getTrustStore());
                } else {
                    if (ssl.getTrustStore() == null) {
                        ssl.setTrustStore(ssl.getKeyStore());
                        ssl.setTrustStoreType(ssl.getKeyStoreType());
                        ssl.setTrustManagerFactoryAlgorithm(ssl.getKeyManagerFactoryAlgorithm());
                    }

                    KeyManager[] keyManagers;
                    TrustManager[] trustManagers;

                    InputStream keystoreInputStream = Resource.newResource(ssl.getKeyStore()).getInputStream();
                    KeyStore keyStore = KeyStore.getInstance(ssl.getKeyStoreType());
                    keyStore.load(keystoreInputStream, ssl.getKeyStorePassword() == null ? null : ssl.getKeyStorePassword().toString().toCharArray());

                    KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(ssl.getKeyManagerFactoryAlgorithm());
                    keyManagerFactory.init(keyStore, ssl.getKeyPassword() == null ? null : ssl.getKeyPassword().toString().toCharArray());
                    keyManagers = keyManagerFactory.getKeyManagers();

                    InputStream truststoreInputStream = Resource.newResource(ssl.getTrustStore()).getInputStream();
                    KeyStore trustStore = KeyStore.getInstance(ssl.getTrustStoreType());
                    trustStore.load(truststoreInputStream, ssl.getTrustStorePassword() == null ? null : ssl.getTrustStorePassword().toString().toCharArray());

                    TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(ssl.getTrustManagerFactoryAlgorithm());
                    trustManagerFactory.init(trustStore);
                    trustManagers = trustManagerFactory.getTrustManagers();

                    SSLContext context = ssl.getProvider() == null
                                                    ? SSLContext.getInstance(ssl.getProtocol())
                                                    : SSLContext.getInstance(ssl.getProtocol(), ssl.getProvider());
                    context.init(keyManagers, trustManagers, new SecureRandom());
                    return context;
                }
            } catch (Exception e) {
                throw new RuntimeException("Unable to create SSL context", e);
            }
        }

    }

}
