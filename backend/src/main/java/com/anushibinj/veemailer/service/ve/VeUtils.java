package com.anushibinj.veemailer.service.ve;

import org.springframework.stereotype.Service;

import com.hpe.adm.nga.sdk.Octane;
import com.hpe.adm.nga.sdk.authentication.Authentication;
import com.hpe.adm.nga.sdk.authentication.SimpleClientAuthentication;

@Service
public class VeUtils {

	public Octane createOctaneClient(String clientId, String clientSecret, String serverUrl, int sharedSpaceId,
			int workspaceId) {

		Authentication authentication = new SimpleClientAuthentication(clientId, clientSecret);
		Octane octane = new Octane.Builder(authentication).Server(serverUrl).sharedSpace(sharedSpaceId)
				.workSpace(workspaceId).build();

		return octane;
	}

}
