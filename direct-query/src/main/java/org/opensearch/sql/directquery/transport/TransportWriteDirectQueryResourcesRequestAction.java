/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.directquery.transport;

import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.sql.commons.transport.directquery.WriteDirectQueryResourcesAction;
import org.opensearch.sql.commons.transport.directquery.WriteDirectQueryResourcesRequest;
import org.opensearch.sql.commons.transport.directquery.WriteDirectQueryResourcesResponse;
import org.opensearch.sql.directquery.DirectQueryExecutorService;
import org.opensearch.sql.directquery.DirectQueryExecutorServiceImpl;
import org.opensearch.sql.directquery.transport.format.DirectQueryCommonsConverter;
import org.opensearch.sql.protocol.response.format.JsonResponseFormatter;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

/*
 * @opensearch.experimental
 */
public class TransportWriteDirectQueryResourcesRequestAction
    extends HandledTransportAction<
        WriteDirectQueryResourcesRequest, WriteDirectQueryResourcesResponse> {

  private final DirectQueryExecutorService directQueryExecutorService;

  public static final String NAME = WriteDirectQueryResourcesAction.NAME;
  public static final WriteDirectQueryResourcesAction ACTION_TYPE =
      WriteDirectQueryResourcesAction.INSTANCE;

  @Inject
  public TransportWriteDirectQueryResourcesRequestAction(
      TransportService transportService,
      ActionFilters actionFilters,
      DirectQueryExecutorServiceImpl directQueryExecutorService) {
    super(NAME, transportService, actionFilters, WriteDirectQueryResourcesRequest::new);
    this.directQueryExecutorService = directQueryExecutorService;
  }

  @Override
  protected void doExecute(
      Task task,
      WriteDirectQueryResourcesRequest request,
      ActionListener<WriteDirectQueryResourcesResponse> listener) {
    try {
      org.opensearch.sql.directquery.rest.model.WriteDirectQueryResourcesRequest legacyRequest =
          DirectQueryCommonsConverter.toLegacy(request);

      org.opensearch.sql.directquery.rest.model.WriteDirectQueryResourcesResponse<?> response =
          directQueryExecutorService.writeDirectQueryResources(legacyRequest);
      String responseContent =
          new JsonResponseFormatter<
              org.opensearch.sql.directquery.rest.model.WriteDirectQueryResourcesResponse>(
              JsonResponseFormatter.Style.PRETTY) {
            @Override
            protected Object buildJsonObject(
                org.opensearch.sql.directquery.rest.model.WriteDirectQueryResourcesResponse r) {
              return r;
            }
          }.format(response);
      listener.onResponse(new WriteDirectQueryResourcesResponse(responseContent));
    } catch (Exception e) {
      listener.onFailure(e);
    }
  }
}
