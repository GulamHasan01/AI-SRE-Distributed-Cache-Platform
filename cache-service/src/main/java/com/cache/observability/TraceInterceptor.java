package com.cache.observability;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

public class TraceInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String traceId = TraceContext.get();
        if (traceId != null && !traceId.isBlank()) {
            request.getHeaders().add(TraceFilter.TRACE_ID_HEADER, traceId);
        }
        return execution.execute(request, body);
    }
}
