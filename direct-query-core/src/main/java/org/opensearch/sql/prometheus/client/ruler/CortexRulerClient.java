/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.prometheus.client.ruler;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.opensearch.secure_sm.AccessController;

/**
 * Cortex/Thanos-compatible ruler client. Issues native Prometheus ruler HTTP calls against the
 * configured ruler URI. This is the default implementation — used whenever {@code
 * prometheus.ruler.type} is absent or set to {@code cortex}.
 */
public class CortexRulerClient extends AbstractRulerClient {

  private static final Logger logger = LogManager.getLogger(CortexRulerClient.class);

  private final OkHttpClient httpClient;
  private final URI rulerUri;

  public CortexRulerClient(OkHttpClient httpClient, URI rulerUri) {
    this.httpClient = httpClient;
    this.rulerUri = rulerUri;
  }

  @Override
  public JSONObject getRules(Map<String, String> queryParams) throws IOException {
    String queryString = paramsToQueryString(queryParams);
    String queryUrl =
        String.format(
            "%s/api/v1/rules%s", rulerUri.toString().replaceAll("/$", ""), queryString);
    logger.debug("Making Prometheus GET rules request: {}", queryUrl);
    Request request = new Request.Builder().url(queryUrl).build();
    try (Response response =
        AccessController.doPrivilegedChecked(() -> httpClient.newCall(request).execute())) {
      String body = readRulerResponse(response, "GET all rules");
      return normalizeRulesResponse(body, null);
    }
  }

  @Override
  public JSONObject getRulesByNamespace(String namespace, Map<String, String> queryParams)
      throws IOException {
    String queryString = paramsToQueryString(queryParams);
    String queryUrl =
        String.format(
            "%s/api/v1/rules/%s%s",
            rulerUri.toString().replaceAll("/$", ""),
            URLEncoder.encode(namespace, StandardCharsets.UTF_8),
            queryString);
    logger.debug("Making Ruler GET request for namespace");
    Request request = new Request.Builder().url(queryUrl).build();
    try (Response response =
        AccessController.doPrivilegedChecked(() -> httpClient.newCall(request).execute())) {
      String body = readRulerResponse(response, "GET namespace " + namespace);
      return normalizeRulesResponse(body, namespace);
    }
  }

  @Override
  public String createOrUpdateRuleGroup(String namespace, String yamlBody) throws IOException {
    String queryUrl =
        String.format(
            "%s/api/v1/rules/%s",
            rulerUri.toString().replaceAll("/$", ""),
            URLEncoder.encode(namespace, StandardCharsets.UTF_8));
    logger.debug("Making Ruler POST request to create/update rule group");
    Request request =
        new Request.Builder()
            .url(queryUrl)
            .header("Content-Type", "application/yaml")
            .post(RequestBody.create(yamlBody.getBytes(StandardCharsets.UTF_8)))
            .build();
    try (Response response =
        AccessController.doPrivilegedChecked(() -> httpClient.newCall(request).execute())) {
      String body = readRulerResponse(response, "POST create/update rule group");
      return body.isEmpty() ? "{\"status\":\"success\"}" : body;
    }
  }

  @Override
  public String deleteRuleNamespace(String namespace) throws IOException {
    String queryUrl =
        String.format(
            "%s/api/v1/rules/%s",
            rulerUri.toString().replaceAll("/$", ""),
            URLEncoder.encode(namespace, StandardCharsets.UTF_8));
    logger.debug("Making Ruler DELETE request for namespace");
    Request request = new Request.Builder().url(queryUrl).delete().build();
    try (Response response =
        AccessController.doPrivilegedChecked(() -> httpClient.newCall(request).execute())) {
      String body = readRulerResponse(response, "DELETE namespace " + namespace);
      return body.isEmpty() ? "{\"status\":\"success\"}" : body;
    }
  }

  @Override
  public String deleteRuleGroup(String namespace, String groupName) throws IOException {
    String queryUrl =
        String.format(
            "%s/api/v1/rules/%s/%s",
            rulerUri.toString().replaceAll("/$", ""),
            URLEncoder.encode(namespace, StandardCharsets.UTF_8),
            URLEncoder.encode(groupName, StandardCharsets.UTF_8));
    logger.debug("Making Ruler DELETE request for group");
    Request request = new Request.Builder().url(queryUrl).delete().build();
    try (Response response =
        AccessController.doPrivilegedChecked(() -> httpClient.newCall(request).execute())) {
      String body = readRulerResponse(response, "DELETE group " + groupName);
      return body.isEmpty() ? "{\"status\":\"success\"}" : body;
    }
  }
}
