package com.wuji.assistant.rag.vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * 按提交顺序等待多路检索。任务数不超过 1 或未给执行器时在当前线程跑。
 *
 * @author liudy
 */
public final class ParallelSearch {

    private ParallelSearch() {
    }

    /**
     * @param tasks    各路任务
     * @param executor 并行执行器；null 或单任务时同步执行
     * @param <T>      结果
     * @return 与 tasks 同序的结果
     */
    public static <T> List<T> joinAll(List<Callable<T>> tasks, Executor executor) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        if (tasks.size() == 1 || executor == null) {
            return runSync(tasks);
        }
        List<CompletableFuture<T>> futures = new ArrayList<>(tasks.size());
        for (Callable<T> task : tasks) {
            futures.add(CompletableFuture.supplyAsync(() -> call(task), executor));
        }
        List<T> out = new ArrayList<>(futures.size());
        for (CompletableFuture<T> future : futures) {
            try {
                out.add(future.join());
            } catch (CompletionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw e;
            }
        }
        return out;
    }

    private static <T> List<T> runSync(List<Callable<T>> tasks) {
        List<T> out = new ArrayList<>(tasks.size());
        for (Callable<T> task : tasks) {
            out.add(call(task));
        }
        return out;
    }

    private static <T> T call(Callable<T> task) {
        try {
            return task.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
