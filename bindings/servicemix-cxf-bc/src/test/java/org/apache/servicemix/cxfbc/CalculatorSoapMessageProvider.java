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

import java.io.InputStream;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.Provider;
import javax.xml.ws.Service;
import javax.xml.ws.ServiceMode;
import javax.xml.ws.WebServiceProvider;



@WebServiceProvider(serviceName = "CalculatorService", 
        portName = "CalculatorPort", 
        targetNamespace = "http://apache.org/cxf/calculator", 
        wsdlLocation = "testutils/calculator.wsdl")
@ServiceMode(value = Service.Mode.MESSAGE)     
@javax.xml.ws.BindingType(value = "http://www.w3.org/2003/05/soap/bindings/HTTP/")
public class CalculatorSoapMessageProvider implements Provider<SOAPMessage> {

    public CalculatorSoapMessageProvider() {
        //
    }
    
    public SOAPMessage invoke(SOAPMessage request) {
        SOAPMessage response = null;
        try {
            System.out.println("Incoming Client Request as a SOAPMessage");
            MessageFactory factory = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
            InputStream is = getClass().getResourceAsStream("provider_rep.xml");
            response =  factory.createMessage(null, is);
            is.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return response;
    }
}
