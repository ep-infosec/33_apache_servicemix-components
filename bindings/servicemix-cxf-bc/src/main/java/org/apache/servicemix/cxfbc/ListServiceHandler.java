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

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.endpoint.ServerRegistry;
import org.apache.cxf.transport.http_jetty.JettyHTTPDestination;
import org.apache.cxf.transport.http_jetty.JettyHTTPServerEngine;
import org.eclipse.jetty.http.HttpMethods;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.AbstractHttpConnection;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.ByteArrayISO8859Writer;
import org.eclipse.jetty.util.StringUtil;


public class ListServiceHandler extends AbstractHandler {


    private Map<String, Bus> allBuses;
    private CxfBcComponent cxfBcComponent;
    
    public ListServiceHandler(Map<String, Bus> allBuses) {
        this.allBuses = allBuses;
    }
    
    public ListServiceHandler(Map<String, Bus> allBuses, CxfBcComponent cxfBcComponent) {
        this.allBuses = allBuses;
        this.cxfBcComponent = cxfBcComponent;
    }
    
    public void handle(String target, HttpServletRequest request,
            HttpServletResponse response, int dispatch) throws IOException,
            ServletException {
        if (response.isCommitted()
                || AbstractHttpConnection.getCurrentConnection().getRequest()
                        .isHandled()) {
            return;
        }

        String method = request.getMethod();

        if (!method.equals(HttpMethods.GET)
                || !request.getRequestURI().equals("/")) {
            return;
        }

        response.setStatus(404);
        response.setContentType(MimeTypes.TEXT_HTML);

        ByteArrayISO8859Writer writer = new ByteArrayISO8859Writer(1500);

        String uri = request.getRequestURI();
        uri = StringUtil.replace(uri, "<", "&lt;");
        uri = StringUtil.replace(uri, ">", "&gt;");

        writer.write("<HTML>\n<HEAD>\n<TITLE>Error 404 - Not Found");
        writer.write("</TITLE>\n<BODY>\n<H2>Error 404 - Not Found.</H2>\n");
        writer.write("No service matched or handled this request.<BR>");
        writer.write("Known services on cxf bc component are: <ul>");

        List<Server> servers = new ArrayList<Server>();
        for (Bus bus : allBuses.values()) {
            ServerRegistry serverRegistry = bus.getExtension(ServerRegistry.class);
            servers.addAll(serverRegistry.getServers());
        }
        int serverPort = request.getServerPort();
        for (Iterator<Server> iter = servers.iterator(); iter.hasNext();) {
            Server server = (Server) iter.next();
            JettyHTTPDestination jhd = (JettyHTTPDestination)server.getDestination();
            if (cxfBcComponent != null
                && !cxfBcComponent.isShowAllServices() 
                && ((JettyHTTPServerEngine)jhd.getEngine()).getPort() != serverPort) {
                continue;
            }
            String address = jhd.getAddress().getAddress().getValue();
            writer.write("<li><a href=\"");
            writer.write(address);
            writer.write("?wsdl\">");
            writer.write(address);
            writer.write("</a></li>\n");
        }
        
        for (int i = 0; i < 10; i++) {
            writer.write("\n<!-- Padding for IE                  -->");
        }

        writer.write("\n</BODY>\n</HTML>\n");
        writer.flush();
        response.setContentLength(writer.size());
        OutputStream out = response.getOutputStream();
        writer.writeTo(out);
        out.close();
    }

    public void handle(String target, Request baseRequest, HttpServletRequest request,
                       HttpServletResponse response) throws IOException, ServletException {
        handle(target, request, response, 0);
                
    }

}
