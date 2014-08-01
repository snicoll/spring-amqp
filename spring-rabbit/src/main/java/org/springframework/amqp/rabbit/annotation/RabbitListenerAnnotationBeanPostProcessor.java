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

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.MethodRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.config.RabbitListenerConfigUtils;
import org.springframework.amqp.rabbit.config.RabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.RabbitListenerEndpointRegistrar;
import org.springframework.amqp.rabbit.config.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.messaging.handler.annotation.support.MessageHandlerMethodFactory;
import org.springframework.messaging.handler.invocation.InvocableHandlerMethod;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Bean post-processor that registers methods annotated with {@link RabbitListener}
 * to be invoked by a AMQP message listener container created under the cover
 * by a {@link org.springframework.amqp.rabbit.config.RabbitListenerContainerFactory}
 * according to the parameters of the annotation.
 *
 * <p>Annotated methods can use flexible arguments as defined by {@link RabbitListener}.
 *
 * <p>This post-processor is automatically registered by Spring's
 * {@code <rabbit:annotation-driven>} XML element, and also by the {@link EnableRabbit}
 * annotation.
 *
 * <p>Auto-detect any {@link RabbitListenerConfigurer} instances in the container,
 * allowing for customization of the registry to be used, the default container
 * factory or for fine-grained control over endpoints registration. See
 * {@link EnableRabbit} Javadoc for complete usage details.
 *
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 * @since 2.0
 * @see RabbitListener
 * @see EnableRabbit
 * @see RabbitListenerConfigurer
 * @see RabbitListenerEndpointRegistrar
 * @see RabbitListenerEndpointRegistry
 * @see org.springframework.amqp.rabbit.config.RabbitListenerEndpoint
 * @see MethodRabbitListenerEndpoint
 */
