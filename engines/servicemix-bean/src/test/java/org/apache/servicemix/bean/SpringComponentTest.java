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
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.InOut;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.xml.namespace.QName;

import org.apache.servicemix.bean.beans.ListenerBean;
import org.apache.servicemix.client.DefaultServiceMixClient;
import org.apache.servicemix.jbi.jaxp.SourceTransformer;
import org.apache.servicemix.jbi.jaxp.StringSource;
import org.apache.servicemix.tck.SpringTestSupport;
import org.apache.xbean.spring.context.ClassPathXmlApplicationContext;
import org.springframework.context.support.AbstractXmlApplicationContext;

public class SpringComponentTest extends SpringTestSupport {

    public void testSendingToStaticEndpoint() throws Exception {
        DefaultServiceMixClient client = new DefaultServiceMixClient(jbi);
        InOnly me = client.createInOnlyExchange();
        me.setService(new QName("urn:test", "service"));
        NormalizedMessage message = me.getInMessage();

        message.setProperty("name", "cheese");
        message.setContent(new StringSource("<hello>world</hello>"));

        client.sendSync(me);
        assertExchangeWorked(me);

        ListenerBean bean = (ListenerBean) getBean("listenerBean");
        assertNotNull("Bean should bave been invoked", bean.getLastExchange());

        log.info("The bean has been invoked: " + bean.getLastExchange());
    }

    public void testSendingInOutToStaticEndpoint() throws Exception {
        DefaultServiceMixClient client = new DefaultServiceMixClient(jbi);
        InOut me = client.createInOutExchange();
        me.setService(new QName("urn:test", "service"));
        NormalizedMessage message = me.getInMessage();

        message.setProperty("name", "cheese");
        message.setContent(new StringSource("<hello>world</hello>"));

        client.sendSync(me);
        client.done(me);
        assertExchangeWorked(me);

        ListenerBean bean = (ListenerBean) getBean("listenerBean");
        assertNotNull("Bean should bave been invoked", bean.getLastExchange());

        log.info("The bean has been invoked: " + bean.getLastExchange());
    }

    protected void assertExchangeWorked(MessageExchange me) throws Exception {
        if (me.getStatus() == ExchangeStatus.ERROR) {
            if (me.getError() != null) {
                throw me.getError();
            } else {
                fail("Received ERROR status");
            }
        } else if (me.getFault() != null) {
            fail("Received fault: " + new SourceTransformer().toString(me.getFault().getContent()));
        }
    }

    protected AbstractXmlApplicationContext createBeanFactory() {
        return new ClassPathXmlApplicationContext("spring.xml");
    }

}
