/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.amqp.support.converter;

import java.util.Map;

import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.AmqpHeaderMapper;
import org.springframework.amqp.support.SimpleAmqpHeaderMapper;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;

/**
 * Convert a {@link Message} from the messaging abstraction to and from a
 * {@link org.springframework.amqp.core.Message} using an underlying
 * {@link MessageConverter} for the payload and a
 * {@link org.springframework.amqp.support.AmqpHeaderMapper} to map the
 * AMQP headers to and from standard message headers.
 *
 * <p>The inbound flag determines how headers should be mapped. If {@code true}
 * (default), the caller is an inbound listener (i.e. parsing an AMQP message
 * is considered to be a request).
 *
 * @author Stephane Nicoll
 * @since 2.0
 */
public class MessagingMessageConverter implements MessageConverter, InitializingBean {

	private boolean inbound;

	private MessageConverter payloadConverter;

	private AmqpHeaderMapper headerMapper;


	/**
	 * Create an instance with a default payload converter for an inbound
	 * handler.
	 * @see org.springframework.amqp.support.converter.SimpleMessageConverter
	 * @see org.springframework.amqp.support.SimpleAmqpHeaderMapper
	 */
	public MessagingMessageConverter() {
		this(true);
	}

	/**
	 * Create an instance with a default payload converter and the specified
	 * inbound flag.
	 * @see org.springframework.amqp.support.converter.SimpleMessageConverter
	 * @see org.springframework.amqp.support.SimpleAmqpHeaderMapper
	 */
	public MessagingMessageConverter(boolean inbound) {
		this(inbound, new SimpleMessageConverter(), new SimpleAmqpHeaderMapper());
	}

	/**
	 * Create an instance with the specified payload converter and
	 * header mapper.
	 */
	public MessagingMessageConverter(boolean inbound, MessageConverter payloadConverter, AmqpHeaderMapper headerMapper) {
		Assert.notNull(payloadConverter, "PayloadConverter must not be null");
		Assert.notNull(headerMapper, "HeaderMapper must not be null");
		this.inbound = inbound;
		this.payloadConverter = payloadConverter;
		this.headerMapper = headerMapper;
	}


	/**
	 * Set the {@link MessageConverter} to use to convert the payload.
	 */
	public void setPayloadConverter(MessageConverter payloadConverter) {
		this.payloadConverter = payloadConverter;
	}

	/**
	 * Set the {@link AmqpHeaderMapper} to use to map AMQP headers to and from
	 * standard message headers.
	 */
	public void setHeaderMapper(AmqpHeaderMapper headerMapper) {
		this.headerMapper = headerMapper;
	}

	/**
	 * Set if this instance should be used against an inbound handler.
	 */
	public void setInbound(boolean inbound) {
		this.inbound = inbound;
	}

	/**
	 * Specify if this instance should be used against an inbound handler. When
	 * used against an inbound handler, an incoming AMQP message is parsed as
	 * a request and and outgoing AMQP message is parsed as a reply.
	 */
	public boolean isInbound() {
		return inbound;
	}

	@Override
	public void afterPropertiesSet() {
		Assert.notNull(this.payloadConverter, "Property 'payloadConverter' is required");
		Assert.notNull(this.headerMapper, "Property 'headerMapper' is required");
	}

	@Override
	public org.springframework.amqp.core.Message toMessage(Object object, MessageProperties messageProperties) throws MessageConversionException {
		if (!(object instanceof Message)) {
			throw new IllegalArgumentException("Could not convert [" + object + "] - only [" +
					Message.class.getName() + "] is handled by this converter");
		}
		Message<?> input = (Message<?>) object;
		org.springframework.amqp.core.Message amqpMessage = this.payloadConverter.toMessage(
				input.getPayload(), messageProperties);
		if (isInbound()) {
			this.headerMapper.fromHeadersToReply(input.getHeaders(), messageProperties);
		}
		else {
			this.headerMapper.fromHeadersToRequest(input.getHeaders(), messageProperties);
		}
		return amqpMessage;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Object fromMessage(org.springframework.amqp.core.Message message) throws MessageConversionException {
		if (message == null) {
			return null;
		}
		Map<String, Object> mappedHeaders = mapHeaders(message.getMessageProperties());
		Object convertedObject = extractPayload(message);
		MessageBuilder<Object> builder = (convertedObject instanceof org.springframework.messaging.Message) ?
				MessageBuilder.fromMessage((org.springframework.messaging.Message<Object>) convertedObject) :
				MessageBuilder.withPayload(convertedObject);
		return builder.copyHeadersIfAbsent(mappedHeaders).build();
	}

	/**
	 * Extract the payload of the specified {@link org.springframework.amqp.core.Message}.
	 */
	protected Object extractPayload(org.springframework.amqp.core.Message message) {
		return this.payloadConverter.fromMessage(message);
	}

	private Map<String, Object> mapHeaders(MessageProperties properties)  {
		if (isInbound()) {
			return this.headerMapper.toHeadersFromRequest(properties);
		}
		else {
			return this.headerMapper.toHeadersFromReply(properties);
		}
	}

}
