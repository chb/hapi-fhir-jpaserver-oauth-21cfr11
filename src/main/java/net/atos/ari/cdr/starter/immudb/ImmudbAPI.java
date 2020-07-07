/*
 * Copyright (c) 2020. Boston Children's Hospital. http://www.childrenshospital.org/research/departments-divisions-programs/programs/chip
 * Author: christopher.gentle@childrens.harvard.edu
 */


package net.atos.ari.cdr.starter.immudb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.Base64Utils;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component("immudb")
public class ImmudbAPI {
    private final Logger logger = LoggerFactory.getLogger(ImmudbAPI.class);

    final private String LOGIN_JSON =
            "{\n" +
            "  \"user\": \"%s\",\n" +
            "  \"password\": \"%s\"\n" +
            "}";


    final private String SET_URI = "/v1/immurestproxy/item";
    final private String SET_JSON = "{\"key\":\"%s\",\"value\":\"%s\"}" ;

    private static final String IMMUDB_BASE_URL = System.getenv("IMMUDB_BASE_URL");
    private static final String IMMUDB_USER = System.getenv("IMMUDB_USER");
    private static final String IMMUDB_PASSWORD = System.getenv("IMMUDB_PASSWORD");

    private WebClient webClient;
    private String baseURL;

    /**
     *
     * The default constructor will create a REST client for communicating with the ImmuDB gateway. if
     * environment variables are present then the URL, user and password will be take from the
     * environment.
     *
     */
    public ImmudbAPI() {
        if ((IMMUDB_BASE_URL == null) || (IMMUDB_USER == null) || (IMMUDB_PASSWORD == null) ) {
            logger.info("No environment variables provided. Using defaults for some or all of: IMMUDB_BASE_URL IMMUDB_USER IMMUDB_PASSWORD");
        } else {
            logger.info("Environment variables provided. Using IMMUDB_BASE_URL IMMUDB_USER IMMUDB_PASSWORD environment variables");
        }
        this.baseURL = (IMMUDB_BASE_URL == null) ? "http://localhost:3323" : IMMUDB_BASE_URL;
        String user = (IMMUDB_USER == null) ? "immu" : IMMUDB_USER;
        String password = (IMMUDB_PASSWORD == null) ? "immu" : IMMUDB_PASSWORD;

        webClient = makeClient(user, password);
    }

    /**
     *
     * The parameterised constructor will create a REST client for communicating with the ImmuDB gateway. with the
     *  URL, user and password taken from the parameters.
     *
     */
    public ImmudbAPI(String username, String password, String baseURL) {
        this.baseURL = baseURL;
        webClient = makeClient(username, password);
    }

    /**
     * Contact the ImmuDB gateway to exchange the "long term" user and password for a bearer token.
     *
     * @param username the immudb/immugw username to use
     * @param password the immudb/immugw password to use
     *
     * @return a bearer token or null
     */

    private String getBearer(String username, String password) {
        webClient
                = WebClient
                .builder()
                .baseUrl(baseURL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        WebClient.RequestHeadersSpec<?> request = webClient
                .post()
                .uri("/v1/immurestproxy/login")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(String.format(
                        LOGIN_JSON,
                        new String(Base64Utils.encode(username.getBytes())),
                        new String(Base64Utils.encode(password.getBytes()))
                ));

        ImmudbToken responseJson = request
                .exchange()
                .block()
                .bodyToMono(ImmudbToken.class)
                .block();

        //String decodedToken = responseJson.getToken() ; // new String(Base64Utils.decode(responseJson.getToken().getBytes()));
        String decodedToken = new String(Base64Utils.decode(responseJson.getToken().getBytes()));
        logger.debug("Received token - {}", decodedToken);
        return decodedToken;
    }

    /**
     * Debug logging for the request and the header
     *
     * @return
     */
    ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            if (logger.isDebugEnabled()) {
                StringBuilder sb = new StringBuilder("Request: \n");
                //append clientRequest method and url
                clientRequest
                        .headers()
                        .forEach((name, values) ->
                        {
                            sb.append(name+":");
                            values.forEach(value -> sb.append(" " + value));
                            sb.append("\n");
                        });
                logger.debug(sb.toString());
            }
            return Mono.just(clientRequest);
        });
    }

    /**
     *
     * Build a web client for use in communicating with the immugw REST API.
     *
     * @param username
     * @param password
     *
     * @return a copy of the created WebClient
     */
    private WebClient makeClient(String username, String password) {

        String bearer = getBearer(username, password);

        webClient = WebClient
                .builder()
                .baseUrl(baseURL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer "+ bearer )
                .filters(exchangeFilterFunctions -> {
                    exchangeFilterFunctions.add(logRequest());
                })
                .build();
        return webClient;

    }

    /**
     * Convenience function for converting some text into a base64 string
     *
     * @param fromPlaintext
     *
     * @return a base64 representation of the bytes in the original String
     */
    private String makeB64(String fromPlaintext) {
        return new String(Base64Utils.encode(fromPlaintext.getBytes()));
    }

    /**
     * Add an item to the immuDB journal.
     *
     * @param key
     * @param value
     *
     * @return the response JSON as a string. This is usually of the form {"index":"n"}
     *
     */
    public String addToJournal(String key, String value) {
        String payload = String.format(SET_JSON, makeB64(key), makeB64(value));

        WebClient.RequestHeadersSpec<?> request =
                webClient
                .post()
                .uri(SET_URI)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(payload);

        String responseJson =
                request.exchange()
                .block()
                .bodyToMono(String.class)
                .block();


        return responseJson;

    }


}
