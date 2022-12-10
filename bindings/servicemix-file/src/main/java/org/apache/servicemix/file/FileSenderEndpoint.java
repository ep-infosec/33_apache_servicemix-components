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
package org.apache.servicemix.file;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;

import org.apache.servicemix.common.endpoints.ProviderEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;
import org.apache.servicemix.util.FileUtil;

/**
 * An endpoint which receives messages from the NMR and writes the message to
 * the file system.
 * 
 * @org.apache.xbean.XBean element="sender"
 * @version $Revision: $
 */
public class FileSenderEndpoint extends ProviderEndpoint implements FileEndpointType {

    private File directory;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    @Deprecated
    private String tempFilePrefix = "servicemix-";
    @Deprecated
    private String tempFileSuffix = ".xml";
    private boolean autoCreateDirectory = true;
    private boolean append = true;
    private boolean overwrite;

    public FileSenderEndpoint() {
        append = false;
    }

    public FileSenderEndpoint(FileComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
        append = false;
    }

    public void validate() throws DeploymentException {
        super.validate();
        if (directory == null) {
            throw new DeploymentException("You must specify the directory property");
        }
        if (isAutoCreateDirectory()) {
            directory.mkdirs();
        }
        if (!directory.isDirectory()) {
            throw new DeploymentException("The directory property must be a directory but was: " + directory);
        }
        if (isOverwrite() && isAppend()) {
        	throw new DeploymentException("You can't have 'append' and 'overwrite' active at the same time.");
        }        	
    }
    
    protected void processInOnly(MessageExchange exchange, NormalizedMessage in) throws Exception {
        OutputStream out = null;
        File newFile = null;
        String name = null;
        String writeTempName = null;
        boolean success = false;
        try {
            name = marshaler.getOutputName(exchange, in);
            if (name == null) {
                newFile = File.createTempFile("" + System.currentTimeMillis(), "tmp", directory);
            } else {
                newFile = new File(directory, name);
                if (newFile.exists()) {
                	if (isOverwrite()) {
                		// overwrite active
                		newFile.delete();
                	} else if (isAppend()) {
                		// all fine, we append
                	} else {
                		// no overwrite and no append
                		newFile = null;
                		throw new IOException("Can not write " + name
                                + " : file already exists and overwrite has not been enabled");
                	}
                }
                writeTempName = marshaler.getTempOutputName(exchange, in) != null ? marshaler.getTempOutputName(exchange, in) : name;
                newFile = new File(directory, writeTempName);
            }
            
            if (!newFile.getParentFile().exists() && isAutoCreateDirectory()) {
                newFile.getParentFile().mkdirs();
            }
            logger.debug("Writing to file: {}", newFile.getCanonicalPath());
            out = new BufferedOutputStream(new FileOutputStream(newFile, append));
            marshaler.writeMessage(exchange, in, out, name);
            success = true;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    logger.error("Caught exception while closing stream on error: {}", e, e);
                }
            }
            if (success) {
                if (name != null && !name.equals(newFile.getName())) {
                    if (isAppend()) {
                        // append mode...now we need to transfer the file content into the original file
                        File targetFile = new File(directory, name);
                        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(newFile));
                        out = new BufferedOutputStream(new FileOutputStream(targetFile, append));
                        try {
                            FileUtil.copyInputStream(bis, out);
                        } catch (IOException ioex) {
                            logger.error("Unable to append to file {}", targetFile.getName(), ioex);
                        } finally {
                            try {
                                out.close();
                            } catch (IOException e) {
                                logger.error("Caught exception while closing stream on error: {}", e, e);
                            }
                            if (!newFile.delete()) {
                                throw new IOException("File " + newFile.getName() + " could not be deleted...");          
                            }
                        }            			
                    } else {
                        // no append mode, so just rename it
                        if (!newFile.renameTo(new File(directory, name))) {
                            throw new IOException("File " + newFile.getName() + " could not be renamed to " + name);            				
                        }           				
                    }
                }
            } else {
                // cleaning up incomplete files after things went wrong
                if (newFile != null) {
                    logger.error("An error occured while writing file {}, deleting the invalid file", newFile.getCanonicalPath());
                    if (!newFile.delete()) {
                        logger.warn("Unable to delete file {} after an error had occured", newFile.getCanonicalPath());
                    }
                } else {
                    logger.error("An error occured while creating file or creating name of this file");
                }
            }
        }
    }

    protected void processInOut(MessageExchange exchange, NormalizedMessage in, NormalizedMessage out)
        throws Exception {
        /** TODO list the files? */
        super.processInOut(exchange, in, out);
    }

    // Properties
    // -------------------------------------------------------------------------

    /**
     * Specifies the directory where the endpoint writes files.
     * 
     * @param directory a <code>File</code> object representing the directory
     */
    public void setDirectory(File directory) {
        this.directory = directory;
    }

    public File getDirectory() {
        return directory;
    }

    /**
     * Specifies a <code>FileMarshaler</code> object that will marshal message
     * data from the NMR into a file. The default file marshaler can write valid
     * XML data. <code>FileMarshaler</code> objects are implementations of
     * <code>org.apache.servicemix.components.util.FileMarshaler</code>.
     * 
     * @param marshaler a <code>FileMarshaler</code> object that can write
     *            message data to the file system
     */
    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    /**
     * Specifies a string to prefix to the beginning of generated file
     * names.
     * 
     * @param filePrefix a string to prefix to generated file names
     */
    @Deprecated
    public void setTempFilePrefix(String tempFilePrefix) {
        this.tempFilePrefix = tempFilePrefix;
    }

    @Deprecated
    public String getTempFilePrefix() {
        return tempFilePrefix;
    }

    /**
     * Specifies a string to append to generated file names.
     * 
     * @param fileSuffix a string to append to generated file names
     */
    @Deprecated
    public void setTempFileSuffix(String tempFileSuffix) {
        this.tempFileSuffix = tempFileSuffix;
    }

    @Deprecated
    public String getTempFileSuffix() {
        return tempFileSuffix;
    }

    /**
     * Specifies if the endpoint should create the target directory if it does
     * not exist. If you set this to <code>false</code> and the directory does
     * not exist, the endpoint will not do anything. Default value:
     * <code>true</code>.
     * 
     * @param autoCreateDirectory a boolean specifying if the endpoint creates
     *            directories
     */
    public void setAutoCreateDirectory(boolean autoCreateDirectory) {
        this.autoCreateDirectory = autoCreateDirectory;
    }

    public boolean isAutoCreateDirectory() {
        return autoCreateDirectory;
    }

    /**
     * Specifies if the endpoint appends data to existing files or if it will
     * overwrite existing files. The default is for the endpoint to overwrite
     * existing files. Setting this to <code>true</code> instructs the endpoint
     * to append data. Default value is <code>false</code>.
     * 
     * @param append a boolean specifying if the endpoint appends data to
     *            existing files
     */
    public void setAppend(boolean append) {
        this.append = append;
    }

    public boolean isAppend() {
        return append;
    }

    /**
     * Specifies if the endpoint overwrites existing files or not. 
     * The default is for the endpoint to not overwrite
     * existing files. Setting this to <code>true</code> instructs the endpoint
     * to overwrite existing files. Default value is <code>false</code>.
     * 
     * @param append a boolean specifying if the endpoint appends data to
     *            existing files
     */
    public void setOverwrite(boolean overwrite) {
		this.overwrite = overwrite;
	}
    
    public boolean isOverwrite() {
		return overwrite;
	}
}
