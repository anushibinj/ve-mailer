package com.anushibinj.veemailer.service.ve;

import com.hpe.adm.nga.sdk.APIMode;
import org.springframework.stereotype.Service;

import com.hpe.adm.nga.sdk.Octane;
import com.hpe.adm.nga.sdk.authentication.Authentication;
import com.hpe.adm.nga.sdk.authentication.SimpleClientAuthentication;

@Service
public class VeUtils {

	private static final APIMode OCTANE_FRONTEND_API_MODE = new APIMode() {
		@Override
		public String getHeaderValue() {
			return "HPE_MQM_UI";
		}

		@Override
		public String getHeaderKey() {
			return "hpeclienttype";
		}
	};

	public Octane createOctaneClient(String clientId, String clientSecret, String serverUrl, int sharedSpaceId,
			int workspaceId) {

		// Send hpeclienttype=HPE_MQM_UI on Octane requests to simulate Octane frontend behavior.
		Authentication authentication = new SimpleClientAuthentication(clientId, clientSecret, OCTANE_FRONTEND_API_MODE);
		Octane octane = new Octane.Builder(authentication).Server(serverUrl).sharedSpace(sharedSpaceId)
				.workSpace(workspaceId).build();

		return octane;
	}

}
