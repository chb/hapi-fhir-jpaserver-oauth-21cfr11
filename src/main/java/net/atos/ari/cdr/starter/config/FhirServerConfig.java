package net.atos.ari.cdr.starter.config;

import java.sql.SQLException;
import java.util.Properties;

import javax.persistence.EntityManagerFactory;
import javax.xml.bind.annotation.XmlType;


import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.config.HapiJpaConfig;
import ca.uhn.fhir.jpa.config.dstu3.JpaDstu3Config;
import ca.uhn.fhir.jpa.config.util.HapiEntityManagerFactoryUtil;
import ca.uhn.fhir.jpa.model.entity.ModelConfig;
import ca.uhn.fhir.jpa.search.HapiHSearchAnalysisConfigurers;
import ca.uhn.fhir.jpa.util.DerbyTenSevenHapiFhirDialect;
import net.atos.ari.cdr.starter.immudb.ImmudbAPI;
import net.atos.ari.cdr.starter.journalinterceptor.JournalInterceptor;
import org.apache.commons.dbcp2.BasicDataSource;
import org.hibernate.search.backend.lucene.cfg.LuceneBackendSettings;
import org.hibernate.search.backend.lucene.cfg.LuceneIndexSettings;
import org.hibernate.search.engine.cfg.BackendSettings;
import org.hl7.fhir.dstu2.model.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import ca.uhn.fhir.jpa.api.config.DaoConfig;
import ca.uhn.fhir.jpa.util.SubscriptionsRequireManualActivationInterceptorDstu3;
import ca.uhn.fhir.rest.server.interceptor.IServerInterceptor;
import ca.uhn.fhir.rest.server.interceptor.LoggingInterceptor;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import net.atos.ari.cdr.starter.oauth2.KeyCloakInterceptor;

/**
 * This is the primary configuration file for the example server
 */
