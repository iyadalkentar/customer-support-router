package com.ikdev.customersupportrouter.aiclassifierservice.service;

import com.ikdev.customersupportrouter.aiclassifierservice.config.LlmPromptProperties;
import com.ikdev.customersupportrouter.aiclassifierservice.event.ClassificationFields;
import com.ikdev.customersupportrouter.aiclassifierservice.memory.ConversationContextFormatter;
import com.ikdev.customersupportrouter.aiclassifierservice.memory.ConversationContextMessage;
import jakarta.annotation.PreDestroy;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ChatClient-backed {@link LlmClient} with a bounded per-call timeout and a
 * single retry on any failure (timeout, parse, or transport).
 *
 * <p>The timeout is enforced at the call site via {@link Future#get(long, TimeUnit)}
 * so it stays scoped to the LLM concern and doesn't leak into the application's
 * shared HTTP client config.
 *
 * <p>Failures that escape the @Retryable boundary (i.e. after the configured
 * number of attempts) are converted to {@link ClassificationFields#FALLBACK} by
 * the message event consumer.
 */
@Service
public class ChatModelService implements LlmClient {

    private final ChatClient chatClient;
    private final String classificationPrompt;
    private final ConversationContextFormatter contextFormatter;
    private final Duration llmTimeout;
    private final ExecutorService llmCalls;
    private final String provider;
    private final ClassificationMetrics classificationMetrics;

    public ChatModelService(
            ChatModel chatModel,
            LlmPromptProperties llmPromptProperties,
            ConversationContextFormatter contextFormatter,
            ClassificationMetrics classificationMetrics,
            @Value("${llm.provider:unknown}") String provider,
            @Value("${llm.timeout:10s}") Duration llmTimeout) {
        this.classificationPrompt = llmPromptProperties.resolve(chatModel.getOptions().getModel());
        this.contextFormatter = contextFormatter;
        this.chatClient = ChatClient.builder(chatModel).build();
        this.llmTimeout = llmTimeout;
        this.provider = provider;
        this.classificationMetrics = classificationMetrics;
        // Bounded pool: at most one in-flight classify call per worker, capped at the
        // machine's available cores (min 2). Backpressure queues callers above that.
        AtomicInteger idx = new AtomicInteger();
        this.llmCalls = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors()),
                r -> {
                    Thread t = new Thread(r, "llm-classify-" + idx.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                });
    }

    @Override
    @Retryable(value = Exception.class, maxRetries = 1)
    public ClassificationFields classify(String content, List<ConversationContextMessage> context) {
        String contextText = contextFormatter.format(context);
        long startNanos = System.nanoTime();

        Future<ClassificationFields> future = llmCalls.submit(() ->
                chatClient.prompt()
                        .user(u -> u.text(classificationPrompt)
                                .param("message", content)
                                .param("context", contextText))
                        .call()
                        .entity(ClassificationFields.class));

        try {
            ClassificationFields fields = future.get(llmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            // Recorded only here, not in a catch/finally: a failing attempt gets
            // retried by @Retryable, and the terminal (post-retry) outcome is what
            // MessageEventConsumer records once retries are exhausted — recording
            // "timeout"/"fallback" per attempt here would mislabel an attempt that
            // was ultimately retried into success.
            classificationMetrics.recordClassification(provider, "success",
                    Duration.ofNanos(System.nanoTime() - startNanos));
            return fields;
        } catch (TimeoutException e) {
            future.cancel(true); // interrupts the worker; frees the executor slot
            throw new LlmTimeoutException("LLM call exceeded " + llmTimeout, e);
        } catch (ExecutionException e) {
            // Unwrap so @Retryable sees the real LLM/parse exception, not the
            // ExecutionException wrapper around it.
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new LlmCallException("LLM call failed", cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmCallException("Interrupted while awaiting LLM response", e);
        }
    }

    @PreDestroy
    void shutdown() {
        llmCalls.shutdownNow();
    }

    /** The LLM call did not complete within the configured timeout. */
    public static class LlmTimeoutException extends RuntimeException {
        public LlmTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The LLM call failed for a non-timeout reason (transport, parse, etc.). */
    public static class LlmCallException extends RuntimeException {
        public LlmCallException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
