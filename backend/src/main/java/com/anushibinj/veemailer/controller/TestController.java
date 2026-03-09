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
		Octane octaneClient = octaneCacheService.getOctaneClient(valueEdgeProperties.getServerUrl(),
				valueEdgeProperties.getClientId(), valueEdgeProperties.getClientSecret(),
				valueEdgeProperties.getSharedSpaceId(), valueEdgeProperties.getWorkspaceId());

		final Query.QueryBuilder subtypeQuery = //
				Query.statement("subtype", QueryMethod.In, new String[] { //
						"defect" //
				}) //
		;

		final Query.QueryBuilder defectTypeQuery = Query.statement("defect_type", QueryMethod.EqualTo,
				Query.statement("id", QueryMethod.In, //
						new String[] { //
								"pgxw2gl93dd60aldlqq5w7596" //
						}));

		final Query.QueryBuilder productUdfQuery = Query.statement("product_udf", QueryMethod.EqualTo,
				Query.statement("id", QueryMethod.In, //
						new String[] { //
								"42z3y3le5o36qfqqzv0oxyj5w", //
								"kozg6dr7lgye3fzm223z8jx2n" //
						}));

		final Query.QueryBuilder phaseQuery = Query.not("phase", QueryMethod.EqualTo,
				Query.statement("id", QueryMethod.In, //
						new String[] { //
								"phase.defect.closed", //
								"phase.feature.done", //
								"phase.quality_story.done", //
								"phase.epic.done", //
								"phase.story.done", //
								"phase.defect.fixed", //
								"phase.defect.rejected", //
								"phase.defect.duplicate", //
								"phase.defect.deferred" //
						}));

		final Query query = subtypeQuery.and(defectTypeQuery).and(productUdfQuery).and(phaseQuery).build();

		OctaneCollection<EntityModel> execute = octaneClient.entityList("work_items").get().query(query)
				.addFields("id", "global_id_udf", "name", "phase", "owner", "product_udf", "customer_udf").execute();
		List<EntityModel> list = execute.stream().toList();
		return list;
	}
}
