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
package org.apache.servicemix.camel;

import org.apache.servicemix.common.Endpoint;
import org.apache.servicemix.common.ServiceMixComponent;
import org.apache.servicemix.common.ServiceUnit;

/**
 */
public interface CamelComponent extends ServiceMixComponent {

    void addJbiComponent(JbiComponent jbiComponent);

    ServiceUnit getServiceUnit();

    void addEndpoint(Endpoint endpoint) throws Exception;

    void removeEndpoint(Endpoint endpoint) throws Exception;

    void activateJbiEndpoint(CamelProviderEndpoint jbiEndpoint) throws Exception;

    void deactivateJbiEndpoint(CamelProviderEndpoint jbiEndpoint) throws Exception;

}
