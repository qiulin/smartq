package com.vonhof.smartq;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class MemoryTaskStore<T extends Task> implements TaskStore<T> {
    private final Map<UUID, T> tasks = new ConcurrentHashMap<UUID, T>();
    private final List<T> queued = Collections.synchronizedList(new LinkedList<T>());
    private final List<T> running = Collections.synchronizedList(new LinkedList<T>());


    @Override
    public T get(UUID id) {
        return tasks.get(id);
    }

    @Override
    public void remove(Task task) {
        tasks.remove(task.getId());
        queued.remove(task);
        running.remove(task);
    }

    @Override
    public void remove(UUID id) {
        tasks.remove(id);
    }

    @Override
    public void queue(T task) {
        tasks.put(task.getId(),task);
        queued.add(task);
    }

    @Override
    public void run(T task) {
        queued.remove(task);
        running.add(task);
    }

    public List<T> getQueued() {
        return queued;
    }

    public List<T> getRunning() {
        return running;
    }

    @Override
    public long queueSize() {
        return queued.size();
    }

    @Override
    public long runningCount() {
        return running.size();
    }
}
