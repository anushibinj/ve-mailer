package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.WorkspaceDiscoveryRequestDto;
import com.anushibinj.veemailer.dto.WorkspaceDiscoveryResponseDto;
import com.anushibinj.veemailer.exception.WorkspaceDiscoveryNotFoundException;
import com.anushibinj.veemailer.util.UrlNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Discovers a workspace's Title and Shortcode from the ValueEdge REST API on behalf of
 * the workspace creation wizard (Step 2). The frontend never calls ValueEdge directly —
 * this service performs the sign-in + shared-space workspace list call using the
 * credentials supplied by the administrator, then matches and parses the result.
 *
 * The Octane SDK ({@link com.hpe.adm.nga.sdk.Octane}) used elsewhere in this app is
 * workspace-scoped (it requires a concrete workspace ID at construction time) and has no
 * notion of the shared-space-level "list workspaces" endpoint used here, so this service
 * talks to ValueEdge directly via {@link RestTemplate}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceDiscoveryService {

    /**
     * Splits a ValueEdge workspace name into a title and trailing alphanumeric shortcode,
     * e.g. "Portfolio-Hyd - 77BD" -> title "Portfolio-Hyd", shortcode "77BD". The shortcode
     * is the trailing token after the last dash-like separator, containing no whitespace.
     */
    private static final Pattern TITLE_SHORTCODE_PATTERN =
            Pattern.compile("^(.+?)\\s*[-\u2013\u2014]\\s*([A-Za-z0-9]+)$");

    static final String UNKNOWN_SHORTCODE = "UNKNOWN";
    static final String SHORTCODE_UNDETECTED_WARNING =
            "Workspace shortcode could not be identified automatically. The workspace will be "
                    + "created as a Draft and cannot be enabled until the shortcode is corrected by a Super Admin.";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WorkspaceDiscoveryResponseDto discover(WorkspaceDiscoveryRequestDto request) {
        String rootUrl = UrlNormalizer.normalizeRootUrl(request.getRootUrl());
        String sharedSpaceId = request.getSharedSpaceId().trim();
        String workspaceId = request.getWorkspaceId().trim();
        String clientId = request.getClientId().trim();
        String clientKey = request.getClientKey().trim();

        List<String> sessionCookies = signIn(rootUrl, clientId, clientKey);
        try {
            String rawBody = fetchWorkspacesJson(rootUrl, sharedSpaceId, sessionCookies);
            return parseDiscoveryResponse(rawBody, workspaceId);
        } finally {
            signOutQuietly(rootUrl, sessionCookies);
        }
    }

    private List<String> signIn(String rootUrl, String clientId, String clientKey) {
        String url = rootUrl + "/authentication/sign_in";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("client_id", clientId, "client_secret", clientKey);

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        } catch (RestClientException ex) {
            log.warn("ValueEdge sign-in failed [rootUrl={}, clientId={}]: {}", rootUrl, clientId, ex.getMessage());
            throw new IllegalArgumentException(
                    "Unable to authenticate with ValueEdge. Please check the Root URL, Client ID and Client Key. "
                            + safeMessage(ex), ex);
        }

        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookies == null || setCookies.isEmpty()) {
            throw new IllegalArgumentException(
                    "ValueEdge sign-in did not return a session. Please verify the Root URL and credentials.");
        }
        return setCookies.stream()
                .map(cookie -> cookie.split(";", 2)[0])
                .collect(Collectors.toList());
    }

    private String fetchWorkspacesJson(String rootUrl, String sharedSpaceId, List<String> sessionCookies) {
        String url = rootUrl + "/api/shared_spaces/" + sharedSpaceId + "/workspaces?fields=name";
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, String.join("; ", sessionCookies));
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        try {
            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return response.getBody();
        } catch (RestClientException ex) {
            log.warn("ValueEdge workspace list request failed [rootUrl={}, sharedSpaceId={}]: {}",
                    rootUrl, sharedSpaceId, ex.getMessage());
            throw new IllegalArgumentException(
                    "Unable to retrieve workspaces from ValueEdge for Shared Space " + sharedSpaceId + ". "
                            + safeMessage(ex), ex);
        }
    }

    private void signOutQuietly(String rootUrl, List<String> sessionCookies) {
        try {
            String url = rootUrl + "/authentication/sign_out";
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.COOKIE, String.join("; ", sessionCookies));
            restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(headers), Void.class);
        } catch (RestClientException ex) {
            // Non-critical — the ValueEdge session will simply expire naturally.
            log.debug("ValueEdge sign-out failed (non-critical): {}", ex.getMessage());
        }
    }

    private WorkspaceDiscoveryResponseDto parseDiscoveryResponse(String rawJson, String workspaceId) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (Exception ex) {
            throw new IllegalArgumentException("ValueEdge returned an unexpected response format.");
        }

        JsonNode dataArray = root.path("data");
        JsonNode match = null;
        if (dataArray.isArray()) {
            for (JsonNode item : dataArray) {
                if (workspaceId.equals(item.path("id").asText(null))) {
                    match = item;
                    break;
                }
            }
        }

        if (match == null) {
            throw new WorkspaceDiscoveryNotFoundException(
                    "No workspace with ID " + workspaceId + " was found in the ValueEdge response.", rawJson);
        }

        return parseTitleAndShortcode(match.path("name").asText(""));
    }

    private WorkspaceDiscoveryResponseDto parseTitleAndShortcode(String name) {
        String trimmedName = name.trim();
        Matcher matcher = TITLE_SHORTCODE_PATTERN.matcher(trimmedName);
        if (matcher.matches()) {
            String title = matcher.group(1).trim();
            String shortcode = matcher.group(2).trim();
            if (!title.isEmpty() && !shortcode.isEmpty()) {
                return WorkspaceDiscoveryResponseDto.builder()
                        .workspaceTitle(title)
                        .workspaceShortcode(shortcode)
                        .shortcodeDetected(true)
                        .build();
            }
        }

        return WorkspaceDiscoveryResponseDto.builder()
                .workspaceTitle(trimmedName)
                .workspaceShortcode(UNKNOWN_SHORTCODE)
                .shortcodeDetected(false)
                .warning(SHORTCODE_UNDETECTED_WARNING)
                .build();
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "";
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }
}
