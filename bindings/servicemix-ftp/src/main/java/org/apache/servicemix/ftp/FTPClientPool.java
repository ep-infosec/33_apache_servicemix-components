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

import java.io.IOException;

import org.apache.commons.net.SocketClient;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPReply;

/**
 * A pool of FTP clients for
 * the <a href="http://jakarta.apache.org/commons/net.html">Jakarta Commons Net</a> library
 *
 * @version $Revision: 426415 $
 * @org.apache.xbean.XBean element="pool"
 */
public class FTPClientPool extends SocketClientPoolSupport {

    public static final int DEFAULT_DATA_TIMEOUT = 120000; // two minutes
    public static final String DEFAULT_CONTROL_ENCODING = FTP.DEFAULT_CONTROL_ENCODING;

    private String username;
    private String password;
    private boolean binaryMode = true;
    private boolean passiveMode;
    private FTPClientConfig config;
    private String controlEncoding = DEFAULT_CONTROL_ENCODING;
    private int dataTimeout = DEFAULT_DATA_TIMEOUT;

    public boolean validateObject(Object object) {
        FTPClient client = (FTPClient) object;
        try {
            return client.sendNoOp();
        } catch (IOException e) {
            throw new RuntimeException("Failed to validate client: " + e, e);
        }
    }

    public void activateObject(Object object) throws Exception {
        FTPClient client = (FTPClient) object;
        client.setReaderThread(true);
    }

    public void passivateObject(Object object) throws Exception {
        FTPClient client = (FTPClient) object;
        client.setReaderThread(false);
    }

    // Properties
    //-------------------------------------------------------------------------
    public String getUsername() {
        return username;
    }

    /**
     * Set the login to use to access the FTP server.
     *
     * @param username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Set the password for logging into the FTP server.
     *
     * @param password to set.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isBinaryMode() {
        return binaryMode;
    }

     /**
     * Use binary mode transfers.  Defaults to <code>true</code>.
     *
     * @param binaryMode is a boolean setting the type of data transfer to binary.  Default value is true.
     */
    public void setBinaryMode(boolean binaryMode) {
        this.binaryMode = binaryMode;
    }

    /**
     * @return the passiveMode
     */
    public boolean isPassiveMode() {
        return passiveMode;
    }

    /**
     * Use passive mode FTP transfers.  Defaults to <code>false</code>
     *
     * @param passiveMode is a boolean setting the type into passive mode.
     */
    public void setPassiveMode(boolean passiveMode) {
        this.passiveMode = passiveMode;
    }

    public FTPClientConfig getConfig() {
        return config;
    }

    /**
     * Configure a custom FTPClientConfig instance to allow more fine-grained control over the FTP connections in the pool.
     *
     * @param config the <code>config</code> implementation to use.
     */
    public void setConfig(FTPClientConfig config) {
        this.config = config;
    }

    /**
     * @return the controlEncoding
     */
    public String getControlEncoding() {
        return controlEncoding;
    }

    /**
     * Configure the encoding used in the FTP control connections.  Defaults to <code>ISO-8859-1</code>
     *
     * @param controlEncoding the controlEncoding to set
     */
    public void setControlEncoding(String controlEncoding) {
        this.controlEncoding = controlEncoding;
    }

    /**
     * @return the dataTimeout
     */
    public int getDataTimeout() {
        return dataTimeout;
    }


    /**
     * Specifies a timeout used on the FTP data connection.  Defaults to <code>120000</code>
     *
     * @param dataTimeout after which the connection should be closed.
     * .
     */
    public void setDataTimeout(int dataTimeout) {
        this.dataTimeout = dataTimeout;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected void connect(SocketClient client) throws Exception {
        FTPClient ftp = (FTPClient) client;

        if (config != null) {
            ftp.configure(config);
        }
        ftp.setControlEncoding(getControlEncoding());

        super.connect(ftp);
        ftp.setDataTimeout(getDataTimeout());

        int code = ftp.getReplyCode();
        if (!FTPReply.isPositiveCompletion(code)) {
            ftp.disconnect();
            throw new ConnectionRefusedException(code);
        }
        if (!ftp.login(getUsername(), getPassword())) {
            ftp.disconnect();
            throw new ConnectionRefusedException(ftp.getReplyCode());
        }
        if (isBinaryMode()) {
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
        }
        if (isPassiveMode()) {
            ftp.enterLocalPassiveMode();
        }
    }

    protected void disconnect(SocketClient client) throws Exception {
        FTPClient ftp = (FTPClient) client;
        if (ftp.isConnected()) {
            ftp.logout();
        }
        super.disconnect(client);
    }

    protected SocketClient createSocketClient() {
        return new FTPClient();
    }
}
