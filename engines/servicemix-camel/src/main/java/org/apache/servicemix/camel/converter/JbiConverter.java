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
package org.apache.servicemix.camel.converter;

import javax.xml.transform.Source;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.servicemix.camel.JbiBinding;
import org.apache.servicemix.jbi.exception.FaultException;

/**
 * Type converter to and from JBI- and ServiceMix-specific types
 */
@Converter
public class JbiConverter {

    @Converter
    public Source convertFaultExceptionToSource(FaultException e) {
        return e.getFault().getContent();
    }

    @Converter
    public FaultException convertExchangeToFaultException(Exchange exchange) {
        Exception exception = exchange.getException();
        if (exception == null) {
            return new FaultException("Unknown error", JbiBinding.getMessageExchange(exchange), null);
        } else {
            FaultException result = new FaultException(exception.getMessage(), JbiBinding.getMessageExchange(exchange), null);
            result.setStackTrace(exception.getStackTrace());
            return result;
        }
    }
   
}
