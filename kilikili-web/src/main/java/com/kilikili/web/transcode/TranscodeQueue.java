package com.kilikili.web.transcode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 进程内转码任务队列：DB 仍是任务唯一事实来源，队列只负责「即时唤醒」与去重。
 * 提交/重试时入队，工作线程阻塞取任务；队列满或重启丢队时由定时扫描兜底，因此不会丢任务。
 */
@Component
public class TranscodeQueue {
    private static final Logger log = LoggerFactory.getLogger(TranscodeQueue.class);
    private static final int CAPACITY = 500;

    private final LinkedBlockingDeque<Long> queue = new LinkedBlockingDeque<>(CAPACITY);
    private final ConcurrentHashMap.KeySetView<Long, Boolean> queued = ConcurrentHashMap.newKeySet();

    /** 普通入队；priority=true 时插到队首（用于立即重试） */
    public boolean offer(long taskId, boolean priority) {
        if (!queued.add(taskId)) return false;
        boolean accepted = priority ? queue.offerFirst(taskId) : queue.offerLast(taskId);
        if (!accepted) {
            queued.remove(taskId);
            log.warn("转码队列已满，任务退回数据库轮询兜底: taskId={}", taskId);
        }
        return accepted;
    }

    public Long take(long timeoutMs) throws InterruptedException {
        Long taskId = queue.pollFirst(timeoutMs, TimeUnit.MILLISECONDS);
        if (taskId != null) queued.remove(taskId);
        return taskId;
    }

    public void remove(long taskId) {
        queued.remove(taskId);
        queue.remove(taskId);
    }

    public int size() {
        return queue.size();
    }
}
