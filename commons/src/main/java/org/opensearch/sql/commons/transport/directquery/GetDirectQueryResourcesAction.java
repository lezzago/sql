/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.commons.transport.directquery;

import org.opensearch.action.ActionType;

/** Transport action type for reading direct-query resources (e.g. Prometheus labels, series). */
public class GetDirectQueryResourcesAction extends ActionType<GetDirectQueryResourcesResponse> {
  public static final String NAME = "cluster:admin/opensearch/direct_query/read/resources";
  public static final GetDirectQueryResourcesAction INSTANCE = new GetDirectQueryResourcesAction();

  private GetDirectQueryResourcesAction() {
    super(NAME, GetDirectQueryResourcesResponse::new);
  }
}
