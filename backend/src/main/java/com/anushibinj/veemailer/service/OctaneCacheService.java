package com.anushibinj.veemailer.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.anushibinj.veemailer.service.ve.VeUtils;
import com.hpe.adm.nga.sdk.Octane;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@Service
public class OctaneCacheService {

	private final VeUtils veClient;

	Map<String, Octane> cache = new HashMap<>();

	public Octane getOctaneClient(String serverUrl, String clientId, String clientSecret, int sharedSpaceId,
			int workspaceId) {
		String key = clientId + ":" + clientSecret + ":" + serverUrl + ":" + sharedSpaceId + ":" + workspaceId;
		if (cache.containsKey(key)) {
			// TODO: check if the cached client is still valid, if not create a new one and
			// update the cache
			return cache.get(key);
		} else {
			Octane octane = veClient.createOctaneClient(clientId, clientSecret, serverUrl, sharedSpaceId, workspaceId);
			cache.put(key, octane);
			return octane;
		}
	}
}
