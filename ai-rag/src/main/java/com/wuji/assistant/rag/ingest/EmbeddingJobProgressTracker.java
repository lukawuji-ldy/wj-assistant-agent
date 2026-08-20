package com.wuji.assistant.rag.ingest;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内批量 Embedding 进度（供管理台轮询）。非持久化。
 *
 * @author liudy
 */
@Component
public class EmbeddingJobProgressTracker {

    private final ConcurrentHashMap<Long, Job> jobs = new ConcurrentHashMap<>();

    public void start(long versionId, int total) {
        Job job = new Job();
        job.status = EmbeddingJobProgress.RUNNING;
        job.total = Math.max(0, total);
        jobs.put(versionId, job);
    }

    public void waiting(long versionId, String message) {
        Job job = jobs.get(versionId);
        if (job == null || !EmbeddingJobProgress.RUNNING.equals(job.status)) {
            return;
        }
        job.message = message;
    }

    public void tick(long versionId, String chunkId, boolean embedded) {
        Job job = jobs.get(versionId);
        if (job == null) {
            return;
        }
        job.processed++;
        if (embedded) {
            job.completed++;
        }
        job.lastChunkId = chunkId;
        job.message = null;
    }

    public void succeed(long versionId) {
        Job job = jobs.get(versionId);
        if (job == null) {
            return;
        }
        job.status = EmbeddingJobProgress.SUCCEEDED;
        job.message = null;
    }

    public void fail(long versionId, String message) {
        Job job = jobs.get(versionId);
        if (job == null) {
            start(versionId, 0);
            job = jobs.get(versionId);
        }
        job.status = EmbeddingJobProgress.FAILED;
        job.message = message;
    }

    public EmbeddingJobProgress snapshot(long versionId) {
        Job job = jobs.get(versionId);
        if (job == null) {
            return EmbeddingJobProgress.idle(versionId);
        }
        return new EmbeddingJobProgress(
                versionId,
                job.status,
                job.total,
                job.completed,
                job.processed,
                job.lastChunkId,
                job.message
        );
    }

    private static final class Job {
        volatile String status = EmbeddingJobProgress.IDLE;
        volatile int total;
        volatile int completed;
        volatile int processed;
        volatile String lastChunkId;
        volatile String message;
    }
}
