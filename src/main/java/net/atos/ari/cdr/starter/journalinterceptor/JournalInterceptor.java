/*
 * Copyright (c) 2020. Boston Children's Hospital. http://www.childrenshospital.org/research/departments-divisions-programs/programs/chip
 * Author: christopher.gentle@childrens.harvard.edu
 */

package net.atos.ari.cdr.starter.journalinterceptor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.interceptor.InterceptorAdapter;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import net.atos.ari.cdr.starter.immudb.ImmudbAPI;
import org.hl7.fhir.dstu3.model.Resource;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Intercept outgoingResponses and ensure we log them to the cryptographic journal (immugw/immudb) before they are
 * delivered.
 */
public class JournalInterceptor extends InterceptorAdapter {
    private final Logger ourLog = LoggerFactory.getLogger(JournalInterceptor.class);
    private static IParser jsonParser = FhirContext.forDstu3().newJsonParser();

    public JournalInterceptor(ImmudbAPI immudbAPI) {
        this.immudbAPI = immudbAPI;
    }

    ImmudbAPI immudbAPI;

    /**
     *
     * Ensure that any resources that have IDs are logged to the immutable journal.
     *
     *
     * @param theRequestDetails
     * @param theResponseObject
     * @return
     */
    @Override
    public boolean outgoingResponse(RequestDetails theRequestDetails, IBaseResource theResponseObject) {
        ServletRequestDetails details = (ServletRequestDetails) theRequestDetails;
        Resource res = (Resource) theResponseObject;
        ourLog.debug("OUTGOING RESPONSE theResponse of resource type {} with response ID: {}", res.getResourceType(), res.getId());
        if (res.hasId()){
             String result = immudbAPI.addToJournal(res.getIdElement().getIdPart(), jsonParser.encodeResourceToString(res));
             ourLog.debug("Journaled {} - result {}", res.getIdElement().getIdPart(), result);
        }

        return outgoingResponse(details, theResponseObject, details.getServletRequest(), details.getServletResponse());
    }

}