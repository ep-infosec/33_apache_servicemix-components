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
package org.apache.servicemix.common.packaging;

import javax.xml.namespace.QName;

/**
 * <p>
 * Provides a value object for gathering information on a consumes element from
 * a ServiceUnitAnalyzer.
 * </p>
 * 
 * @author Philip Dodds
 * @version $Revision: 426415 $
 * @since 3.0
 * @see ServiceUnitAnalyzer
 */
public class Consumes {

	public static final int LINKTYPE_STANDARD = 1;

	public static final int LINKTYPE_HARD = 2;

	public static final int LINKTYPE_SOFT = 3;

	private QName interfaceName;

	private QName serviceName;

	private String endpointName;

	private int linkType;

	public String getEndpointName() {
		return endpointName;
	}

	public void setEndpointName(String endpointName) {
		this.endpointName = endpointName;
	}

	public QName getInterfaceName() {
		return interfaceName;
	}

	public void setInterfaceName(QName interfaceName) {
		this.interfaceName = interfaceName;
	}

	public int getLinkType() {
		return linkType;
	}

	public void setLinkType(int linkType) {
		this.linkType = linkType;
	}

	public QName getServiceName() {
		return serviceName;
	}

	public void setServiceName(QName serviceName) {
		this.serviceName = serviceName;
	}

	public boolean isValid() {
		return (getServiceName() != null || getInterfaceName() != null);
	}
}
