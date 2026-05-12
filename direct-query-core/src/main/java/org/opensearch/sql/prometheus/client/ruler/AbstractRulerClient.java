/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.prometheus.client.ruler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opensearch.sql.prometheus.exception.PrometheusClientException;

/**
 * Shared helpers for ruler implementations: response reading, query-string building, and rule
 * response normalization.
 */
abstract class AbstractRulerClient implements RulerClient {

  private static final Logger logger = LogManager.getLogger(AbstractRulerClient.class);
  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

  protected String paramsToQueryString(Map<String, String> queryParams) {
    String queryString =
        queryParams.entrySet().stream()
            .map(
                entry ->
                    URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
    return queryString.isEmpty() ? "" : "?" + queryString;
  }

  /**
   * Reads a Ruler API response, returning the body string on success or throwing on failure.
   * Consolidates the error-handling pattern shared by all Ruler methods.
   */
  protected String readRulerResponse(Response response, String operationDescription)
      throws IOException {
    if (response.isSuccessful()) {
      return Objects.requireNonNull(response.body(), "Ruler response body is null").string();
    } else {
      String errorBody = response.body() != null ? response.body().string() : "No response body";
      logger.error(
          "Ruler {} request failed with code: {}, error body: {}",
          operationDescription,
          response.code(),
          errorBody);
      throw new PrometheusClientException(
          String.format(
              "Ruler request failed with code: %s. Error details: %s",
              response.code(), errorBody));
    }
  }

  /**
   * Normalizes a raw rule response body into a consistent {"groups":[...]} JSONObject. Handles
   * three response formats:
   *
   * <ul>
   *   <li>Prometheus/AMP JSON: {"status":"success","data":{"groups":[...]}} - extracts data
   *   <li>Cortex/Thanos YAML (all rules): Map of namespace to list of rule groups
   *   <li>Cortex/Thanos YAML (single namespace): List of rule groups
   * </ul>
   */
  protected JSONObject normalizeRulesResponse(String body, String namespace) {
    if (body.isEmpty()) {
      return new JSONObject().put("groups", new JSONArray());
    }

    try {
      JSONObject jsonObject = new JSONObject(body);
      if ("success".equals(jsonObject.optString("status")) && jsonObject.has("data")) {
        return jsonObject.getJSONObject("data");
      }
      if (jsonObject.has("groups")) {
        return jsonObject;
      }
    } catch (JSONException e) {
      // Not JSON — fall through to YAML parsing
    }

    JSONArray groupsArray = new JSONArray();
    try {
      Map<String, Object> parsed =
          YAML_MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {});
      addGroupsFromParsed(parsed, groupsArray);
      return new JSONObject().put("groups", groupsArray);
    } catch (Exception mapEx) {
      try {
        List<Map<String, Object>> parsed =
            YAML_MAPPER.readValue(body, new TypeReference<List<Map<String, Object>>>() {});
        addGroupsFromList(parsed, namespace, groupsArray);
        return new JSONObject().put("groups", groupsArray);
      } catch (Exception listEx) {
        logger.warn(
            "Failed to parse rules response body, returning empty groups: {}",
            listEx.getMessage());
        return new JSONObject().put("groups", new JSONArray());
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void addGroupsFromParsed(Map<String, Object> namespacesMap, JSONArray groupsArray) {
    for (Map.Entry<String, Object> entry : namespacesMap.entrySet()) {
      if (entry.getValue() instanceof List) {
        addGroupsFromList(
            (List<Map<String, Object>>) entry.getValue(), entry.getKey(), groupsArray);
      }
    }
  }

  private void addGroupsFromList(
      List<Map<String, Object>> groups, String namespace, JSONArray groupsArray) {
    for (Map<String, Object> group : groups) {
      JSONObject groupObj = new JSONObject(group);
      if (namespace != null) {
        groupObj.put("file", namespace);
      }
      groupsArray.put(groupObj);
    }
  }
}
