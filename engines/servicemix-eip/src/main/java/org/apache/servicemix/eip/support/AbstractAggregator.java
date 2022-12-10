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
package org.apache.servicemix.eip.support;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;

import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.messaging.RobustInOnly;

import org.apache.servicemix.common.JbiConstants;
import org.apache.servicemix.common.util.MessageUtil;
import org.apache.servicemix.eip.EIPEndpoint;
import org.apache.servicemix.store.Store;
import org.apache.servicemix.store.StoreFactory;
import org.apache.servicemix.store.memory.MemoryStore;
import org.apache.servicemix.store.memory.MemoryStoreFactory;
import org.apache.servicemix.timers.Timer;
import org.apache.servicemix.timers.TimerListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aggregator can be used to wait and combine several messages.
 * This component implements the
 * <a href="http://www.enterpriseintegrationpatterns.com/Aggregator.html">Aggregator</a>
 * pattern.
 *
 * Closed aggregations are being kept in a {@link Store}.  By default, we will use a simple 
 * {@link MemoryStore}, but you can set your own {@link StoreFactory} to use other implementations.
 * 
 * TODO: distributed lock manager
 * TODO: persistent / transactional timer
 *
 * @author gnodet
 * @version $Revision: 376451 $
 */
public abstract class AbstractAggregator extends EIPEndpoint {

    private final Logger logger = LoggerFactory.getLogger(AbstractAggregator.class);

    private ExchangeTarget target;
    
    private boolean rescheduleTimeouts;
    
    private boolean synchronous;

    private Store closedAggregates;
    private StoreFactory closedAggregatesStoreFactory;

    private boolean copyProperties = true;

    private boolean copyAttachments = true;

    private boolean reportErrors = false;

    private boolean reportClosedAggregatesAsErrors = false;
    
    private boolean reportTimeoutAsErrors;
    
    private ConcurrentMap<String, Timer> timers = new ConcurrentHashMap<String, Timer>();
    
    /**
     * @return the synchronous
     */
    public boolean isSynchronous() {
        return synchronous;
    }

