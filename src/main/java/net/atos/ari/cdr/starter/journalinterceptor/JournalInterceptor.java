package net.atos.ari.cdr.starter.journalinterceptor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.ResponseDetails;
import ca.uhn.fhir.rest.server.interceptor.InterceptorAdapter;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import net.atos.ari.cdr.starter.immudb.ImmudbAPI;
import net.atos.ari.cdr.starter.oauth2.KeyCloakInterceptor;
import org.hl7.fhir.dstu3.model.Resource;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class JournalInterceptor extends InterceptorAdapter {
    private final Logger ourLog = LoggerFactory.getLogger(JournalInterceptor.class);
    private static IParser jsonParser = FhirContext.forDstu3().newJsonParser();

    public JournalInterceptor(ImmudbAPI immudbAPI) {
        this.immudbAPI = immudbAPI;
    }

    ImmudbAPI immudbAPI;

    @Override
    public boolean outgoingResponse(RequestDetails theRequestDetails, IBaseResource theResponseObject) {
        ServletRequestDetails details = (ServletRequestDetails) theRequestDetails;
        Resource res = (Resource) theResponseObject;
        ourLog.debug("OUTGOING RESPONSE theResponse of resource type {} with response ID: {}", res.getResourceType(), res.getId());
        if (res.hasId()){
             String result = immudbAPI.addToJournal(res.getId(), jsonParser.encodeResourceToString(res));
             ourLog.debug("Journaled {} - result {}", res.getIdElement().getIdPart(), result);
        }

        return outgoingResponse(details, theResponseObject, details.getServletRequest(), details.getServletResponse());
    }

}