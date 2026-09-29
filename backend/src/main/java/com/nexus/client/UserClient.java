package com.nexus.client;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

/**
 * Service that retrieves User data from mock or enterprise user APIs.
 */
@Service
public class UserClient {

    public static final String DEFAULT_USERS_ENDPOINT = "https://api.jsoning.com/mock/x1f6odpaon/users";

    private final RestTemplate restTemplate;

    public UserClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Retrieves the list of users from the default mock API endpoint.
     *
     * @return immutable list of {@link User}; empty list if request fails
     */
    public List<User> fetchAllUsers() {
        return fetchUsersFromEndpoint(DEFAULT_USERS_ENDPOINT);
    }

    /**
     * Retrieves the list of users from a custom/generic endpoint URL.
     *
     * @param endpointUrl Target HTTP API endpoint URL
     * @return immutable list of {@link User}; empty list if request fails
     */
    public List<User> fetchUsersFromEndpoint(String endpointUrl) {
        if (endpointUrl == null || endpointUrl.trim().isEmpty()) {
            System.err.println("UserClient error: Endpoint URL is null or empty");
            return Collections.emptyList();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<User[]> response = restTemplate.exchange(
                    endpointUrl.trim(),
                    HttpMethod.GET,
                    requestEntity,
                    User[].class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return List.of(response.getBody());
            } else {
                System.err.println("Unexpected response from [" + endpointUrl + "]: " + response.getStatusCode());
                return Collections.emptyList();
            }
        } catch (RestClientException ex) {
            System.err.println("Error calling Users API [" + endpointUrl + "]: " + ex.getMessage());
            return Collections.emptyList();
        }
    }
}
