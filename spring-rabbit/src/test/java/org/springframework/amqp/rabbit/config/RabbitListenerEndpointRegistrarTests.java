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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.beans.factory.support.StaticListableBeanFactory;

import static org.junit.Assert.*;

/**
 * @author Stephane Nicoll
 */
public class RabbitListenerEndpointRegistrarTests {

	@Rule
	public final ExpectedException thrown = ExpectedException.none();

	private final RabbitListenerEndpointRegistrar registrar = new RabbitListenerEndpointRegistrar();

	private final RabbitListenerEndpointRegistry registry = new RabbitListenerEndpointRegistry();

	private final RabbitListenerContainerTestFactory containerFactory = new RabbitListenerContainerTestFactory();


	@Before
	public void setup() {
		registrar.setEndpointRegistry(registry);
		registrar.setBeanFactory(new StaticListableBeanFactory());
	}

	@Test
	public void registerNullEndpoint() {
		thrown.expect(IllegalArgumentException.class);
		registrar.registerEndpoint(null, containerFactory);
	}

	@Test
	public void registerNullContainerFactoryIsAllowed() throws Exception {
		SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
		endpoint.setId("some id");
		registrar.setContainerFactory(containerFactory);
		registrar.registerEndpoint(endpoint, null);
		registrar.afterPropertiesSet();
		assertNotNull("Container not created", registry.getListenerContainer("some id"));
		assertEquals(1, registry.getListenerContainers().size());
	}

	@Test
	public void registerNullContainerFactoryWithNoDefault() throws Exception {
		SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
		endpoint.setId("some id");
		registrar.registerEndpoint(endpoint, null);

		thrown.expect(IllegalStateException.class);
		thrown.expectMessage(endpoint.toString());
		registrar.afterPropertiesSet();
	}

	@Test
	public void registerContainerWithoutFactory() throws Exception {
		SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
		endpoint.setId("myEndpoint");
		registrar.setContainerFactory(containerFactory);
		registrar.registerEndpoint(endpoint);
		registrar.afterPropertiesSet();
		assertNotNull("Container not created", registry.getListenerContainer("myEndpoint"));
		assertEquals(1, registry.getListenerContainers().size());
	}

}
