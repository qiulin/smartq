package com.vonhof.smartq.example;


import com.vonhof.smartq.*;
import com.vonhof.smartq.server.SmartQServer;
import org.apache.log4j.PropertyConfigurator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.SQLException;

public class DemoServer {
    public static final InetSocketAddress ADDRESS = new InetSocketAddress("127.0.0.1",54321);


    private static TaskStore<Task> makePGStore() throws SQLException, IOException {
        PostgresTaskStore<Task> store = new PostgresTaskStore<Task>(Task.class);
        store.setTableName("demo_queue");
        store.createTable();
        return store;
    }

    public static void main(String[] args) throws IOException, InterruptedException, SQLException {
        PropertyConfigurator.configure("log4j.properties");

        TaskStore<Task> store = makePGStore();

        final SmartQ<Task, DefaultTaskResult> queue = new SmartQ<Task, DefaultTaskResult>(store);

        //queue.requeueAll(); //If any was left as running - move them back to queue.

        final SmartQServer<Task> publisher = new SmartQServer<Task>(ADDRESS, queue);

        publisher.listen();

        Thread emitter = new Thread() {
            @Override
            public void run() {
                while(!interrupted()) {
                    try {
                        Task task = new Task("test");
                        //System.out.println(new Date() + " - Task submitted: " + task.getId());
                        queue.submit(task);
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        };

        emitter.start();
        emitter.join();
    }
}
