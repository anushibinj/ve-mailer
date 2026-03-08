package com.anushibinj.veemailer.service.ve;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "valueedge")
@Getter
@Setter
public class ValueEdgeProperties {

	private String serverUrl;
	private String clientId;
	private String clientSecret;
	private int sharedSpaceId;
	private int workspaceId;

}
