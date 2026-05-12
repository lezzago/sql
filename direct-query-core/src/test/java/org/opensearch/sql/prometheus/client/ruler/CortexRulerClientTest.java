/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.prometheus.client.ruler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.sql.prometheus.exception.PrometheusClientException;

/**
 * Regression anchor for the Cortex/Thanos ruler path. These tests guard against any drift in the
 * wire shape (URL, headers, body, URL encoding) after the {@link RulerClient} extraction. If any
 * of these fail, the refactor has changed caller-visible behavior.
 */
public class CortexRulerClientTest {

  private MockWebServer mockWebServer;
  private CortexRulerClient client;

  @BeforeEach
  public void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    client =
        new CortexRulerClient(
            new OkHttpClient.Builder().build(),
            URI.create(String.format("http://%s:%s", "localhost", mockWebServer.getPort())));
  }

  @AfterEach
  public void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  public void testGetRulesPrometheusJsonFormat() throws IOException {
    String successResponse =
        "{\"status\":\"success\",\"data\":{\"groups\":[{\"name\":\"example\",\"file\":\"rules.yml\"}]}}";
    mockWebServer.enqueue(new MockResponse().setBody(successResponse));

    JSONObject result = client.getRules(new HashMap<>());
    assertNotNull(result);
    assertTrue(result.has("groups"));
    JSONArray groups = result.getJSONArray("groups");
    assertEquals(1, groups.length());
    assertEquals("example", groups.getJSONObject(0).getString("name"));
  }

  @Test
  public void testGetRulesCortexYamlFormat() throws IOException {
    String yamlResponse =
        "test_namespace:\n  - name: example\n    rules:\n      - record: test\n        expr: up\n";
    mockWebServer.enqueue(new MockResponse().setBody(yamlResponse));

    JSONObject result = client.getRules(new HashMap<>());
    assertNotNull(result);
    JSONArray groups = result.getJSONArray("groups");
    assertEquals(1, groups.length());
    assertEquals("example", groups.getJSONObject(0).getString("name"));
    assertEquals("test_namespace", groups.getJSONObject(0).getString("file"));
  }

  @Test
  public void testGetRulesRequestUrl() throws Exception {
    mockWebServer.enqueue(new MockResponse().setBody("{\"status\":\"success\",\"data\":{\"groups\":[]}}"));
    client.getRules(new HashMap<>());
    RecordedRequest recorded = mockWebServer.takeRequest();
    assertEquals("/api/v1/rules", recorded.getPath());
    assertEquals("GET", recorded.getMethod());
  }

  @Test
  public void testGetRulesWithQueryParams() throws Exception {
    mockWebServer.enqueue(new MockResponse().setBody("{\"status\":\"success\",\"data\":{\"groups\":[]}}"));
    HashMap<String, String> params = new HashMap<>();
    params.put("type", "alert");
    client.getRules(params);
    RecordedRequest recorded = mockWebServer.takeRequest();
    assertTrue(recorded.getPath().startsWith("/api/v1/rules?"));
    assertTrue(recorded.getPath().contains("type=alert"));
  }

  @Test
  public void testGetRulesHttpError() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Internal error"));

    PrometheusClientException exception =
        assertThrows(
            PrometheusClientException.class,
            () -> client.getRules(new HashMap<>()));
    assertTrue(exception.getMessage().contains("500"));
  }

  @Test
  public void testGetRulesByNamespaceYaml() throws IOException {
    String yamlResponse = "- name: example\n  rules: []\n";
    mockWebServer.enqueue(new MockResponse().setBody(yamlResponse));

    JSONObject result = client.getRulesByNamespace("test_namespace", new HashMap<>());
    JSONArray groups = result.getJSONArray("groups");
    assertEquals(1, groups.length());
    assertEquals("example", groups.getJSONObject(0).getString("name"));
    assertEquals("test_namespace", groups.getJSONObject(0).getString("file"));
  }

  @Test
  public void testGetRulesByNamespaceUrlEncodesSpecialCharacters() throws Exception {
    mockWebServer.enqueue(new MockResponse().setBody("- name: g\n  rules: []\n"));
    client.getRulesByNamespace("my namespace/special", new HashMap<>());
    RecordedRequest recorded = mockWebServer.takeRequest();
    String path = recorded.getPath();
    assertTrue(path.contains("my+namespace%2Fspecial") || path.contains("my%20namespace%2Fspecial"));
  }

  @Test
  public void testGetRulesByNamespaceHttpError() {
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(404).setBody("Namespace not found"));
    PrometheusClientException exception =
        assertThrows(
            PrometheusClientException.class,
            () -> client.getRulesByNamespace("missing_ns", new HashMap<>()));
    assertTrue(exception.getMessage().contains("404"));
  }

  @Test
  public void testCreateOrUpdateRuleGroupSuccess() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(202).setBody(""));
    String yamlBody = "name: example_group\nrules: []\n";
    String result = client.createOrUpdateRuleGroup("test_namespace", yamlBody);
    assertTrue(result.contains("success"));

    RecordedRequest recorded = mockWebServer.takeRequest();
    assertEquals("POST", recorded.getMethod());
    assertEquals("/api/v1/rules/test_namespace", recorded.getPath());
    assertEquals("application/yaml", recorded.getHeader("Content-Type"));
    assertEquals(yamlBody, recorded.getBody().readUtf8());
  }

  @Test
  public void testCreateOrUpdateRuleGroupWithResponseBody() throws IOException {
    String responseBody = "{\"status\":\"success\",\"data\":null}";
    mockWebServer.enqueue(new MockResponse().setResponseCode(202).setBody(responseBody));
    String result = client.createOrUpdateRuleGroup("test_namespace", "name: test\nrules: []\n");
    assertEquals(responseBody, result);
  }

  @Test
  public void testCreateOrUpdateRuleGroupHttpError() {
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(400).setBody("Bad Request: invalid YAML"));
    PrometheusClientException exception =
        assertThrows(
            PrometheusClientException.class,
            () -> client.createOrUpdateRuleGroup("test_namespace", "invalid yaml"));
    assertTrue(exception.getMessage().contains("Ruler request failed with code: 400"));
  }

  @Test
  public void testDeleteRuleNamespaceSuccess() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));
    String result = client.deleteRuleNamespace("test_namespace");
    assertTrue(result.contains("success"));

    RecordedRequest recorded = mockWebServer.takeRequest();
    assertEquals("DELETE", recorded.getMethod());
    assertEquals("/api/v1/rules/test_namespace", recorded.getPath());
  }

  @Test
  public void testDeleteRuleNamespaceHttpError() {
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(404).setBody("Namespace not found"));
    PrometheusClientException exception =
        assertThrows(
            PrometheusClientException.class,
            () -> client.deleteRuleNamespace("missing_ns"));
    assertTrue(exception.getMessage().contains("Ruler request failed with code: 404"));
  }

  @Test
  public void testDeleteRuleGroupSuccess() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));
    String result = client.deleteRuleGroup("test_namespace", "test_group");
    assertTrue(result.contains("success"));

    RecordedRequest recorded = mockWebServer.takeRequest();
    assertEquals("DELETE", recorded.getMethod());
    assertEquals("/api/v1/rules/test_namespace/test_group", recorded.getPath());
  }

  @Test
  public void testDeleteRuleGroupUrlEncodesSpecialCharacters() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));
    client.deleteRuleGroup("ns with spaces", "group/name");
    RecordedRequest recorded = mockWebServer.takeRequest();
    String path = recorded.getPath();
    assertTrue(path.contains("ns+with+spaces") || path.contains("ns%20with%20spaces"));
    assertTrue(path.contains("group%2Fname"));
  }

  @Test
  public void testDeleteRuleGroupHttpError() {
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(404).setBody("Group not found"));
    PrometheusClientException exception =
        assertThrows(
            PrometheusClientException.class,
            () -> client.deleteRuleGroup("test_namespace", "missing_group"));
    assertTrue(exception.getMessage().contains("Ruler request failed with code: 404"));
  }

  @Test
  public void testCreateOrUpdateRuleGroupHttpErrorWithNullBody() throws IOException {
    CortexRulerClient nullBodyClient = buildNullBodyClient(400, "Bad Request");
    PrometheusClientException exception =
        assertThrows(
            PrometheusClientException.class,
            () -> nullBodyClient.createOrUpdateRuleGroup("ns", "name: g\nrules: []\n"));
    assertTrue(exception.getMessage().contains("No response body"));
  }

  @Test
  public void testDeleteRuleNamespaceHttpErrorWithNullBody() throws IOException {
    CortexRulerClient nullBodyClient = buildNullBodyClient(500, "Server Error");
    PrometheusClientException exception =
        assertThrows(
            PrometheusClientException.class,
            () -> nullBodyClient.deleteRuleNamespace("ns"));
    assertTrue(exception.getMessage().contains("No response body"));
  }

  @Test
  public void testDeleteRuleGroupHttpErrorWithNullBody() throws IOException {
    CortexRulerClient nullBodyClient = buildNullBodyClient(500, "Server Error");
    PrometheusClientException exception =
        assertThrows(
            PrometheusClientException.class,
            () -> nullBodyClient.deleteRuleGroup("ns", "g"));
    assertTrue(exception.getMessage().contains("No response body"));
  }

  @Test
  public void testGetRulesBareYamlListFormat() throws IOException {
    String yamlResponse = "- name: group1\n  rules:\n    - record: test\n      expr: up\n";
    mockWebServer.enqueue(new MockResponse().setBody(yamlResponse));

    JSONObject result = client.getRules(new HashMap<>());
    JSONArray groups = result.getJSONArray("groups");
    assertEquals(1, groups.length());
    assertEquals("group1", groups.getJSONObject(0).getString("name"));
    assertFalse(groups.getJSONObject(0).has("file"));
  }

  private CortexRulerClient buildNullBodyClient(int code, String message) throws IOException {
    Request dummyRequest = new Request.Builder().url(mockWebServer.url("/")).build();
    Response nullBodyResponse =
        new Response.Builder()
            .request(dummyRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(message)
            .body(null)
            .build();
    OkHttpClient spyClient = spy(new OkHttpClient());
    Call mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(nullBodyResponse);
    doAnswer(invocation -> mockCall).when(spyClient).newCall(any(Request.class));

    return new CortexRulerClient(
        spyClient,
        URI.create(String.format("http://%s:%s", "localhost", mockWebServer.getPort())));
  }
}
