package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.AiPreferences;
import com.anushibinj.veemailer.repository.AiPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * Builds a Spring AI {@link ChatClient} dynamically from database-backed
 * {@link AiPreferences} configuration. This replaces the static
 * {@code spring.ai.openai.*} property approach so that AI settings can be
 * updated at runtime through the Admin Control Panel without restarting the app.
 *
 * <p>A new {@code ChatClient} is constructed on every call to {@link #getChatClient()}.
 * The construction is lightweight (no network calls occur until the client is actually
 * used to make an AI request).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicAiClientService {

    private final AiPreferencesRepository repository;

    /**
     * Builds and returns a {@link ChatClient} configured from the current database settings.
     *
     * @throws IllegalStateException if no AI preferences have been configured yet
     */
    public ChatClient getChatClient() {
        AiPreferences prefs = getEntity();
        if (prefs == null) {
            throw new IllegalStateException(
                    "AI preferences are not configured. Please configure them in the Admin Control Panel.");
        }

        // Use JDK HTTP client to avoid Jetty classpath conflict (same reason as AppConfig)
        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory());

        OpenAiApi openAiApi = new OpenAiApi(
                prefs.getBaseUrl(),
                prefs.getApiKey(),
                prefs.getChatCompletionsPath(),
                "/v1/embeddings",
                restClientBuilder,
                WebClient.builder(),
                new DefaultResponseErrorHandler());

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(prefs.getModel())
                .build();

        OpenAiChatModel chatModel = new OpenAiChatModel(openAiApi, options);
        return ChatClient.create(chatModel);
    }

    /**
     * Returns the raw {@link AiPreferences} entity, or {@code null} if not yet configured.
     */
    public AiPreferences getEntity() {
        List<AiPreferences> all = repository.findAll();
        return all.isEmpty() ? null : all.get(0);
    }
}
