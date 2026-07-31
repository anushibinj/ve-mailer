package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.WorkspaceDiscoveryRequestDto;
import com.anushibinj.veemailer.dto.WorkspaceDiscoveryResponseDto;
import com.anushibinj.veemailer.exception.WorkspaceDiscoveryNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceDiscoveryServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private WorkspaceDiscoveryService discoveryService;

    private static final String ROOT_URL = "https://ot-internal.saas.microfocus.com";
    private static final String SIGN_IN_URL = ROOT_URL + "/authentication/sign_in";
    private static final String WORKSPACES_URL = ROOT_URL + "/api/shared_spaces/4001/workspaces?fields=name";
    private static final String SIGN_OUT_URL = ROOT_URL + "/authentication/sign_out";

    @BeforeEach
    void setUp() {
        discoveryService = new WorkspaceDiscoveryService(restTemplate, new ObjectMapper());
    }

    private void stubSuccessfulSignIn() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, "LWSSO_COOKIE_KEY=abc123; Path=/; HttpOnly");
        ResponseEntity<String> signInResponse = new ResponseEntity<>("{}", headers, HttpStatus.OK);
        when(restTemplate.exchange(eq(SIGN_IN_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(signInResponse);
    }

    private void stubWorkspacesResponse(String body) {
        when(restTemplate.exchange(eq(WORKSPACES_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));
    }

    private WorkspaceDiscoveryRequestDto buildRequest(String workspaceId) {
        return new WorkspaceDiscoveryRequestDto(ROOT_URL, "4001", workspaceId, "client-id", "client-secret");
    }

    @Test
    void discover_MatchingWorkspaceId_ParsesTitleAndShortcode() {
        stubSuccessfulSignIn();
        stubWorkspacesResponse("{"
                + "\"total_count\":1,"
                + "\"data\":[{\"type\":\"workspace\",\"id\":\"5015\",\"name\":\"Portfolio-Hyd - 77BD\"}],"
                + "\"exceeds_total_count\":false}");

        WorkspaceDiscoveryResponseDto result = discoveryService.discover(buildRequest("5015"));

        assertThat(result.getWorkspaceTitle()).isEqualTo("Portfolio-Hyd");
        assertThat(result.getWorkspaceShortcode()).isEqualTo("77BD");
        assertThat(result.isShortcodeDetected()).isTrue();
        assertThat(result.getWarning()).isNull();
    }

    @Test
    void discover_MatchingWorkspaceId_SelectsCorrectItemFromMultipleResults() {
        stubSuccessfulSignIn();
        stubWorkspacesResponse("{"
                + "\"total_count\":3,"
                + "\"data\":["
                + "{\"id\":\"5009\",\"name\":\"Alpha - AA11\"},"
                + "{\"id\":\"5015\",\"name\":\"Customer ABC - XY99\"},"
                + "{\"id\":\"5010\",\"name\":\"Beta - BB22\"}],"
                + "\"exceeds_total_count\":false}");

        WorkspaceDiscoveryResponseDto result = discoveryService.discover(buildRequest("5015"));

        assertThat(result.getWorkspaceTitle()).isEqualTo("Customer ABC");
        assertThat(result.getWorkspaceShortcode()).isEqualTo("XY99");
        assertThat(result.isShortcodeDetected()).isTrue();
    }

    @Test
    void discover_NameDoesNotParse_FallsBackToUnknownShortcode() {
        stubSuccessfulSignIn();
        stubWorkspacesResponse("{"
                + "\"total_count\":1,"
                + "\"data\":[{\"id\":\"5015\",\"name\":\"Just A Plain Name\"}],"
                + "\"exceeds_total_count\":false}");

        WorkspaceDiscoveryResponseDto result = discoveryService.discover(buildRequest("5015"));

        assertThat(result.getWorkspaceTitle()).isEqualTo("Just A Plain Name");
        assertThat(result.getWorkspaceShortcode()).isEqualTo("UNKNOWN");
        assertThat(result.isShortcodeDetected()).isFalse();
        assertThat(result.getWarning()).isNotBlank();
    }

    @Test
    void discover_NoMatchingWorkspaceId_ThrowsWithRawResponse() {
        stubSuccessfulSignIn();
        String rawJson = "{\"total_count\":2,\"data\":["
                + "{\"id\":\"5009\",\"name\":\"Alpha - AA11\"},"
                + "{\"id\":\"5010\",\"name\":\"Beta - BB22\"}],"
                + "\"exceeds_total_count\":false}";
        stubWorkspacesResponse(rawJson);

        assertThatThrownBy(() -> discoveryService.discover(buildRequest("5015")))
                .isInstanceOf(WorkspaceDiscoveryNotFoundException.class)
                .satisfies(ex -> {
                    WorkspaceDiscoveryNotFoundException notFound = (WorkspaceDiscoveryNotFoundException) ex;
                    assertThat(notFound.getRawResponse()).isEqualTo(rawJson);
                    assertThat(notFound.getMessage()).contains("5015");
                });
    }

    @Test
    void discover_SignInFails_ThrowsIllegalArgumentException() {
        when(restTemplate.exchange(eq(SIGN_IN_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("connection refused"));

        assertThatThrownBy(() -> discoveryService.discover(buildRequest("5015")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void discover_SignInReturnsNoCookies_ThrowsIllegalArgumentException() {
        ResponseEntity<String> signInResponse = new ResponseEntity<>("{}", new HttpHeaders(), HttpStatus.OK);
        when(restTemplate.exchange(eq(SIGN_IN_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(signInResponse);

        assertThatThrownBy(() -> discoveryService.discover(buildRequest("5015")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void discover_WorkspaceListRequestFails_ThrowsIllegalArgumentException() {
        stubSuccessfulSignIn();
        when(restTemplate.exchange(eq(WORKSPACES_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("timeout"));

        assertThatThrownBy(() -> discoveryService.discover(buildRequest("5015")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void discover_AlwaysSignsOut_EvenWhenNotFoundThrown() {
        stubSuccessfulSignIn();
        stubWorkspacesResponse("{\"total_count\":0,\"data\":[]}");

        assertThatThrownBy(() -> discoveryService.discover(buildRequest("5015")))
                .isInstanceOf(WorkspaceDiscoveryNotFoundException.class);

        org.mockito.Mockito.verify(restTemplate)
                .exchange(eq(SIGN_OUT_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class));
    }
}
