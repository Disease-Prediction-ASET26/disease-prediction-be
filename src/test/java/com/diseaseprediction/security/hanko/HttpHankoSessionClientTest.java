package com.diseaseprediction.security.hanko;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpHankoSessionClientTest {

    private MockRestServiceServer server;
    private HttpHankoSessionClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hanko.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpHankoSessionClient(builder.build());
    }

    @Test
    void sendsTheTokenInTheBodyAndReadsIsValid() {
        server.expect(requestTo("http://hanko.test/sessions/validate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"session_token\":\"token-1\"}"))
                .andRespond(
                        withSuccess(
                                "{\"is_valid\":true,\"claims\":{\"subject\":\"x\"}}",
                                MediaType.APPLICATION_JSON));

        assertThat(client.isSessionValid("token-1")).isTrue();
        server.verify();
    }

    @Test
    void invalidSessionIsFalse() {
        server.expect(requestTo("http://hanko.test/sessions/validate"))
                .andRespond(withSuccess("{\"is_valid\":false}", MediaType.APPLICATION_JSON));

        assertThat(client.isSessionValid("token-1")).isFalse();
    }

    @Test
    void serverErrorMeansUnavailableAndTheMessageHasNoToken() {
        server.expect(requestTo("http://hanko.test/sessions/validate"))
                .andRespond(withServerError().body("token-1 echoed back"));

        assertThatThrownBy(() -> client.isSessionValid("token-1"))
                .isInstanceOf(IdentityProviderUnavailableException.class)
                .message()
                .doesNotContain("token-1");
    }

    @Test
    void responseWithoutIsValidMeansUnavailable() {
        server.expect(requestTo("http://hanko.test/sessions/validate"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.isSessionValid("token-1"))
                .isInstanceOf(IdentityProviderUnavailableException.class);
    }
}
