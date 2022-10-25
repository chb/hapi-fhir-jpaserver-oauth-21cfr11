
/*
 * Copyright (c) 2020. Boston Children's Hospital. http://www.childrenshospital.org/research/departments-divisions-programs/programs/chip
 * Author: christopher.gentle@childrens.harvard.edu
 */

package net.atos.ari.cdr.starter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.jpa.api.config.DaoConfig;
import ca.uhn.fhir.jpa.api.dao.IFhirSystemDao;
import ca.uhn.fhir.jpa.config.GeneratedDaoAndResourceProviderConfigDstu3;
import ca.uhn.fhir.jpa.graphql.GraphQLProvider;
import ca.uhn.fhir.jpa.provider.TerminologyUploaderProvider;
import ca.uhn.fhir.jpa.provider.dstu3.JpaConformanceProviderDstu3;
import ca.uhn.fhir.jpa.provider.dstu3.JpaSystemProviderDstu3;
import ca.uhn.fhir.jpa.search.DatabaseBackedPagingProvider;
import ca.uhn.fhir.jpa.subscription.match.config.WebsocketDispatcherConfig;
import ca.uhn.fhir.narrative.DefaultThymeleafNarrativeGenerator;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.ETagSupportEnum;
import ca.uhn.fhir.rest.server.ElementsSupportEnum;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.*;
import ca.uhn.fhir.rest.server.provider.ResourceProviderFactory;
import ca.uhn.fhir.rest.server.util.ISearchParamRegistry;
import ca.uhn.hapi.converters.server.VersionedApiConverterInterceptor;
import net.atos.ari.cdr.starter.config.FhirServerConfig;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Meta;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import javax.servlet.ServletException;

public class JpaRestfulServer extends RestfulServer {

    private static final long serialVersionUID = 1L;

    private AnnotationConfigWebApplicationContext myAppCtx;

    @SuppressWarnings("unchecked")
    @Override
    protected void initialize() throws ServletException {
        super.initialize();

        // Get the spring context from the web container (it's declared in web.xml)
        WebApplicationContext parentAppCtx = ContextLoaderListener.getCurrentWebApplicationContext();

        /*
         * Support FHIR DSTU3 format. This means that the server
         * will use the DSTU3 bundle format and other DSTU3 encoding changes.
         */
        FhirVersionEnum fhirVersion = FhirVersionEnum.DSTU3;


        ResourceProviderFactory beans;
        @SuppressWarnings("rawtypes")
        IFhirSystemDao systemDao;
        ETagSupportEnum etagSupport;
        String baseUrlProperty;
        List<Object> providers = new ArrayList<>();
        GeneratedDaoAndResourceProviderConfigDstu3 a;
        /*
         * The conformance provider exports the supported resources, search parameters, etc for
         * this server. The JPA version adds resource counts to the exported statement, so it
         * is a nice addition.
         */

        if (fhirVersion == FhirVersionEnum.DSTU3) {
            myAppCtx = new AnnotationConfigWebApplicationContext();
            myAppCtx.setServletConfig(getServletConfig());
            myAppCtx.setParent(parentAppCtx);
            myAppCtx.register(FhirServerConfig.class, WebsocketDispatcherConfig.class);
            myAppCtx.refresh();
            setFhirContext(FhirContext.forDstu3());
            beans = myAppCtx.getBean("myResourceProvidersDstu3", ResourceProviderFactory.class);
            providers.add(myAppCtx.getBean("mySystemProviderDstu3", JpaSystemProviderDstu3.class));
            systemDao = myAppCtx.getBean("mySystemDaoDstu3", IFhirSystemDao.class);
            etagSupport = ETagSupportEnum.ENABLED;

            JpaConformanceProviderDstu3 confProvider = new JpaConformanceProviderDstu3(this, systemDao, myAppCtx.getBean(DaoConfig.class), myAppCtx.getBean(ISearchParamRegistry.class));
            confProvider.setImplementationDescription("HAPI FHIR DSTU3 Server");
            setServerConformanceProvider(confProvider);

            providers.add(myAppCtx.getBean(TerminologyUploaderProvider.class));
            providers.add(myAppCtx.getBean(GraphQLProvider.class));
        } else
            throw new IllegalStateException();

        providers.add(myAppCtx.getBean(JpaSystemProviderDstu3.class));

        /*
         * This server tries to dynamically generate narratives
         */
        FhirContext ctx = getFhirContext();
        ctx.setNarrativeGenerator(new DefaultThymeleafNarrativeGenerator());

        /*
         * The resource and system providers (which actually implement the various FHIR
         * operations in this server) are all retrieved from the spring context above
         * and are provided to the server here.
         */
        registerProviders(beans.createProviders());
        registerProviders(providers);

        /*
         * Enable CORS
         */
        CorsInterceptor corsInterceptor = new CorsInterceptor();
        registerInterceptor(corsInterceptor);

        /*
         * Enable FHIRPath evaluation
         */
        registerInterceptor(new FhirPathFilterInterceptor());

        /*
         * Enable version conversion
         */
        registerInterceptor(new VersionedApiConverterInterceptor());

        /*
        	 * Enable ETag Support (this is already the default)
        	 */
        setETagSupport(ETagSupportEnum.ENABLED);

        /*
         * We want to format the response using nice HTML if it's a browser, since this
         * makes things a little easier for testers.
         */
        ResponseHighlighterInterceptor responseHighlighterInterceptor = new ResponseHighlighterInterceptor();
        responseHighlighterInterceptor.setShowRequestHeaders(false);
        responseHighlighterInterceptor.setShowResponseHeaders(true);
        registerInterceptor(responseHighlighterInterceptor);

        registerInterceptor(new BanUnsupportedHttpMethodsInterceptor());

        /*
         * Default to JSON and pretty printing
         */
        setDefaultPrettyPrint(true);
        setDefaultResponseEncoding(EncodingEnum.JSON);

        /*
         * Use extended support for the _elements parameter
         */
        setElementsSupport(ElementsSupportEnum.EXTENDED);

        /*
         * Spool results to the database
         */
        setPagingProvider(myAppCtx.getBean(DatabaseBackedPagingProvider.class));

        /*
         * Load interceptors for the server from Spring (these are defined in FhirServerConfig.java)
         */
        Collection<IServerInterceptor> interceptorBeans = myAppCtx
                .getBeansOfType(IServerInterceptor.class)
                .values();
        for (IServerInterceptor interceptor : interceptorBeans) {
            this.registerInterceptor(interceptor);
        }

    }

}
