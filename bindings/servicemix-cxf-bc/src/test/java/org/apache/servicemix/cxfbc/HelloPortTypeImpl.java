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

import javax.xml.ws.Holder;

import uri.helloworld.HelloFault_Exception;
import uri.helloworld.HelloHeader;
import uri.helloworld.HelloPortType;
import uri.helloworld.HelloRequest;
import uri.helloworld.HelloResponse;
import uri.helloworld.SayHiRequest;
import uri.helloworld.SayHiResponse;

public class HelloPortTypeImpl implements HelloPortType {

   

    public HelloResponse hello(HelloRequest body, Holder<HelloHeader> header1)
            throws HelloFault_Exception {
        HelloResponse rep = new HelloResponse();
        rep.setText(body.getText() + header1.value.getId());
        header1.value.setId("ret" + header1.value.getId());
        return rep;    
    }

    public SayHiResponse sayHi(SayHiRequest body) {
        SayHiResponse ret = new SayHiResponse();
        ret.setText("hello" + body.getText());
        return ret;
    }

}
