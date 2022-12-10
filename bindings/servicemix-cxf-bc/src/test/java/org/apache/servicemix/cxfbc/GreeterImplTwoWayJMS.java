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
package org.apache.servicemix.cxfbc;



@javax.jws.WebService(
        serviceName = "HelloWorldService", 
        portName = "HelloWorldPortProxy", 
        endpointInterface = "org.apache.hello_world_soap_http.Greeter",
        targetNamespace = "http://apache.org/hello_world_soap_http",
        wsdlLocation = "org/apache/servicemix/cxfbc/ws/security/hello_world.wsdl"
    )
public class GreeterImplTwoWayJMS 
    extends org.apache.hello_world_soap_http.GreeterImpl {
    static int count = 3;
    public String greetMe(String me) {
        System.out.println("\n\n*** GreetMe called with: " + me + "***\n\n");
        if ("ffang".equals(me)) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        if ("wait".equals(me)) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        if ("transaction".equals(me) && count > 0) {
            //do some test designed for CxfBcJmsTransactionTest
            count--;
            throw new RuntimeException("triger jms transport transaction rollback");
        }
        return "Hello " + me;
    }
        
}
