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

import org.slf4j.Logger;

import javax.jbi.component.ServiceUnitManager;
import javax.jbi.management.DeploymentException;
import javax.jbi.management.LifeCycleMBean;

/**
 * <p>
 * A simple service unit manager.
 * This service unit manager uses {@link Deployer} objects
 * to handle different type of service units.
 * </p>
 *
 * @author Guillaume Nodet
 * @version $Revision$
 * @since 3.0
 */
public class BaseServiceUnitManager implements ServiceUnitManager {

    private final transient Logger logger;
    
    protected ServiceMixComponent component;
    
    protected Deployer[] deployers;
    
    protected boolean persistent;
    
    public BaseServiceUnitManager(ServiceMixComponent component, Deployer[] deployers) {
        this(component, deployers, false);
    }

    public BaseServiceUnitManager(ServiceMixComponent component, Deployer[] deployers, boolean persistent) {
        this.component = component;
        this.logger = component.getLogger();
        this.deployers = deployers;
        this.persistent = persistent;
    }
    
    /* (non-Javadoc)
     * @see javax.jbi.component.ServiceUnitManager#deploy(java.lang.String, java.lang.String)
     */
    public synchronized String deploy(String serviceUnitName, String serviceUnitRootPath) throws DeploymentException {
        try {
            logger.debug("Deploying service unit");
            if (serviceUnitName == null || serviceUnitName.length() == 0) {
                throw new IllegalArgumentException("serviceUnitName should be non null and non empty");
            }
            if (getServiceUnit(serviceUnitName) != null) {
                throw failure("deploy", "Service Unit '" + serviceUnitName + "' is already deployed", null);
            }
            ServiceUnit su = doDeploy(serviceUnitName, serviceUnitRootPath);
            if (su == null) {
                throw failure("deploy", "Unable to find suitable deployer for Service Unit '" + serviceUnitName + "'", null);
            }
            component.getRegistry().registerServiceUnit(su);
            logger.debug("Service unit deployed");
            return createSuccessMessage("deploy");
        } catch (DeploymentException e) {
            throw e;
        } catch (Exception e) {
            throw failure("deploy", "Unable to deploy service unit", e);
        }
    }
    
    protected ServiceUnit doDeploy(String serviceUnitName, String serviceUnitRootPath) throws Exception {
        for (int i = 0; i < deployers.length; i++) {
            if (deployers[i].canDeploy(serviceUnitName, serviceUnitRootPath)) {
                return deployers[i].deploy(serviceUnitName, serviceUnitRootPath);
            }
        }
        return null;
    }

