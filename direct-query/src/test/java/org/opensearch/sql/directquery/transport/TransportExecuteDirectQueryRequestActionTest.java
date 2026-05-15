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
import org.opensearch.sql.commons.transport.directquery.ExecuteDirectQueryRequest;
import org.opensearch.sql.commons.transport.directquery.ExecuteDirectQueryResponse;
import org.opensearch.sql.directquery.DirectQueryExecutorServiceImpl;
import org.opensearch.sql.spark.rest.model.LangType;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

/*
 * @opensearch.experimental
 */
public class TransportExecuteDirectQueryRequestActionTest {

  @Mock private TransportService transportService;
  @Mock private ActionFilters actionFilters;
  @Mock private DirectQueryExecutorServiceImpl mockExecutorService;
  @Mock private Task task;
  @Mock private ActionListener<ExecuteDirectQueryResponse> actionListener;

  private TransportExecuteDirectQueryRequestAction transportAction;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);

    transportAction =
        new TransportExecuteDirectQueryRequestAction(
            transportService, actionFilters, mockExecutorService);
  }

  @Test
  public void testDoExecuteSuccess() {
    ExecuteDirectQueryRequest commonsRequest = new ExecuteDirectQueryRequest();
    commonsRequest.setDataSources("prom-ds");
    commonsRequest.setQuery("up");
    commonsRequest.setLanguage(LangType.PROMQL.getText());

    org.opensearch.sql.directquery.rest.model.ExecuteDirectQueryResponse mockResponse =
        new org.opensearch.sql.directquery.rest.model.ExecuteDirectQueryResponse();
    mockResponse.setQueryId("test-query-id");
    mockResponse.setSessionId("test-session-id");
    mockResponse.setResult("{\"data\":{\"resultType\":\"vector\",\"result\":[]}}");
    mockResponse.setDataSourceType("prometheus");

    when(mockExecutorService.executeDirectQuery(
            any(org.opensearch.sql.directquery.rest.model.ExecuteDirectQueryRequest.class)))
        .thenReturn(mockResponse);

    transportAction.doExecute(task, commonsRequest, actionListener);

    verify(actionListener).onResponse(any(ExecuteDirectQueryResponse.class));
  }

  @Test
  public void testDoExecuteFailure() {
    ExecuteDirectQueryRequest commonsRequest = new ExecuteDirectQueryRequest();
    commonsRequest.setDataSources("prom-ds");
    commonsRequest.setLanguage(LangType.PROMQL.getText());

    RuntimeException exception = new RuntimeException("Test exception");
    when(mockExecutorService.executeDirectQuery(
            any(org.opensearch.sql.directquery.rest.model.ExecuteDirectQueryRequest.class)))
        .thenThrow(exception);

    transportAction.doExecute(task, commonsRequest, actionListener);

    verify(actionListener).onFailure(exception);
  }
}
