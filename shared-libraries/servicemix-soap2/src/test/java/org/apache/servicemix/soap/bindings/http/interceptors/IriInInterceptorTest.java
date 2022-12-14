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
package org.apache.servicemix.soap.bindings.http.interceptors;

import java.util.List;

import junit.framework.TestCase;

import org.apache.servicemix.soap.bindings.http.interceptors.IriDecoderHelper.Param;

public class IriInInterceptorTest extends TestCase {

    public void test1() {
        List<Param> p = IriDecoderHelper.decodeIri("http://host:8192/service/392/4?name=nodet", 
                                                   "http://host:8192/service/{id}/{nb}");
        assertNotNull(p);
        assertEquals(3, p.size());
        assertEquals(new Param("id", "392"), p.get(0));
        assertEquals(new Param("nb", "4"), p.get(1));
        assertEquals(new Param("name", "nodet"), p.get(2));
    }
    
}
