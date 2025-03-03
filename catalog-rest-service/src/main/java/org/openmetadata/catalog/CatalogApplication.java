/*
 *  Copyright 2021 Collate
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.openmetadata.catalog;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.health.conf.HealthConfiguration;
import io.dropwizard.health.core.HealthCheckBundle;
import io.dropwizard.jdbi3.JdbiFactory;
import io.dropwizard.jersey.errors.EarlyEofExceptionMapper;
import io.dropwizard.jersey.errors.LoggingExceptionMapper;
import io.dropwizard.jersey.jackson.JsonProcessingExceptionMapper;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.server.DefaultServerFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ServerProperties;
import org.jdbi.v3.core.Jdbi;
import org.openmetadata.catalog.events.EventFilter;
import org.openmetadata.catalog.exception.CatalogGenericExceptionMapper;
import org.openmetadata.catalog.exception.ConstraintViolationExceptionMapper;
import org.openmetadata.catalog.exception.JsonMappingExceptionMapper;
import org.openmetadata.catalog.module.CatalogModule;
import org.openmetadata.catalog.resources.CollectionRegistry;
import org.openmetadata.catalog.resources.config.ConfigResource;
import org.openmetadata.catalog.resources.search.SearchResource;
import org.openmetadata.catalog.security.AuthenticationConfiguration;
import org.openmetadata.catalog.security.AuthorizerConfiguration;
import org.openmetadata.catalog.security.CatalogAuthorizer;
import org.openmetadata.catalog.security.NoopAuthorizer;
import org.openmetadata.catalog.security.NoopFilter;
import org.openmetadata.catalog.security.auth.CatalogSecurityContextRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Response;
import java.lang.reflect.InvocationTargetException;

/**
 * Main catalog application
 */
public class CatalogApplication extends Application<CatalogApplicationConfig> {
  public static final Logger LOG = LoggerFactory.getLogger(CatalogApplication.class);
  private Injector injector;
  private CatalogAuthorizer authorizer;
  public CatalogApplication() {
  }

  @Override
  public void run(CatalogApplicationConfig catalogConfig, Environment environment) throws ClassNotFoundException,
          IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {

    final JdbiFactory factory = new JdbiFactory();
    final Jdbi jdbi = factory.build(environment, catalogConfig.getDataSourceFactory(), "mysql3");

//    SqlLogger sqlLogger = new SqlLogger() {
//      @Override
//      public void logAfterExecution(StatementContext context) {
//        LOG.info("sql {}, parameters {}, timeTaken {} ms", context.getRenderedSql(),
//                context.getBinding().toString(), context.getElapsedTime(ChronoUnit.MILLIS));
//      }
//    };
//    jdbi.setSqlLogger(sqlLogger);


    // Register Authorizer
    registerAuthorizer(catalogConfig, environment, jdbi);

    // Registering config api
    environment.jersey().register(new ConfigResource(catalogConfig));

    // Unregister dropwizard default exception mappers
    ((DefaultServerFactory) catalogConfig.getServerFactory()).setRegisterDefaultExceptionMappers(false);
    environment.jersey().property(ServerProperties.RESPONSE_SET_STATUS_OVER_SEND_ERROR, true);
    environment.jersey().register(MultiPartFeature.class);
    environment.jersey().register(CatalogGenericExceptionMapper.class);

    // Override constraint violation mapper to catch Json validation errors
    environment.jersey().register(new ConstraintViolationExceptionMapper());

    // Restore dropwizard default exception mappers
    environment.jersey().register(new LoggingExceptionMapper<>() {});
    environment.jersey().register(new JsonProcessingExceptionMapper(true));
    environment.jersey().register(new EarlyEofExceptionMapper());
    environment.jersey().register(JsonMappingExceptionMapper.class);
    environment.healthChecks().register("UserDatabaseCheck", new CatalogHealthCheck(catalogConfig, jdbi));
    registerResources(catalogConfig, environment, jdbi);

    // Register Event Handler
    registerEventFilter(catalogConfig, environment, jdbi);
  }

