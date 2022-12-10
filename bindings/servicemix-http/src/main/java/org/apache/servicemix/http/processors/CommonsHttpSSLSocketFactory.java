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
package org.apache.servicemix.http.processors;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.SecureProtocolSocketFactory;
import org.apache.servicemix.common.security.KeystoreManager;
import org.apache.servicemix.http.SslParameters;
import org.eclipse.jetty.util.resource.Resource;
import org.springframework.core.io.ClassPathResource;

import javax.jbi.JBIException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.*;
import java.security.KeyStore;

public class CommonsHttpSSLSocketFactory implements SecureProtocolSocketFactory {

    private SSLSocketFactory factory;

    public CommonsHttpSSLSocketFactory(SslParameters ssl, KeystoreManager keystoreManager) throws Exception {
        if (ssl.isManaged()) {
            createManagedFactory(ssl, keystoreManager);
        } else {
            createUnmanagedFactory(ssl);
        }
    }

    protected final void createManagedFactory(SslParameters ssl, KeystoreManager keystoreManager) throws Exception {
        factory = keystoreManager.createSSLFactory(ssl.getProvider(), ssl.getProtocol(), ssl
                        .getKeyManagerFactoryAlgorithm(), ssl.getKeyStore(), ssl.getKeyAlias(), ssl.getTrustStore());
    }

    protected final void createUnmanagedFactory(SslParameters ssl) throws Exception {
        SSLContext context;
        if (ssl.getProvider() == null) {
            context = SSLContext.getInstance(ssl.getProtocol());
        } else {
            context = SSLContext.getInstance(ssl.getProtocol(), ssl.getProvider());
        }
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(ssl.getKeyManagerFactoryAlgorithm());
        String keyStore = ssl.getKeyStore();
        if (keyStore == null) {
            keyStore = System.getProperty("javax.net.ssl.keyStore");
            if (keyStore == null) {
                throw new IllegalArgumentException("keyStore or system property javax.net.ssl.keyStore must be set");
            }
        }
        if (keyStore.startsWith("classpath:")) {
            try {
                String res = keyStore.substring(10);
                URL url = new ClassPathResource(res).getURL();
                keyStore = url.toString();
            } catch (IOException e) {
                throw new JBIException("Unable to find keyStore " + keyStore, e);
            }
        }
        String keyStorePassword = ssl.getKeyStorePassword();
        if (keyStorePassword == null) {
            keyStorePassword = System.getProperty("javax.net.ssl.keyStorePassword");
            if (keyStorePassword == null) {
                throw new IllegalArgumentException(
                                "keyStorePassword or system property javax.net.ssl.keyStorePassword must be set");
            }
        }
        String trustStore = ssl.getTrustStore();
        String trustStorePassword = null;
        if (trustStore == null) {
            trustStore = System.getProperty("javax.net.ssl.trustStore");
        }
        if (trustStore != null) {
            if (trustStore.startsWith("classpath:")) {
                try {
                    String res = trustStore.substring(10);
                    URL url = new ClassPathResource(res).getURL();
                    trustStore = url.toString();
                } catch (IOException e) {
                    throw new JBIException("Unable to find trustStore " + trustStore, e);
                }
            }
            trustStorePassword = ssl.getTrustStorePassword();
            if (trustStorePassword == null) {
                trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword");
                if (trustStorePassword == null) {
                    throw new IllegalArgumentException(
                          "trustStorePassword or system property javax.net.ssl.trustStorePassword must be set");
                }
            }
        }
        KeyStore ks = KeyStore.getInstance(ssl.getKeyStoreType());
        ks.load(Resource.newResource(keyStore).getInputStream(), keyStorePassword.toCharArray());
        keyManagerFactory.init(ks, ssl.getKeyPassword() != null ? ssl.getKeyPassword().toCharArray() : keyStorePassword
                        .toCharArray());
        if (trustStore != null) {
            KeyStore ts = KeyStore.getInstance(ssl.getTrustStoreType());
            ts.load(Resource.newResource(trustStore).getInputStream(), trustStorePassword.toCharArray());
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(ssl
                            .getTrustManagerFactoryAlgorithm());
            trustManagerFactory.init(ts);
            context.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(),
                            new java.security.SecureRandom());
        } else {
            context.init(keyManagerFactory.getKeyManagers(), null, new java.security.SecureRandom());
        }
        factory = context.getSocketFactory();
    }

    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException,
                    UnknownHostException {
        return factory.createSocket(socket, host, port, autoClose);
    }

    public Socket createSocket(String host, int port, InetAddress localAddress, int localPort) throws IOException,
                    UnknownHostException {
        return factory.createSocket(host, port, localAddress, localPort);
    }

    public Socket createSocket(String host, int port, InetAddress localAddress, int localPort,
                    HttpConnectionParams params) throws IOException, UnknownHostException, ConnectTimeoutException {
        if (params == null) {
            throw new IllegalArgumentException("Parameters may not be null");
        }
        int timeout = params.getConnectionTimeout();
        if (timeout == 0) {
            return createSocket(host, port, localAddress, localPort);
        } else {
            Socket socket = factory.createSocket();
            SocketAddress localaddr = new InetSocketAddress(localAddress, localPort);
            SocketAddress remoteaddr = new InetSocketAddress(host, port);
            socket.bind(localaddr);
            socket.connect(remoteaddr, timeout);
            return socket;
        }
    }

    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        return factory.createSocket(host, port);
    }

}
