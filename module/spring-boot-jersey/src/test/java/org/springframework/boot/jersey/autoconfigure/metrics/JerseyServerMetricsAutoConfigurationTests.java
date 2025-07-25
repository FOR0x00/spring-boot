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

package org.springframework.boot.jersey.autoconfigure.metrics;

import java.net.URI;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.observation.MeterObservationHandler;
import io.micrometer.observation.Observation.Context;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.glassfish.jersey.micrometer.server.ObservationApplicationEventListener;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jersey.autoconfigure.JerseyAutoConfiguration;
import org.springframework.boot.jersey.autoconfigure.ResourceConfigCustomizer;
import org.springframework.boot.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.web.server.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JerseyServerMetricsAutoConfiguration}.
 *
 * @author Michael Weirauch
 * @author Michael Simons
 * @author Moritz Halbritter
 */
class JerseyServerMetricsAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JerseyServerMetricsAutoConfiguration.class));

	private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner(
			AnnotationConfigServletWebServerApplicationContext::new)
		.withConfiguration(AutoConfigurations.of(JerseyAutoConfiguration.class,
				JerseyServerMetricsAutoConfiguration.class, TomcatServletWebServerAutoConfiguration.class,
				TomcatServletWebServerAutoConfiguration.class, SimpleMetricsExportAutoConfiguration.class,
				ObservationAutoConfiguration.class, MetricsAutoConfiguration.class))
		.withUserConfiguration(ResourceConfiguration.class)
		.withPropertyValues("server.port:0");

	@Test
	void shouldOnlyBeActiveInWebApplicationContext() {
		this.contextRunner.run((context) -> assertThat(context).doesNotHaveBean(ResourceConfigCustomizer.class));
	}

	@Test
	void shouldProvideAllNecessaryBeans() {
		this.webContextRunner.run((context) -> assertThat(context).hasBean("jerseyMetricsUriTagFilter")
			.hasSingleBean(ResourceConfigCustomizer.class));
	}

	@Test
	void httpRequestsAreTimed() {
		this.webContextRunner.withUserConfiguration(MetricsConfiguration.class).run((context) -> {
			doRequest(context);
			Thread.sleep(500);
			MeterRegistry registry = context.getBean(MeterRegistry.class);
			Timer timer = registry.get("http.server.requests").tag("uri", "/users/{id}").timer();
			assertThat(timer.count()).isOne();
		});
	}

	@Test
	void noHttpRequestsTimedWhenJerseyInstrumentationMissingFromClasspath() {
		this.webContextRunner.withClassLoader(new FilteredClassLoader(ObservationApplicationEventListener.class))
			.run((context) -> {
				doRequest(context);
				MeterRegistry registry = context.getBean(MeterRegistry.class);
				assertThat(registry.find("http.server.requests").timer()).isNull();
			});
	}

	private static void doRequest(AssertableWebApplicationContext context) {
		int port = context.getSourceApplicationContext(AnnotationConfigServletWebServerApplicationContext.class)
			.getWebServer()
			.getPort();
		RestTemplate restTemplate = new RestTemplate();
		restTemplate.getForEntity(URI.create("http://localhost:" + port + "/users/3"), String.class);
	}

	@Configuration(proxyBeanMethods = false)
	static class ResourceConfiguration {

		@Bean
		ResourceConfig resourceConfig() {
			return new ResourceConfig().register(new TestResource());
		}

		@Path("/users")
		public static class TestResource {

			@GET
			@Path("/{id}")
			public String getUser(@PathParam("id") String id) {
				return id;
			}

		}

	}

	@Configuration(proxyBeanMethods = false)
	static class MetricsConfiguration {

		@Bean
		MeterObservationHandler<Context> meterObservationHandler(MeterRegistry registry) {
			return new DefaultMeterObservationHandler(registry);
		}

	}

}
