/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.commons.transport.directquery;

import org.opensearch.action.ActionType;

/**
 * Transport action type for writing direct-query resources (e.g. Prometheus alerting rules,
 * silences).
 */
public class WriteDirectQueryResourcesAction extends ActionType<WriteDirectQueryResourcesResponse> {
  public static final String NAME = "cluster:admin/opensearch/direct_query/write/resources";
  public static final WriteDirectQueryResourcesAction INSTANCE =
      new WriteDirectQueryResourcesAction();

  private WriteDirectQueryResourcesAction() {
    super(NAME, WriteDirectQueryResourcesResponse::new);
  }
}
