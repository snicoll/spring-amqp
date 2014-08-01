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

package org.springframework.amqp.rabbit.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.messaging.handler.annotation.support.MessageHandlerMethodFactory;
import org.springframework.util.Assert;

/**
 * Helper bean for registering {@link RabbitListenerEndpoint} with
 * a {@link RabbitListenerEndpointRegistry}.
 *
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 * @since 2.0
 * @see org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer
 */
public class RabbitListenerEndpointRegistrar implements BeanFactoryAware, InitializingBean {

	private RabbitListenerEndpointRegistry endpointRegistry;

	private MessageHandlerMethodFactory messageHandlerMethodFactory;

	private RabbitListenerContainerFactory<?> containerFactory;

	private String containerFactoryBeanName;

	private BeanFactory beanFactory;

	private final List<AmqpListenerEndpointDescriptor> endpointDescriptors =
			new ArrayList<AmqpListenerEndpointDescriptor>();


	/**
	 * Set the {@link RabbitListenerEndpointRegistry} instance to use.
	 */
	public void setEndpointRegistry(RabbitListenerEndpointRegistry endpointRegistry) {
		this.endpointRegistry = endpointRegistry;
	}

	/**
	 * Return the {@link RabbitListenerEndpointRegistry} instance for this
	 * registrar, may be {@code null}.
	 */
	public RabbitListenerEndpointRegistry getEndpointRegistry() {
		return this.endpointRegistry;
	}

	/**
	 * Set the {@link MessageHandlerMethodFactory} to use to configure the message
	 * listener responsible to serve an endpoint detected by this processor.
	 * <p>By default, {@link DefaultMessageHandlerMethodFactory} is used and it
	 * can be configured further to support additional method arguments
	 * or to customize conversion and validation support. See
	 * {@link DefaultMessageHandlerMethodFactory} javadoc for more details.
	 */
	public void setMessageHandlerMethodFactory(MessageHandlerMethodFactory rabbitHandlerMethodFactory) {
		this.messageHandlerMethodFactory = rabbitHandlerMethodFactory;
	}

	/**
	 * Return the custom {@link MessageHandlerMethodFactory} to use, if any.
	 */
	public MessageHandlerMethodFactory getMessageHandlerMethodFactory() {
		return this.messageHandlerMethodFactory;
	}

	/**
	 * Set the {@link RabbitListenerContainerFactory} to use in case a {@link RabbitListenerEndpoint}
	 * is registered with a {@code null} container factory.
	 * <p>Alternatively, the bean name of the {@link RabbitListenerContainerFactory} to use
	 * can be specified for a lazy lookup, see {@link #setContainerFactoryBeanName}.
	 */
	public void setContainerFactory(RabbitListenerContainerFactory<?> containerFactory) {
		this.containerFactory = containerFactory;
	}

	/**
	 * Set the bean name of the {@link RabbitListenerContainerFactory} to use in case
	 * a {@link RabbitListenerEndpoint} is registered with a {@code null} container factory.
	 * Alternatively, the container factory instance can be registered directly:
	 * see {@link #setContainerFactory(RabbitListenerContainerFactory)}.
	 * @see #setBeanFactory
	 */
	public void setContainerFactoryBeanName(String containerFactoryBeanName) {
		this.containerFactoryBeanName = containerFactoryBeanName;
	}

	/**
	 * A {@link org.springframework.beans.factory.BeanFactory} only needs to be available in conjunction with
	 * {@link #setContainerFactoryBeanName}.
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}


	@Override
	public void afterPropertiesSet() {
		registerAllEndpoints();
	}

	protected void registerAllEndpoints() {
		for (AmqpListenerEndpointDescriptor descriptor : this.endpointDescriptors) {
			this.endpointRegistry.registerListenerContainer(descriptor.endpoint, resolveContainerFactory(descriptor));
		}
	}

	private RabbitListenerContainerFactory<?> resolveContainerFactory(AmqpListenerEndpointDescriptor descriptor) {
		if (descriptor.containerFactory != null) {
			return descriptor.containerFactory;
		}
		else if (this.containerFactory != null) {
			return this.containerFactory;
		}
		else if (this.containerFactoryBeanName != null) {
			Assert.state(this.beanFactory != null, "BeanFactory must be set to obtain container factory by bean name");
			this.containerFactory = this.beanFactory.getBean(
					this.containerFactoryBeanName, RabbitListenerContainerFactory.class);
			return this.containerFactory;  // Consider changing this if live change of the factory is required
		}
		else {
			throw new IllegalStateException("Could not resolve the " +
					RabbitListenerContainerFactory.class.getSimpleName() + " to use for [" +
					descriptor.endpoint + "] no factory was given and no default is set.");
		}
	}

	/**
	 * Register a new {@link RabbitListenerEndpoint} alongside the
	 * {@link RabbitListenerContainerFactory} to use to create the underlying container.
	 * <p>The {@code factory} may be {@code null} if the default factory has to be
	 * used for that endpoint.
	 */
	public void registerEndpoint(RabbitListenerEndpoint endpoint, RabbitListenerContainerFactory<?> factory) {
		Assert.notNull(endpoint, "Endpoint must be set");
		// Factory may be null, we defer the resolution right before actually creating the container
		this.endpointDescriptors.add(new AmqpListenerEndpointDescriptor(endpoint, factory));
	}

	/**
	 * Register a new {@link RabbitListenerEndpoint} using the default
	 * {@link RabbitListenerContainerFactory} to create the underlying container.
	 * @see #setContainerFactory(RabbitListenerContainerFactory)
	 * @see #registerEndpoint(RabbitListenerEndpoint, RabbitListenerContainerFactory)
	 */
	public void registerEndpoint(RabbitListenerEndpoint endpoint) {
		registerEndpoint(endpoint, null);
	}


	private static class AmqpListenerEndpointDescriptor {

		public final RabbitListenerEndpoint endpoint;

		public final RabbitListenerContainerFactory<?> containerFactory;

		public AmqpListenerEndpointDescriptor(RabbitListenerEndpoint endpoint, RabbitListenerContainerFactory<?> containerFactory) {
			this.endpoint = endpoint;
			this.containerFactory = containerFactory;
		}
	}

}
