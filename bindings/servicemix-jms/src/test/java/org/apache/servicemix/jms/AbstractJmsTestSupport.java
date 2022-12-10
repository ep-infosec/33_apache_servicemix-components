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
package org.apache.servicemix.jms;

import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.Fault;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.MessagingException;
import javax.naming.Context;
import javax.naming.InitialContext;

import junit.framework.TestCase;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.jndi.ActiveMQInitialContextFactory;
import org.apache.activemq.pool.PooledConnectionFactory;
import org.apache.activemq.xbean.BrokerFactoryBean;
import org.apache.servicemix.MessageExchangeListener;
import org.apache.servicemix.client.DefaultServiceMixClient;
import org.apache.servicemix.client.ServiceMixClient;
import org.apache.servicemix.components.util.ComponentSupport;
import org.apache.servicemix.jbi.container.JBIContainer;
import org.apache.servicemix.jbi.jaxp.StringSource;
import org.jencks.GeronimoPlatformTransactionManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jms.core.JmsTemplate;

public abstract class AbstractJmsTestSupport extends TestCase {
    
    protected JBIContainer container;
    protected BrokerService broker;
    protected ActiveMQConnectionFactory connectionFactory;
    protected JmsTemplate jmsTemplate;
    protected ServiceMixClient client;

    protected void setUp() throws Exception {
        createInitContext();
        createJmsBroker();
        createJbiContainer();
        createSmxClient();
        createJmsConnectionFactory();
        createJmsTemplate();
    }

    protected void tearDown() throws Exception {
        if (container != null) {
            container.stop();
            container.shutDown();
        }
        if (broker != null) {
            broker.stop();
        }
    }

    protected void createInitContext() throws Exception {
        System.setProperty(Context.INITIAL_CONTEXT_FACTORY, ActiveMQInitialContextFactory.class.getName());
        System.setProperty(Context.PROVIDER_URL, "vm://localhost");
    }

    protected void createJbiContainer() throws Exception {
        container = new JBIContainer();
        configureJbiContainer();
        container.init();
        container.start();
    }

    protected void createJmsBroker() throws Exception {
        BrokerFactoryBean bfb = new BrokerFactoryBean(new ClassPathResource("org/apache/servicemix/jms/activemq.xml"));
        bfb.afterPropertiesSet();
        broker = bfb.getBroker();
        configureJmsBroker();
        broker.start();
    }

    protected void createSmxClient() throws Exception {
        client = new DefaultServiceMixClient(container);
    }

    protected void createJmsConnectionFactory() throws Exception {
        connectionFactory = new ActiveMQConnectionFactory("vm://localhost");
    }

    protected void createJmsTemplate() throws Exception {
        jmsTemplate = new JmsTemplate(new PooledConnectionFactory(connectionFactory));
    }

    protected void configureJbiContainer() throws Exception {
        container.setEmbedded(true);
        container.setUseMBeanServer(false);
        container.setCreateMBeanServer(false);
        container.setCreateJmxConnector(false);
        container.setRootDir("target/smx-data");
        container.setMonitorInstallationDirectory(false);
        container.setNamingContext(new InitialContext());
        container.setTransactionManager(new GeronimoPlatformTransactionManager());
    }

    protected void configureJmsBroker() throws Exception {
        
    }

    protected static class ReturnErrorComponent extends ComponentSupport implements MessageExchangeListener {
        private Exception exception;
     
        public ReturnErrorComponent(Exception exception) {
            this.exception = exception;
        }
     
        public void onMessageExchange(MessageExchange exchange) throws MessagingException {
            if (exchange.getStatus() == ExchangeStatus.ACTIVE) {
                fail(exchange, exception);
            }
        }
    }
 
    protected static class ReturnFaultComponent extends ComponentSupport implements MessageExchangeListener {
        public void onMessageExchange(MessageExchange exchange) throws MessagingException {
            if (exchange.getStatus() == ExchangeStatus.ACTIVE) {
                Fault fault = exchange.createFault();
                fault.setContent(new StringSource("<fault/>"));
                fail(exchange, fault);
            }
        }
    }
     
}
