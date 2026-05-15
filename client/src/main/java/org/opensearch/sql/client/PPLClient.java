/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.client;

import org.opensearch.core.action.ActionListener;
import org.opensearch.sql.commons.transport.ppl.PPLQueryRequest;
import org.opensearch.sql.commons.transport.ppl.PPLQueryResponse;

/** Thin client interface for invoking PPL transport APIs from another OpenSearch plugin. */
public interface PPLClient {

  /** Execute a PPL query via the transport layer. */
  void executePPLQuery(PPLQueryRequest request, ActionListener<PPLQueryResponse> listener);
}
