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

import java.util.Collections;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.support.SimpleAmqpHeaderMapper;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.Assert.*;

/**
 * @author Stephane Nicoll
 */
public class MessagingMessageConverterTests {

	@Rule
	public final ExpectedException thrown = ExpectedException.none();

	private final MessagingMessageConverter converter = new MessagingMessageConverter();

	private final TestAmqpHeaderMapper testAmqpHeaderMapper = new TestAmqpHeaderMapper();

	@Test
	public void onlyHandlesMessage() {
		thrown.expect(IllegalArgumentException.class);
		converter.toMessage(new Object(), new MessageProperties());
	}

	@Test
	public void toMessageWithTextMessage() {
		org.springframework.amqp.core.Message message = converter
				.toMessage(MessageBuilder.withPayload("Hello World").build(), new MessageProperties());

		assertEquals(MessageProperties.CONTENT_TYPE_TEXT_PLAIN, message.getMessageProperties().getContentType());
		assertEquals("Hello World", new String(message.getBody()));
	}

	@Test
	public void fromNull() {
		assertNull(converter.fromMessage(null));
	}

	@Test
	public void customPayloadConverter() throws Exception {
		converter.setPayloadConverter(new SimpleMessageConverter() {
			@Override
			public Object fromMessage(org.springframework.amqp.core.Message message) throws MessageConversionException {
				String payload = new String(message.getBody());
				return Long.parseLong(payload);
			}
		});

		Message<?> msg = (Message<?>) converter.fromMessage(createTextMessage("1224"));
		assertEquals(1224L, msg.getPayload());
	}

	@Test
	public void payloadIsAMessage() {
		final Message<String> message = MessageBuilder.withPayload("Test").setHeader("inside", true).build();
		converter.setPayloadConverter(new SimpleMessageConverter() {

			@Override
			public Object fromMessage(org.springframework.amqp.core.Message amqpMessage) throws MessageConversionException {
				return message;
			}
		});
		Message<?> msg = (Message<?>) converter.fromMessage(createTextMessage("foo"));
		assertEquals(message.getPayload(), msg.getPayload());
		assertEquals(true, msg.getHeaders().get("inside"));
	}

	@Test
	public void inboundParseIncomingMessage() {
		converter.setHeaderMapper(testAmqpHeaderMapper);
		org.springframework.amqp.core.Message amqpMessage = createTextMessage("Foo");
		amqpMessage.getMessageProperties().setReplyTo("replyTo");
		amqpMessage.getMessageProperties().setMessageId("id-1234");
		checkIncomingHeader(amqpMessage, "replyTo", null);
	}

	@Test
	public void inboundCreateOutgoingMessage() {
		converter.setHeaderMapper(testAmqpHeaderMapper);
		checkOutgoingHeader(null, "id-1234");
	}

	@Test
	public void outboundParseIncomingMessage() {
		converter.setInbound(false);
		converter.setHeaderMapper(testAmqpHeaderMapper);
		org.springframework.amqp.core.Message amqpMessage = createTextMessage("Foo");
		amqpMessage.getMessageProperties().setReplyTo("replyTo");
		amqpMessage.getMessageProperties().setMessageId("id-1234");
		checkIncomingHeader(amqpMessage, null, "id-1234");
	}

	@Test
	public void outboundCreateOutgoingMessage() {
		converter.setInbound(false);
		converter.setHeaderMapper(testAmqpHeaderMapper);
		checkOutgoingHeader("replyTo", null);
	}

	private void checkIncomingHeader(org.springframework.amqp.core.Message amqpMessage, String replyTo, String messageId) {
		Message<?> message = (Message<?>) converter.fromMessage(amqpMessage);
		assertEquals(replyTo, message.getHeaders().get(AmqpHeaders.REPLY_TO));
		assertEquals(messageId, message.getHeaders().get(AmqpHeaders.MESSAGE_ID));
	}

	private void checkOutgoingHeader(String replyTo, String messageId) {
		Message<String> message = MessageBuilder.withPayload("foo")
				.setHeader(AmqpHeaders.REPLY_TO, "replyTo")
				.setHeader(AmqpHeaders.MESSAGE_ID, "id-1234").build();
		org.springframework.amqp.core.Message amqpMessage = converter.toMessage(message, new MessageProperties());
		assertEquals(replyTo, amqpMessage.getMessageProperties().getReplyTo());
		assertEquals(messageId, amqpMessage.getMessageProperties().getMessageId());
	}


	public org.springframework.amqp.core.Message createTextMessage(String body) {
		MessageProperties properties = new MessageProperties();
		properties.setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);
		return new org.springframework.amqp.core.Message(body.getBytes(), properties);
	}

	/**
	 * A test header mapper that only maps specified fields.
	 */
	private static class TestAmqpHeaderMapper extends SimpleAmqpHeaderMapper {

		@Override
		protected List<String> getStandardRequestHeaderNames() {
			return Collections.singletonList(AmqpHeaders.REPLY_TO);
		}

		@Override
		protected List<String> getStandardReplyHeaderNames() {
			return Collections.singletonList(AmqpHeaders.MESSAGE_ID);
		}
	}

}
