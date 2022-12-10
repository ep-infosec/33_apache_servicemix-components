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
package org.apache.servicemix.common.osgi;

import java.util.Map;

import javax.jbi.management.DeploymentException;

import org.apache.servicemix.common.Endpoint;
import org.apache.servicemix.common.DefaultComponent;
import org.apache.servicemix.common.DefaultServiceUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * This class is used by components bundles to track endpoints that they know about.
 * Endpoints are wrapped into {@link EndpointWrapper} interfaces to be able to access
 * the underlying object easily and bypass spring-DM proxies.
 * </p>
 */
public class EndpointTracker {

    private final Logger logger = LoggerFactory.getLogger(EndpointTracker.class);

    protected DefaultComponent component;

    public DefaultComponent getComponent() {
        return component;
    }

    public void setComponent(DefaultComponent component) {
        this.component = component;
    }

    public void register(EndpointWrapper wrapper, Map properties) throws Exception {
        logger.debug("[" + component.getComponentName() + "] Endpoint registered with properties: " + properties);
        Endpoint endpoint = wrapper.getEndpoint();
        if (component.isKnownEndpoint(endpoint)) {
            logger.debug("[" + component.getComponentName() + "] Endpoint recognized");
            try {
                OsgiServiceUnit su = new OsgiServiceUnit(component, endpoint, wrapper.getClassLoader());
                component.getRegistry().registerServiceUnit(su);
            } finally {
                //get chance to do some clean up
                wrapper.setDeployed();
            }
        }
    }

    public void unregister(EndpointWrapper wrapper, Map properties) throws Exception {
        // The endpoints are deployed by the JBI deployer when the DeployedAssembly is processed.
        // The JBI deployer is responsible for managing the SA lifecycle and will undeploy the SU itself
    }

    public static class OsgiServiceUnit extends DefaultServiceUnit {
        private final Endpoint endpoint;
        private final ClassLoader classLoader;
        public OsgiServiceUnit(DefaultComponent component, Endpoint endpoint, ClassLoader classLoader) throws DeploymentException {
            this.component = component;
            this.endpoint = endpoint;
            this.classLoader = classLoader;
            this.endpoint.setServiceUnit(this);
            this.endpoint.validate();
            this.name = endpoint.getKey();
            addEndpoint(this.endpoint);
        }
        public Endpoint getEndpoint() {
            return endpoint;
        }
        public ClassLoader  getConfigurationClassLoader() {
            if (classLoader != null) {
                return classLoader;
            } else {
                return super.getConfigurationClassLoader();
            }
        }
    }

}
