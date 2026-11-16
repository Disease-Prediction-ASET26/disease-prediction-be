package com.diseaseprediction.security.hanko;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * Calls Hanko's {@code POST /sessions/validate} with {@code {"session_token": "..."}} and reads
 * {@code is_valid}. Short timeouts: the check runs on every uncached request.
 */
@Component
public class HttpHankoSessionClient implements HankoSessionClient {

    private final RestClient restClient;

    @Autowired
    public HttpHankoSessionClient(HankoProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient =
                RestClient.builder()
                        .baseUrl(properties.apiUrl().toString())
                        .requestFactory(requestFactory)
                        .build();
    }

    HttpHankoSessionClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public boolean isSessionValid(String sessionToken) {
        ValidateResponse response;
        try {
            response =
                    restClient
                            .post()
                            .uri("/sessions/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(new ValidateRequest(sessionToken))
                            .retrieve()
                            .body(ValidateResponse.class);
        } catch (RestClientException exception) {
            // Only the exception type: messages of some client exceptions include response bodies.
            throw new IdentityProviderUnavailableException(
                    "Hanko session check failed (" + exception.getClass().getSimpleName() + ")",
                    exception);
        }
        if (response == null || response.valid() == null) {
            throw new IdentityProviderUnavailableException(
                    "Hanko session check returned no is_valid field");
        }
        return response.valid();
    }

    record ValidateRequest(@JsonProperty("session_token") String sessionToken) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ValidateResponse(@JsonProperty("is_valid") Boolean valid) {}
}
