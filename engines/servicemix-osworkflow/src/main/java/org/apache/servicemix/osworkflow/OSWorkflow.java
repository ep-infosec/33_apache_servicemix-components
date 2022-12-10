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
package org.apache.servicemix.osworkflow;

import java.util.Map;

import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.RobustInOnly;


import com.opensymphony.workflow.InvalidInputException;
import com.opensymphony.workflow.InvalidRoleException;
import com.opensymphony.workflow.Workflow;
import com.opensymphony.workflow.WorkflowException;
import com.opensymphony.workflow.basic.BasicWorkflow;
import com.opensymphony.workflow.config.DefaultConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OSWorkflow implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(OSWorkflow.class);

    public static final String KEY_EXCHANGE = "exchange";

    public static final String KEY_IN_MESSAGE = "in-message";

    public static final String KEY_ENDPOINT = "endpoint";

    public static final String KEY_CALLER = "caller";

    public static final String KEY_ASYNC_PROCESSING = "asynchronous";
    
    private Workflow osWorkflowInstance;

    private String caller;

    private String osWorkflowName;

    private Map map;

    private int action = -1;

    private long workflowId = -1L;

    private boolean finished;

    private boolean aborted;

    private OSWorkflowEndpoint endpoint;

    private MessageExchange exchange;

    /**
     * creates and initializes a new workflow object
     * 
     * @param ep
     *            the endpoint reference
     * @param workflowName
     *            the unique workflow name as defined in workflows.xml
     * @param action
     *            the initial action
     * @param map
     *            the value map
     * @param caller
     *            the caller
     * @param exchange
     *            the received message exchange
     */
    public OSWorkflow(OSWorkflowEndpoint ep, String workflowName, int action,
            Map map, String caller, MessageExchange exchange) {

        this.endpoint = ep; // remember the endpoint which called the osworkflow
        this.osWorkflowName = workflowName;
        this.osWorkflowInstance = null;
        this.action = action;
        this.map = map;
        this.caller = caller;
        this.exchange = exchange;

        // now fill the transient vars with some useful objects
        this.map.put(KEY_ENDPOINT, this.endpoint);
        this.map.put(KEY_CALLER, this.caller);
        this.map.put(KEY_IN_MESSAGE, this.exchange.getMessage("in"));
        this.map.put(KEY_EXCHANGE, this.exchange);
        this.map.put(KEY_ASYNC_PROCESSING, this.exchange instanceof InOnly || this.exchange instanceof RobustInOnly);
    }

    /**
     * initializes the workflow and a default config
     * 
     * @return the unique workflow id
     */
    private long createWorkflow() throws InvalidRoleException,
            InvalidInputException, WorkflowException {
        this.osWorkflowInstance = new BasicWorkflow(this.caller);
        DefaultConfiguration config = new DefaultConfiguration();
        this.osWorkflowInstance.setConfiguration(config);
        long wfId = this.osWorkflowInstance.initialize(this.osWorkflowName, this.action, this.map);
        return wfId;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Runnable#run()
     */
    public void run() {
        // call the endpoint method for init actions
        this.endpoint.preWorkflow();

        logger.debug("Starting workflow...");
        logger.debug("Name:       {}", this.osWorkflowName);
        logger.debug("Action:     {}", this.action);
        logger.debug("Caller:     {}", this.caller);
        logger.debug("Map:        {}", this.map);

        // loop as long as there are more actions to do and the workflow is not
        // finished or aborted
        while (!finished && !aborted) {
            // initial creation
            if (this.osWorkflowInstance == null) {
                try {
                    this.workflowId = createWorkflow();
                } catch (Exception ex) {
                    logger.error("Error creating the workflow", ex);
                    aborted = true;
                    break;
                }
            }

            // determine the available actions
            int[] availableActions = this.osWorkflowInstance.getAvailableActions(this.workflowId, this.map);

            // check if there are more actions available
            if (availableActions.length == 0) {
                // no, no more actions available - workflow finished
                logger.debug("No more actions. Workflow is finished...");
                this.finished = true;
            } else {
                // get first available action to execute
                int nextAction = availableActions[0];

                logger.debug("call action " + nextAction);
                try {
                    // call the action
                    this.osWorkflowInstance.doAction(this.workflowId,nextAction, this.map);
                } catch (InvalidInputException iiex) {
                    logger.error(iiex.getMessage());
                    aborted = true;
                } catch (WorkflowException wfex) {
                    logger.error(wfex.getMessage());
                    aborted = true;
                }
            }
        }

        logger.debug("Stopping workflow...");
        logger.debug("Name:       {}", this.osWorkflowName);
        logger.debug("Action:     {}", this.action);
        logger.debug("Caller:     {}", this.caller);
        logger.debug("Map:        {}", this.map);
        logger.debug("WorkflowId: {}", this.workflowId);
        logger.debug("End state:  {}", (finished ? "Finished" : "Aborted"));

        // call the endpoint method for cleanup actions or message exchange
        this.endpoint.postWorkflow();
    }
}
