/**
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
package org.apache.servicemix.vfs;

import java.io.IOException;
import java.io.OutputStream;

import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.MessagingException;
import javax.jbi.messaging.NormalizedMessage;

import org.apache.commons.vfs.FileContent;
import org.apache.commons.vfs.FileObject;
import org.apache.commons.vfs.FileSystemManager;
import org.apache.servicemix.common.endpoints.ProviderEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An endpoint which receives messages from the NMR and writes the message to 
 * the virtual file system.
 *
 * @org.apache.xbean.XBean element="sender"
 * 
 * @author lhein
 */
public class VFSSendingEndpoint extends ProviderEndpoint implements VFSEndpointType {

    private final Logger logger = LoggerFactory.getLogger(VFSSendingEndpoint.class);

    private FileObject file;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private String path;
    private FileSystemManager fileSystemManager;

    /**
     * resolves the given path to a file object
     */
    protected void resolvePath() throws Exception {
    	if (file == null) {
    		file = FileObjectResolver.resolveToFileObject(getFileSystemManager(), getPath());
        }
    }
    
    @Override
    protected void processInOnly(MessageExchange exchange, NormalizedMessage in)
    		throws Exception {
    	// resolve the file path
    	resolvePath();
    	
        OutputStream out = null;
        String tmpName = null;
        String name = null;
        FileObject tmpFile = null;
        FileObject newFile = null;
        FileContent content = null;
        try {
            name = marshaler.getOutputName(exchange, in);
            if (name == null) {
                throw new MessagingException("No output name available. Cannot output message!");
            }
            tmpName = marshaler.getTempOutputName(exchange, in);
            file.close(); // remove any cached informations
            if (tmpName != null) {
            	// writing to temp file first
            	tmpFile = tmpName != null ? file.resolveFile(tmpName) : null;
                tmpFile.close();
                content = tmpFile.getContent();
            } else {
            	// writing to target file
            	newFile = file.resolveFile(name);
            	newFile.close(); // remove any cached informations
                content = newFile.getContent();
            }
            // remove any cached informations
            content.close();
            if (content != null) {
                out = content.getOutputStream();
            }
            if (out == null) {
                throw new MessagingException("No output stream available for output name: " + name);
            }
            marshaler.writeMessage(exchange, in, out, name);
        }
        finally {
            if (out != null) {
                try {
                    out.close();
                }
                catch (IOException e) {
                    logger.error("Caught exception while closing stream on error: {}", e.getMessage(), e);
                }
            }
            if (tmpName != null && name != null && !name.equals(tmpName)) {
            	if (!tmpFile.canRenameTo(newFile)) {
            		throw new IOException("File " + tmpName + " could not be renamed to " + name);
            	} else {
            		tmpFile.moveTo(newFile);
            	}
            }
        }
    }
    
    /**
     * Specifies a <code>String</code> object representing the path of the 
     * file/folder to be polled.<br /><br />
     * <b><u>Examples:</u></b><br />
     * <ul>
     *  <li>file:///home/lhein/pollFolder</li>
     *  <li>zip:file:///home/lhein/pollFolder/myFile.zip</li>
     *  <li>jar:http://www.myhost.com/files/Examples.jar</li>
     *  <li>jar:../lib/classes.jar!/META-INF/manifest.mf</li>
     *  <li>tar:gz:http://anyhost/dir/mytar.tar.gz!/mytar.tar!/path/in/tar/README.txt</li>
     *  <li>tgz:file://anyhost/dir/mytar.tgz!/somepath/somefile</li>
     *  <li>gz:/my/gz/file.gz</li>
     *  <li>http://myusername@somehost/index.html</li>
     *  <li>webdav://somehost:8080/dist</li>
     *  <li>ftp://myusername:mypassword@somehost/pub/downloads/somefile.tgz</li>
     *  <li>sftp://myusername:mypassword@somehost/pub/downloads/somefile.tgz</li>
     *  <li>smb://somehost/home</li>
     *  <li>tmp://dir/somefile.txt</li>
     *  <li>res:path/in/classpath/image.png</li>
     *  <li>ram:///any/path/to/file.txt</li>
     *  <li>mime:file:///your/path/mail/anymail.mime!/filename.pdf</li>
     * </ul>
     * 
     * For further details have a look at {@link http://commons.apache.org/vfs/filesystems.html}.
     * <br /><br />
     * 
     * @param path a <code>String</code> object that represents a file/folder/vfs
     */
    public void setPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return this.path;
    }

    /**
     * sets the file system manager
     * 
     * @param fileSystemManager the file system manager
     */
    public void setFileSystemManager(FileSystemManager fileSystemManager) {
        this.fileSystemManager = fileSystemManager;
    }

    public FileSystemManager getFileSystemManager() {
        return this.fileSystemManager;
    }

    /**
     * Specifies a <code>FileMarshaler</code> object that will marshal file data
     * into the NMR. The default file marshaller can read valid XML data.
     * <code>FileMarshaler</code> objects are implementations of
     * <code>org.apache.servicemix.components.util.FileMarshaler</code>.
     * 
     * @param marshaler a <code>FileMarshaler</code> object that can read data
     *            from the file system.
     */
    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }
}
