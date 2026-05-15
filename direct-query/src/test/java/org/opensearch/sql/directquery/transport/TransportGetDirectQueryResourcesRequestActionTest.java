/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.directquery.transport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.core.action.ActionListener;
import org.opensearch.sql.commons.transport.directquery.GetDirectQueryResourcesRequest;
import org.opensearch.sql.commons.transport.directquery.GetDirectQueryResourcesResponse;
import org.opensearch.sql.directquery.DirectQueryExecutorServiceImpl;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

/*
 * @opensearch.experimental
 */
public class TransportGetDirectQueryResourcesRequestActionTest {

  @Mock private TransportService transportService;
  @Mock private ActionFilters actionFilters;
  @Mock private DirectQueryExecutorServiceImpl mockExecutorService;
  @Mock private Task task;
  @Mock private ActionListener<GetDirectQueryResourcesResponse> actionListener;

  private TransportGetDirectQueryResourcesRequestAction transportAction;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);

    transportAction =
        new TransportGetDirectQueryResourcesRequestAction(
            transportService, actionFilters, mockExecutorService);
  }

  @Test
  public void testDoExecuteSuccess() {
    GetDirectQueryResourcesRequest commonsRequest = new GetDirectQueryResourcesRequest();
    commonsRequest.setDataSource("prom");

    org.opensearch.sql.directquery.rest.model.GetDirectQueryResourcesResponse mockResponse =
        new org.opensearch.sql.directquery.rest.model.GetDirectQueryResourcesResponse();

    when(mockExecutorService.getDirectQueryResources(
            any(org.opensearch.sql.directquery.rest.model.GetDirectQueryResourcesRequest.class)))
        .thenReturn(mockResponse);

    transportAction.doExecute(task, commonsRequest, actionListener);

    verify(actionListener).onResponse(any(GetDirectQueryResourcesResponse.class));
  }

  @Test
  public void testDoExecuteFailure() {
    GetDirectQueryResourcesRequest commonsRequest = new GetDirectQueryResourcesRequest();
    commonsRequest.setDataSource("prom");

    RuntimeException exception = new RuntimeException("Test exception");
    when(mockExecutorService.getDirectQueryResources(
            any(org.opensearch.sql.directquery.rest.model.GetDirectQueryResourcesRequest.class)))
        .thenThrow(exception);

    transportAction.doExecute(task, commonsRequest, actionListener);

    verify(actionListener).onFailure(exception);
  }
}
