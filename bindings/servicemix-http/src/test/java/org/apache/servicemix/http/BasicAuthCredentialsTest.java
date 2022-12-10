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
package org.apache.servicemix.http;

import junit.framework.TestCase;

import org.apache.xbean.spring.context.ClassPathXmlApplicationContext;
import org.springframework.context.ApplicationContext;

public class BasicAuthCredentialsTest extends TestCase {

    private ApplicationContext context;

    @Override
    protected void setUp() throws Exception {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(new String[] { "org/apache/servicemix/http/basic-auth.xml" }, false);
        context.setValidating(false);
        context.refresh();
        this.context = context;
    }

    public void testApplyCredentials() throws Exception {
        BasicAuthTestSupport support = (BasicAuthTestSupport) context.getBean("BasicAuthTestSupport");
        assertEquals("testuser", support.getBasicAuth().getUsername().evaluate(null, null));
        assertEquals("testpass", support.getBasicAuth().getPassword().evaluate(null, null));
    }
    
    public void testNTLMCredentials() throws Exception {
        BasicAuthTestSupport support = (BasicAuthTestSupport) context.getBean("NTLMAuthTestSupport");
        assertEquals("testuser", support.getBasicAuth().getUsername().evaluate(null, null));
        assertEquals("testpass", support.getBasicAuth().getPassword().evaluate(null, null));
        assertEquals("testdomain", support.getBasicAuth().getDomain().evaluate(null, null));
        assertEquals("testhost", support.getBasicAuth().getHost().evaluate(null, null));
    }

}