public class RabbitListenerAnnotationBeanPostProcessor
		implements BeanPostProcessor, Ordered, BeanFactoryAware, SmartInitializingSingleton {

	/**
	 * The bean name of the default {@link org.springframework.amqp.rabbit.config.RabbitListenerContainerFactory}.
	 */
	static final String DEFAULT_RABBIT_LISTENER_CONTAINER_FACTORY_BEAN_NAME = "rabbitListenerContainerFactory";


	private RabbitListenerEndpointRegistry endpointRegistry;

	private String containerFactoryBeanName = DEFAULT_RABBIT_LISTENER_CONTAINER_FACTORY_BEAN_NAME;

	private BeanFactory beanFactory;

	private final RabbitHandlerMethodFactoryAdapter messageHandlerMethodFactory = new RabbitHandlerMethodFactoryAdapter();

	private final RabbitListenerEndpointRegistrar registrar = new RabbitListenerEndpointRegistrar();

	@Override
	public int getOrder() {
		return LOWEST_PRECEDENCE;
	}

	/**
	 * Set the {@link RabbitListenerEndpointRegistry} that will hold the created
	 * endpoint and manage the lifecycle of the related listener container.
	 */
	public void setEndpointRegistry(RabbitListenerEndpointRegistry endpointRegistry) {
		this.endpointRegistry = endpointRegistry;
	}

	/**
	 * Set the name of the {@link RabbitListenerContainerFactory} to use by default.
	 * <p>If none is specified, "rabbitListenerContainerFactory" is assumed to be defined.
	 */
	public void setContainerFactoryBeanName(String containerFactoryBeanName) {
		this.containerFactoryBeanName = containerFactoryBeanName;
	}

	/**
	 * Set the {@link MessageHandlerMethodFactory} to use to configure the message
	 * listener responsible to serve an endpoint detected by this processor.
	 * <p>By default, {@link DefaultMessageHandlerMethodFactory} is used and it
	 * can be configured further to support additional method arguments
	 * or to customize conversion and validation support. See
	 * {@link DefaultMessageHandlerMethodFactory} Javadoc for more details.
	 */
	public void setMessageHandlerMethodFactory(MessageHandlerMethodFactory messageHandlerMethodFactory) {
		this.messageHandlerMethodFactory.setMessageHandlerMethodFactory(messageHandlerMethodFactory);
	}

	/**
	 * Making a {@link BeanFactory} available is optional; if not set,
	 * {@link RabbitListenerConfigurer} beans won't get autodetected and an
	 * {@link #setEndpointRegistry endpoint registry} has to be explicitly configured.
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}


	@Override
	public void afterSingletonsInstantiated() {
		this.registrar.setBeanFactory(this.beanFactory);

		if (this.beanFactory instanceof ListableBeanFactory) {
			Map<String, RabbitListenerConfigurer> instances =
					((ListableBeanFactory) this.beanFactory).getBeansOfType(RabbitListenerConfigurer.class);
			for (RabbitListenerConfigurer configurer : instances.values()) {
				configurer.configureRabbitListeners(this.registrar);
			}
		}

		if (this.registrar.getEndpointRegistry() == null) {
			if (this.endpointRegistry == null) {
				Assert.state(this.beanFactory != null, "BeanFactory must be set to find endpoint registry by bean name");
				this.endpointRegistry = this.beanFactory.getBean(
						RabbitListenerConfigUtils.RABBIT_LISTENER_ENDPOINT_REGISTRY_BEAN_NAME, RabbitListenerEndpointRegistry.class);
			}
			this.registrar.setEndpointRegistry(this.endpointRegistry);
		}

		if (this.containerFactoryBeanName != null) {
			this.registrar.setContainerFactoryBeanName(this.containerFactoryBeanName);
		}

		// Set the custom handler method factory once resolved by the configurer
		MessageHandlerMethodFactory handlerMethodFactory = this.registrar.getMessageHandlerMethodFactory();
		if (handlerMethodFactory != null) {
			this.messageHandlerMethodFactory.setMessageHandlerMethodFactory(handlerMethodFactory);
		}

		// Actually register all listeners
		this.registrar.afterPropertiesSet();
	}


	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(final Object bean, String beanName) throws BeansException {
		Class<?> targetClass = AopUtils.getTargetClass(bean);
		ReflectionUtils.doWithMethods(targetClass, new ReflectionUtils.MethodCallback() {
			@Override
			public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
				RabbitListener rabbitListener = AnnotationUtils.getAnnotation(method, RabbitListener.class);
				if (rabbitListener != null) {
					processAmqpListener(rabbitListener, method, bean);
				}
			}
		});
		return bean;
	}

	protected void processAmqpListener(RabbitListener rabbitListener, Method method, Object bean) {
		if (AopUtils.isJdkDynamicProxy(bean)) {
			try {
				// Found a @RabbitListener method on the target class for this JDK proxy ->
				// is it also present on the proxy itself?
				method = bean.getClass().getMethod(method.getName(), method.getParameterTypes());
			}
			catch (SecurityException ex) {
				ReflectionUtils.handleReflectionException(ex);
			}
			catch (NoSuchMethodException ex) {
				throw new IllegalStateException(String.format(
						"@RabbitListener method '%s' found on bean target class '%s', " +
								"but not found in any interface(s) for bean JDK proxy. Either " +
								"pull the method up to an interface or switch to subclass (CGLIB) " +
								"proxies by setting proxy-target-class/proxyTargetClass " +
								"attribute to 'true'", method.getName(), method.getDeclaringClass().getSimpleName()));
			}
		}

		MethodRabbitListenerEndpoint endpoint = new MethodRabbitListenerEndpoint();
		endpoint.setBean(bean);
		endpoint.setMethod(method);
		endpoint.setMessageHandlerMethodFactory(this.messageHandlerMethodFactory);
		if (StringUtils.hasText(rabbitListener.id())) {
			endpoint.setId(rabbitListener.id());
		}

		String[] queues = rabbitListener.queues();
		if (rabbitListener.queueReferences()) {
			Assert.state(this.beanFactory != null, "BeanFactory must be set to resolve queues by bean name");
			Queue[] queueInstances = new Queue[queues.length];
			for (int i = 0; i < queues.length; i++) {
				String queueRef = queues[i];
				try {
					queueInstances[i] = this.beanFactory.getBean(queueRef, Queue.class);
				}
				catch (NoSuchBeanDefinitionException ex) {
					throw new BeanInitializationException("Could not register rabbit listener endpoint on [" +
							method + "], no queue with id '" +
							queueRef + "' was found in the application context", ex);
				}
			}
			endpoint.setQueues(queueInstances);
		}
		else {
			endpoint.setQueueNames(queues);
		}

		endpoint.setExclusive(rabbitListener.exclusive());
		if (rabbitListener.priority() >= 0) {
			endpoint.setPriority(rabbitListener.priority());
		}
		if (StringUtils.hasText(rabbitListener.responseRoutingKey())) {
			endpoint.setResponseRoutingKey(rabbitListener.responseRoutingKey());
		}
		if (StringUtils.hasText(rabbitListener.admin())) {
			Assert.state(this.beanFactory != null, "BeanFactory must be set to resolve RabbitAdmin by bean name");
			try {
				endpoint.setAdmin(this.beanFactory.getBean(rabbitListener.admin(), RabbitAdmin.class));
			}
			catch (NoSuchBeanDefinitionException ex) {
				throw new BeanInitializationException("Could not register rabbit listener endpoint on [" +
						method + "], no " + RabbitAdmin.class.getSimpleName() + " with id '" +
						rabbitListener.admin() + "' was found in the application context", ex);
			}
		}


		RabbitListenerContainerFactory<?> factory = null;
		String containerFactoryBeanName = rabbitListener.containerFactory();
		if (StringUtils.hasText(containerFactoryBeanName)) {
			Assert.state(this.beanFactory != null, "BeanFactory must be set to obtain container factory by bean name");
			try {
				factory = this.beanFactory.getBean(containerFactoryBeanName, RabbitListenerContainerFactory.class);
			}
			catch (NoSuchBeanDefinitionException ex) {
				throw new BeanInitializationException("Could not register rabbit listener endpoint on [" +
						method + "], no " + RabbitListenerContainerFactory.class.getSimpleName() + " with id '" +
						containerFactoryBeanName + "' was found in the application context", ex);
			}
		}

		this.registrar.registerEndpoint(endpoint, factory);
	}

	/**
	 * An {@link MessageHandlerMethodFactory} adapter that offers a configurable underlying
	 * instance to use. Useful if the factory to use is determined once the endpoints
	 * have been registered but not created yet.
	 * @see RabbitListenerEndpointRegistrar#setMessageHandlerMethodFactory
	 */
	private class RabbitHandlerMethodFactoryAdapter implements MessageHandlerMethodFactory {

		private MessageHandlerMethodFactory messageHandlerMethodFactory;

		public void setMessageHandlerMethodFactory(MessageHandlerMethodFactory rabbitHandlerMethodFactory1) {
			this.messageHandlerMethodFactory = rabbitHandlerMethodFactory1;
		}

		@Override
		public InvocableHandlerMethod createInvocableHandlerMethod(Object bean, Method method) {
			return getMessageHandlerMethodFactory().createInvocableHandlerMethod(bean, method);
		}

		private MessageHandlerMethodFactory getMessageHandlerMethodFactory() {
			if (this.messageHandlerMethodFactory == null) {
				this.messageHandlerMethodFactory = createDefaultMessageHandlerMethodFactory();
			}
			return this.messageHandlerMethodFactory;
		}

		private MessageHandlerMethodFactory createDefaultMessageHandlerMethodFactory() {
			DefaultMessageHandlerMethodFactory defaultFactory = new DefaultMessageHandlerMethodFactory();
			defaultFactory.setBeanFactory(beanFactory);
			defaultFactory.afterPropertiesSet();
			return defaultFactory;
		}
	}

}
