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
package org.apache.servicemix.mail.marshaler;

import org.apache.servicemix.components.util.MarshalerSupport;

import javax.activation.DataSource;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.util.*;

/**
 * this is the abstract super class of all marshalers which want to convert
 * between normalized messages and mail messages and vice versa.
 *
 * @author lhein
 */
public abstract class AbstractMailMarshaler extends MarshalerSupport {
    /**
     * ------------------------------------------------------------------------
     * the mail tags are used for parsing a mime mail for specific tags
     * ------------------------------------------------------------------------
     */

    /**
     * this is the tag for the "To" field of the email
     */
    public static final String MAIL_TAG_TO = "To";

    /**
     * this is the tag for the "Cc" field of the email
     */
    public static final String MAIL_TAG_CC = "Cc";

    /**
     * this is the tag for the "Bcc" field of the email
     */
    public static final String MAIL_TAG_BCC = "Bcc";

    /**
     * this is the tag for the "From" field of the email
     */
    public static final String MAIL_TAG_FROM = "From";

    /**
     * this is the tag for the "Subject" field of the email
     */
    public static final String MAIL_TAG_SUBJECT = "Subject";

    /**
     * this is the tag for the "Reply-To" field of the email
     */
    public static final String MAIL_TAG_REPLYTO = "Reply-To";

    /**
     * this is the tag for the "Date" field of the email
     */
    public static final String MAIL_TAG_SENTDATE = "Date";

    /**
     * ------------------------------------------------------------------------
     * the email constants are used for transporting specific information in
     * message properties
     * ------------------------------------------------------------------------
     */

    /**
     * this tag is used in normalized messages to represent the address the
     * mail was sent to OR the address this exchange should be mailed to
     */
    public static final String MSG_TAG_TO = "org.apache.servicemix.mail.to";

    /**
     * this tag is used in normalized messages to represent the CC address the
     * mail was sent to OR the CC address this exchange should be mailed to
     */
    public static final String MSG_TAG_CC = "org.apache.servicemix.mail.cc";

    /**
     * this tag is used in normalized messages to represent the BCC address
     * this exchange should be mailed to
     */
    public static final String MSG_TAG_BCC = "org.apache.servicemix.mail.bcc";

    /**
     * this tag is used in normalized messages to represent the address the
     * mail was received from OR the address this exchange should be sent from
     */
    public static final String MSG_TAG_FROM = "org.apache.servicemix.mail.from";

    /**
     * this tag is used in normalized messages to represent the subject of the
     * mail received OR the subject of the mail to be sent
     */
    public static final String MSG_TAG_SUBJECT = "org.apache.servicemix.mail.subject";

    /**
     * this tag is used in normalized messages to represent the reply-to address
     * of the mail received OR the reply-to address of the mail to be sent
     */
    public static final String MSG_TAG_REPLYTO = "org.apache.servicemix.mail.replyto";

    /**
     * this tag is used in normalized messages to represent the date when
     * the mail was sent
     */
    public static final String MSG_TAG_SENTDATE = "org.apache.servicemix.mail.sentdate";

    /**
     * this tag is used in normalized messages to represent the content type of
     * the mail received or the mail to be sent
     */
    public static final String MSG_TAG_MAIL_CONTENT_TYPE ="org.apache.servicemix.mail.type";

    /**
     * this tag is used in normalized messages to represent the charset used in
     * the received mail or to be used in the mail to be sent
     */
    public static final String MSG_TAG_MAIL_CHARSET = "org.apache.servicemix.mail.charset";

    /**
     * this tag is used in normalized messages to flag that the attachments have to be
     * treatened as inline attachments
     */
    public static final String MSG_TAG_MAIL_USE_INLINE_ATTACHMENTS = "org.apache.servicemix.mail.attachments.inline";

    /**
     * this tag is used in normalized messages to represent the plain text content used in
     * the received mail or to be used in the mail to be sent
     */
    public static final String MSG_TAG_TEXT = "org.apache.servicemix.mail.text";

    /**
     * this tag is used in normalized messages to represent the html content used in
     * the received mail or to be used in the mail to be sent
     */
    public static final String MSG_TAG_HTML = "org.apache.servicemix.mail.html";

    /**
     * the default sender address for outgoing mails
     */
    public static final String DEFAULT_SENDER = "no-reply@localhost";

    /**
     * this tag is used in normalized messages to represent the user which owns the mail account
     */
    public static final String MSG_TAG_USER = "org.apache.servicemix.mail.username";

