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
package org.apache.servicemix.soap.bindings.http.impl;

import javax.xml.namespace.QName;

import org.apache.servicemix.soap.bindings.http.model.Wsdl2HttpHeader;

public class Wsdl2HttpHeaderImpl implements Wsdl2HttpHeader {

    private String name;
    private QName type;
    private boolean required;
    
    /**
     * @return the name
     */
    public String getName() {
        return name;
    }
    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }
    /**
     * @return the required
     */
    public boolean isRequired() {
        return required;
    }
    /**
     * @param required the required to set
     */
    public void setRequired(boolean required) {
        this.required = required;
    }
    /**
     * @return the type
     */
    public QName getType() {
        return type;
    }
    /**
     * @param type the type to set
     */
    public void setType(QName type) {
        this.type = type;
    }

}