@Configuration
@Import({JpaDstu3Config.class})
@EnableTransactionManagement()
public class FhirServerConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(FhirServerConfig.class);

    private static final String DEFAULT_MYSQL_PORT = "3306";
    private static final String DEFAULT_POSTGRESQL_PORT = "5432";
    private static final String DEFAULT_LUCENE_FOLDER = "target/lucenefiles";

    private static final String MARIADB_VENDOR = "MARIADB";
    private static final String MYSQL_VENDOR = "MYSQL";
    private static final String POSTGRESQL_VENDOR = "POSTGRESQL";
    private static final String DERBY_VENDOR = "DERBY";

    private static final String LOCALHOST = "localhost";

    // Const from properties
    private static final String DB_VENDOR = System.getenv("DB_VENDOR") == null ? DERBY_VENDOR : System.getenv("DB_VENDOR");
    private static final String DB_HOST = System.getenv("DB_HOST") == null ? LOCALHOST : System.getenv("DB_HOST");
    private static String DB_PORT = null;
    private static final String DB_USER = System.getenv("DB_USER") == null ? "" : System.getenv("DB_USER");
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD") == null ? "" : System.getenv("DB_PASSWORD");
    private static final String DB_DATABASE = System.getenv("DB_DATABASE") == null ? "" : System.getenv("DB_DATABASE");
    private static final String LUCENE_FOLDER = System.getenv("LUCENE_FOLDER") == null ? DEFAULT_LUCENE_FOLDER : System.getenv("LUCENE_FOLDER");

    public static final String FHIR_LUCENE_LOCATION_DSTU3 = "fhir.lucene.location.dstu3";

    /**
     * Configure FHIR properties around the the JPA server via this bean
     */
    @Bean()
    public DaoConfig daoConfig() {
        DaoConfig retVal = new DaoConfig();
        retVal.setAllowMultipleDelete(true);
        retVal.addSupportedSubscriptionType(Subscription.SubscriptionChannelType.WEBSOCKET);
        retVal.addSupportedSubscriptionType(Subscription.SubscriptionChannelType.RESTHOOK);
        retVal.addSupportedSubscriptionType(Subscription.SubscriptionChannelType.EMAIL);
        retVal.setEnableInMemorySubscriptionMatching(true);
        return retVal;
    }

    @Bean
    public ModelConfig modelConfig() {
        return daoConfig().getModelConfig();
    }

    /**
     * The following bean configures the database connection.
     * The 'url' property value of "jdbc:derby:directory:jpaserver_derby_files;create=true"
     * indicates that the server should save resources in a directory called "jpaserver_derby_files".
     *
     * A URL to a remote database could also be placed here, along with login credentials and other properties supported by BasicDataSource.
     * @throws SQLException
     */
    @Bean(destroyMethod = "close")
    public BasicDataSource dataSource() {
        BasicDataSource retVal = new BasicDataSource();
        try {

            switch (DB_VENDOR) {
            case MYSQL_VENDOR:
                retVal.setDriver(new com.mysql.jdbc.Driver());
                DB_PORT = System.getenv("DB_PORT") == null ? DEFAULT_MYSQL_PORT : System.getenv("DB_PORT");
                break;
            case MARIADB_VENDOR:
                retVal.setDriver(new org.mariadb.jdbc.Driver());
                DB_PORT = System.getenv("DB_PORT") == null ? DEFAULT_MYSQL_PORT : System.getenv("DB_PORT");
                break;
            case POSTGRESQL_VENDOR:
                retVal.setDriver(new org.postgresql.Driver());
                DB_PORT = System.getenv("DB_PORT") == null ? DEFAULT_POSTGRESQL_PORT : System.getenv("DB_PORT");
                break;
            case DERBY_VENDOR:
            default:
                retVal.setDriver(new org.apache.derby.jdbc.EmbeddedDriver());
                retVal.setUrl("jdbc:derby:directory:target/jpaserver_derby_files;create=true");
            }
            retVal.setUsername(DB_USER);
            retVal.setPassword(DB_PASSWORD);
            if (DB_VENDOR.equalsIgnoreCase(DERBY_VENDOR) == false)
                retVal.setUrl("jdbc:" + DB_VENDOR.toLowerCase() + "://" + DB_HOST + ":" + DB_PORT + "/" + DB_DATABASE + "?useSSL=false&serverTimezone=UTC");
            return retVal;
        } catch (SQLException sqlex) {
            LOGGER.error("Exception in database connection", sqlex);
            return null;
        }
    }

    @Bean()
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(ConfigurableListableBeanFactory theConfigurableListableBeanFactory, FhirContext theFhirContext) {
        LocalContainerEntityManagerFactoryBean retVal = HapiEntityManagerFactoryUtil.newEntityManagerFactory(theConfigurableListableBeanFactory, theFhirContext);
        retVal.setPersistenceUnitName("HAPI_PU");
        retVal.setDataSource(dataSource());
        retVal.setJpaProperties(jpaProperties());
        return retVal;
    }

    private Properties jpaProperties() {
        Properties extraProperties = new Properties();
        LOGGER.info("DB_VENDOR: {}", DB_VENDOR);

        switch (DB_VENDOR) {
        case MYSQL_VENDOR:
            extraProperties.put("hibernate.dialect", "org.hibernate.dialect.MySQL5InnoDBDialect");
            break;
        case MARIADB_VENDOR:
            extraProperties.put("hibernate.dialect", "org.hibernate.dialect.MariaDB103Dialect");
            break;
        case POSTGRESQL_VENDOR:
            extraProperties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
            break;
        case DERBY_VENDOR:
        default:
            extraProperties.put("hibernate.dialect", DerbyTenSevenHapiFhirDialect.class.getName());
        }
        extraProperties.put("hibernate.format_sql", Boolean.TRUE.toString());
        extraProperties.put("hibernate.show_sql", Boolean.FALSE.toString());
        extraProperties.put("hibernate.hbm2ddl.auto", "update");
        extraProperties.put("hibernate.jdbc.batch_size", "20");
        extraProperties.put("hibernate.cache.use_query_cache", Boolean.FALSE.toString());
        extraProperties.put("hibernate.cache.use_second_level_cache", Boolean.FALSE.toString());
        extraProperties.put("hibernate.cache.use_structured_entries", Boolean.FALSE.toString());
        extraProperties.put("hibernate.cache.use_minimal_puts", Boolean.FALSE.toString());

        extraProperties.put(BackendSettings.backendKey(BackendSettings.TYPE), "lucene");
        extraProperties.put(BackendSettings.backendKey(LuceneBackendSettings.ANALYSIS_CONFIGURER),
                HapiHSearchAnalysisConfigurers.HapiLuceneAnalysisConfigurer.class.getName());
        extraProperties.put(BackendSettings.backendKey(LuceneIndexSettings.DIRECTORY_TYPE), "local-filesystem");
        extraProperties.put(BackendSettings.backendKey(LuceneIndexSettings.DIRECTORY_ROOT), LUCENE_FOLDER);
        extraProperties.put(BackendSettings.backendKey(LuceneBackendSettings.LUCENE_VERSION), "LUCENE_CURRENT");

        return extraProperties;
    }

    /**
     * Do some fancy logging to create a nice access log that has details about each incoming request.
     */
    public LoggingInterceptor loggingInterceptor() {
        LoggingInterceptor retVal = new LoggingInterceptor();
        retVal.setLoggerName("fhirtest.access");
        retVal.setMessageFormat(
            "Path[${servletPath}] Source[${requestHeader.x-forwarded-for}] Operation[${operationType} ${operationName} ${idOrResourceName}] UA[${requestHeader.user-agent}] Params[${requestParameters}] ResponseEncoding[${responseEncodingNoDefault}]");
        retVal.setLogExceptions(true);
        retVal.setErrorMessageFormat("ERROR - ${requestVerb} ${requestUrl}");
        return retVal;
    }

    /**
     * This interceptor adds some pretty syntax highlighting in responses when a browser is detected
     */
    @Bean(autowire = Autowire.BY_TYPE)
    public ResponseHighlighterInterceptor responseHighlighterInterceptor() {
        return new ResponseHighlighterInterceptor();
    }

    @Bean(autowire = Autowire.BY_TYPE)
    public IServerInterceptor subscriptionSecurityInterceptor() {
        return new SubscriptionsRequireManualActivationInterceptorDstu3();
    }

    @Bean(autowire = Autowire.BY_TYPE)
    public IServerInterceptor subscriptionKeyCloakInterceptor() {
        return new KeyCloakInterceptor();
    }

    @Bean(autowire = Autowire.BY_TYPE)
    public IServerInterceptor journalInterceptor() {
        return new JournalInterceptor(new ImmudbAPI());
    }


    @Bean()
    public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        JpaTransactionManager retVal = new JpaTransactionManager();
        retVal.setEntityManagerFactory(entityManagerFactory);
        return retVal;
    }

}
