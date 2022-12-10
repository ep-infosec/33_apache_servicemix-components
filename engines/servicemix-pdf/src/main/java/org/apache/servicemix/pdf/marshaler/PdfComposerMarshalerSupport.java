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
package org.apache.servicemix.pdf.marshaler;

import javax.jbi.messaging.NormalizedMessage;

/**
 * <p>
 * This interface describes the behavior of the PDFComposer marshaller.
 * </p>
 * 
 * @author jbonofre
 */
public interface PdfComposerMarshalerSupport {
    
    /**
     * <p>
     * Unmarshal the content of the in <code>NormalizedMessage</code> to a <code>PdfComposerRequest</code>.
     * </p>
     * 
     * @param in the "in" message.
     * @return the <code>PdfComposerRequest</code>.
     * @throws Exception in case of unmarshalling message.
     */
    public PdfComposerRequest unmarshal(NormalizedMessage in) throws Exception;

}
