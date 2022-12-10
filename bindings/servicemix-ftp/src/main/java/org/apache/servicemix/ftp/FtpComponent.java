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
package org.apache.servicemix.ftp;

import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.jbi.servicedesc.ServiceEndpoint;

import org.apache.servicemix.common.DefaultComponent;
import org.apache.servicemix.common.Endpoint;
import org.apache.servicemix.common.util.IntrospectionSupport;
import org.apache.servicemix.common.util.URISupport;

/**
 * An FTP based component
 *
 * @version $Revision: $
 * @org.apache.xbean.XBean element="component" description="FTP Component"
 */
public class FtpComponent extends DefaultComponent {

    private FtpEndpointType[] endpoints;

    public FtpEndpointType[] getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(FtpEndpointType[] endpoints) {
        this.endpoints = endpoints;
    }

    protected List getConfiguredEndpoints() {
        return asList(getEndpoints());
    }

    protected Class[] getEndpointClasses() {
        return new Class[] {FtpPollerEndpoint.class, FtpSenderEndpoint.class};
    }

    protected Endpoint getResolvedEPR(ServiceEndpoint ep) throws Exception {
        FtpSenderEndpoint ftpEndpoint = new FtpSenderEndpoint(this, ep);
        URI uri = new URI(ep.getEndpointName());
        Map map = URISupport.parseQuery(uri.getQuery());
        IntrospectionSupport.setProperties(ftpEndpoint, map);
        ftpEndpoint.setUri(uri);
        ftpEndpoint.validate();
        return ftpEndpoint;
    }

}