    /**
     * this tag is used in normalized messages to represent the password of the mail account
     */
    public static final String MSG_TAG_PASSWORD = "org.apache.servicemix.mail.password";

    /**
     * this tag is used in normalized messages to represent the mail server hostname
     */
    public static final String MSG_TAG_HOST = "org.apache.servicemix.mail.host";

    /**
     * this tag is used in normalized messages to represent the used protocol
     */
    public static final String MSG_TAG_PROTOCOL = "org.apache.servicemix.mail.protocol";

    /**
     * this tag is used in normalized messages to represent the port number used for mail server
     */
    public static final String MSG_TAG_PORT = "org.apache.servicemix.mail.port";

    /**
     * the dummy subject - set if no subject was provided
     */
    public static final String DUMMY_SUBJECT = "no subject";

    /**
     * the dummy content - set if no content was provided
     */
    public static final String DUMMY_CONTENT = "<no-content />";

    /**
     * a map of files to be cleaned up later on
     */
    private final Map< String, List<File> > temporaryFilesMap = Collections.synchronizedMap(new HashMap<String, List<File> >());

    /**
     * This method is used to convert a mime mail message received via an
     * email server into a normalized message which will be sent to the bus.
     * If you want to specify your own conversion behaviour you have to override
     * this method with your own logic.
     *
     * @param exchange  the message exchange that will be sent to the bus
     * @param nmsg      the normalized in-message to be filled by this method
     * @param mailMsg   the mail message that was received via mail server
     * @throws javax.mail.MessagingException on any conversion errors
     */
    public abstract void convertMailToJBI(MessageExchange exchange, NormalizedMessage nmsg,
                                          MimeMessage mailMsg) throws javax.mail.MessagingException;

    /**
     * This method is used to convert a normalized message from the bus into a
     * mime mail message to be sent to a mail server.
     * If you want to specify your own conversion behaviour you have to override
     * this method with your own logic.
     *
     * @param mimeMessage               the mime mail message to be filled by this method
     * @param exchange                  the message exchange from JBI bus
     * @param nmsg                      the normalized message to transform to mail message
     * @param configuredSender          the sender configured in the xbean
     * @param configuredReceiver        the receiver configured in the xbean
     * @throws javax.mail.MessagingException on conversion errors
     */
    public abstract void convertJBIToMail(MimeMessage mimeMessage, MessageExchange exchange,
                                          NormalizedMessage nmsg, String configuredSender,
                                          String configuredReceiver) throws javax.mail.MessagingException;

    /**
     * returns the default sender for outgoing mails
     * Override this method to deliver your own default sender!
     *
     * @return  the default sender address as string
     */
    public String getDefaultSenderForOutgoingMails() {
        return AbstractMailMarshaler.DEFAULT_SENDER;
    }

    /**
     * This helper method extracts all attachments from the normalized message
     * into a map of attachments which may be used for filling the attachments
     * of an outgoing mail
     *
     * @param normalizedMessage     the normalized message with attachments
     * @return  a map of attachments
     */
    protected final Map<String, DataSource> getAttachmentsMapFromNormalizedMessage(
                                                                                   NormalizedMessage normalizedMessage) {
        // get attachment from Normalize Message (used for sending)
        Map<String, DataSource> attachments = new HashMap<String, DataSource>();
        String oneAttachmentName;
        Set attNames = normalizedMessage.getAttachmentNames();
        for (Object attName : attNames) {
            oneAttachmentName = (String) attName;
            DataSource oneAttchmentInputString = normalizedMessage.getAttachment(oneAttachmentName)
                    .getDataSource();
            attachments.put(oneAttachmentName, oneAttchmentInputString);
        }

        return attachments;
    }

    /**
     * adds a temporary file resource to the list of to be cleaned up resources
     *
     * @param id        the id of the message exchange
     * @param tmpFile   the temp resource
     */
    protected final void addTemporaryResource(String id, File tmpFile) {
        if (!this.temporaryFilesMap.containsKey(id)) {
            this.temporaryFilesMap.put(id, new ArrayList<File>());
        }
        this.temporaryFilesMap.get(id).add(tmpFile);
    }

    /**
     * deletes all temporary resources of a given exchange id
     *
     * @param id        the exchange id
     */
    public final void cleanUpResources(String id) {
        List<File> list = this.temporaryFilesMap.get(id);
        if (list != null) {
            for (File f : list) {
                f.delete();
            }
            list.clear();
            this.temporaryFilesMap.remove(id);
        }
    }
}
