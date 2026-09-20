import protoc.hello.HelloWorldRequest;
import protoc.hello.HelloWorldResponse;
import rpc.RpcClient;
import rpc.ServiceFinder;
import service.HelloService;

import java.util.*;
import java.util.concurrent.*;

public class Application {

    public static void main(String[] args) throws Exception {
        // 用法: java LoadTest [threads] [durationSec] [warmupSec]
        int threads     = args.length > 0 ? Integer.parseInt(args[0]) : 32;
        int durationSec = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        int warmupSec   = args.length > 2 ? Integer.parseInt(args[2]) : 5;

        System.out.println("=== RPC Load Test ===");
        System.out.printf("threads  : %d%n", threads);
        System.out.printf("warmup   : %d s%n", warmupSec);
        System.out.printf("duration : %d s%n%n", durationSec);

        if (warmupSec > 0) {
            System.out.println("[warmup] running...");
            Result w = runPhase(threads, warmupSec);
            System.out.printf("[warmup] ok=%d fail=%d%n%n", w.success, w.fail);
        }

        System.out.println("[run] running...");
        Result r = runPhase(threads, durationSec);
        report(r);

    }

    // ================== 一次压测阶段 ==================
    static Result runPhase(int threads, int durationSec) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads); // 所有 client 建连完成
        CountDownLatch start = new CountDownLatch(1);       // 统一发令枪

        List<Future<WorkerResult>> futures = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(new Worker(i, durationSec, ready, start)));
        }

        if (!ready.await(60, TimeUnit.SECONDS)) {
            throw new IllegalStateException("client 初始化超时");
        }

        long t0 = System.nanoTime();
        start.countDown();   // 全体同时开跑

        long success = 0, fail = 0;
        List<long[]> chunks = new ArrayList<>();
        int totalSamples = 0;

        for (Future<WorkerResult> f : futures) {
            WorkerResult wr = f.get();      // 等待每个线程跑完
            success += wr.success;
            fail    += wr.fail;
            if (wr.count > 0) {
                long[] copy = Arrays.copyOf(wr.latencies, wr.count);
                chunks.add(copy);
                totalSamples += wr.count;
            }
        }
        long elapsed = System.nanoTime() - t0;
        pool.shutdown();

        // 合并所有延迟样本并排序
        long[] all = new long[totalSamples];
        int pos = 0;
        for (long[] c : chunks) {
            System.arraycopy(c, 0, all, pos, c.length);
            pos += c.length;
        }
        Arrays.sort(all);

        return new Result(success, fail, elapsed, all);
    }

    // ================== Worker ==================
    static class Worker implements Callable<WorkerResult> {
        final int id, durationSec;
        final CountDownLatch ready, start;

        Worker(int id, int durationSec, CountDownLatch ready, CountDownLatch start) {
            this.id = id;
            this.durationSec = durationSec;
            this.ready = ready;
            this.start = start;
        }

        @Override
        public WorkerResult call() throws Exception {
            HelloService service = new RpcClient(new ServiceFinder()).newService(HelloService.class);

            long[] lat = new long[1 << 16];  // 64K 起步，按需扩容
            int n = 0;
            long success = 0, fail = 0;

            ready.countDown();
            start.await();                    // 等发令枪

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(durationSec);

            while (System.nanoTime() < deadline) {
                long t0 = System.nanoTime();
                try {
                    HelloWorldRequest req = HelloWorldRequest.newBuilder()
                            .setMsg("hello").build();
                    HelloWorldResponse res = service.hello(req);
                    long t1 = System.nanoTime();

                    if (res != null && "world".equals(res.getRes())) {
                        success++;
                        if (n == lat.length) {
                            lat = Arrays.copyOf(lat, lat.length << 1);
                        }
                        lat[n++] = t1 - t0;
                    } else {
                        fail++;
                    }
                } catch (Throwable e) {
                    fail++;                   // 超时 / 连接断开 / 反序列化失败等
                }
            }

            return new WorkerResult(success, fail, lat, n);
        }
    }

    // ================== 数据载体 ==================
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
        final long[] latencies;   // 已排序
        Result(long s, long f, long e, long[] lat) {
            success = s; fail = f; elapsedNanos = e; latencies = lat;
        }
    }

    // ================== 报告 ==================
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
        System.out.printf("error rate  : %.4f%%%n",
                total == 0 ? 0.0 : 100.0 * r.fail / total);
        System.out.println("-----------------------------");
        System.out.printf("QPS(succ)   : %.2f%n", seconds > 0 ? r.success / seconds : 0);
        System.out.printf("QPS(total)  : %.2f%n", seconds > 0 ? total / seconds : 0);
        System.out.println("-----------------------------");

        if (a.length == 0) {
            System.out.println("no successful samples");
            System.out.println("=============================");
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

    /** 最近秩法 (nearest-rank) 计算分位数，a 必须已升序 */
    static long pct(long[] a, double p) {
        int idx = (int) Math.ceil(p / 100.0 * a.length) - 1;
        if (idx < 0) idx = 0;
        if (idx >= a.length) idx = a.length - 1;
        return a[idx];
    }
}