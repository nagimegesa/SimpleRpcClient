package com.rpcclient;

import com.rpcclient.protoc.hello.HelloWorldRequest;
import com.rpcclient.protoc.hello.HelloWorldResponse;
import com.rpcclient.rpc.RpcClient;
import com.rpcclient.rcpservice.HelloService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.concurrent.*;

@SpringBootTest
public class ApplicationTest {

    @Resource
    RpcClient rpcClient;

    @Test
    void loadTest() throws Exception {
        int threads     = 32;
        int durationSec = 30;
        int warmupSec   = 5;

        System.out.println("=== RPC Load Test ===");

        System.out.println("[warmup] running...");
        Result w = runPhase(threads, warmupSec, rpcClient);
        System.out.printf("[warmup] ok=%d fail=%d%n%n", w.success, w.fail);

        System.out.println("[run] running...");
        Result r = runPhase(threads, durationSec, rpcClient);
        report(r);
    }

    // runPhase 要接收 rpcClient
    static Result runPhase(int threads, int durationSec, RpcClient rpcClient) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<WorkerResult>> futures = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(new Worker(i, durationSec, ready, start, rpcClient)));
        }

        ready.await(60, TimeUnit.SECONDS);
        long t0 = System.nanoTime();
        start.countDown();

        long success = 0, fail = 0;
        List<long[]> chunks = new ArrayList<>();
        int totalSamples = 0;

        for (Future<WorkerResult> f : futures) {
            WorkerResult wr = f.get();
            success += wr.success;
            fail    += wr.fail;
            if (wr.count > 0) {
                chunks.add(Arrays.copyOf(wr.latencies, wr.count));
                totalSamples += wr.count;
            }
        }
        long elapsed = System.nanoTime() - t0;
        pool.shutdown();

        long[] all = new long[totalSamples];
        int pos = 0;
        for (long[] c : chunks) {
            System.arraycopy(c, 0, all, pos, c.length);
            pos += c.length;
        }
        Arrays.sort(all);
        return new Result(success, fail, elapsed, all);
    }

    static class Worker implements Callable<WorkerResult> {
        final int id, durationSec;
        final CountDownLatch ready, start;
        final RpcClient rpcClient;   // 必须传进来

        Worker(int id, int durationSec, CountDownLatch ready, CountDownLatch start, RpcClient rpcClient) {
            this.id = id;
            this.durationSec = durationSec;
            this.ready = ready;
            this.start = start;
            this.rpcClient = rpcClient;
        }

        @Override
        public WorkerResult call() throws Exception {
            HelloService service = rpcClient.newService(HelloService.class);

            long[] lat = new long[1 << 16];
            int n = 0;
            long success = 0, fail = 0;

            ready.countDown();
            start.await();

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(durationSec);
            while (System.nanoTime() < deadline) {
                long t0 = System.nanoTime();
                try {
                    HelloWorldRequest req = HelloWorldRequest.newBuilder().setMsg("hello").build();
                    HelloWorldResponse res = service.hello(req);
                    long t1 = System.nanoTime();
                    if (res != null && "world".equals(res.getRes())) {
                        success++;
                        if (n == lat.length) lat = Arrays.copyOf(lat, lat.length << 1);
                        lat[n++] = t1 - t0;
                    } else {
                        fail++;
                    }
                } catch (Throwable e) {
                    fail++;
                }
            }
            return new WorkerResult(success, fail, lat, n);
        }
    }

    static class WorkerResult {
        final long success, fail;
        final long[] latencies;
        final int count;
        WorkerResult(long s, long f, long[] lat, int c) {
            success = s; fail = f; latencies = lat; count = c;
        }
    }

    static class Result {
        final long success, fail, elapsedNanos;
        final long[] latencies;
        Result(long s, long f, long e, long[] lat) {
            success = s; fail = f; elapsedNanos = e; latencies = lat;
        }
    }

    static void report(Result r) {
        long total = r.success + r.fail;
        double seconds = r.elapsedNanos / 1e9;
        long[] a = r.latencies;

        System.out.println();
        System.out.println("========== Results ==========");
        System.out.printf("elapsed     : %.3f s%n", seconds);
        System.out.printf("total       : %d%n", total);
        System.out.printf("success     : %d%n", r.success);
        System.out.printf("fail        : %d%n", r.fail);
        System.out.printf("error rate  : %.4f%%%n", total == 0 ? 0.0 : 100.0 * r.fail / total);
        System.out.printf("QPS(succ)   : %.2f%n", seconds > 0 ? r.success / seconds : 0);
        System.out.printf("QPS(total)  : %.2f%n", seconds > 0 ? total / seconds : 0);

        if (a.length == 0) {
            System.out.println("no successful samples");
            return;
        }

        System.out.println("Latency (ms):");
        System.out.printf("  min       : %.3f%n", a[0] / 1e6);
        System.out.printf("  avg       : %.3f%n", avg(a) / 1e6);
        System.out.printf("  p50       : %.3f%n", pct(a, 50)  / 1e6);
        System.out.printf("  p90       : %.3f%n", pct(a, 90)  / 1e6);
        System.out.printf("  p95       : %.3f%n", pct(a, 95)  / 1e6);
        System.out.printf("  p99       : %.3f%n", pct(a, 99)  / 1e6);
        System.out.printf("  p99.9     : %.3f%n", pct(a, 99.9)/ 1e6);
        System.out.printf("  max       : %.3f%n", a[a.length - 1] / 1e6);
        System.out.println("=============================");
    }

    static double avg(long[] a) {
        long sum = 0;
        for (long v : a) sum += v;
        return (double) sum / a.length;
    }

    static long pct(long[] a, double p) {
        int idx = (int) Math.ceil(p / 100.0 * a.length) - 1;
        if (idx < 0) idx = 0;
        if (idx >= a.length) idx = a.length - 1;
        return a[idx];
    }
}