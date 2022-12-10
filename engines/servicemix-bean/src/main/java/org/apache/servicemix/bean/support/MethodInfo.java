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
package org.apache.servicemix.bean.support;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.MessagingException;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.servicemix.expression.Expression;

/**
 * @version $Revision: $
 */
public class MethodInfo {
    
    private final Method method;
    private final Expression parametersExpression;

    public MethodInfo(Method method, Expression parametersExpression) {
        this.method = method;
        this.parametersExpression = parametersExpression;
    }


    public MethodInvocation createMethodInvocation(final Object pojo, 
            final MessageExchange messageExchange) throws MessagingException {
        final Object[] arguments = (Object[]) parametersExpression.evaluate(
                messageExchange, messageExchange.getMessage("in"));
        return new MethodInvocation() {
            public Method getMethod() {
                return method;
            }
            public Object[] getArguments() {
                return arguments;
            }
            public Object proceed() throws Throwable {
                return invoke(method, pojo, arguments, messageExchange);
            }
            public Object getThis() {
                return pojo;
            }
            public AccessibleObject getStaticPart() {
                return method;
            }
        };

    }

    protected Object invoke(Method mth, Object pojo, Object[] arguments, 
            MessageExchange exchange) throws IllegalAccessException, InvocationTargetException {
        return mth.invoke(pojo, arguments);
    }
}
