package com.vonhof.smartq.example;


import com.vonhof.smartq.DefaultTaskResult;
import com.vonhof.smartq.MemoryTaskStore;
import com.vonhof.smartq.PostgresTaskStore;
import com.vonhof.smartq.QueueListener;
import com.vonhof.smartq.RedisTaskStore;
import com.vonhof.smartq.SmartQ;
import com.vonhof.smartq.Task;
import com.vonhof.smartq.TaskStore;
import org.apache.log4j.Logger;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

public class Benchmark {
    public static final BenchmarkListener BENCHMARK_LISTENER = new BenchmarkListener();
    public static final MemoryTaskStore<Task> STORE = new MemoryTaskStore<Task>();

    private static TaskStore<Task> makeRedisStore() {
        JedisPoolConfig jConf = new JedisPoolConfig();
        jConf.setMaxActive(50);
        jConf.setMaxWait(50);

        final JedisPool jedis = new JedisPool(jConf,"localhost",6379,0);
        RedisTaskStore<Task> store = new RedisTaskStore<Task>(jedis, Task.class);
        store.setNamespace("benchmark/");
        return store;
    }

    private static TaskStore<Task> makePGStore() throws SQLException, IOException {
        PostgresTaskStore<Task> store = new PostgresTaskStore<Task>(Task.class);
        store.setTableName("benchmark_queue");
        store.createTable();
        store.reset();
        return store;
    }

    private static TaskStore<Task> makeMemStore() {
        return STORE;
    }

    private static TaskStore<Task> makeStore() throws SQLException, IOException {
        return makePGStore();
    }

    public static void main (String[] args) throws InterruptedException, IOException, SQLException {

        final SmartQ<Task, DefaultTaskResult> queue = new SmartQ<Task, DefaultTaskResult>(makeStore());
        queue.addListener(BENCHMARK_LISTENER);

        List<StressSubscriber> subscribers = new ArrayList<StressSubscriber>();

        for(int i = 0; i < 20; i++) {
            StressSubscriber subscriber = new StressSubscriber(i);
            subscribers.add(subscriber);
            subscriber.start();
        }

        System.out.println("Submitting tasks");


        for(int i = 0; i < 30; i++) {
            StressPublisher publisher = new StressPublisher(i);
            publisher.start();
        }


        System.out.println("Done submitting");


        while(true) {
            Thread.sleep(1000);

            if (queue.size() < 1) {
                break;
            }
        }

        System.out.println("Queue is done");

        for(StressSubscriber subscriber: subscribers) {
            subscriber.interrupt();
        }

        System.out.println("All done");

        BENCHMARK_LISTENER.close();
    }

    private static class BenchmarkListener implements QueueListener {

        private final AtomicInteger acquires = new AtomicInteger(0);
        private final AtomicInteger submits = new AtomicInteger(0);
        private final AtomicInteger dones = new AtomicInteger(0);
        private long interval = 1000;
        private Timer timer = new Timer();
        private long first;

        private BenchmarkListener() {
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    int aSince = acquires.intValue();
                    int sSince = submits.intValue();
                    int dSince = dones.intValue();

                    if (first < 1) {
                         return;
                    }
                    int secs = (int) ((System.currentTimeMillis()-first) / 1000);

                    if (secs < 1) return;

                    int aPerSec = aSince / secs;
                    int sPerSec = sSince / secs;
                    int dPerSec = dSince / secs;

                    System.out.println(String.format("Acquires: %s (%s / s) | Submits: %s (%s / s) | Done: %s (%s / s) | Time: %s secs",
                            aSince,aPerSec,
                            sSince,sPerSec,
                            dSince,dPerSec,
                            secs));

                }
            }, interval, interval);

        }

        @Override
        public void onAcquire(Task t) {
            if (first == 0) {
                first = System.currentTimeMillis();
            }
            acquires.incrementAndGet();
        }

        @Override
        public void onSubmit(Task t) {
            submits.incrementAndGet();
        }

        @Override
        public void onDone(Task t) {
            dones.incrementAndGet();
        }

        public void close() {
            timer.cancel();
        }
    }

    private static class StressPublisher extends Thread {
        private static final Logger log = Logger.getLogger(StressSubscriber.class);
        private final SmartQ<Task, DefaultTaskResult> queue;

        private StressPublisher(int num) throws IOException, SQLException {
            super("Stress Publisher "+num);

            queue = new SmartQ<Task, DefaultTaskResult>(makeStore());
            queue.addListener(BENCHMARK_LISTENER);
        }

        @Override
        public void run() {
            for(int i = 0; i < 10000; i++) {
                try {
                    queue.submit(new Task()
                            .withPriority((int) Math.round(Math.random() * 10)));
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    private static class StressSubscriber extends Thread {
        private static final Logger log = Logger.getLogger(StressSubscriber.class);
        private final SmartQ<Task, DefaultTaskResult> queue;

        private StressSubscriber(int num) throws IOException, SQLException {
            super("Stress subscriber "+num);

            queue = new SmartQ<Task, DefaultTaskResult>(makeStore());
            queue.addListener(BENCHMARK_LISTENER);
        }

        @Override
        public void run() {
            while(true) {
                try {
                    Task t = queue.acquire();
                    queue.acknowledge(t.getId());
                } catch (InterruptedException e) {
                    return;
                } catch (Exception e) {
                    log.error("Failed to ack", e);
                }
            }
        }
    }
}
