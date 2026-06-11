package com.project.flowfinserver.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
public class SyncStatusService {

    public enum SyncStatus { SYNCING, DONE, FAILED }

    private final Map<String, SyncStatus> statusMap = new ConcurrentHashMap<>();

    private String key(Long userId, String type) {
        return userId + ":" + type;
    }

    public void setSyncing(Long userId, String type) {
        statusMap.put(key(userId, type), SyncStatus.SYNCING);
    }

    public void setDone(Long userId, String type) {
        statusMap.put(key(userId, type), SyncStatus.DONE);
    }

    public void setFailed(Long userId, String type) {
        statusMap.put(key(userId, type), SyncStatus.FAILED);
    }

    public SyncStatus getStatus(Long userId, String type) {
        return statusMap.getOrDefault(key(userId, type), SyncStatus.DONE);
    }
}
