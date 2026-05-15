/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.directquery.rest;

import static org.opensearch.core.rest.RestStatus.BAD_REQUEST;
import static org.opensearch.core.rest.RestStatus.INTERNAL_SERVER_ERROR;
import static org.opensearch.rest.RestRequest.Method.POST;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.OpenSearchException;
import org.opensearch.common.annotation.ExperimentalApi;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestRequest;
import org.opensearch.sql.commons.transport.directquery.DirectQueryResultEntry;
import org.opensearch.sql.commons.transport.directquery.ExecuteDirectQueryResponse;
import org.opensearch.sql.common.setting.Settings;
import org.opensearch.sql.datasource.client.exceptions.DataSourceClientException;
import org.opensearch.sql.datasources.exceptions.ErrorMessage;
import org.opensearch.sql.datasources.utils.Scheduler;
import org.opensearch.sql.directquery.rest.model.ExecuteDirectQueryRequest;
import org.opensearch.sql.directquery.transport.TransportExecuteDirectQueryRequestAction;
import org.opensearch.sql.directquery.transport.format.DirectQueryCommonsConverter;
import org.opensearch.sql.directquery.transport.format.DirectQueryRequestConverter;
import org.opensearch.sql.directquery.transport.model.datasource.DataSourceResult;
import org.opensearch.sql.directquery.transport.model.datasource.PrometheusResult;
import org.opensearch.sql.directquery.validator.DirectQueryRequestValidator;
import org.opensearch.sql.opensearch.setting.OpenSearchSettings;
import org.opensearch.sql.opensearch.util.RestRequestUtil;
import org.opensearch.sql.protocol.response.format.JsonResponseFormatter;
import org.opensearch.transport.client.node.NodeClient;

/*
 * @opensearch.experimental
 */
@ExperimentalApi
@RequiredArgsConstructor
public class RestDirectQueryManagementAction extends BaseRestHandler {

  public static final String DIRECT_QUERY_ACTIONS = "direct_query_actions";
  public static final String BASE_DIRECT_QUERY_ACTION_URL =
      "/_plugins/_directquery/_query/{dataSources}";

  private static final Logger LOG = LogManager.getLogger(RestDirectQueryManagementAction.class);
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private final OpenSearchSettings settings;

  @Override
  public String getName() {
    return DIRECT_QUERY_ACTIONS;
  }

  @Override
  public List<Route> routes() {
    return ImmutableList.of(new Route(POST, BASE_DIRECT_QUERY_ACTION_URL));
  }

  @Override
  protected RestChannelConsumer prepareRequest(RestRequest restRequest, NodeClient nodeClient) {
    // This line consumes the dataSources parameter from the path
    String dataSources = restRequest.param("dataSources");

    // Also consume all other request parameters to prevent similar errors
    RestRequestUtil.consumeAllRequestParameters(restRequest);

    if (!dataSourcesEnabled()) {
      return dataSourcesDisabledError(restRequest);
    }

    if (Objects.requireNonNull(restRequest.method()) == POST) {
      return executeDirectQueryRequest(restRequest, nodeClient, dataSources);
    }
    return restChannel ->
        restChannel.sendResponse(
            new BytesRestResponse(
                RestStatus.METHOD_NOT_ALLOWED, String.valueOf(restRequest.method())));
  }

  private RestChannelConsumer executeDirectQueryRequest(
      RestRequest restRequest, NodeClient nodeClient, String dataSources) {
    return restChannel -> {
      try {
        ExecuteDirectQueryRequest directQueryRequest =
            DirectQueryRequestConverter.fromXContentParser(restRequest.contentParser());

        // If the datasource is not specified in the payload, use the path parameter
        if (directQueryRequest.getDataSources() == null) {
          directQueryRequest.setDataSources(dataSources);
        }

        // Generate a session ID if one is not provided in the request
        if (directQueryRequest.getSessionId() == null) {
          directQueryRequest.setSessionId(java.util.UUID.randomUUID().toString());
        }

        // Validate request using the dedicated validator
        DirectQueryRequestValidator.validateRequest(directQueryRequest);

        Scheduler.schedule(
            nodeClient,
            () ->
                nodeClient.execute(
                    TransportExecuteDirectQueryRequestAction.ACTION_TYPE,
                    DirectQueryCommonsConverter.fromLegacy(directQueryRequest),
                    new ActionListener<>() {
                      @Override
                      public void onResponse(ExecuteDirectQueryResponse response) {
                        // Format the response here at the REST layer using JsonResponseFormatter
                        String formattedResponse = formatDirectQueryResponse(response);
                        restChannel.sendResponse(
                            new BytesRestResponse(
                                RestStatus.OK,
                                "application/json; charset=UTF-8",
                                formattedResponse));
                      }

                      @Override
                      public void onFailure(Exception e) {
                        handleException(e, restChannel, restRequest.method());
                      }
                    }));
      } catch (Exception e) {
        handleException(e, restChannel, restRequest.method());
      }
    };
  }

