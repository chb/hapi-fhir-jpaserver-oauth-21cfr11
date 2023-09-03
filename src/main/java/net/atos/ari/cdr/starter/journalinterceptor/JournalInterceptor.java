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
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Intercept outgoingResponses and ensure we log them to the cryptographic journal (immugw/immudb) before they are
 * delivered.
 */
public class JournalInterceptor extends InterceptorAdapter {
    private final Logger ourLog = LoggerFactory.getLogger(JournalInterceptor.class);
    private static IParser jsonParser = FhirContext.forR4().newJsonParser();

    public JournalInterceptor(ImmudbAPI immudbAPI) {
        this.immudbAPI = immudbAPI;
    }

    ImmudbAPI immudbAPI;

    /**
     *
     * Ensure that any resources that have IDs are logged to the immutable journal.
     *
     * This is a simple implementation that sends anything with an ID to the journal on the assumption that
     * any returned resource represents either a post-mutation state (for POST/PUT/PATCH HTTP verbs with the "representation"
     * header set) or the current state of a resource in the database.
     *
     * For a cleaner version, add a filter to capture _only_ resources from mutation verbs.
     *
     * @param theRequestDetails
     * @param theResponseObject
     * @return
     */
    @Override
    public boolean outgoingResponse(RequestDetails theRequestDetails, IBaseResource theResponseObject) {
        try {
            ServletRequestDetails details = (ServletRequestDetails) theRequestDetails;
            Resource res = (Resource) theResponseObject;
            ourLog.debug("OUTGOING RESPONSE theResponse of resource type {} with response ID: {}", res.getResourceType(), res.getId());
            /*
             * Journal all returned resources.
             */
            if (res.hasId()){
                String result = immudbAPI.addToJournal(res.getIdElement().getIdPart(), jsonParser.encodeResourceToString(res));
                ourLog.debug("Journaled {} - result {}", res.getIdElement().getIdPart(), result);
            }

            /**
             * 21CFR11: In a production system a failure to add the record to a journal or queue should result in a
             * boolean false being returned to flag that a problem was encountered with the journaling of resource
             * and provenance information. In this example implementation we will assume everything is fine if there is no
             * exception 🙃
             */

            return outgoingResponse(details, theResponseObject, details.getServletRequest(), details.getServletResponse());
        } catch (Exception ex) {
            Resource res = (Resource) theResponseObject;
            ourLog.info("ERROR sending {} to the journal", res.hasId() ? res.getIdElement().getIdPart() : "(No ID)" );
            return false;
        }
    }

}