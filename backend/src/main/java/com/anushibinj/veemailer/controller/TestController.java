package com.anushibinj.veemailer.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.anushibinj.veemailer.service.OctaneCacheService;
import com.anushibinj.veemailer.service.ve.ValueEdgeProperties;
import com.hpe.adm.nga.sdk.Octane;
import com.hpe.adm.nga.sdk.entities.OctaneCollection;
import com.hpe.adm.nga.sdk.model.EntityModel;
import com.hpe.adm.nga.sdk.query.Query;
import com.hpe.adm.nga.sdk.query.QueryMethod;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
public class TestController {

	private final OctaneCacheService octaneCacheService;
	private final ValueEdgeProperties valueEdgeProperties;

	@GetMapping
	public List<EntityModel> test() {
		Octane octaneClient = octaneCacheService.getOctaneClient(
				valueEdgeProperties.getServerUrl(),
				valueEdgeProperties.getClientId(),
				valueEdgeProperties.getClientSecret(),
				valueEdgeProperties.getSharedSpaceId(),
				valueEdgeProperties.getWorkspaceId());

		final Query.QueryBuilder idQueryBuilder = Query.statement("subtype", QueryMethod.EqualTo, "defect")
//				.andNot(
//				"phase", QueryMethod.In,
//				new String[] { "phase.defect.closed", "phase.feature.done", "phase.quality_story.done",
//						"phase.epic.done", "phase.story.done", "phase.defect.fixed", "phase.defect.rejected",
//						"phase.defect.duplicate", "phase.defect.deferred" })
		;
		final Query query = idQueryBuilder.build();

		OctaneCollection<EntityModel> execute = octaneClient.entityList("work_items").get().query(query).execute();
		List<EntityModel> list = execute.stream().toList();
		return list;
	}
}
