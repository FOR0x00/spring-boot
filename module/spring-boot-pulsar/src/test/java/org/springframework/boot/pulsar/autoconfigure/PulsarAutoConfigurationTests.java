/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.pulsar.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.ProducerBuilder;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.ReaderBuilder;
import org.apache.pulsar.client.api.interceptor.ProducerInterceptor;
import org.apache.pulsar.common.schema.SchemaType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.pulsar.annotation.PulsarBootstrapConfiguration;
import org.springframework.pulsar.annotation.PulsarListenerAnnotationBeanPostProcessor;
import org.springframework.pulsar.annotation.PulsarReaderAnnotationBeanPostProcessor;
import org.springframework.pulsar.cache.provider.caffeine.CaffeineCacheProvider;
import org.springframework.pulsar.config.ConcurrentPulsarListenerContainerFactory;
import org.springframework.pulsar.config.DefaultPulsarReaderContainerFactory;
import org.springframework.pulsar.config.PulsarListenerContainerFactory;
import org.springframework.pulsar.config.PulsarListenerEndpointRegistry;
import org.springframework.pulsar.config.PulsarReaderEndpointRegistry;
import org.springframework.pulsar.core.CachingPulsarProducerFactory;
import org.springframework.pulsar.core.ConsumerBuilderCustomizer;
import org.springframework.pulsar.core.DefaultPulsarClientFactory;
import org.springframework.pulsar.core.DefaultPulsarConsumerFactory;
import org.springframework.pulsar.core.DefaultPulsarProducerFactory;
import org.springframework.pulsar.core.DefaultPulsarReaderFactory;
import org.springframework.pulsar.core.DefaultSchemaResolver;
import org.springframework.pulsar.core.DefaultTopicResolver;
import org.springframework.pulsar.core.ProducerBuilderCustomizer;
import org.springframework.pulsar.core.PulsarAdministration;
import org.springframework.pulsar.core.PulsarConsumerFactory;
import org.springframework.pulsar.core.PulsarProducerFactory;
import org.springframework.pulsar.core.PulsarReaderFactory;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.pulsar.core.PulsarTopicBuilder;
import org.springframework.pulsar.core.ReaderBuilderCustomizer;
import org.springframework.pulsar.core.SchemaResolver;
import org.springframework.pulsar.core.TopicResolver;
import org.springframework.pulsar.listener.PulsarContainerProperties.TransactionSettings;
import org.springframework.pulsar.reactive.config.DefaultReactivePulsarListenerContainerFactory;
import org.springframework.pulsar.transaction.PulsarAwareTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link PulsarAutoConfiguration}.
 *
 * @author Chris Bono
 * @author Alexander Preuß
 * @author Soby Chacko
 * @author Phillip Webb
 */
class PulsarAutoConfigurationTests {

	private static final String INTERNAL_PULSAR_LISTENER_ANNOTATION_PROCESSOR = "org.springframework.pulsar.config.internalPulsarListenerAnnotationProcessor";

