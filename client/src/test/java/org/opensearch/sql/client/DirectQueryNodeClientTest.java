/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.client;

import static org.mockito.Mockito.verify;

import org.junit.Test;
import org.mockito.Mockito;
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

public class DirectQueryNodeClientTest {

  @Test
  @SuppressWarnings("unchecked")
  public void executeDirectQueryDispatchesToActionType() {
    Client client = Mockito.mock(Client.class);
    DirectQueryNodeClient subject = new DirectQueryNodeClient(client);
    ExecuteDirectQueryRequest request = new ExecuteDirectQueryRequest();
    ActionListener<ExecuteDirectQueryResponse> listener = Mockito.mock(ActionListener.class);

    subject.executeDirectQuery(request, listener);

    verify(client).execute(ExecuteDirectQueryAction.INSTANCE, request, listener);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void getDirectQueryResourcesDispatchesToActionType() {
    Client client = Mockito.mock(Client.class);
    DirectQueryNodeClient subject = new DirectQueryNodeClient(client);
    GetDirectQueryResourcesRequest request = new GetDirectQueryResourcesRequest();
    ActionListener<GetDirectQueryResourcesResponse> listener = Mockito.mock(ActionListener.class);

    subject.getDirectQueryResources(request, listener);

    verify(client).execute(GetDirectQueryResourcesAction.INSTANCE, request, listener);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void writeDirectQueryResourcesDispatchesToActionType() {
    Client client = Mockito.mock(Client.class);
    DirectQueryNodeClient subject = new DirectQueryNodeClient(client);
    WriteDirectQueryResourcesRequest request = new WriteDirectQueryResourcesRequest();
    ActionListener<WriteDirectQueryResourcesResponse> listener = Mockito.mock(ActionListener.class);

    subject.writeDirectQueryResources(request, listener);

    verify(client).execute(WriteDirectQueryResourcesAction.INSTANCE, request, listener);
  }
}
