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
package org.apache.servicemix.soap.ws.addressing;

import org.apache.servicemix.soap.core.AbstractPolicy;

public class WsAddressingPolicy extends AbstractPolicy {

    public WsAddressingPolicy() {
        getInterceptors(Phase.ServerIn).add(new WsAddressingInOperationInterceptor(this, true));
        getInterceptors(Phase.ServerIn).add(new WsAddressingInDestinationInterceptor(this, true));

        getInterceptors(Phase.ServerOut).add(new WsAddressingOutInterceptor(this, true));

        getInterceptors(Phase.ClientOut).add(new WsAddressingOutInterceptor(this, false));
    }

}
