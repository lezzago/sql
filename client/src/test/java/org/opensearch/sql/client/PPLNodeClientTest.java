/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.client;

import static org.mockito.Mockito.verify;

import org.junit.Test;
import org.mockito.Mockito;
import org.opensearch.core.action.ActionListener;
import org.opensearch.sql.commons.transport.ppl.PPLQueryAction;
import org.opensearch.sql.commons.transport.ppl.PPLQueryRequest;
import org.opensearch.sql.commons.transport.ppl.PPLQueryResponse;
import org.opensearch.transport.client.Client;

public class PPLNodeClientTest {

  @Test
  @SuppressWarnings("unchecked")
  public void executeDispatchesToActionType() {
    Client client = Mockito.mock(Client.class);
    PPLNodeClient subject = new PPLNodeClient(client);
    PPLQueryRequest request = new PPLQueryRequest("source=t", null, "/_plugins/_ppl");
    ActionListener<PPLQueryResponse> listener = Mockito.mock(ActionListener.class);

    subject.executePPLQuery(request, listener);

    verify(client).execute(PPLQueryAction.INSTANCE, request, listener);
  }
}
