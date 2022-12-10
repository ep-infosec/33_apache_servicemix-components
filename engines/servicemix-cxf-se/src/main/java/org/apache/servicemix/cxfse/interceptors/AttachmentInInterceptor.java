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
package org.apache.servicemix.cxfse.interceptors;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.activation.DataHandler;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;

import org.apache.cxf.attachment.AttachmentImpl;

import org.apache.cxf.message.Attachment;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;


public class AttachmentInInterceptor extends AbstractPhaseInterceptor<Message> {
     
    
    public AttachmentInInterceptor() {
        super(Phase.RECEIVE);
    }
    
    public void handleMessage(Message message) {
        List<Attachment> attachmentList = new ArrayList<Attachment>();
        MessageExchange exchange = message.get(MessageExchange.class);
        NormalizedMessage norMessage = null;
        if (isRequestor(message)) {
            norMessage = (NormalizedMessage) exchange.getMessage("out");
        } else { 
            norMessage = (NormalizedMessage) exchange.getMessage("in");
        }
        if (norMessage == null) {
            norMessage = (NormalizedMessage) exchange.getFault();
        }
        if (norMessage ==null) {
            return;
        }
        Set names = norMessage.getAttachmentNames();
        for (Object obj : names) {
            String id = (String)obj;
            DataHandler dh = norMessage.getAttachment(id);
            attachmentList.add(new AttachmentImpl(id, dh));
        }
        
        message.setAttachments(attachmentList);
    }
    
    protected boolean isRequestor(Message message) {
        return Boolean.TRUE.equals(message.get(Message.REQUESTOR_ROLE));
    }
    
}
