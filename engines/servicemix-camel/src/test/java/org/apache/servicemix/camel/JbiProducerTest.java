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

import java.util.List;

import javax.jbi.management.DeploymentException;

import org.apache.camel.builder.RouteBuilder;
import org.apache.servicemix.jbi.container.ActivationSpec;
import org.junit.Test;

/**
 * Test for {@link JbiEndpoint.JbiProducer}  
 */
public class JbiProducerTest extends JbiTestSupport {
  
    /*
     * Ensure that no exceptions get thrown when shutting down the routes
     */
    @Test
    public void testShutdown() throws Exception {
        client.stop();
        try {
            camelContext.stop();
        } catch (DeploymentException e) {
            fail("Shutdown should not throw " + e);
        }
    }
    
    @Override
    public void tearDown() throws Exception {
        // testing shutdown, so will do this manually
    }
    
    @Override
    protected void appendJbiActivationSpecs(List<ActivationSpec> activationSpecList) {
        // no additional activation specs required
    }
  
    @Override
    protected RouteBuilder createRoutes() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // two routes that target the same service endpoint 
                from("jbi:service:urn:test:service1").to("jbi:service:urn:test:target-service");
                from("jbi:service:urn:test:service2").to("jbi:service:urn:test:target-service");
            }
        };
    }
}
