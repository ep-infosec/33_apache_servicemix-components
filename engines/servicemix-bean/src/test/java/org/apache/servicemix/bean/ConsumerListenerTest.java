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
package org.apache.servicemix.bean;

import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOut;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.xml.namespace.QName;

import junit.framework.TestCase;

import org.apache.servicemix.bean.beans.ConsumerListener;
import org.apache.servicemix.client.DefaultServiceMixClient;
import org.apache.servicemix.client.ServiceMixClient;
import org.apache.servicemix.components.util.EchoComponent;
import org.apache.servicemix.jbi.container.JBIContainer;
import org.apache.servicemix.jbi.jaxp.SourceTransformer;
import org.apache.servicemix.jbi.jaxp.StringSource;

public class ConsumerListenerTest extends TestCase {

    protected JBIContainer jbi;

    protected void setUp() throws Exception {
        jbi = new JBIContainer();
        jbi.setEmbedded(true);
        jbi.init();
    }

    protected void tearDown() throws Exception {
        jbi.shutDown();
    }

    public void test() throws Exception {
        BeanComponent bc = new BeanComponent();
        BeanEndpoint ep = new BeanEndpoint();
        ep.setService(new QName("bean"));
        ep.setEndpoint("endpoint");
        ep.setBeanType(ConsumerListener.class);
        bc.setEndpoints(new BeanEndpoint[] {ep });
        jbi.activateComponent(bc, "servicemix-bean");

        EchoComponent echo = new EchoComponent(new QName("echo"), "endpoint");
        jbi.activateComponent(echo, "echo");

        jbi.start();

        ServiceMixClient client = new DefaultServiceMixClient(jbi);
        InOut me = client.createInOutExchange();
        me.setService(new QName("bean"));
        me.setOperation(new QName("receive"));
        NormalizedMessage nm = me.getInMessage();
        nm.setContent(new StringSource("<hello>world</hello>"));
        client.sendSync(me);
        assertExchangeWorked(me);
        if (me.getStatus() == ExchangeStatus.ERROR) {
            if (me.getError() != null) {
                throw me.getError();
            } else {
                fail("Received ERROR status");
            }
        } else if (me.getFault() != null) {
            String txt = new SourceTransformer().toString(me.getFault().getContent());
            client.done(me);
            fail("Received fault: " + txt);
        } else {
            client.done(me);
        }
    }

    protected void assertExchangeWorked(MessageExchange me) throws Exception {
    }

}