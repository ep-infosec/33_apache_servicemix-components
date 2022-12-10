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
package org.apache.servicemix.camel;

import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.StringSource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.servicemix.client.DefaultServiceMixClient;
import org.apache.servicemix.client.ServiceMixClient;
import org.apache.servicemix.jbi.exception.FaultException;
import org.junit.Test;

import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOut;
import javax.xml.namespace.QName;

/**
 * Test to ensure possibility of conveying exceptions over the JMS/JCA flow with convertException=true
 */
public class JbiInOutCamelJMSFlowExceptionHandledTest extends JbiCamelErrorHandlingTestSupport {

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // make sure all exchanges in the ESB are serializable (e.g. for use with JMS/JCA flow)
        enableCheckForSerializableExchanges();
    }

    @Test
    public void testInOutWithErrorHandledTrue() throws Exception {
        MockEndpoint errors = getMockEndpoint("mock:errors");
        errors.expectedMessageCount(1);

        ServiceMixClient client = new DefaultServiceMixClient(jbiContainer);
        InOut exchange = client.createInOutExchange();
        exchange.setService(new QName("urn:test", "error-handled-true"));
        exchange.getInMessage().setContent(new StringSource(MESSAGE));
        client.sendSync(exchange);
        assertEquals(ExchangeStatus.ACTIVE, exchange.getStatus());
        client.done(exchange);

        errors.assertIsSatisfied();

        // let's wait a moment to make sure that the last DONE MessageExchange is handled
        Thread.sleep(500);
    }

    @Override
    protected RouteBuilder createRoutes() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(CamelException.class).handled(true).to("mock:errors");

                from("jbi:service:urn:test:error-handled-true")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            throw new CamelException();
                        }
                    })
                    .setBody(constant("<msg>Done</msg>"));
            }
        };
    }
}
