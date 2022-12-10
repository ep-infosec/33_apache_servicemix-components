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
package org.apache.servicemix.wsn.client;

import java.math.BigInteger;
import java.util.List;

import javax.jbi.JBIException;
import javax.jbi.component.ComponentContext;
import javax.xml.ws.wsaddressing.W3CEndpointReference;

import org.oasis_open.docs.wsn.b_2.DestroyPullPoint;
import org.oasis_open.docs.wsn.b_2.GetMessages;
import org.oasis_open.docs.wsn.b_2.GetMessagesResponse;
import org.oasis_open.docs.wsn.b_2.NotificationMessageHolderType;

public class PullPoint extends AbstractWSAClient {

    public PullPoint(ComponentContext context, W3CEndpointReference pullPoint) {
        super(context, pullPoint);
    }

    public List<NotificationMessageHolderType> getMessages(int max) throws JBIException {
        GetMessages getMessages = new GetMessages();
        getMessages.setMaximumNumber(BigInteger.valueOf(max));
        GetMessagesResponse response = (GetMessagesResponse) request(getMessages);
        return response.getNotificationMessage();
    }

    public void destroy() throws JBIException {
        request(new DestroyPullPoint());
    }

}
