/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.client;

import lombok.RequiredArgsConstructor;
import org.opensearch.core.action.ActionListener;
import org.opensearch.sql.commons.transport.directquery.ExecuteDirectQueryAction;
import org.opensearch.sql.commons.transport.directquery.ExecuteDirectQueryRequest;
import org.opensearch.sql.commons.transport.directquery.ExecuteDirectQueryResponse;
import org.opensearch.sql.commons.transport.directquery.GetDirectQueryResourcesAction;
import org.opensearch.sql.commons.transport.directquery.GetDirectQueryResourcesRequest;
import org.opensearch.sql.commons.transport.directquery.GetDirectQueryResourcesResponse;
import org.opensearch.sql.commons.transport.directquery.WriteDirectQueryResourcesAction;
import org.opensearch.sql.commons.transport.directquery.WriteDirectQueryResourcesRequest;
import org.opensearch.sql.commons.transport.directquery.WriteDirectQueryResourcesResponse;
import org.opensearch.transport.client.Client;

/** Node-local {@link DirectQueryClient} dispatching transport requests via {@link Client}. */
@RequiredArgsConstructor
public class DirectQueryNodeClient implements DirectQueryClient {

  private final Client client;

  @Override
  public void executeDirectQuery(
      ExecuteDirectQueryRequest request, ActionListener<ExecuteDirectQueryResponse> listener) {
    client.execute(ExecuteDirectQueryAction.INSTANCE, request, listener);
  }

  @Override
  public void getDirectQueryResources(
      GetDirectQueryResourcesRequest request,
      ActionListener<GetDirectQueryResourcesResponse> listener) {
    client.execute(GetDirectQueryResourcesAction.INSTANCE, request, listener);
  }

  @Override
  public void writeDirectQueryResources(
      WriteDirectQueryResourcesRequest request,
      ActionListener<WriteDirectQueryResourcesResponse> listener) {
    client.execute(WriteDirectQueryResourcesAction.INSTANCE, request, listener);
  }
}
