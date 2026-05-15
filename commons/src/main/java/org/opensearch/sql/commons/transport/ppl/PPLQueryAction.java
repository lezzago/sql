/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.commons.transport.ppl;

import org.opensearch.action.ActionType;

/** Transport action type for PPL queries. */
public class PPLQueryAction extends ActionType<PPLQueryResponse> {
  public static final String NAME = "cluster:admin/opensearch/ppl";
  public static final PPLQueryAction INSTANCE = new PPLQueryAction();

  private PPLQueryAction() {
    super(NAME, PPLQueryResponse::new);
  }
}
