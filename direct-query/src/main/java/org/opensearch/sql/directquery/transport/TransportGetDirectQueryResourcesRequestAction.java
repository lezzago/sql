/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.directquery.transport;

import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.sql.commons.transport.directquery.GetDirectQueryResourcesAction;
import org.opensearch.sql.commons.transport.directquery.GetDirectQueryResourcesRequest;
import org.opensearch.sql.commons.transport.directquery.GetDirectQueryResourcesResponse;
import org.opensearch.sql.directquery.DirectQueryExecutorService;
import org.opensearch.sql.directquery.DirectQueryExecutorServiceImpl;
import org.opensearch.sql.directquery.transport.format.DirectQueryCommonsConverter;
import org.opensearch.sql.protocol.response.format.JsonResponseFormatter;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

/*
 * @opensearch.experimental
 */
public class TransportGetDirectQueryResourcesRequestAction
    extends HandledTransportAction<GetDirectQueryResourcesRequest, GetDirectQueryResourcesResponse> {

  private final DirectQueryExecutorService directQueryExecutorService;

  public static final String NAME = GetDirectQueryResourcesAction.NAME;
  public static final GetDirectQueryResourcesAction ACTION_TYPE =
      GetDirectQueryResourcesAction.INSTANCE;

  @Inject
  public TransportGetDirectQueryResourcesRequestAction(
      TransportService transportService,
      ActionFilters actionFilters,
      DirectQueryExecutorServiceImpl directQueryExecutorService) {
    super(NAME, transportService, actionFilters, GetDirectQueryResourcesRequest::new);
    this.directQueryExecutorService = directQueryExecutorService;
  }

  @Override
  protected void doExecute(
      Task task,
      GetDirectQueryResourcesRequest request,
      ActionListener<GetDirectQueryResourcesResponse> listener) {
    try {
      org.opensearch.sql.directquery.rest.model.GetDirectQueryResourcesRequest legacyRequest =
          DirectQueryCommonsConverter.toLegacy(request);

      org.opensearch.sql.directquery.rest.model.GetDirectQueryResourcesResponse<?> response =
          directQueryExecutorService.getDirectQueryResources(legacyRequest);
      String responseContent =
          new JsonResponseFormatter<
              org.opensearch.sql.directquery.rest.model.GetDirectQueryResourcesResponse>(
              JsonResponseFormatter.Style.PRETTY) {
            @Override
            protected Object buildJsonObject(
                org.opensearch.sql.directquery.rest.model.GetDirectQueryResourcesResponse r) {
              return r;
            }
          }.format(response);
      listener.onResponse(new GetDirectQueryResourcesResponse(responseContent));
    } catch (Exception e) {
      listener.onFailure(e);
    }
  }
}
