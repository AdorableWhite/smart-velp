package com.velp.infrastructure.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velp.common.constants.AppConstants;
import com.velp.domain.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 基于 Redis 的媒体任务仓库实现。
 * 
 * 相比于 InMemoryMediaRepository，本实现允许后端多节点部署。
 * 所有节点共享同一个 Redis 实例，从而保证任务状态在集群内的一致性。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "velp.repository.type", havingValue = "redis")
public class RedisMediaRepository implements MediaRepository {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    
    // Redis 中存储任务的统一前缀
    private static final String REDIS_KEY_PREFIX = "velp:task:";

    @Override
    public void saveTaskStatus(String taskId, String status, int progress, String videoId, String error, String url, String title) {
        try {
            // 1. 获取现有状态，保留创建时间等不变量
            TaskStatus existing = getTaskStatus(taskId).orElse(null);
            String finalUrl = (url != null) ? url : (existing != null ? existing.url() : "");
            String finalTitle = (title != null) ? title : (existing != null ? existing.title() : "");
            long createdAt = (existing != null && existing.createdAt() > 0) ? existing.createdAt() : System.currentTimeMillis();

            // 2. 构建新的状态对象
            TaskStatus taskStatus = new TaskStatus(status, progress, videoId, error, finalUrl, finalTitle, createdAt);
            
            // 3. 将对象序列化为 JSON 字符串并存入 Redis
            String json = objectMapper.writeValueAsString(taskStatus);
            redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + taskId, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize task status for Redis", e);
        }
    }

    @Override
    public void deleteTask(String taskId) {
        // 从 Redis 中移除指定 Key
        redisTemplate.delete(REDIS_KEY_PREFIX + taskId);
    }

    @Override
    public void deleteFailedTasks() {
        // 扫描所有以 velp:task: 开头的 Key
        java.util.Set<String> keys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");
        if (keys != null) {
            for (String key : keys) {
                String json = redisTemplate.opsForValue().get(key);
                if (json == null) continue;
                try {
                    TaskStatus status = objectMapper.readValue(json, TaskStatus.class);
                    // 如果状态为 FAILED，则执行删除
                    if (AppConstants.TaskStatus.FAILED.equals(status.status())) {
                        redisTemplate.delete(key);
                    }
                } catch (Exception e) {
                    log.error("Failed to parse task status from Redis for cleanup", e);
                }
            }
        }
    }

    @Override
    public Optional<TaskStatus> getTaskStatus(String taskId) {
        // 从 Redis 读取 JSON 字符串并还原为对象
        String json = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + taskId);
        if (json == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, TaskStatus.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize task status from Redis", e);
            return Optional.empty();
        }
    }

    @Override
    public List<TaskEntry> getAllTasks() {
        // 获取所有任务并转换为列表，用于前端任务列表展示
        java.util.Set<String> keys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");
        if (keys == null) return List.of();

        return keys.stream()
                .map(key -> {
                    String taskId = key.substring(REDIS_KEY_PREFIX.length());
                    String json = redisTemplate.opsForValue().get(key);
                    if (json == null) return null;
                    try {
                        TaskStatus status = objectMapper.readValue(json, TaskStatus.class);
                        return new TaskEntry(
                                taskId,
                                status.status(),
                                status.progress(),
                                status.videoId(),
                                status.url(),
                                status.title(),
                                status.createdAt()
                        );
                    } catch (Exception e) {
                        log.error("Failed to parse task status from Redis", e);
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }
}
