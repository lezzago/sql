/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.directquery.transport;

import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.sql.commons.transport.directquery.ExecuteDirectQueryAction;
import org.opensearch.sql.commons.transport.directquery.ExecuteDirectQueryRequest;
import org.opensearch.sql.commons.transport.directquery.ExecuteDirectQueryResponse;
import org.opensearch.sql.directquery.DirectQueryExecutorService;
import org.opensearch.sql.directquery.DirectQueryExecutorServiceImpl;
import org.opensearch.sql.directquery.transport.format.DirectQueryCommonsConverter;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

/*
 * @opensearch.experimental
 */
public class TransportExecuteDirectQueryRequestAction
    extends HandledTransportAction<ExecuteDirectQueryRequest, ExecuteDirectQueryResponse> {

  private final DirectQueryExecutorService directQueryExecutorService;

  public static final String NAME = ExecuteDirectQueryAction.NAME;
  public static final ExecuteDirectQueryAction ACTION_TYPE = ExecuteDirectQueryAction.INSTANCE;

  @Inject
  public TransportExecuteDirectQueryRequestAction(
      TransportService transportService,
      ActionFilters actionFilters,
      DirectQueryExecutorServiceImpl directQueryExecutorService) {
    super(NAME, transportService, actionFilters, ExecuteDirectQueryRequest::new);
    this.directQueryExecutorService = directQueryExecutorService;
  }

  @Override
  protected void doExecute(
      Task task,
      ExecuteDirectQueryRequest request,
      ActionListener<ExecuteDirectQueryResponse> listener) {
    try {
      org.opensearch.sql.directquery.rest.model.ExecuteDirectQueryRequest legacyRequest =
          DirectQueryCommonsConverter.toLegacy(request);

      org.opensearch.sql.directquery.rest.model.ExecuteDirectQueryResponse legacyResponse =
          directQueryExecutorService.executeDirectQuery(legacyRequest);

      listener.onResponse(
          DirectQueryCommonsConverter.toCommonsResponse(
              legacyResponse.getQueryId(),
              legacyResponse.getResult(),
              legacyResponse.getSessionId(),
              legacyRequest.getDataSources(),
              legacyResponse.getDataSourceType()));
    } catch (Exception e) {
      listener.onFailure(e);
    }
  }
}