	private static final String INTERNAL_PULSAR_READER_ANNOTATION_PROCESSOR = "org.springframework.pulsar.config.internalPulsarReaderAnnotationProcessor";

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(PulsarAutoConfiguration.class))
		.withBean(PulsarClient.class, () -> mock(PulsarClient.class));

	@Test
	void whenPulsarNotOnClasspathAutoConfigurationIsSkipped() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(PulsarAutoConfiguration.class))
			.withClassLoader(new FilteredClassLoader(PulsarClient.class))
			.run((context) -> assertThat(context).doesNotHaveBean(PulsarAutoConfiguration.class));
	}

	@Test
	void whenSpringPulsarNotOnClasspathAutoConfigurationIsSkipped() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(PulsarTemplate.class))
			.run((context) -> assertThat(context).doesNotHaveBean(PulsarAutoConfiguration.class));
	}

	@Test
	void whenCustomPulsarListenerAnnotationProcessorDefinedAutoConfigurationIsSkipped() {
		this.contextRunner.withBean(INTERNAL_PULSAR_LISTENER_ANNOTATION_PROCESSOR, String.class, () -> "bean")
			.run((context) -> assertThat(context).doesNotHaveBean(PulsarBootstrapConfiguration.class));
	}

	@Test
	void whenCustomPulsarReaderAnnotationProcessorDefinedAutoConfigurationIsSkipped() {
		this.contextRunner.withBean(INTERNAL_PULSAR_READER_ANNOTATION_PROCESSOR, String.class, () -> "bean")
			.run((context) -> assertThat(context).doesNotHaveBean(PulsarBootstrapConfiguration.class));
	}

	@Test
	void autoConfiguresBeans() {
		this.contextRunner.run((context) -> assertThat(context).hasSingleBean(PulsarConfiguration.class)
			.hasSingleBean(PulsarConnectionDetails.class)
			.hasSingleBean(DefaultPulsarClientFactory.class)
			.hasSingleBean(PulsarClient.class)
			.hasSingleBean(PulsarTopicBuilder.class)
			.hasSingleBean(PulsarAdministration.class)
			.hasSingleBean(DefaultSchemaResolver.class)
			.hasSingleBean(DefaultTopicResolver.class)
			.hasSingleBean(CachingPulsarProducerFactory.class)
			.hasSingleBean(PulsarTemplate.class)
			.hasSingleBean(DefaultPulsarConsumerFactory.class)
			.hasSingleBean(ConcurrentPulsarListenerContainerFactory.class)
			.hasSingleBean(DefaultPulsarReaderFactory.class)
			.hasSingleBean(DefaultPulsarReaderContainerFactory.class)
			.hasSingleBean(PulsarListenerAnnotationBeanPostProcessor.class)
			.hasSingleBean(PulsarListenerEndpointRegistry.class)
			.hasSingleBean(PulsarReaderAnnotationBeanPostProcessor.class)
			.hasSingleBean(PulsarReaderEndpointRegistry.class));
	}

	@Test
	void topicDefaultsCanBeDisabled() {
		this.contextRunner.withPropertyValues("spring.pulsar.defaults.topic.enabled=false")
			.run((context) -> assertThat(context).doesNotHaveBean(PulsarTopicBuilder.class));
	}

	@Nested
	class ProducerFactoryTests {

		private final ApplicationContextRunner contextRunner = PulsarAutoConfigurationTests.this.contextRunner;

		@Test
		@SuppressWarnings("unchecked")
		void whenHasUserDefinedBeanDoesNotAutoConfigureBean() {
			PulsarProducerFactory<String> producerFactory = mock(PulsarProducerFactory.class);
			this.contextRunner
				.withBean("customPulsarProducerFactory", PulsarProducerFactory.class, () -> producerFactory)
				.run((context) -> assertThat(context).getBean(PulsarProducerFactory.class).isSameAs(producerFactory));
		}

		@Test
		void whenNoPropertiesUsesCachingPulsarProducerFactory() {
			this.contextRunner.run((context) -> assertThat(context).getBean(PulsarProducerFactory.class)
				.isExactlyInstanceOf(CachingPulsarProducerFactory.class));
		}

		@Test
		void whenCachingDisabledUsesDefaultPulsarProducerFactory() {
			this.contextRunner.withPropertyValues("spring.pulsar.producer.cache.enabled=false")
				.run((context) -> assertThat(context).getBean(PulsarProducerFactory.class)
					.isExactlyInstanceOf(DefaultPulsarProducerFactory.class));
		}

		@Test
		void whenCachingEnabledUsesCachingPulsarProducerFactory() {
			this.contextRunner.withPropertyValues("spring.pulsar.producer.cache.enabled=true")
				.run((context) -> assertThat(context).getBean(PulsarProducerFactory.class)
					.isExactlyInstanceOf(CachingPulsarProducerFactory.class));
		}

		@Test
		void whenCachingEnabledAndCaffeineNotOnClasspathStillUsesCaffeine() {
			this.contextRunner.withClassLoader(new FilteredClassLoader(Caffeine.class))
				.withPropertyValues("spring.pulsar.producer.cache.enabled=true")
				.run((context) -> {
					assertThat(context).getBean(CachingPulsarProducerFactory.class)
						.extracting("producerCache")
						.extracting(Object::getClass)
						.isEqualTo(CaffeineCacheProvider.class);
					assertThat(context).getBean(CachingPulsarProducerFactory.class)
						.extracting("producerCache.cache")
						.extracting(Object::getClass)
						.extracting(Class::getName)
						.asString()
						.startsWith("org.springframework.pulsar.shade.com.github.benmanes.caffeine.cache.");
				});
		}

		@Test
		void whenCustomCachingPropertiesCreatesConfiguredBean() {
			this.contextRunner
				.withPropertyValues("spring.pulsar.producer.cache.expire-after-access=100s",
						"spring.pulsar.producer.cache.maximum-size=5150",
						"spring.pulsar.producer.cache.initial-capacity=200")
				.run((context) -> assertThat(context).getBean(CachingPulsarProducerFactory.class)
					.extracting("producerCache.cache.cache")
					.hasFieldOrPropertyWithValue("maximum", 5150L)
					.hasFieldOrPropertyWithValue("expiresAfterAccessNanos", TimeUnit.SECONDS.toNanos(100)));
		}

		@Test
		void whenHasTopicNamePropertyCreatesConfiguredBean() {
			this.contextRunner.withPropertyValues("spring.pulsar.producer.topic-name=my-topic")
				.run((context) -> assertThat(context).getBean(DefaultPulsarProducerFactory.class)
					.hasFieldOrPropertyWithValue("defaultTopic", "my-topic"));
		}

		@Test
		void injectsExpectedBeans() {
			this.contextRunner
				.withPropertyValues("spring.pulsar.producer.topic-name=my-topic",
						"spring.pulsar.producer.cache.enabled=false")
				.run((context) -> assertThat(context).getBean(DefaultPulsarProducerFactory.class)
					.hasFieldOrPropertyWithValue("pulsarClient", context.getBean(PulsarClient.class))
					.hasFieldOrPropertyWithValue("topicResolver", context.getBean(TopicResolver.class))
					.extracting("topicBuilder")
					.isNotNull());
		}

		@Test
		void hasNoTopicBuilderWhenTopicDefaultsAreDisabled() {
			this.contextRunner.withPropertyValues("spring.pulsar.defaults.topic.enabled=false")
				.run((context) -> assertThat(context).getBean(DefaultPulsarProducerFactory.class)
					.extracting("topicBuilder")
					.isNull());
		}

		@ParameterizedTest
		@ValueSource(booleans = { true, false })
		<T> void whenHasUserDefinedCustomizersAppliesInCorrectOrder(boolean cachingEnabled) {
			this.contextRunner
				.withPropertyValues("spring.pulsar.producer.cache.enabled=" + cachingEnabled,
						"spring.pulsar.producer.name=fromPropsCustomizer")
				.withUserConfiguration(ProducerBuilderCustomizersConfig.class)
				.run((context) -> {
					DefaultPulsarProducerFactory<?> producerFactory = context
						.getBean(DefaultPulsarProducerFactory.class);
					Customizers<ProducerBuilderCustomizer<T>, ProducerBuilder<T>> customizers = Customizers
						.of(ProducerBuilder.class, ProducerBuilderCustomizer::customize);
					assertThat(customizers.fromField(producerFactory, "defaultConfigCustomizers")).callsInOrder(
							ProducerBuilder::producerName, "fromPropsCustomizer", "fromCustomizer1", "fromCustomizer2");
				});
		}

		@TestConfiguration(proxyBeanMethods = false)
		static class ProducerBuilderCustomizersConfig {

			@Bean
			@Order(200)
			ProducerBuilderCustomizer<?> customizerFoo() {
				return (builder) -> builder.producerName("fromCustomizer2");
			}

			@Bean
			@Order(100)
			ProducerBuilderCustomizer<?> customizerBar() {
				return (builder) -> builder.producerName("fromCustomizer1");
			}

		}

	}

	@Nested
	class TemplateTests {

		private final ApplicationContextRunner contextRunner = PulsarAutoConfigurationTests.this.contextRunner;

		@Test
		@SuppressWarnings("unchecked")
		void whenHasUserDefinedBeanDoesNotAutoConfigureBean() {
			PulsarTemplate<String> template = mock(PulsarTemplate.class);
			this.contextRunner.withBean("customPulsarTemplate", PulsarTemplate.class, () -> template)
				.run((context) -> assertThat(context).getBean(PulsarTemplate.class).isSameAs(template));
		}

		@Test
		void injectsExpectedBeans() {
			PulsarProducerFactory<?> producerFactory = mock(PulsarProducerFactory.class);
			SchemaResolver schemaResolver = mock(SchemaResolver.class);
			TopicResolver topicResolver = mock(TopicResolver.class);
			this.contextRunner
				.withBean("customPulsarProducerFactory", PulsarProducerFactory.class, () -> producerFactory)
				.withBean("schemaResolver", SchemaResolver.class, () -> schemaResolver)
				.withBean("topicResolver", TopicResolver.class, () -> topicResolver)
				.run((context) -> assertThat(context).getBean(PulsarTemplate.class)
					.hasFieldOrPropertyWithValue("producerFactory", producerFactory)
					.hasFieldOrPropertyWithValue("schemaResolver", schemaResolver)
					.hasFieldOrPropertyWithValue("topicResolver", topicResolver));
		}

		@Test
		<T> void whenHasUseDefinedProducerInterceptorInjectsBean() {
			ProducerInterceptor interceptor = mock(ProducerInterceptor.class);
			this.contextRunner.withBean("customProducerInterceptor", ProducerInterceptor.class, () -> interceptor)
				.run((context) -> {
					PulsarTemplate<?> pulsarTemplate = context.getBean(PulsarTemplate.class);
					Customizers<ProducerBuilderCustomizer<T>, ProducerBuilder<T>> customizers = Customizers
						.of(ProducerBuilder.class, ProducerBuilderCustomizer::customize);
					assertThat(customizers.fromField(pulsarTemplate, "interceptorsCustomizers"))
						.callsInOrder(ProducerBuilder::intercept, interceptor);
				});
		}

		@Test
		<T> void whenHasUseDefinedProducerInterceptorsInjectsBeansInCorrectOrder() {
			this.contextRunner.withUserConfiguration(InterceptorTestConfiguration.class).run((context) -> {
				ProducerInterceptor interceptorFoo = context.getBean("interceptorFoo", ProducerInterceptor.class);
				ProducerInterceptor interceptorBar = context.getBean("interceptorBar", ProducerInterceptor.class);
				PulsarTemplate<?> pulsarTemplate = context.getBean(PulsarTemplate.class);
				Customizers<ProducerBuilderCustomizer<T>, ProducerBuilder<T>> customizers = Customizers
					.of(ProducerBuilder.class, ProducerBuilderCustomizer::customize);
				assertThat(customizers.fromField(pulsarTemplate, "interceptorsCustomizers"))
					.callsInOrder(ProducerBuilder::intercept, interceptorBar, interceptorFoo);
			});
		}

		@Test
		void whenNoPropertiesEnablesObservation() {
			this.contextRunner.run((context) -> assertThat(context).getBean(PulsarTemplate.class)
				.hasFieldOrPropertyWithValue("observationEnabled", false));
		}

		@Test
		void whenObservationsEnabledEnablesObservation() {
			this.contextRunner.withPropertyValues("spring.pulsar.template.observations-enabled=true")
				.run((context) -> assertThat(context).getBean(PulsarTemplate.class)
					.hasFieldOrPropertyWithValue("observationEnabled", true));
		}

		@Test
		void whenObservationsDisabledDoesNotEnableObservation() {
			this.contextRunner.withPropertyValues("spring.pulsar.template.observations-enabled=false")
				.run((context) -> assertThat(context).getBean(PulsarTemplate.class)
					.hasFieldOrPropertyWithValue("observationEnabled", false));
		}

		@Test
		void whenTransactionEnabledTrueEnablesTransactions() {
			this.contextRunner.withPropertyValues("spring.pulsar.transaction.enabled=true")
				.run((context) -> assertThat(context.getBean(PulsarTemplate.class).transactions().isEnabled())
					.isTrue());
		}

		@Configuration(proxyBeanMethods = false)
		static class InterceptorTestConfiguration {

			@Bean
			@Order(200)
			ProducerInterceptor interceptorFoo() {
				return mock(ProducerInterceptor.class);
			}

			@Bean
			@Order(100)
			ProducerInterceptor interceptorBar() {
				return mock(ProducerInterceptor.class);
			}

		}

	}

	@Nested
	class ConsumerFactoryTests {

		private final ApplicationContextRunner contextRunner = PulsarAutoConfigurationTests.this.contextRunner;

		@Test
		@SuppressWarnings("unchecked")
		void whenHasUserDefinedBeanDoesNotAutoConfigureBean() {
			PulsarConsumerFactory<String> consumerFactory = mock(PulsarConsumerFactory.class);
			this.contextRunner
				.withBean("customPulsarConsumerFactory", PulsarConsumerFactory.class, () -> consumerFactory)
				.run((context) -> assertThat(context).getBean(PulsarConsumerFactory.class).isSameAs(consumerFactory));
		}

		@Test
		void injectsExpectedBeans() {
			this.contextRunner.run((context) -> assertThat(context).getBean(DefaultPulsarConsumerFactory.class)
				.hasFieldOrPropertyWithValue("pulsarClient", context.getBean(PulsarClient.class))
				.extracting("topicBuilder")
				.isNotNull());
		}

		@Test
		void hasNoTopicBuilderWhenTopicDefaultsAreDisabled() {
			this.contextRunner.withPropertyValues("spring.pulsar.defaults.topic.enabled=false")
				.run((context) -> assertThat(context).getBean(DefaultPulsarConsumerFactory.class)
					.hasFieldOrPropertyWithValue("pulsarClient", context.getBean(PulsarClient.class))
					.extracting("topicBuilder")
					.isNull());
		}

		@Test
		<T> void whenHasUserDefinedCustomizersAppliesInCorrectOrder() {
			this.contextRunner.withPropertyValues("spring.pulsar.consumer.name=fromPropsCustomizer")
				.withUserConfiguration(ConsumerBuilderCustomizersConfig.class)
				.run((context) -> {
					DefaultPulsarConsumerFactory<?> consumerFactory = context
						.getBean(DefaultPulsarConsumerFactory.class);
					Customizers<ConsumerBuilderCustomizer<T>, ConsumerBuilder<T>> customizers = Customizers
						.of(ConsumerBuilder.class, ConsumerBuilderCustomizer::customize);
					assertThat(customizers.fromField(consumerFactory, "defaultConfigCustomizers")).callsInOrder(
							ConsumerBuilder::consumerName, "fromPropsCustomizer", "fromCustomizer1", "fromCustomizer2");
				});
		}

		@Test
		void injectsExpectedBeanWithExplicitGenericType() {
			this.contextRunner.withBean(ExplicitGenericTypeConfig.class)
				.run((context) -> assertThat(context).getBean(ExplicitGenericTypeConfig.class)
					.hasFieldOrPropertyWithValue("consumerFactory", context.getBean(PulsarConsumerFactory.class))
					.hasFieldOrPropertyWithValue("containerFactory",
							context.getBean(ConcurrentPulsarListenerContainerFactory.class)));
		}

		@TestConfiguration(proxyBeanMethods = false)
		static class ConsumerBuilderCustomizersConfig {

			@Bean
			@Order(200)
			ConsumerBuilderCustomizer<?> customizerFoo() {
				return (builder) -> builder.consumerName("fromCustomizer2");
			}

			@Bean
			@Order(100)
			ConsumerBuilderCustomizer<?> customizerBar() {
				return (builder) -> builder.consumerName("fromCustomizer1");
			}

		}

		static class ExplicitGenericTypeConfig {

			@Autowired
			PulsarConsumerFactory<TestType> consumerFactory;

			@Autowired
			ConcurrentPulsarListenerContainerFactory<TestType> containerFactory;

			static class TestType {

			}

		}

	}

	@Nested
	class ListenerTests {

		private final ApplicationContextRunner contextRunner = PulsarAutoConfigurationTests.this.contextRunner;

		@Test
		void whenHasUserDefinedListenerContainerFactoryBeanDoesNotAutoConfigureBean() {
			PulsarListenerContainerFactory listenerContainerFactory = mock(PulsarListenerContainerFactory.class);
			this.contextRunner
				.withBean("pulsarListenerContainerFactory", PulsarListenerContainerFactory.class,
						() -> listenerContainerFactory)
				.run((context) -> assertThat(context).getBean(PulsarListenerContainerFactory.class)
					.isSameAs(listenerContainerFactory));
		}

		@Test
		@SuppressWarnings("rawtypes")
		void injectsExpectedBeans() {
			PulsarConsumerFactory<?> consumerFactory = mock(PulsarConsumerFactory.class);
			SchemaResolver schemaResolver = mock(SchemaResolver.class);
			TopicResolver topicResolver = mock(TopicResolver.class);
			this.contextRunner.withBean("pulsarConsumerFactory", PulsarConsumerFactory.class, () -> consumerFactory)
				.withBean("schemaResolver", SchemaResolver.class, () -> schemaResolver)
				.withBean("topicResolver", TopicResolver.class, () -> topicResolver)
				.run((context) -> assertThat(context).getBean(ConcurrentPulsarListenerContainerFactory.class)
					.hasFieldOrPropertyWithValue("consumerFactory", consumerFactory)
					.extracting(ConcurrentPulsarListenerContainerFactory::getContainerProperties)
					.hasFieldOrPropertyWithValue("schemaResolver", schemaResolver)
					.hasFieldOrPropertyWithValue("topicResolver", topicResolver));
		}

		@Test
		@SuppressWarnings("unchecked")
		void whenHasUserDefinedListenerAnnotationBeanPostProcessorBeanDoesNotAutoConfigureBean() {
			PulsarListenerAnnotationBeanPostProcessor<String> listenerAnnotationBeanPostProcessor = mock(
					PulsarListenerAnnotationBeanPostProcessor.class);
			this.contextRunner
				.withBean("org.springframework.pulsar.config.internalPulsarListenerAnnotationProcessor",
						PulsarListenerAnnotationBeanPostProcessor.class, () -> listenerAnnotationBeanPostProcessor)
				.run((context) -> assertThat(context).getBean(PulsarListenerAnnotationBeanPostProcessor.class)
					.isSameAs(listenerAnnotationBeanPostProcessor));
		}

		@Test
		void whenHasCustomProperties() {
			List<String> properties = new ArrayList<>();
			properties.add("spring.pulsar.listener.schema-type=avro");
			this.contextRunner.withPropertyValues(properties.toArray(String[]::new)).run((context) -> {
				ConcurrentPulsarListenerContainerFactory<?> factory = context
					.getBean(ConcurrentPulsarListenerContainerFactory.class);
				assertThat(factory.getContainerProperties().getSchemaType()).isEqualTo(SchemaType.AVRO);
			});
		}

		@Test
		void whenNoPropertiesEnablesObservation() {
			this.contextRunner
				.run((context) -> assertThat(context).getBean(ConcurrentPulsarListenerContainerFactory.class)
					.hasFieldOrPropertyWithValue("containerProperties.observationEnabled", false));
		}

		@Test
		void whenObservationsEnabledEnablesObservation() {
			this.contextRunner.withPropertyValues("spring.pulsar.listener.observation-enabled=true")
				.run((context) -> assertThat(context).getBean(ConcurrentPulsarListenerContainerFactory.class)
					.hasFieldOrPropertyWithValue("containerProperties.observationEnabled", true));
		}

		@Test
		void whenObservationsDisabledDoesNotEnableObservation() {
			this.contextRunner.withPropertyValues("spring.pulsar.listener.observation-enabled=false")
				.run((context) -> assertThat(context).getBean(ConcurrentPulsarListenerContainerFactory.class)
					.hasFieldOrPropertyWithValue("containerProperties.observationEnabled", false));
		}

		@Test
		@EnabledForJreRange(min = JRE.JAVA_21)
		void whenVirtualThreadsAreEnabledOnJava21AndLaterListenerContainerShouldUseVirtualThreads() {
			this.contextRunner.withPropertyValues("spring.threads.virtual.enabled=true").run((context) -> {
				ConcurrentPulsarListenerContainerFactory<?> factory = context
					.getBean(ConcurrentPulsarListenerContainerFactory.class);
				assertThat(factory.getContainerProperties().getConsumerTaskExecutor())
					.isInstanceOf(VirtualThreadTaskExecutor.class);
				Object taskExecutor = factory.getContainerProperties().getConsumerTaskExecutor();
				Object virtualThread = ReflectionTestUtils.getField(taskExecutor, "virtualThreadFactory");
				Thread threadCreated = ((ThreadFactory) virtualThread).newThread(mock(Runnable.class));
				assertThat(threadCreated.getName()).containsPattern("pulsar-consumer-[0-9]+");
			});
		}

		@Test
		@EnabledForJreRange(max = JRE.JAVA_20)
		void whenVirtualThreadsAreEnabledOnJava20AndEarlierListenerContainerShouldNotUseVirtualThreads() {
			this.contextRunner.withPropertyValues("spring.threads.virtual.enabled=true").run((context) -> {
				ConcurrentPulsarListenerContainerFactory<?> factory = context
					.getBean(ConcurrentPulsarListenerContainerFactory.class);
				assertThat(factory.getContainerProperties().getConsumerTaskExecutor()).isNull();
			});
		}

		@Test
		void whenTransactionEnabledTrueListenerContainerShouldUseTransactions() {
			this.contextRunner.withPropertyValues("spring.pulsar.transaction.enabled=true").run((context) -> {
				ConcurrentPulsarListenerContainerFactory<?> factory = context
					.getBean(ConcurrentPulsarListenerContainerFactory.class);
				TransactionSettings transactions = factory.getContainerProperties().transactions();
				assertThat(transactions.isEnabled()).isTrue();
				assertThat(transactions.getTransactionManager()).isNotNull();
			});
		}

		@Test
		void whenTransactionEnabledFalseListenerContainerShouldNotUseTransactions() {
			this.contextRunner.withPropertyValues("spring.pulsar.transaction.enabled=false").run((context) -> {
				ConcurrentPulsarListenerContainerFactory<?> factory = context
					.getBean(ConcurrentPulsarListenerContainerFactory.class);
				TransactionSettings transactions = factory.getContainerProperties().transactions();
				assertThat(transactions.isEnabled()).isFalse();
				assertThat(transactions.getTransactionManager()).isNull();
			});
		}

		@Test
		void whenHasUserDefinedCustomizersAppliesInCorrectOrder() {
			this.contextRunner.withUserConfiguration(ListenerContainerFactoryCustomizersConfig.class)
				.run((context) -> assertThat(context).getBean(ConcurrentPulsarListenerContainerFactory.class)
					.hasFieldOrPropertyWithValue("containerProperties.subscriptionName", ":bar:foo"));
		}

		@TestConfiguration(proxyBeanMethods = false)
		static class ListenerContainerFactoryCustomizersConfig {

			@Bean
			@Order(50)
			PulsarContainerFactoryCustomizer<DefaultReactivePulsarListenerContainerFactory<?>> customizerIgnored() {
				return (containerFactory) -> {
					throw new IllegalStateException("should-not-have-matched");
				};
			}

			@Bean
			@Order(200)
			PulsarContainerFactoryCustomizer<ConcurrentPulsarListenerContainerFactory<?>> customizerFoo() {
				return (containerFactory) -> appendToSubscriptionName(containerFactory, ":foo");
			}

			@Bean
			@Order(100)
			PulsarContainerFactoryCustomizer<ConcurrentPulsarListenerContainerFactory<?>> customizerBar() {
				return (containerFactory) -> appendToSubscriptionName(containerFactory, ":bar");
			}

			private void appendToSubscriptionName(ConcurrentPulsarListenerContainerFactory<?> containerFactory,
					String valueToAppend) {
				String subscriptionName = containerFactory.getContainerProperties().getSubscriptionName();
				String updatedValue = (subscriptionName != null) ? subscriptionName + valueToAppend : valueToAppend;
				containerFactory.getContainerProperties().setSubscriptionName(updatedValue);
			}

		}

	}

	@Nested
	class ReaderFactoryTests {

		private final ApplicationContextRunner contextRunner = PulsarAutoConfigurationTests.this.contextRunner;

		@Test
		@SuppressWarnings("unchecked")
		void whenHasUserDefinedBeanDoesNotAutoConfigureBean() {
			PulsarReaderFactory<String> readerFactory = mock(PulsarReaderFactory.class);
			this.contextRunner.withBean("customPulsarReaderFactory", PulsarReaderFactory.class, () -> readerFactory)
				.run((context) -> assertThat(context).getBean(PulsarReaderFactory.class).isSameAs(readerFactory));
		}

		@Test
		void injectsExpectedBeans() {
			this.contextRunner.run((context) -> assertThat(context).getBean(DefaultPulsarReaderFactory.class)
				.hasFieldOrPropertyWithValue("pulsarClient", context.getBean(PulsarClient.class))
				.extracting("topicBuilder")
				.isNotNull());
		}

		@Test
		void hasNoTopicBuilderWhenTopicDefaultsAreDisabled() {
			this.contextRunner.withPropertyValues("spring.pulsar.defaults.topic.enabled=false")
				.run((context) -> assertThat(context).getBean(DefaultPulsarReaderFactory.class)
					.extracting("topicBuilder")
					.isNull());
		}

		@Test
		<T> void whenHasUserDefinedReaderBuilderCustomizersAppliesInCorrectOrder() {
			this.contextRunner.withPropertyValues("spring.pulsar.reader.name=fromPropsCustomizer")
				.withUserConfiguration(ReaderBuilderCustomizersConfig.class)
				.run((context) -> {
					DefaultPulsarReaderFactory<?> readerFactory = context.getBean(DefaultPulsarReaderFactory.class);
					Customizers<ReaderBuilderCustomizer<T>, ReaderBuilder<T>> customizers = Customizers
						.of(ReaderBuilder.class, ReaderBuilderCustomizer::customize);
					assertThat(customizers.fromField(readerFactory, "defaultConfigCustomizers")).callsInOrder(
							ReaderBuilder::readerName, "fromPropsCustomizer", "fromCustomizer1", "fromCustomizer2");
				});
		}

		@Test
		@EnabledForJreRange(min = JRE.JAVA_21)
		void whenVirtualThreadsAreEnabledOnJava21AndLaterReaderShouldUseVirtualThreads() {
			this.contextRunner.withPropertyValues("spring.threads.virtual.enabled=true").run((context) -> {
				DefaultPulsarReaderContainerFactory<?> factory = context
					.getBean(DefaultPulsarReaderContainerFactory.class);
				assertThat(factory.getContainerProperties().getReaderTaskExecutor())
					.isInstanceOf(VirtualThreadTaskExecutor.class);
				Object taskExecutor = factory.getContainerProperties().getReaderTaskExecutor();
				Object virtualThread = ReflectionTestUtils.getField(taskExecutor, "virtualThreadFactory");
				Thread threadCreated = ((ThreadFactory) virtualThread).newThread(mock(Runnable.class));
				assertThat(threadCreated.getName()).containsPattern("pulsar-reader-[0-9]+");
			});
		}

		@Test
		@EnabledForJreRange(max = JRE.JAVA_20)
		void whenVirtualThreadsAreEnabledOnJava20AndEarlierReaderShouldNotUseVirtualThreads() {
			this.contextRunner.withPropertyValues("spring.threads.virtual.enabled=true").run((context) -> {
				DefaultPulsarReaderContainerFactory<?> factory = context
					.getBean(DefaultPulsarReaderContainerFactory.class);
				assertThat(factory.getContainerProperties().getReaderTaskExecutor()).isNull();
			});
		}

		@Test
		void whenHasUserDefinedFactoryCustomizersAppliesInCorrectOrder() {
			this.contextRunner.withUserConfiguration(ReaderContainerFactoryCustomizersConfig.class)
				.run((context) -> assertThat(context).getBean(DefaultPulsarReaderContainerFactory.class)
					.hasFieldOrPropertyWithValue("containerProperties.readerListener", ":bar:foo"));
		}

		@TestConfiguration(proxyBeanMethods = false)
		static class ReaderBuilderCustomizersConfig {

			@Bean
			@Order(200)
			ReaderBuilderCustomizer<?> customizerFoo() {
				return (builder) -> builder.readerName("fromCustomizer2");
			}

			@Bean
			@Order(100)
			ReaderBuilderCustomizer<?> customizerBar() {
				return (builder) -> builder.readerName("fromCustomizer1");
			}

		}

		@TestConfiguration(proxyBeanMethods = false)
		static class ReaderContainerFactoryCustomizersConfig {

			@Bean
			@Order(50)
			PulsarContainerFactoryCustomizer<DefaultReactivePulsarListenerContainerFactory<?>> customizerIgnored() {
				return (containerFactory) -> {
					throw new IllegalStateException("should-not-have-matched");
				};
			}

			@Bean
			@Order(200)
			PulsarContainerFactoryCustomizer<DefaultPulsarReaderContainerFactory<?>> customizerFoo() {
				return (containerFactory) -> appendToReaderListener(containerFactory, ":foo");
			}

			@Bean
			@Order(100)
			PulsarContainerFactoryCustomizer<DefaultPulsarReaderContainerFactory<?>> customizerBar() {
				return (containerFactory) -> appendToReaderListener(containerFactory, ":bar");
			}

			private void appendToReaderListener(DefaultPulsarReaderContainerFactory<?> containerFactory,
					String valueToAppend) {
				Object readerListener = containerFactory.getContainerProperties().getReaderListener();
				String updatedValue = (readerListener != null) ? readerListener + valueToAppend : valueToAppend;
				containerFactory.getContainerProperties().setReaderListener(updatedValue);
			}

		}

	}

	@Nested
	class TransactionManagerTests {

		private final ApplicationContextRunner contextRunner = PulsarAutoConfigurationTests.this.contextRunner;

		@Test
		@SuppressWarnings("unchecked")
		void whenUserHasDefinedATransactionManagerTheAutoConfigurationBacksOff() {
			PulsarAwareTransactionManager txnMgr = mock(PulsarAwareTransactionManager.class);
			this.contextRunner.withBean("customTransactionManager", PulsarAwareTransactionManager.class, () -> txnMgr)
				.run((context) -> assertThat(context).getBean(PulsarAwareTransactionManager.class).isSameAs(txnMgr));
		}

		@Test
		void whenNoPropertiesAreSetTransactionManagerShouldNotBeDefined() {
			this.contextRunner
				.run((context) -> assertThat(context).doesNotHaveBean(PulsarAwareTransactionManager.class));
		}

		@Test
		void whenTransactionEnabledFalseTransactionManagerIsNotAutoConfigured() {
			this.contextRunner.withPropertyValues("spring.pulsar.transaction.enabled=false")
				.run((context) -> assertThat(context).doesNotHaveBean(PulsarAwareTransactionManager.class));
		}

		@Test
		void whenTransactionEnabledTrueTransactionManagerIsAutoConfigured() {
			this.contextRunner.withPropertyValues("spring.pulsar.transaction.enabled=true")
				.run((context) -> assertThat(context).hasSingleBean(PulsarAwareTransactionManager.class));
		}

	}

}
