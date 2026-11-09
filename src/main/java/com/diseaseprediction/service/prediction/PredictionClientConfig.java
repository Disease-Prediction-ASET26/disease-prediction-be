package com.diseaseprediction.service.prediction;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Chooses the {@link PredictionClient}: the live {@link HttpPredictionClient} when {@code
 * app.prediction.base-url} is set, otherwise the {@link StubPredictionClient}.
 */
@Configuration
public class PredictionClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    /** Generous: the first call loads the NLP models in the Python service. */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(120);

    @Bean
    PredictionClient predictionClient(@Value("${app.prediction.base-url:}") String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return new StubPredictionClient();
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return new HttpPredictionClient(
                RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build());
    }
}
