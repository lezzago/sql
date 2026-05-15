/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.commons.transport.directquery;

import org.opensearch.action.ActionType;

/** Transport action type for executing a direct query against an external data source. */
public class ExecuteDirectQueryAction extends ActionType<ExecuteDirectQueryResponse> {
  public static final String NAME = "cluster:admin/opensearch/direct_query/read/query";
  public static final ExecuteDirectQueryAction INSTANCE = new ExecuteDirectQueryAction();

  private ExecuteDirectQueryAction() {
    super(NAME, ExecuteDirectQueryResponse::new);
  }
}