  @SneakyThrows
  @Override
  public void initialize(Bootstrap<CatalogApplicationConfig> bootstrap) {
    bootstrap.addBundle(new SwaggerBundle<>() {
    @Override
      protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(CatalogApplicationConfig catalogConfig) {
        return catalogConfig.getSwaggerBundleConfig();
      }
    });
    bootstrap.addBundle(new AssetsBundle("/assets", "/", "index.html", "static"));
    bootstrap.addBundle(new HealthCheckBundle<>() {
      @Override
      protected HealthConfiguration getHealthConfiguration(final CatalogApplicationConfig configuration) {
        return configuration.getHealthConfiguration();
      }
    });
    //bootstrap.addBundle(new CatalogJdbiExceptionsBundle());
    super.initialize(bootstrap);
  }

  private void registerAuthorizer(CatalogApplicationConfig catalogConfig, Environment environment, Jdbi jdbi)
          throws NoSuchMethodException, ClassNotFoundException, IllegalAccessException, InvocationTargetException,
          InstantiationException  {
    AuthorizerConfiguration authorizerConf = catalogConfig.getAuthorizerConfiguration();
    AuthenticationConfiguration authenticationConfiguration = catalogConfig.getAuthenticationConfiguration();
    if (authorizerConf != null) {
      authorizer = ((Class<CatalogAuthorizer>) Class.forName(authorizerConf.getClassName()))
              .getConstructor().newInstance();
      authorizer.init(authorizerConf, jdbi);
      String filterClazzName = authorizerConf.getContainerRequestFilter();
      ContainerRequestFilter filter;
      if (StringUtils.isEmpty(filterClazzName)) {
        filter = new CatalogSecurityContextRequestFilter(); // default
      } else {
        filter = ((Class<ContainerRequestFilter>) Class.forName(filterClazzName))
                .getConstructor(AuthenticationConfiguration.class).newInstance(authenticationConfiguration);
      }
      LOG.info("Registering ContainerRequestFilter: {}", filter.getClass().getCanonicalName());
      environment.jersey().register(filter);
    } else {
      LOG.info("Authorizer config not set, setting noop authorizer");
      authorizer = NoopAuthorizer.class.getConstructor().newInstance();
      ContainerRequestFilter filter = NoopFilter.class.getConstructor().newInstance();
      environment.jersey().register(filter);
    }
    injector = Guice.createInjector(new CatalogModule(authorizer));
  }

  private void registerEventFilter(CatalogApplicationConfig catalogConfig, Environment environment, Jdbi jdbi) {
    if (catalogConfig.getEventHandlerConfiguration() != null) {
      ContainerResponseFilter eventFilter = new EventFilter(catalogConfig, jdbi);
      environment.jersey().register(eventFilter);
    }
  }

  private void registerResources(CatalogApplicationConfig config, Environment environment, Jdbi jdbi) {
    CollectionRegistry.getInstance().registerResources(jdbi, environment, config, authorizer);

    environment.lifecycle().manage(new Managed() {
      @Override
      public void start() {
      }

      @Override
      public void stop() {
        long startTime = System.currentTimeMillis();
        LOG.info("Took " + (System.currentTimeMillis() - startTime) + " ms to close all the services");
      }
    });
    environment.jersey().register(new SearchResource(config.getElasticSearchConfiguration()));
    environment.jersey().register(new JsonPatchProvider());
    ErrorPageErrorHandler eph = new ErrorPageErrorHandler();
    eph.addErrorPage(Response.Status.NOT_FOUND.getStatusCode(), "/");
    environment.getApplicationContext().setErrorHandler(eph);
  }

  public static void main(String[] args) throws Exception {
    CatalogApplication catalogApplication = new CatalogApplication();
    catalogApplication.run(args);
  }
}
