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
package org.apache.servicemix.soap.core;

import java.util.HashSet;
import java.util.Set;

import org.apache.servicemix.soap.api.Interceptor;
import org.apache.servicemix.soap.api.Message;

/**
 * @author <a href="mailto:gnodet [at] gmail.com">Guillaume Nodet</a>
 */
public abstract class AbstractInterceptor implements Interceptor {
    
    private String id;
    private Set<String> before = new HashSet<String>();
    private Set<String> after = new HashSet<String>();

    
    public AbstractInterceptor() {
        id = getClass().getName();
    }

    public void addBefore(String i) {
        before.add(i);
    }

    public void addAfter(String i) {
        after.add(i);
    }

    public Set<String> getAfter() {
        return after;
    }

    public void setAfter(Set<String> a) {
        this.after = a;
    }

    public Set<String> getBefore() {
        return before;
    }

    public void setBefore(Set<String> b) {
        this.before = b;
    }

    public String getId() {
        return id;
    }

    public void setId(String i) {
        this.id = i;
    }

    public void handleFault(Message message) {
    }
    
}
