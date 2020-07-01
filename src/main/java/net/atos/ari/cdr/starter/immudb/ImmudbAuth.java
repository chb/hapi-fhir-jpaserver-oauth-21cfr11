package net.atos.ari.cdr.starter.immudb;

import net.atos.ari.cdr.starter.journalinterceptor.JournalInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.Base64Utils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Collections;

public class ImmudbAuth {
    private final Logger logger = LoggerFactory.getLogger(ImmudbAuth.class);

    final private String LOGIN_JSON =
            "{\n" +
            "  \"user\": \"%s\",\n" +
            "  \"password\": \"%s\"\n" +
            "}";


    final private String SET_URI = "/v1/immurestproxy/item";
    final private String SET_JSON =
            "{\"key\":\"%s\",\"value\":\"%s\"}" ;


    private WebClient webClient;



    public ImmudbAuth(String username, String password) {

        webClient = makeClient(username, password);
    }

    private String getBearer(String username, String password) {
        WebClient loginClient
                = WebClient
                .builder()
                .baseUrl("http://localhost:3323")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        WebClient.RequestHeadersSpec<?> request = loginClient
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

    private WebClient makeClient(String username, String password) {

        String bearer = getBearer(username, password);

        webClient = WebClient
                .builder()
                .baseUrl("http://localhost:3323")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer", bearer )
                .filters(exchangeFilterFunctions -> {
                    exchangeFilterFunctions.add(logRequest());
                })
                .build();
        return webClient;

    }

    private String makeB64(String fromPlaintext) {
        return new String(Base64Utils.encode(fromPlaintext.getBytes()));
    }

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
