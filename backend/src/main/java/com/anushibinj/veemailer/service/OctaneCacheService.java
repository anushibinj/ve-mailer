package com.anushibinj.veemailer.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.anushibinj.veemailer.service.ve.VeUtils;
import com.hpe.adm.nga.sdk.Octane;

import lombok.AllArgsConstructor;

/**
 * Caches Octane clients by connection identity. Uses a {@link ConcurrentHashMap} because the
 * retry driver, the hourly poller, and the manual "Run now" action can all call this
 * concurrently on different threads.
 */
@AllArgsConstructor
@Service
public class OctaneCacheService {

	private final VeUtils veClient;

	Map<String, Octane> cache = new ConcurrentHashMap<>();

	public Octane getOctaneClient(String serverUrl, String clientId, String clientSecret, int sharedSpaceId,
			int workspaceId) {
		String key = buildKey(clientId, clientSecret, serverUrl, sharedSpaceId, workspaceId);
		return cache.computeIfAbsent(key,
				k -> veClient.createOctaneClient(clientId, clientSecret, serverUrl, sharedSpaceId, workspaceId));
	}

	/**
	 * Evicts a cached client so the next {@link #getOctaneClient} rebuilds it from scratch,
	 * rather than reusing a session that may be dead. Called before each retry attempt: a
	 * transient failure could mean the cached client's session no longer works.
	 */
	public void evict(String serverUrl, String clientId, String clientSecret, int sharedSpaceId, int workspaceId) {
		cache.remove(buildKey(clientId, clientSecret, serverUrl, sharedSpaceId, workspaceId));
	}

	private String buildKey(String clientId, String clientSecret, String serverUrl, int sharedSpaceId, int workspaceId) {
		return clientId + ":" + clientSecret + ":" + serverUrl + ":" + sharedSpaceId + ":" + workspaceId;
	}
}
