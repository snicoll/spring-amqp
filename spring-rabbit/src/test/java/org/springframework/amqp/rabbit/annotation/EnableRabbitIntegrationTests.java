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

import java.util.UUID;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.test.BrokerRunning;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.*;

/**
 *
 * @author Stephane Nicoll
 */
@ContextConfiguration(classes = EnableRabbitIntegrationTests.EnableRabbitConfig.class)
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext
public class EnableRabbitIntegrationTests {

	@Rule
	public final BrokerRunning brokerRunning = BrokerRunning.isRunning();

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Test
	public void test() {
		assertEquals("FOO", rabbitTemplate.convertSendAndReceive("foo"));
	}

	public static class MyService {

		@RabbitListener(queues = "requestQueue", queueReferences = true)
		public String capitalize(String foo) {
			return foo.toUpperCase();
		}
	}

	@Configuration
	@EnableRabbit
	public static class EnableRabbitConfig {

		@Bean
		public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory() {
			SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
			factory.setConnectionFactory(rabbitConnectionFactory());
			return factory;
		}

		@Bean
		public MyService myService() {
			return new MyService();
		}

		// Rabbit infrastructure setup

		@Bean
		public ConnectionFactory rabbitConnectionFactory() {
			CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
			connectionFactory.setHost("localhost");
			return connectionFactory;
		}

		@Bean
		public RabbitTemplate rabbitTemplate() {
			RabbitTemplate template = new RabbitTemplate(rabbitConnectionFactory());
			template.setExchange(ex().getName());
			template.setRoutingKey("test");
			return template;
		}

		@Bean
		public DirectExchange ex() {
			return new DirectExchange(UUID.randomUUID().toString(), false, true);
		}

		@Bean
		public Binding binding() {
			return BindingBuilder.bind(requestQueue()).to(ex()).with("test");
		}

		@Bean
		public Queue requestQueue() {
			return new AnonymousQueue();
		}

		@Bean
		public RabbitAdmin admin() {
			return new RabbitAdmin(rabbitConnectionFactory());
		}
	}

}
