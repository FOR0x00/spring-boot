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

package org.springframework.boot.security.oauth2.client.autoconfigure.servlet;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.BeanIds;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthenticatedPrincipalOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationCodeGrantFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.web.filter.CompositeFilter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OAuth2ClientWebSecurityAutoConfiguration}.
 *
 * @author Madhura Bhave
 * @author Andy Wilkinson
 */
class OAuth2ClientWebSecurityAutoConfigurationTests {

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(OAuth2ClientWebSecurityAutoConfiguration.class));

	@Test
	void autoConfigurationIsConditionalOnAuthorizedClientService() {
		this.contextRunner
			.run((context) -> assertThat(context).doesNotHaveBean(OAuth2ClientWebSecurityAutoConfiguration.class));
	}

	@Test
	void configurationRegistersAuthorizedClientRepositoryBean() {
		this.contextRunner.withUserConfiguration(OAuth2AuthorizedClientServiceConfiguration.class)
			.run((context) -> assertThat(context).hasSingleBean(OAuth2AuthorizedClientRepository.class));
	}

	@Test
	void authorizedClientRepositoryBeanIsConditionalOnMissingBean() {
		this.contextRunner.withUserConfiguration(OAuth2AuthorizedClientRepositoryConfiguration.class).run((context) -> {
			assertThat(context).hasSingleBean(OAuth2AuthorizedClientRepository.class);
			assertThat(context).hasBean("testAuthorizedClientRepository");
		});
	}

	@Test
	void securityConfigurerConfiguresOAuth2Login() {
		this.contextRunner.withUserConfiguration(OAuth2AuthorizedClientServiceConfiguration.class).run((context) -> {
			ClientRegistrationRepository expected = context.getBean(ClientRegistrationRepository.class);
			ClientRegistrationRepository actual = (ClientRegistrationRepository) ReflectionTestUtils.getField(
					getSecurityFilters(context, OAuth2LoginAuthenticationFilter.class).get(0),
					"clientRegistrationRepository");
			assertThat(isEqual(expected.findByRegistrationId("first"), actual.findByRegistrationId("first"))).isTrue();
			assertThat(isEqual(expected.findByRegistrationId("second"), actual.findByRegistrationId("second")))
				.isTrue();
		});
	}

	@Test
	void securityConfigurerConfiguresAuthorizationCode() {
		this.contextRunner.withUserConfiguration(OAuth2AuthorizedClientServiceConfiguration.class).run((context) -> {
			ClientRegistrationRepository expected = context.getBean(ClientRegistrationRepository.class);
			ClientRegistrationRepository actual = (ClientRegistrationRepository) ReflectionTestUtils.getField(
					getSecurityFilters(context, OAuth2AuthorizationCodeGrantFilter.class).get(0),
					"clientRegistrationRepository");
			assertThat(isEqual(expected.findByRegistrationId("first"), actual.findByRegistrationId("first"))).isTrue();
			assertThat(isEqual(expected.findByRegistrationId("second"), actual.findByRegistrationId("second")))
				.isTrue();
		});
	}

	@Test
	void securityConfigurerBacksOffWhenClientRegistrationBeanAbsent() {
		this.contextRunner.withUserConfiguration(TestConfig.class).run((context) -> {
			assertThat(getSecurityFilters(context, OAuth2LoginAuthenticationFilter.class)).isEmpty();
			assertThat(getSecurityFilters(context, OAuth2AuthorizationCodeGrantFilter.class)).isEmpty();
		});
	}

	@Test
	void securityFilterChainConfigBacksOffWhenOtherSecurityFilterChainBeanPresent() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class))
			.withUserConfiguration(TestSecurityFilterChainConfiguration.class)
			.run((context) -> {
				assertThat(getSecurityFilters(context, OAuth2LoginAuthenticationFilter.class)).isEmpty();
				assertThat(getSecurityFilters(context, OAuth2AuthorizationCodeGrantFilter.class)).isEmpty();
				assertThat(context).getBean(OAuth2AuthorizedClientService.class).isNotNull();
			});
	}

	@Test
	void securityFilterChainConfigConditionalOnSecurityFilterChainClass() {
		this.contextRunner.withUserConfiguration(ClientRegistrationRepositoryConfiguration.class)
			.withClassLoader(new FilteredClassLoader(SecurityFilterChain.class))
			.run((context) -> {
				assertThat(getSecurityFilters(context, OAuth2LoginAuthenticationFilter.class)).isEmpty();
				assertThat(getSecurityFilters(context, OAuth2AuthorizationCodeGrantFilter.class)).isEmpty();
			});
	}

	private List<Filter> getSecurityFilters(AssertableWebApplicationContext context, Class<? extends Filter> filter) {
		return getSecurityFilterChain(context).getFilters().stream().filter(filter::isInstance).toList();
	}

	private SecurityFilterChain getSecurityFilterChain(AssertableWebApplicationContext context) {
		Filter springSecurityFilterChain = context.getBean(BeanIds.SPRING_SECURITY_FILTER_CHAIN, Filter.class);
		FilterChainProxy filterChainProxy = getFilterChainProxy(springSecurityFilterChain);
		SecurityFilterChain securityFilterChain = filterChainProxy.getFilterChains().get(0);
		return securityFilterChain;
	}

	private FilterChainProxy getFilterChainProxy(Filter filter) {
		if (filter instanceof FilterChainProxy filterChainProxy) {
			return filterChainProxy;
		}
		if (filter instanceof CompositeFilter) {
			List<?> filters = (List<?>) ReflectionTestUtils.getField(filter, "filters");
			return (FilterChainProxy) filters.stream()
				.filter(FilterChainProxy.class::isInstance)
				.findFirst()
				.orElseThrow();
		}
		throw new IllegalStateException("No FilterChainProxy found");
	}

	private boolean isEqual(ClientRegistration reg1, ClientRegistration reg2) {
		boolean result = ObjectUtils.nullSafeEquals(reg1.getClientId(), reg2.getClientId());
		result = result && ObjectUtils.nullSafeEquals(reg1.getClientName(), reg2.getClientName());
		result = result && ObjectUtils.nullSafeEquals(reg1.getClientSecret(), reg2.getClientSecret());
		result = result && ObjectUtils.nullSafeEquals(reg1.getScopes(), reg2.getScopes());
		result = result && ObjectUtils.nullSafeEquals(reg1.getRedirectUri(), reg2.getRedirectUri());
		result = result && ObjectUtils.nullSafeEquals(reg1.getRegistrationId(), reg2.getRegistrationId());
		result = result
				&& ObjectUtils.nullSafeEquals(reg1.getAuthorizationGrantType(), reg2.getAuthorizationGrantType());
		result = result && ObjectUtils.nullSafeEquals(reg1.getProviderDetails().getAuthorizationUri(),
				reg2.getProviderDetails().getAuthorizationUri());
		result = result && ObjectUtils.nullSafeEquals(reg1.getProviderDetails().getUserInfoEndpoint(),
				reg2.getProviderDetails().getUserInfoEndpoint());
		result = result && ObjectUtils.nullSafeEquals(reg1.getProviderDetails().getTokenUri(),
				reg2.getProviderDetails().getTokenUri());
		return result;
	}

	@Configuration(proxyBeanMethods = false)
	@EnableWebSecurity
	static class TestConfig {

		@Bean
		TomcatServletWebServerFactory tomcat() {
			return new TomcatServletWebServerFactory(0);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@Import(ClientRegistrationRepositoryConfiguration.class)
	static class OAuth2AuthorizedClientServiceConfiguration {

		@Bean
		InMemoryOAuth2AuthorizedClientService authorizedClientService(
				ClientRegistrationRepository clientRegistrationRepository) {
			return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@Import(OAuth2AuthorizedClientServiceConfiguration.class)
	static class OAuth2AuthorizedClientRepositoryConfiguration {

		@Bean
		OAuth2AuthorizedClientRepository testAuthorizedClientRepository(
				OAuth2AuthorizedClientService authorizedClientService) {
			return new AuthenticatedPrincipalOAuth2AuthorizedClientRepository(authorizedClientService);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@Import(TestConfig.class)
	static class ClientRegistrationRepositoryConfiguration {

		@Bean
		ClientRegistrationRepository clientRegistrationRepository() {
			List<ClientRegistration> registrations = new ArrayList<>();
			registrations.add(getClientRegistration("first", "https://user-info-uri.com"));
			registrations.add(getClientRegistration("second", "https://other-user-info"));
			return new InMemoryClientRegistrationRepository(registrations);
		}

		private ClientRegistration getClientRegistration(String id, String userInfoUri) {
			ClientRegistration.Builder builder = ClientRegistration.withRegistrationId(id);
			builder.clientName("foo")
				.clientId("foo")
				.clientAuthenticationMethod(
						org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.scope("read")
				.clientSecret("secret")
				.redirectUri("https://redirect-uri.com")
				.authorizationUri("https://authorization-uri.com")
				.tokenUri("https://token-uri.com")
				.userInfoUri(userInfoUri)
				.userNameAttributeName("login");
			return builder.build();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@Import(OAuth2AuthorizedClientServiceConfiguration.class)
	static class TestSecurityFilterChainConfiguration {

		@Bean
		SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
			return http.securityMatcher("/**")
				.authorizeHttpRequests((authorize) -> authorize.anyRequest().authenticated())
				.build();

		}

	}

}
