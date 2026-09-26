package com.rpcclient.rpc.interceptor;

import com.rpcclient.rpc.RpcContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class MetricInterceptor implements RpcInterceptor {

    private final MeterRegistry meterRegistry;

    private final ConcurrentHashMap<String, Counter> callCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> callTimers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> retryEventCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DistributionSummary> retryPerCallSummaries = new ConcurrentHashMap<>();

    public MetricInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Object process(RpcContext context, RpcInterceptorChain chain) {
        log.debug("[MetricInterceptor] invoked, service="
                + context.getServiceName() + ", method=" + context.getMethodName());
        long start = System.nanoTime();
        String result = "success";
        try {
            return chain.process(context);
        } catch (Throwable t) {
            result = "failure";
            throw t;
        } finally {
            long elapsed = System.nanoTime() - start;

            String service = safe(context.getServiceName());
            String method  = safe(context.getMethodName());

            // 1. 调用次数 & 耗时
            counter(service, method, result).increment();
            timer(service, method).record(elapsed, TimeUnit.NANOSECONDS);

            // 2. 重试 / 超时：总量 + 每次调用分布
            recordRetry("connection_closed",  context.getConnectionClosedRetryCount());
            recordRetry("connection_timeout", context.getConnectionTimeoutRetryCount());
            recordRetry("call_retry",         context.getCallRetryCount());
            recordRetry("call_timeout",       context.getCallTimeoutCount());
        }
    }

    /** 记录一次调用中某种原因的重试情况 */
    private void recordRetry(String reason, int count) {
        if (count < 0) return;

        // (a) 总量：所有调用加起来发生了多少次该原因的重试
        retryEventCounter(reason).increment(count);

        // (b) 分布：该次调用的重试次数，用于算平均值 / P99
        retryPerCallSummary(reason).record(count);
    }

    private Counter retryEventCounter(String reason) {
        return retryEventCounters.computeIfAbsent(reason, r ->
                Counter.builder("rpc.call.retry.events")
                        .description("RPC 调用过程中发生的重试/超时事件总数")
                        .tag("reason", r)
                        .register(meterRegistry)
        );
    }

    private DistributionSummary retryPerCallSummary(String reason) {
        return retryPerCallSummaries.computeIfAbsent(reason, r ->
                DistributionSummary.builder("rpc.call.retry.per_call")
                        .description("每次 RPC 调用中发生的重试/超时次数分布")
                        .tag("reason", r)
                        .serviceLevelObjectives(0.5, 1, 3, 6, 9) // 这里使用 0.5 代替 0
                        .register(meterRegistry)
        );
    }

    private Counter counter(String service, String method, String result) {
        return callCounters.computeIfAbsent(service + "|" + method + "|" + result, k ->
                Counter.builder("rpc.call.count")
                        .tag("service", service).tag("method", method).tag("result", result)
                        .register(meterRegistry));
    }

    private Timer timer(String service, String method) {
        return callTimers.computeIfAbsent(service + "|" + method, k ->
                Timer.builder("rpc.call.duration")
                        .tag("service", service).tag("method", method)
                        .publishPercentileHistogram()
                        .register(meterRegistry));
    }

    private String safe(String s) {
        return s == null ? "unknown" : s;
    }

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE; // 统计指标在最外层
    }
}