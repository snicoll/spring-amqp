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

package org.springframework.amqp.rabbit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.messaging.handler.annotation.MessageMapping;

/**
 * Annotation that marks a method to be the target of a Rabbit message
 * listener on the specified {@link #queues()}. The {@link #containerFactory()}
 * identifies the {@link org.springframework.amqp.rabbit.config.RabbitListenerContainerFactory
 * RabbitListenerContainerFactory} to use to build the rabbit listener container. If not
 * set, a <em>default</em> container factory is assumed to be available with a bean
 * name of {@code rabbitListenerContainerFactory} unless an explicit default has been
 * provided through configuration.
 *
 * <p>Processing of {@code @RabbitListener} annotations is performed by
 * registering a {@link RabbitListenerAnnotationBeanPostProcessor}. This can be
 * done manually or, more conveniently, through the {@code <rabbit:annotation-driven/>}
 * element or {@link EnableRabbit} annotation.
 *
 * <p>Annotated methods are allowed to have flexible signatures similar to what
 * {@link MessageMapping} provides, that is
 * <ul>
 * <li>{@link com.rabbitmq.client.Channel} to get access to the Channel</li>
 * <li>{@link org.springframework.amqp.core.Message} or one if subclass to get
 * access to the raw AMQP message</li>
 * <li>{@link org.springframework.messaging.Message} to use the messaging abstraction counterpart</li>
 * <li>{@link org.springframework.messaging.handler.annotation.Payload @Payload}-annotated method
 * arguments including the support of validation</li>
 * <li>{@link org.springframework.messaging.handler.annotation.Header @Header}-annotated method
 * arguments to extract a specific header value, including standard AMQP headers defined by
 * {@link org.springframework.amqp.support.AmqpHeaders AmqpHeaders}</li>
 * <li>{@link org.springframework.messaging.handler.annotation.Headers @Headers}-annotated
 * argument that must also be assignable to {@link java.util.Map} for getting access to all
 * headers.</li>
 * <li>{@link org.springframework.messaging.MessageHeaders MessageHeaders} arguments for
 * getting access to all headers.</li>
 * <li>{@link org.springframework.messaging.support.MessageHeaderAccessor MessageHeaderAccessor}
 * or {@link org.springframework.amqp.support.AmqpMessageHeaderAccessor AmqpMessageHeaderAccessor}
 * for convenient access to all method arguments.</li>
 * </ul>
 *
 * <p>Annotated method may have a non {@code void} return type. When they do, the result of the
 * method invocation is sent as a reply to the queue defined by the
 * {@link org.springframework.amqp.core.MessageProperties#getReplyTo() ReplyTo}  header of the
 * incoming message. When this value is not set, a default queue can be provided by
 * adding @{@link org.springframework.messaging.handler.annotation.SendTo SendTo} to the method
 * declaration.
 *
 * @author Stephane Nicoll
 * @since 2.0
 * @see EnableRabbit
 * @see RabbitListenerAnnotationBeanPostProcessor
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@MessageMapping
@Documented
public @interface RabbitListener {

	/**
	 * The identifier of this endpoint. When an endpoint is resolved against
	 * its container, its full is generated.
	 * <p>if none is specified an auto-generated one is provided.
	 * @see org.springframework.amqp.rabbit.config.RabbitListenerEndpointRegistry#getListenerContainer(String)
	 */
	String id() default "";

	/**
	 * The bean name of the {@link org.springframework.amqp.rabbit.config.RabbitListenerContainerFactory}
	 * to use to create the message listener container responsible to serve this endpoint.
	 * <p>If not specified, the default container factory is used, if any.
	 */
	String containerFactory() default "";

	/**
	 * The queues for this listener. If {@link #queueReferences()} is {@code false}, these are considered
	 * queue names; otherwise, these are bean names that should be resolved as
	 * {@link org.springframework.amqp.core.Queue Queue} instances from the context.
	 */
	String[] queues();

	/**
	 * Specify if {@link #queues()} define queue names (false, default) or bean names that should
	 * be resolved as {@link org.springframework.amqp.core.Queue Queue} instance (true).
	 */
	boolean queueReferences() default false;

	/**
	 * When {@code true}, a single consumer in the container will have exclusive use of the
	 * {@link #queues()}, preventing other consumers from receiving messages from the
	 * queues. When {@code true}, requires a concurrency of 1. Default {@code false}.
	 */
	boolean exclusive() default false;

	/**
	 * The priority of this endpoint. Requires RabbitMQ 3.2 or higher.
	 */
	int priority() default -1;

	/**
	 * The routing key to send along with a response message.
	 * <p>This will be applied in case of a request message that does not carry
	 * a "replyTo" property. Note: This only applies to a listener method with
	 * a return value, for which each result object will be converted into a
	 * response message.
	 */
	String responseRoutingKey() default "";

	/**
	 * Reference to a {@link org.springframework.amqp.rabbit.core.RabbitAdmin
	 * RabbitAdmin}. Required if the listener is using auto-delete
	 * queues and those queues are configured for conditional declaration. This
	 * is the admin that will (re)declare those queues when the container is
	 * (re)started. See the reference documentation for more information.
	 */
	String admin() default "";

}