    /**
     * Boolean flag that Controls whether the aggregate (when ready) will be sent synchronously or not.
     * On ServiceMix 3.x, this can have an effect upon the flow and transaction semantics.
     * The default value is <code>false</code>.
     *
     * @param synchronous the synchronous to set
     */
    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }

    /**
     * @return the rescheduleTimeouts
     */
    public boolean isRescheduleTimeouts() {
        return rescheduleTimeouts;
    }

    /**
     * Boolean flag controlling if aggregate timeouts are rescheduled each time a new message is added
     * to the aggregate.  If <code>false</code>, the timeout will expire when the specified amount of
     * time elapsed after the first message is received for this aggregate.  If <code>true</code>,
     * the timeout will expire when the specified amount of time elapsed after the last message is received.
     * The default value is <code>false</code>.
     *
     * @param rescheduleTimeouts the rescheduleTimeouts to set
     */
    public void setRescheduleTimeouts(boolean rescheduleTimeouts) {
        this.rescheduleTimeouts = rescheduleTimeouts;
    }

    /**
     * @return the target
     */
    public ExchangeTarget getTarget() {
        return target;
    }

    /**
     * The exchange target that will be used to send the aggregate to.
     *
     * @param target the target to set
     */
    public void setTarget(ExchangeTarget target) {
        this.target = target;
    }

    public boolean isCopyProperties() {
        return copyProperties;
    }

    /**
     * Copy all properties from the incoming messages to the aggregated
     * message.  Default value is <code>true</code>.
     *
     * @param copyProperties
     */
    public void setCopyProperties(boolean copyProperties) {
        this.copyProperties = copyProperties;
    }

    public boolean isCopyAttachments() {
        return copyAttachments;
    }

    /**
     * Copy all attachments from the incoming messages to the aggregated
     * message.  Default value is <code>true</code>.
     *
     * @param copyAttachments
     */
    public void setCopyAttachments(boolean copyAttachments) {
        this.copyAttachments = copyAttachments;
    }

    public boolean isReportErrors() {
        return reportErrors;
    }

    /**
     * Sets whether the aggregator should report errors happening when sending the
     * aggregate on all exchanges that compose the aggregate.
     * The default value is <code>false</code>, meaning that if any error occur, this
     * error will be lost.
     * Note that if this flag is set to <code>true</code>, all exchanges received as part of a given aggregate
     * will be hold until the aggregate is sent and the DONE / ERROR status is received back.
     *
     * @param reportErrors <code>boolean</code> indicating if errors should be reported back to consumers
     */
    public void setReportErrors(boolean reportErrors) {
        this.reportErrors = reportErrors;
    }

    public boolean isReportClosedAggregatesAsErrors() {
        return reportClosedAggregatesAsErrors;
    }

    /**
     * Sets whether the aggregator should report errors on incoming exchanges received after a given
     * aggregate has been closed.
     * The default value is <code>false</code>, meaning that such exchanges will be silently sent back
     * with a DONE status and discarded with respect to the aggregation process.
     *
     * @param reportClosedAggregatesAsErrors <code>boolean</code> indicating if exchanges received for a
     *           closed aggregates should be send back with an ERROR status
     */
    public void setReportClosedAggregatesAsErrors(boolean reportClosedAggregatesAsErrors) {
        this.reportClosedAggregatesAsErrors = reportClosedAggregatesAsErrors;
    }
    
    /**
     * Sets whether the aggregator should reports errors on incoming exchanges already received when
     * a timeout occurs.
     * The default value is <code>false</code>, meaning that such exchanges will be silently sent back
     * a DONE status.
     *  
     * @param reportTimeoutAsErrors <code>boolean</code> indicating if exchanges received prior to a
     *             timeout should be sent back with an ERROR status
     */
    public void setReportTimeoutAsErrors(boolean reportTimeoutAsErrors) {
        this.reportTimeoutAsErrors = reportTimeoutAsErrors;
    }

    /**
     * @return the reportTimeoutAsErrors
     */
    public boolean isReportTimeoutAsErrors() {
        return reportTimeoutAsErrors;
    }

    /* (non-Javadoc)
     * @see org.apache.servicemix.eip.EIPEndpoint#processSync(javax.jbi.messaging.MessageExchange)
     */
    protected void processSync(MessageExchange exchange) throws Exception {
        throw new IllegalStateException();
    }
    
    /**
     * Access the currently configured {@link StoreFactory} for storing closed aggregations
     */
    public StoreFactory getClosedAggregatesStoreFactory() {
        return closedAggregatesStoreFactory;
    }

    /**
     * Set a new {@link StoreFactory} for creating the {@link Store} to hold closed aggregations.
     * 
     * If it hasn't been set, a simple {@link MemoryStoreFactory} will be used by default.
     * 
     * @param closedAggregatesStoreFactory
     */
    public void setClosedAggregatesStoreFactory(StoreFactory closedAggregatesStoreFactory) {
        this.closedAggregatesStoreFactory = closedAggregatesStoreFactory;
    }

    /* (non-Javadoc)
     * @see org.apache.servicemix.eip.EIPEndpoint#processAsync(javax.jbi.messaging.MessageExchange)
     */
    protected void processAsync(MessageExchange exchange) throws Exception {
        throw new IllegalStateException();
    }
    
    @Override
    public void start() throws Exception {
        super.start();
        if (closedAggregatesStoreFactory == null) {
            closedAggregatesStoreFactory = new MemoryStoreFactory();
        }
        closedAggregates = closedAggregatesStoreFactory.open(getService().toString() + getEndpoint() + "-closed-aggregates");
        if (reportTimeoutAsErrors && !reportErrors) {
            throw new IllegalArgumentException(
                    "ReportTimeoutAsErrors property may only be set if ReportTimeout property is also set!");
        }
    }

    /* (non-Javadoc)
     * @see org.apache.servicemix.common.ExchangeProcessor#process(javax.jbi.messaging.MessageExchange)
     */
    public void process(MessageExchange exchange) throws Exception {
        // Handle an exchange as a PROVIDER
        if (exchange.getRole() == MessageExchange.Role.PROVIDER) {
            if (exchange.getStatus() != ExchangeStatus.ACTIVE) {
                // ignore DONE / ERROR status from consumers
                return;
            }
            if (!(exchange instanceof InOnly)
                && !(exchange instanceof RobustInOnly)) {
                fail(exchange, new UnsupportedOperationException("Use an InOnly or RobustInOnly MEP"));
            } else {
                processProvider(exchange);
            }
        // Handle an exchange as a CONSUMER
        } else {
            if (exchange.getStatus() == ExchangeStatus.ACTIVE) {
                throw new IllegalStateException("Unexpected active consumer exchange received");
            }
            if (reportErrors) {
                String corrId = (String) exchange.getProperty(getService().toString() + ":" + getEndpoint() + ":correlation");
                List<MessageExchange> exchanges = (List<MessageExchange>) store.load(corrId + "-exchanges");
                if (exchanges != null) {
                    for (MessageExchange me : exchanges) {
                        if (exchange.getStatus() == ExchangeStatus.ERROR) {
                            me.setError(exchange.getError());
                        }
                        me.setStatus(exchange.getStatus());
                        send(me);
                    }
                }
            }
        }
    }

    private void processProvider(MessageExchange exchange) throws Exception {
        final String processCorrelationId = (String) exchange.getProperty(JbiConstants.CORRELATION_ID);

        NormalizedMessage in = MessageUtil.copyIn(exchange);
        final String correlationId = getCorrelationID(exchange, in);
        if (correlationId == null || correlationId.length() == 0) {
            throw new IllegalArgumentException("Could not retrieve correlation id for incoming exchange");
        }
        // Load existing aggregation
        Lock lock = getLockManager().getLock(correlationId);
        lock.lock();
        boolean removeLock = true;
        try {
            Object aggregation = store.load(correlationId);
            Date timeout = null;
            // Create a new aggregate
            if (aggregation == null) {
                if (isAggregationClosed(correlationId)) {
                    // TODO: should we return an error here ?
                } else {
                    aggregation = createAggregation(correlationId);
                    timeout = getTimeout(aggregation);
                }
            } else if (isRescheduleTimeouts()) {
                timeout = getTimeout(aggregation);
            }
            // If the aggregation is not closed
            if (aggregation != null) {
                if (reportErrors) {
                    List<MessageExchange> exchanges = (List<MessageExchange>) store.load(correlationId + "-exchanges");
                    if (exchanges == null) {
                        exchanges = new ArrayList<MessageExchange>();
                    }
                    exchanges.add(exchange);
                    store.store(correlationId + "-exchanges", exchanges);
                    removeLock = false;
                }
                if (addMessage(aggregation, in, exchange)) {
                    sendAggregate(processCorrelationId, correlationId, aggregation, false, isSynchronous(exchange));
                } else {
                    store.store(correlationId, aggregation);
                    if (timeout != null) {
                        logger.debug("Scheduling timeout at {} for aggregate {}", timeout, correlationId);
                        Timer t = getTimerManager().schedule(new TimerListener() {
                            public void timerExpired(Timer timer) {
                                AbstractAggregator.this.onTimeout(processCorrelationId, correlationId, timer);
                            }
                        }, timeout);
                        timers.put(correlationId, t);
                    }
                    removeLock = false;
                }
                if (!reportErrors) {
                    done(exchange);
                }
            } else {
                if (reportClosedAggregatesAsErrors) {
                    fail(exchange, new ClosedAggregateException());
                } else {
                    done(exchange);
                }
            }
        } finally {
            try {
                lock.unlock();
            } catch (Exception ex) {
                logger.info("Caught exception while attempting to release aggregation lock", ex);
            }
            if (removeLock) {
                lockManager.removeLock(correlationId);
            }
        }
    }

    protected void sendAggregate(String processCorrelationId,
                                 String correlationId,
                                 Object aggregation,
                                 boolean timeout,
                                 boolean sync) throws Exception {
        InOnly me = getExchangeFactory().createInOnlyExchange();
        if (processCorrelationId != null) {
            me.setProperty(JbiConstants.CORRELATION_ID, processCorrelationId);
        }
        me.setProperty(getService().toString() + ":" + getEndpoint() + ":correlation", correlationId);
        target.configureTarget(me, getContext());
        NormalizedMessage nm = me.createMessage();
        me.setInMessage(nm);
        buildAggregate(aggregation, nm, me, timeout);
        closeAggregation(correlationId);
        if (sync) {
            sendSync(me);
        } else {
            send(me);
        }
    }

    protected void onTimeout(String processCorrelationId, String correlationId, Timer timer) {
        logger.debug("Timeout expired for aggregate {}", correlationId);
        Lock lock = getLockManager().getLock(correlationId);
        lock.lock();
        try {
            // the timeout event could have been fired before timer was canceled
            Timer t = timers.get(correlationId);
            if (t == null || !t.equals(timer)) {
                return;
            }
            timers.remove(correlationId);
            Object aggregation = store.load(correlationId);
            if (aggregation != null) {
                if (reportTimeoutAsErrors) {
                    List<MessageExchange> exchanges = (List<MessageExchange>) store.load(correlationId + "-exchanges");
                    if (exchanges != null) {
                        TimeoutException timeoutException = new TimeoutException();
                        for (MessageExchange me : exchanges) {
                            me.setError(timeoutException);
                            me.setStatus(ExchangeStatus.ERROR);
                            send(me);
                        }
                    }
                    closeAggregation(correlationId);
                } else {
                    sendAggregate(processCorrelationId, correlationId, aggregation, true, isSynchronous());
                }
            } else if (!isAggregationClosed(correlationId)) {
                throw new IllegalStateException("Aggregation is not closed, but can not be retrieved from the store");
            } else {
                logger.debug("Aggregate {} is closed", correlationId);
            }
        } catch (Exception e) {
            logger.info("Caught exception while processing timeout aggregation", e);
        } finally {
            try {
                lock.unlock();
            } catch (Exception ex) {
                logger.info("Caught exception while attempting to release timeout aggregation lock", ex);
            } 
            lockManager.removeLock(correlationId);
        }
    }

    /**
     * Check if the aggregation with the given correlation id is closed or not.
     * Called when the aggregation has not been found in the store.
     *
     * @param correlationId
     * @return
     * @throws Exception 
     */
    protected boolean isAggregationClosed(String correlationId) throws Exception {
        // TODO: implement this using a persistent / cached behavior
        Object data = closedAggregates.load(correlationId);
        if (data != null) {
            closedAggregates.store(correlationId, data);
        }
        return data != null;
    }

    /**
     * Mark an aggregation as closed
     * @param correlationId
     * @throws Exception 
     */
    protected void closeAggregation(String correlationId) throws Exception {
        // TODO: implement this using a persistent / cached behavior
        closedAggregates.store(correlationId, Boolean.TRUE);
    }

    private boolean isSynchronous(MessageExchange exchange) {
        return isSynchronous()
                || (exchange.isTransacted() && Boolean.TRUE.equals(exchange.getProperty(JbiConstants.SEND_SYNC)));
    }

    /**
     * Retrieve the correlation ID of the given exchange
     * @param exchange
     * @param message
     * @return the correlationID
     * @throws Exception
     */
    protected abstract String getCorrelationID(MessageExchange exchange, NormalizedMessage message) throws Exception;

    /**
     * Creates a new empty aggregation.
     * @param correlationID
     * @return a newly created aggregation
     */
    protected abstract Object createAggregation(String correlationID) throws Exception;

    /**
     * Returns the date when the onTimeout method should be called if the aggregation is not completed yet,
     * or null if the aggregation has no timeout.
     *
     * @param aggregate
     * @return
     */
    protected abstract Date getTimeout(Object aggregate);

    /**
     * Add a newly received message to this aggregation
     *
     * @param aggregate
     * @param message
     * @param exchange
     * @return <code>true</code> if the aggregate id complete
     */
    protected abstract boolean addMessage(Object aggregate,
                                          NormalizedMessage message,
                                          MessageExchange exchange) throws Exception;

    /**
     * Fill the given JBI message with the aggregation result.
     *
     * @param aggregate
     * @param message
     * @param exchange
     * @param timeout <code>false</code> if the aggregation has completed or <code>true</code>
     *                  if this aggregation has timed out
     */
    protected abstract void buildAggregate(Object aggregate,
                                           NormalizedMessage message,
                                           MessageExchange exchange,
                                           boolean timeout) throws Exception;

    /**
     * Error used to report that the aggregate has already been closed
     */
    public static class ClosedAggregateException extends Exception {
    }
}
