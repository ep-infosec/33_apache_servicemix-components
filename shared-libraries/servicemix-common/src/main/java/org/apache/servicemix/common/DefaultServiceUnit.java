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
package org.apache.servicemix.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.management.LifeCycleMBean;

public class DefaultServiceUnit implements ServiceUnit {

    protected ServiceMixComponent component;

    protected String name;

    protected String rootPath;

    protected String status = LifeCycleMBean.SHUTDOWN;

    protected Map<String, Endpoint> endpoints = new LinkedHashMap<String, Endpoint>();

    public DefaultServiceUnit() {
    }

    public DefaultServiceUnit(ServiceMixComponent component) {
        this.component = component;
    }

    public synchronized void init() throws Exception {
        if (this.status == LifeCycleMBean.SHUTDOWN) {
            // Activate endpoints
            List<Endpoint> activated = new ArrayList<Endpoint>();
            try {
                for (Endpoint endpoint : getEndpoints()) {
                    endpoint.activate();
                    activated.add(endpoint);
                }
                this.status = LifeCycleMBean.STOPPED;
            } catch (Exception e) {
                // Deactivate activated endpoints
                for (Endpoint endpoint : activated) {
                    try {
                        endpoint.deactivate();
                    } catch (Exception e2) {
                        // do nothing
                    }
                }
                throw e;
            }
        }
    }

    public synchronized void start() throws Exception {
        if (this.status == LifeCycleMBean.STOPPED) {
            // Activate endpoints
            List<Endpoint> activated = new ArrayList<Endpoint>();
            try {
                for (Endpoint endpoint : getEndpoints()) {
                    endpoint.start();
                }
                this.status = LifeCycleMBean.STARTED;
            } catch (Exception e) {
                // Deactivate activated endpoints
                for (Endpoint endpoint : activated) {
                    try {
                        endpoint.stop();
                    } catch (Exception e2) {
                        // do nothing
                    }
                }
                throw e;
            }
        }
    }

    public synchronized void stop() throws Exception {
        if (this.status == LifeCycleMBean.STARTED) {
            this.status = LifeCycleMBean.STOPPED;
            // Stop endpoints
            Exception exception = null;
            for (Endpoint endpoint : getEndpoints()) {
                try {
                    endpoint.stop();
                } catch (Exception e) {
                    exception = e;
                }
            }
            if (exception != null) {
                throw exception;
            }
        }
    }

    public synchronized void shutDown() throws Exception {
        if (this.status == LifeCycleMBean.STARTED) {
            stop();
        }
        if (this.status == LifeCycleMBean.STOPPED) {
            this.status = LifeCycleMBean.SHUTDOWN;
            // Deactivate endpoints
            Exception exception = null;
            for (Endpoint endpoint : getEndpoints()) {
                try {
                    // TODO: uncomment when all tests in various components work fine
                    // TODO: we also need to find a way to not wait forever on this call
                    //component.prepareShutdown(endpoint);
                    endpoint.deactivate();
                } catch (Exception e) {
                    exception = e;
                }
            }
            if (exception != null) {
                throw exception;
            }
        }
    }

    public String getCurrentState() {
        return status;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    /**
     * @return Returns the component.
     */
    public ServiceMixComponent getComponent() {
        return component;
    }

    /**
     * @param component
     *            The component to set.
     */
    public void setComponent(ServiceMixComponent component) {
        this.component = component;
    }

    public Collection<Endpoint> getEndpoints() {
        return this.endpoints.values();
    }

    public synchronized void addEndpoint(Endpoint endpoint) throws DeploymentException {
        String key = endpoint.getKey();
        if (this.endpoints.put(key, endpoint) != null) {
            throw new DeploymentException(
                    "More than one endpoint found in the SU for key: " + key);
        }
        if (this.status == LifeCycleMBean.STOPPED) {
            try {
                endpoint.activate();
            } catch (Exception e) {
                throw new DeploymentException(e);
            }
        } else if (this.status == LifeCycleMBean.STARTED) {
            try {
                endpoint.activate();
                endpoint.start();
            } catch (Exception e) {
                throw new DeploymentException(e);
            }
        }
    }

    public synchronized void removeEndpoint(Endpoint endpoint) throws DeploymentException {
        String key = endpoint.getKey();
        if (this.endpoints.remove(key) == null) {
            throw new DeploymentException("Endpoint not found in the SU for key: " + EndpointSupport.getKey(endpoint));
        }
        if (this.status == LifeCycleMBean.STOPPED) {
            try {
                component.prepareShutdown(endpoint);
                endpoint.deactivate();
            } catch (Exception e) {
                throw new DeploymentException(e);
            }
        } else if (this.status == LifeCycleMBean.STARTED) {
            try {
                endpoint.stop();
                component.prepareShutdown(endpoint);
                endpoint.deactivate();
            } catch (Exception e) {
                throw new DeploymentException(e);
            }
        }
    }

    public Endpoint getEndpoint(String key) {
        return this.endpoints.get(key);
    }

    public ClassLoader getConfigurationClassLoader() {
        return component.getClass().getClassLoader();
    }

}