    /* (non-Javadoc)
     * @see javax.jbi.component.ServiceUnitManager#init(java.lang.String, java.lang.String)
     */
    public synchronized void init(String serviceUnitName, String serviceUnitRootPath) throws DeploymentException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            logger.debug("Initializing service unit");
            if (serviceUnitName == null || serviceUnitName.length() == 0) {
                throw new IllegalArgumentException("serviceUnitName should be non null and non empty");
            }
            ServiceUnit su = getServiceUnit(serviceUnitName);
            if (su == null) {
                if (!persistent) {
                    su = doDeploy(serviceUnitName, serviceUnitRootPath);
                    if (su == null) {
                        throw failure("init", "Unable to find suitable deployer for Service Unit '" + serviceUnitName + "'", null);
                    }
                    component.getRegistry().registerServiceUnit(su);
                } else {
                    throw failure("init", "Service Unit '" + serviceUnitName + "' is not deployed", null);
                }
            }
            if (!LifeCycleMBean.SHUTDOWN.equals(su.getCurrentState())) {
                throw failure("init", "ServiceUnit should be in a SHUTDOWN state", null);
            }
            Thread.currentThread().setContextClassLoader(su.getConfigurationClassLoader());
            su.init();
            logger.debug("Service unit initialized");
        } catch (DeploymentException e) {
            throw e;
        } catch (Exception e) {
            throw failure("init", "Unable to init service unit", e);
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }

    /* (non-Javadoc)
     * @see javax.jbi.component.ServiceUnitManager#start(java.lang.String)
     */
    public synchronized void start(String serviceUnitName) throws DeploymentException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            logger.debug("Starting service unit");
            if (serviceUnitName == null || serviceUnitName.length() == 0) {
                throw new IllegalArgumentException("serviceUnitName should be non null and non empty");
            }
            ServiceUnit su = (ServiceUnit) getServiceUnit(serviceUnitName);
            if (su == null) {
                throw failure("start", "Service Unit '" + serviceUnitName + "' is not deployed", null);
            }
            if (!LifeCycleMBean.STOPPED.equals(su.getCurrentState())) {
                throw failure("start", "ServiceUnit should be in a STOPPED state", null);
            }
            Thread.currentThread().setContextClassLoader(su.getConfigurationClassLoader());
            su.start();
            logger.debug("Service unit started");
        } catch (DeploymentException e) {
            throw e;
        } catch (Exception e) {
            throw failure("start", "Unable to start service unit", e);
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }

    /* (non-Javadoc)
     * @see javax.jbi.component.ServiceUnitManager#stop(java.lang.String)
     */
    public synchronized void stop(String serviceUnitName) throws DeploymentException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            logger.debug("Stopping service unit");
            if (serviceUnitName == null || serviceUnitName.length() == 0) {
                throw new IllegalArgumentException("serviceUnitName should be non null and non empty");
            }
            ServiceUnit su = getServiceUnit(serviceUnitName);
            if (su == null) {
                throw failure("stop", "Service Unit '" + serviceUnitName + "' is not deployed", null);
            }
            if (!LifeCycleMBean.STARTED.equals(su.getCurrentState())) {
                throw failure("stop", "ServiceUnit should be in a STARTED state", null);
            }
            Thread.currentThread().setContextClassLoader(su.getConfigurationClassLoader());
            su.stop();
            logger.debug("Service unit stopped");
        } catch (DeploymentException e) {
            throw e;
        } catch (Exception e) {
            throw failure("stop", "Unable to stop service unit", e);
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }

    /* (non-Javadoc)
     * @see javax.jbi.component.ServiceUnitManager#shutDown(java.lang.String)
     */
    public synchronized void shutDown(String serviceUnitName) throws DeploymentException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            logger.debug("Shutting down service unit");
            if (serviceUnitName == null || serviceUnitName.length() == 0) {
                throw new IllegalArgumentException("serviceUnitName should be non null and non empty");
            }
            ServiceUnit su = getServiceUnit(serviceUnitName);
            if (su == null) {
                throw failure("shutDown", "Service Unit '" + serviceUnitName + "' is not deployed", null);
            }
            if (!LifeCycleMBean.STOPPED.equals(su.getCurrentState())) {
                throw failure("shutDown", "ServiceUnit should be in a STOPPED state", null);
            }
            Thread.currentThread().setContextClassLoader(su.getConfigurationClassLoader());
            su.shutDown();
            logger.debug("Service unit shut down");
        } catch (DeploymentException e) {
            throw e;
        } catch (Exception e) {
            throw failure("shutDown", "Unable to shutdown service unit", e);
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }

    /* (non-Javadoc)
     * @see javax.jbi.component.ServiceUnitManager#undeploy(java.lang.String, java.lang.String)
     */
    public synchronized String undeploy(String serviceUnitName, String serviceUnitRootPath) throws DeploymentException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            logger.debug("Undeploying service unit");
            logger.debug("Shutting down service unit");
            if (serviceUnitName == null || serviceUnitName.length() == 0) {
                throw new IllegalArgumentException("serviceUnitName should be non null and non empty");
            }
            ServiceUnit su = getServiceUnit(serviceUnitName);
            if (su == null) {
                throw failure("undeploy", "Service Unit '" + serviceUnitName + "' is not deployed", null);
            }
            if (!LifeCycleMBean.SHUTDOWN.equals(su.getCurrentState())) {
                throw failure("undeploy", "ServiceUnit should be in a SHUTDOWN state", null);
            }
            Thread.currentThread().setContextClassLoader(su.getConfigurationClassLoader());
            if (serviceUnitRootPath != null) {
                doUndeploy(su);
            }
            component.getRegistry().unregisterServiceUnit(su);
            logger.debug("Service unit undeployed");
            return createSuccessMessage("undeploy");
        } catch (DeploymentException e) {
            throw e;
        } catch (Exception e) {
            throw failure("undeploy", "Unable to undeploy service unit", e);
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }

    protected void doUndeploy(ServiceUnit su) throws Exception {
        for (int i = 0; i < deployers.length; i++) {
            if (deployers[i].canDeploy(su.getName(), su.getRootPath())) {
                deployers[i].undeploy(su);
                return;
            }
        }
        throw failure("undeploy", "Unable to find suitable deployer for Service Unit '" + su.getName() + "'", null);
    }
    
    protected DeploymentException failure(String task, String info, Exception e) throws DeploymentException {
        ManagementSupport.Message msg = new ManagementSupport.Message();
        msg.setComponent(component.getComponentName());
        msg.setTask(task);
        msg.setResult("FAILED");
        msg.setType("ERROR");
        msg.setException(e);
        msg.setMessage(info);
        return new DeploymentException(ManagementSupport.createComponentMessage(msg));
    }

    protected String createSuccessMessage(String task) {
        ManagementSupport.Message msg = new ManagementSupport.Message();
        msg.setComponent(component.getComponentName());
        msg.setTask(task);
        msg.setResult("SUCCESS");
        return ManagementSupport.createComponentMessage(msg);
    }
    
    protected ServiceUnit getServiceUnit(String name) {
        return component.getRegistry().getServiceUnit(name);
    }
    
}
