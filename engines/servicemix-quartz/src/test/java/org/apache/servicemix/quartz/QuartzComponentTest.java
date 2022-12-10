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
package org.apache.servicemix.quartz;

import javax.xml.namespace.QName;

import junit.framework.TestCase;

import org.apache.servicemix.jbi.container.JBIContainer;
import org.springframework.scheduling.quartz.SimpleTriggerBean;

public class QuartzComponentTest extends TestCase {

    public void test() throws Exception {
        JBIContainer jbi = new JBIContainer();
        jbi.setEmbedded(true);
        jbi.init();
        
        QuartzComponent quartz = new QuartzComponent();
        QuartzEndpoint endpoint = new QuartzEndpoint();
        endpoint.setService(new QName("quartz"));
        endpoint.setEndpoint("endpoint");
        endpoint.setTargetService(new QName("countDownReceiver"));
        SimpleTriggerBean trigger = new SimpleTriggerBean();
        trigger.setRepeatInterval(100);
        trigger.setName("trigger");
        trigger.afterPropertiesSet();
        endpoint.setTrigger(trigger);
        quartz.setEndpoints(new QuartzEndpoint[] {endpoint });
        jbi.activateComponent(quartz, "servicemix-quartz");
        
        CountDownReceiverComponent receiver = new CountDownReceiverComponent(new QName("countDownReceiver"), "endpoint", 1, 3000);
        jbi.activateComponent(receiver, "countDownReceiver");
        
        jbi.start();

        assertTrue(receiver.getMessageList().flushMessages().size() > 0);
        
        quartz.stop();
        // Pause to allow messages triggered before stopping the quartz
        // component to be delivered.
        Thread.sleep(1000);
        receiver.getMessageList().flushMessages();
        // Pause to see if any newly triggered messages are incorrectly
        // delivered since the quartz component was stopped.
        Thread.sleep(1000);
        assertEquals(0, receiver.getMessageList().flushMessages().size());
        
        quartz.start();
        receiver.reset();
        
        assertTrue(receiver.getMessageList().flushMessages().size() > 0);

        jbi.shutDown();
    }
    
}
