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
package org.apache.servicemix.http.jetty;

import org.apache.servicemix.common.security.KeystoreManager;
import org.eclipse.jetty.server.ssl.SslSocketConnector;

import javax.net.ssl.SSLServerSocketFactory;

public class ServiceMixSslSocketConnector extends SslSocketConnector {

    private String keyAlias;

    private KeystoreManager keystoreManager;

    /**
     * @return the keystoreManager
     */
    public KeystoreManager getKeystoreManager() {
        return keystoreManager;
    }

    /**
     * @param keystoreManager
     *            the keystoreManager to set
     */
    public void setKeystoreManager(KeystoreManager keystoreManager) {
        this.keystoreManager = keystoreManager;
    }

    /**
     * @return the keyAlias
     */
    public String getKeyAlias() {
        return keyAlias;
    }

    /**
     * @param keyAlias
     *            the keyAlias to set
     */
    public void setKeyAlias(String keyAlias) {
        this.keyAlias = keyAlias;
    }

    protected SSLServerSocketFactory createFactory() throws Exception {
        return keystoreManager.createSSLServerFactory(getProvider(), getProtocol(), getSslKeyManagerFactoryAlgorithm(),
                        getKeystore(), getKeyAlias(), getTruststore());
    }
}
