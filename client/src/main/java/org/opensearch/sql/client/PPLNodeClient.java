/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.client;

import lombok.RequiredArgsConstructor;
import org.opensearch.core.action.ActionListener;
import org.opensearch.sql.commons.transport.ppl.PPLQueryAction;
import org.opensearch.sql.commons.transport.ppl.PPLQueryRequest;
import org.opensearch.sql.commons.transport.ppl.PPLQueryResponse;
import org.opensearch.transport.client.Client;

/**
 * {@link PPLClient} implementation that dispatches transport requests via a node-local {@link
 * Client}. Mirrors the {@code MachineLearningNodeClient} pattern from ML-Commons.
 */
@RequiredArgsConstructor
public class PPLNodeClient implements PPLClient {

  private final Client client;

  @Override
  public void executePPLQuery(PPLQueryRequest request, ActionListener<PPLQueryResponse> listener) {
    client.execute(PPLQueryAction.INSTANCE, request, listener);
  }
}