  /** Format the direct query response using JsonResponseFormatter */
  private String formatDirectQueryResponse(ExecuteDirectQueryResponse response) {
    try {
      Map<String, DataSourceResult> typedResults = decodeResults(response);
      // Create a formatter that converts the response to a pretty JSON format
      return new JsonResponseFormatter<ExecuteDirectQueryResponse>(
          JsonResponseFormatter.Style.PRETTY) {
        @Override
        protected Object buildJsonObject(ExecuteDirectQueryResponse response) {
          return new DirectQueryResult(response.getQueryId(), typedResults, response.getSessionId());
        }
      }.format(response);
    } catch (Exception e) {
      LOG.error("Error formatting direct query response", e);
      return "{\"error\": \"" + e.getMessage() + "\"}";
    }
  }

  /** Decode opaque commons result entries back into typed {@link DataSourceResult} POJOs. */
  private static Map<String, DataSourceResult> decodeResults(ExecuteDirectQueryResponse response) {
    Map<String, DataSourceResult> decoded = new HashMap<>();
    for (Map.Entry<String, DirectQueryResultEntry> entry : response.getResults().entrySet()) {
      DirectQueryResultEntry value = entry.getValue();
      String type = value.getType() == null ? "" : value.getType().toLowerCase(Locale.ROOT);
      try {
        if ("prometheus".equals(type)) {
          decoded.put(entry.getKey(), OBJECT_MAPPER.treeToValue(withType(value.getJson(), type), PrometheusResult.class));
        } else {
          throw new IllegalStateException("Unsupported data source type: " + value.getType());
        }
      } catch (Exception e) {
        throw new IllegalStateException(
            "Failed to decode result for data source " + entry.getKey() + ": " + e.getMessage(), e);
      }
    }
    return decoded;
  }

  /**
   * Parse {@code json} and ensure the {@code type} discriminator required by {@code @JsonTypeInfo}
   * on {@link DataSourceResult} is set. Existing {@code type} values are preserved.
   */
  private static JsonNode withType(String json, String type) throws IOException {
    JsonNode node = json == null ? OBJECT_MAPPER.createObjectNode() : OBJECT_MAPPER.readTree(json);
    if (!(node instanceof ObjectNode)) {
      throw new IllegalStateException("Expected JSON object, got: " + node.getNodeType());
    }
    ObjectNode obj = (ObjectNode) node;
    if (!obj.has("type")) {
      obj.put("type", type);
    }
    return obj;
  }

  /** Simple class to represent the formatted response */
  @Getter
  private static class DirectQueryResult {
    private final String queryId;
    private final Map<String, Object> results;
    private final String sessionId;

    public DirectQueryResult(String queryId, Map<String, ?> results, String sessionId) {
      this.queryId = queryId;
      this.results = (Map<String, Object>) results;
      this.sessionId = sessionId;
    }
  }

  private void handleException(
      Exception e, RestChannel restChannel, RestRequest.Method requestMethod) {
    if (e instanceof OpenSearchException) {
      OpenSearchException exception = (OpenSearchException) e;
      reportError(restChannel, exception, exception.status());
    } else {
      LOG.error("Error happened during request handling", e);
      if (isClientError(e)) {
        reportError(restChannel, e, BAD_REQUEST);
      } else {
        reportError(restChannel, e, INTERNAL_SERVER_ERROR);
      }
    }
  }

  private void reportError(final RestChannel channel, final Exception e, final RestStatus status) {
    channel.sendResponse(
        new BytesRestResponse(status, new ErrorMessage(e, status.getStatus()).toString()));
  }

  private static boolean isClientError(Exception e) {
    return e instanceof IllegalArgumentException
        || e instanceof IllegalStateException
        || e instanceof DataSourceClientException
        || e instanceof IllegalAccessException;
  }

  private boolean dataSourcesEnabled() {
    return settings.getSettingValue(Settings.Key.DATASOURCES_ENABLED);
  }

  private RestChannelConsumer dataSourcesDisabledError(RestRequest request) {
    RestRequestUtil.consumeAllRequestParameters(request);

    return channel -> {
      reportError(
          channel,
          new IllegalAccessException(
              String.format("%s setting is false", Settings.Key.DATASOURCES_ENABLED.getKeyValue())),
          BAD_REQUEST);
    };
  }
}
