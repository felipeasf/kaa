/*
 * Copyright 2014 CyberVision, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kaaproject.kaa.server.operations.service.http.commands;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import org.kaaproject.kaa.common.endpoint.CommonEPConstans;
import org.kaaproject.kaa.common.endpoint.security.MessageEncoderDecoder;
import org.kaaproject.kaa.server.common.server.BadRequestException;
import org.kaaproject.kaa.server.common.server.http.AbstractCommand;
import org.kaaproject.kaa.server.operations.pojo.exceptions.GetDeltaException;
import org.kaaproject.kaa.server.operations.service.akka.actors.io.platform.AvroEncDec;
import org.kaaproject.kaa.server.operations.service.akka.messages.io.request.SyncStatistics;

/**
 * The Class AbstractOperationsCommand.
 *
 * @param <T>
 *            the generic type
 * @param <R>
 *            the generic type
 */
public abstract class AbstractHttpSyncCommand extends AbstractCommand implements SyncStatistics {

    /** The signature. */
    private byte[] requestSignature;

    /** The encoded request session key. */
    private byte[] requestKey;

    /** The request data. */
    private byte[] requestData;

    /** The response body. */
    private byte[] responseBody;
    
    private String nextProtocol;

    /**
     * Gets the type of channel that issued this command.
     *
     * @return the response converter class
     */
    public abstract ChannelType getChannelType();

    /**
     * Instantiates a new abstract operations command.
     */
    public AbstractHttpSyncCommand() {
        super();
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.kaaproject.kaa.server.common.http.server.CommandProcessor#parse()
     */
    @Override
    public void parse() throws Exception {
        LOG.trace("CommandName: " + COMMAND_NAME + ": Parse..");
        HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(factory, getRequest());
        if (decoder.isMultipart()) {
            LOG.trace("Chunked: " + HttpHeaders.isTransferEncodingChunked(getRequest()));
            LOG.trace(": Multipart..");
            List<InterfaceHttpData> datas = decoder.getBodyHttpDatas();
            if (!datas.isEmpty()) {
                for (InterfaceHttpData data : datas) {
                    LOG.trace("Multipart1 name " + data.getName() + " type " + data.getHttpDataType().name());
                    if (data.getHttpDataType() == HttpDataType.Attribute) {
                        Attribute attribute = (Attribute) data;
                        if (data.getName().equals(CommonEPConstans.REQUEST_SIGNATURE_ATTR_NAME)) {
                            requestSignature = attribute.get();
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("Multipart name " + data.getName() + " type " + data.getHttpDataType().name() + " Signature set. size: "
                                        + requestSignature.length);
                                LOG.trace(MessageEncoderDecoder.bytesToHex(requestSignature));
                            }

                        } else if (data.getName().equals(CommonEPConstans.REQUEST_KEY_ATTR_NAME)) {
                            requestKey = attribute.get();
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("Multipart name " + data.getName() + " type " + data.getHttpDataType().name() + " requestKey set. size: "
                                        + requestKey.length);
                                LOG.trace(MessageEncoderDecoder.bytesToHex(requestKey));
                            }
                        } else if (data.getName().equals(CommonEPConstans.REQUEST_DATA_ATTR_NAME)) {
                            requestData = attribute.get();
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("Multipart name " + data.getName() + " type " + data.getHttpDataType().name() + " requestData set. size: "
                                        + requestData.length);
                                LOG.trace(MessageEncoderDecoder.bytesToHex(requestData));
                            }
                        } else if (data.getName().equals(CommonEPConstans.NEXT_PROTOCOL_ATTR_NAME)) {
                            nextProtocol = attribute.getString();
                            LOG.trace("[{}] next protocol is {}", getSessionUuid(), nextProtocol);
                        }
                    }
                }
            } else {
                LOG.error("Multipart.. size 0");
                throw new BadRequestException("HTTP Request inccorect, multiprat size is 0");
            }
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.kaaproject.kaa.server.common.http.server.CommandProcessor#Process ()
     */
    @Override
    public void process() throws BadRequestException, GeneralSecurityException, IOException, GetDeltaException {
    }

    public byte[] getRequestSignature() {
        return requestSignature;
    }

    public byte[] getRequestkey() {
        return requestKey;
    }

    public byte[] getRequestData() {
        return requestData;
    }

    public byte[] getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(byte[] responseBody) {
        this.responseBody = responseBody;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.kaaproject.kaa.server.common.http.server.CommandProcessor#
     * getHttpResponse()
     */
    @Override
    public HttpResponse getResponse() {
        LOG.trace("CommandName: " + COMMAND_NAME + ": getHttpResponse..");

        FullHttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.copiedBuffer(responseBody));

        httpResponse.headers().set(CONTENT_TYPE, CommonEPConstans.RESPONSE_CONTENT_TYPE);
        httpResponse.headers().set(CONTENT_LENGTH, httpResponse.content().readableBytes());
        httpResponse.headers().set(CommonEPConstans.RESPONSE_TYPE, CommonEPConstans.RESPONSE_TYPE_OPERATION);
        if (isNeedConnectionClose()) {
            httpResponse.headers().set(CONNECTION, HttpHeaders.Values.CLOSE);
        } else {
            if (HttpHeaders.isKeepAlive(getRequest())) {
                httpResponse.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
            } else {
                httpResponse.headers().set(CONNECTION, HttpHeaders.Values.CLOSE);
            }
        }
        return httpResponse;
    }

    public static String getCommandName() {
        return COMMAND_NAME;
    }

    @Override
    public void reportSyncTime(long syncTime){
        setSyncTime(syncTime);
    }

    @Override
    public String getNextProtocol() {
        return nextProtocol != null ? nextProtocol : AvroEncDec.AVRO_ENC_DEC_ID;
    }
}
