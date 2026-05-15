/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.client;

import org.opensearch.core.action.ActionListener;
import org.opensearch.sql.commons.transport.directquery.ExecuteDirectQueryRequest;
import org.opensearch.sql.commons.transport.directquery.ExecuteDirectQueryResponse;
import org.opensearch.sql.commons.transport.directquery.GetDirectQueryResourcesRequest;
import org.opensearch.sql.commons.transport.directquery.GetDirectQueryResourcesResponse;
import org.opensearch.sql.commons.transport.directquery.WriteDirectQueryResourcesRequest;
import org.opensearch.sql.commons.transport.directquery.WriteDirectQueryResourcesResponse;

/** Thin client interface for invoking Direct Query transport APIs. */
public interface DirectQueryClient {

  /** Execute a direct query against an external data source. */
  void executeDirectQuery(
      ExecuteDirectQueryRequest request, ActionListener<ExecuteDirectQueryResponse> listener);

  /** Read direct-query resources (e.g. Prometheus labels, series). */
  void getDirectQueryResources(
      GetDirectQueryResourcesRequest request,
      ActionListener<GetDirectQueryResourcesResponse> listener);

  /** Write direct-query resources (e.g. Prometheus alerting rules, silences). */
  void writeDirectQueryResources(
      WriteDirectQueryResourcesRequest request,
      ActionListener<WriteDirectQueryResourcesResponse> listener);
}
