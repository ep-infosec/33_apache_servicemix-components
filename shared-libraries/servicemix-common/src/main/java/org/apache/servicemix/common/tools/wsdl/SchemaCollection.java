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
package org.apache.servicemix.common.tools.wsdl;

import java.net.URI;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * <p>
 * Collection of schemas.
 * </p>
 *  
 * @author gnodet
 */
public class SchemaCollection {

    private final Logger logger = LoggerFactory.getLogger(SchemaCollection.class);
    
    private Map<String, Schema> schemas;
    private URI baseUri;
    
    public SchemaCollection() {
        this(null);
    }
    
    public SchemaCollection(URI baseUri) {
        logger.debug("Initializing schema collection with baseUri: {}", baseUri);
        this.baseUri = baseUri;
        this.schemas = new HashMap<String, Schema>();
    }
    
    public Schema getSchema(String namespaceURI) {
        return schemas.get(namespaceURI);
    }
    
    public void read(Element elem, URI sourceUri) throws Exception {
    	String namespace = elem.getAttribute("targetNamespace");
    	Schema schema = schemas.get(namespace);
    	if (schema == null) {
	        schema = new Schema();
	        schema.addSourceUri(sourceUri);
	        schema.setRoot(elem);
	        schema.setNamespace(elem.getAttribute("targetNamespace"));
	        schemas.put(schema.getNamespace(), schema);
    	} else if (!schema.getSourceUris().contains(sourceUri)) {
    		NodeList nodes = elem.getChildNodes();
    		for (int i = 0; i < nodes.getLength(); i++) {
    			schema.getRoot().appendChild(schema.getRoot().getOwnerDocument().importNode(nodes.item(i), true));
    		}
    		schema.addSourceUri(sourceUri);
    	}
        handleImports(schema, sourceUri);
    }
    
    public void read(String location, URI baseUri) throws Exception {
        logger.debug("Reading schema at '{}' with baseUri '{}'", location, baseUri);
        if (baseUri == null) {
            baseUri = this.baseUri;
        }
        URI loc;
        if (baseUri != null) {
            loc = resolve(baseUri, location);
            if (!loc.isAbsolute()) {
                throw new IllegalArgumentException("Unable to resolve '" + loc.toString() + "' relative to '" + baseUri + "'");
            }
        } else {
            loc = new URI(location);
            if (!loc.isAbsolute()) {
                throw new IllegalArgumentException("Location '" + loc.toString() + "' is not absolute and no baseUri specified");
            }
        }
        InputSource inputSource = new InputSource();
        inputSource.setByteStream(loc.toURL().openStream());
        inputSource.setSystemId(loc.toString());
        read(inputSource);
    }
    
    public void read(InputSource inputSource) throws Exception {
        DocumentBuilderFactory docFac = DocumentBuilderFactory.newInstance();
        docFac.setNamespaceAware(true);
        DocumentBuilder builder = docFac.newDocumentBuilder();
        Document doc = builder.parse(inputSource);
        read(doc.getDocumentElement(), 
             inputSource.getSystemId() != null ? new URI(inputSource.getSystemId()) : null);
    }
    
    protected void handleImports(Schema schema, URI baseUri) throws Exception {
        NodeList children = schema.getRoot().getChildNodes();
        List<Element> imports = new ArrayList<Element>();
        List<Element> includes = new ArrayList<Element>();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element ce = (Element) child;
                if ("http://www.w3.org/2001/XMLSchema".equals(ce.getNamespaceURI()) &&
                    "import".equals(ce.getLocalName())) {
                    imports.add(ce);
                } else if ("http://www.w3.org/2001/XMLSchema".equals(ce.getNamespaceURI()) &&
                           "include".equals(ce.getLocalName())) {
                	includes.add(ce);
                }
            }
        }
        for (Iterator<Element> iter = imports.iterator(); iter.hasNext();) {
            Element ce = iter.next();
            String namespace = ce.getAttribute("namespace");
            String location = ce.getAttribute("schemaLocation");
            schema.addImport(namespace);
            schema.getRoot().removeChild(ce);
            if (location != null && !"".equals(location)) {
            	read(location, baseUri);
            }
        }
        for (Iterator<Element> iter = includes.iterator(); iter.hasNext();) {
            Element ce = iter.next();
	        String location = ce.getAttribute("schemaLocation");
            Node parentNode = ce.getParentNode();
            Element root = schema.getRoot();
            if (root == parentNode) { 
                logger.debug("Removing child include node: {}", ce);
                schema.getRoot().removeChild(ce);
            } else {
                logger.warn("Skipping child include node removal: {}", ce);
            }
	        if (location != null && !"".equals(location)) {
	            read(location, baseUri);
	        }
        }
    }
    
    protected static URI resolve(URI base, String location) {
        if ("jar".equals(base.getScheme())) {
            String str = base.toString();
            String[] parts = str.split("!");
            parts[1] = URI.create(parts[1]).resolve(location).toString();
            return URI.create(parts[0] + "!" + parts[1]);
        }
        return base.resolve(location);
    }

    public int getSize() {
        if (schemas != null) {
           return schemas.size();
        } else {
           return 0;
        }
     }
     
     public Collection<Schema> getSchemas() {
        if (schemas != null) {
           return schemas.values();
        } else {
           return Collections.emptySet();
        }
     }
}
