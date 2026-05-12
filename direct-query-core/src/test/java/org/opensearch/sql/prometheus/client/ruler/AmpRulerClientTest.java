/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.prometheus.client.ruler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.opensearch.sql.prometheus.exception.PrometheusClientException;

/** Unit tests for {@link AmpRulerClient}. */
public class AmpRulerClientTest {

  private static final String WORKSPACE = "ws-abc";
  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

  private MockWebServer mockWebServer;
  private AmpRulerClient client;
  private RulerClient readDelegate;

  @BeforeEach
  public void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    readDelegate = Mockito.mock(RulerClient.class);
    client =
        new AmpRulerClient(
            new OkHttpClient.Builder().build(),
            URI.create(String.format("http://%s:%s", "localhost", mockWebServer.getPort())),
            WORKSPACE,
            readDelegate);
  }

  @AfterEach
  public void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  public void testConstructorRejectsBlankWorkspaceId() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AmpRulerClient(
                new OkHttpClient(),
                URI.create("http://localhost"),
                " ",
                readDelegate));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AmpRulerClient(
                new OkHttpClient(), URI.create("http://localhost"), null, readDelegate));
  }

  @Test
  public void testGetRulesDelegatesToReadClient() throws IOException {
    Mockito.when(readDelegate.getRules(Mockito.anyMap()))
        .thenReturn(new JSONObject().put("groups", new java.util.ArrayList<>()));
    JSONObject result = client.getRules(new HashMap<>());
    assertNotNull(result);
    Mockito.verify(readDelegate).getRules(Mockito.anyMap());
    assertEquals(0, mockWebServer.getRequestCount());
  }

  @Test
  public void testGetRulesByNamespaceDelegatesToReadClient() throws IOException {
    Mockito.when(readDelegate.getRulesByNamespace(Mockito.eq("ns"), Mockito.anyMap()))
        .thenReturn(new JSONObject().put("groups", new java.util.ArrayList<>()));
    client.getRulesByNamespace("ns", new HashMap<>());
    Mockito.verify(readDelegate).getRulesByNamespace(Mockito.eq("ns"), Mockito.anyMap());
    assertEquals(0, mockWebServer.getRequestCount());
  }

  @Test
  public void testCreateRuleGroupBareYamlNormalizesToWrapper() throws Exception {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(202)
            .setBody(
                "{\"name\":\"dq-1\",\"arn\":\"arn:x\",\"status\":{\"statusCode\":\"CREATING\"}}"));

    String yamlBody =
        "name: g\nrules:\n  - record: r\n    expr: up\n";
    String result = client.createOrUpdateRuleGroup("dq-1", yamlBody);
    assertTrue(result.contains("CREATING"));

    RecordedRequest recorded = mockWebServer.takeRequest();
    assertEquals("POST", recorded.getMethod());
    assertEquals("/workspaces/ws-abc/rulegroupsnamespaces", recorded.getPath());
    assertEquals("application/json", recorded.getHeader("Content-Type"));

    JSONObject body = new JSONObject(recorded.getBody().readUtf8());
    assertEquals("dq-1", body.getString("name"));
    String clientToken = body.getString("clientToken");
    assertFalse(clientToken.isBlank());
    String decoded =
        new String(Base64.getDecoder().decode(body.getString("data")),
            java.nio.charset.StandardCharsets.UTF_8);

    Map<String, Object> parsed =
        YAML_MAPPER.readValue(decoded, new TypeReference<Map<String, Object>>() {});
    assertTrue(parsed.containsKey("groups"));
    @SuppressWarnings("unchecked")
    List<Object> groups = (List<Object>) parsed.get("groups");
    assertEquals(1, groups.size());
    @SuppressWarnings("unchecked")
    Map<String, Object> g = (Map<String, Object>) groups.get(0);
    assertEquals("g", g.get("name"));
  }

  @Test
  public void testCreateRuleGroupWrapperYamlPassesThrough() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(202).setBody(""));

    String yamlBody =
        "groups:\n  - name: g\n    rules:\n      - record: r\n        expr: up\n";
    String result = client.createOrUpdateRuleGroup("dq-1", yamlBody);
    assertTrue(result.contains("success"));

    RecordedRequest recorded = mockWebServer.takeRequest();
    JSONObject body = new JSONObject(recorded.getBody().readUtf8());
    String decoded =
        new String(Base64.getDecoder().decode(body.getString("data")),
            java.nio.charset.StandardCharsets.UTF_8);
    Map<String, Object> parsed =
        YAML_MAPPER.readValue(decoded, new TypeReference<Map<String, Object>>() {});
    assertTrue(parsed.containsKey("groups"));
  }

  @Test
  public void testCreateRuleGroupConflictRetriesAsPut() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(409).setBody("{\"message\":\"exists\"}"));
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(""));

    String yamlBody = "groups:\n  - name: g\n    rules: []\n";
    String result = client.createOrUpdateRuleGroup("dq-1", yamlBody);
    assertTrue(result.contains("success"));
    assertEquals(2, mockWebServer.getRequestCount());

    RecordedRequest first = mockWebServer.takeRequest();
    assertEquals("POST", first.getMethod());
    JSONObject firstBody = new JSONObject(first.getBody().readUtf8());
    String firstToken = firstBody.getString("clientToken");

    RecordedRequest second = mockWebServer.takeRequest();
    assertEquals("PUT", second.getMethod());
    assertEquals("/workspaces/ws-abc/rulegroupsnamespaces/dq-1", second.getPath());
    JSONObject secondBody = new JSONObject(second.getBody().readUtf8());
    assertFalse(secondBody.has("name"));
    assertTrue(secondBody.has("data"));
    String secondToken = secondBody.getString("clientToken");
    assertNotEquals(firstToken, secondToken);
  }

  @Test
  public void testPutBodyReflectsRenamedGroup() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(409).setBody(""));
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(""));

    String yamlBody = "groups:\n  - name: g2\n    rules: []\n";
    client.createOrUpdateRuleGroup("dq-1", yamlBody);

    mockWebServer.takeRequest(); // POST
    RecordedRequest put = mockWebServer.takeRequest();
    JSONObject putBody = new JSONObject(put.getBody().readUtf8());
    String decoded =
        new String(Base64.getDecoder().decode(putBody.getString("data")),
            java.nio.charset.StandardCharsets.UTF_8);
    Map<String, Object> parsed =
        YAML_MAPPER.readValue(decoded, new TypeReference<Map<String, Object>>() {});
    @SuppressWarnings("unchecked")
    List<Object> groups = (List<Object>) parsed.get("groups");
    @SuppressWarnings("unchecked")
    Map<String, Object> g = (Map<String, Object>) groups.get(0);
    assertEquals("g2", g.get("name"));
  }

  @Test
  public void testCreateRuleGroupNon409ErrorPropagates() {
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(400).setBody("validation error"));
    String yamlBody = "name: g\nrules: []\n";
    PrometheusClientException ex =
        assertThrows(
            PrometheusClientException.class,
            () -> client.createOrUpdateRuleGroup("dq-1", yamlBody));
    assertTrue(ex.getMessage().contains("400"));
    assertTrue(ex.getMessage().contains("validation error"));
  }

  @Test
  public void testRejectMultiGroupYamlBeforeHttp() {
    String yamlBody =
        "groups:\n  - name: g1\n    rules: []\n  - name: g2\n    rules: []\n";
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> client.createOrUpdateRuleGroup("dq-multi", yamlBody));
    assertTrue(ex.getMessage().contains("exactly one rule group"));
    assertTrue(ex.getMessage().contains("dq-multi"));
    assertEquals(0, mockWebServer.getRequestCount());
  }

  @Test
  public void testRejectMalformedYamlBeforeHttp() {
    String yamlBody = "\t\tnot: valid: yaml:";
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> client.createOrUpdateRuleGroup("dq-bad", yamlBody));
    assertTrue(ex.getMessage().contains("dq-bad"));
    assertEquals(0, mockWebServer.getRequestCount());
  }

  @Test
  public void testRejectNonMappingYaml() {
    // A YAML document that parses as a list rather than a mapping.
    String yamlBody = "- just: a list\n- not a mapping\n";
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> client.createOrUpdateRuleGroup("dq-x", yamlBody));
    assertTrue(ex.getMessage().contains("must be a mapping"));
    assertEquals(0, mockWebServer.getRequestCount());
  }

  @Test
  public void testRejectRuleGroupMissingName() {
    String yamlBody = "rules:\n  - record: r\n    expr: up\n";
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> client.createOrUpdateRuleGroup("dq-x", yamlBody));
    assertTrue(ex.getMessage().contains("missing required field 'name'"));
    assertEquals(0, mockWebServer.getRequestCount());
  }

  @Test
  public void testRejectInvalidNamespaceName() {
    String yamlBody = "name: g\nrules: []\n";
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> client.createOrUpdateRuleGroup("bad name with spaces", yamlBody));
    assertTrue(ex.getMessage().contains("bad name with spaces"));
    assertEquals(0, mockWebServer.getRequestCount());
  }

  @Test
  public void testRejectNamespaceNameTooLong() {
    String longName = "a".repeat(65);
    String yamlBody = "name: g\nrules: []\n";
    assertThrows(
        IllegalArgumentException.class,
        () -> client.createOrUpdateRuleGroup(longName, yamlBody));
    assertEquals(0, mockWebServer.getRequestCount());
  }

  @Test
  public void testLegalNamespaceWithDotsAndUnderscoresSucceeds() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(202).setBody(""));
    String yamlBody = "name: g\nrules: []\n";
    client.createOrUpdateRuleGroup("ns.with-dots_only", yamlBody);
    RecordedRequest recorded = mockWebServer.takeRequest();
    JSONObject body = new JSONObject(recorded.getBody().readUtf8());
    assertEquals("ns.with-dots_only", body.getString("name"));
  }

  @Test
  public void testDeleteRuleNamespaceSuccess() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(""));
    String result = client.deleteRuleNamespace("dq-1");
    assertTrue(result.contains("success"));

    RecordedRequest recorded = mockWebServer.takeRequest();
    assertEquals("DELETE", recorded.getMethod());
    assertTrue(recorded.getPath().startsWith("/workspaces/ws-abc/rulegroupsnamespaces/dq-1"));
    assertTrue(recorded.getPath().contains("clientToken="));
  }

  @Test
  public void testDeleteRuleNamespaceReturnsBodyWhenNonEmpty() throws Exception {
    String body = "{\"status\":{\"statusCode\":\"DELETING\"}}";
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(body));
    String result = client.deleteRuleNamespace("dq-1");
    assertEquals(body, result);
  }

  @Test
  public void testPutSuccessReturnsBodyWhenNonEmpty() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(409).setBody("exists"));
    String putBody = "{\"status\":{\"statusCode\":\"UPDATING\"}}";
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(putBody));

    String yamlBody = "groups:\n  - name: g\n    rules: []\n";
    String result = client.createOrUpdateRuleGroup("dq-1", yamlBody);
    assertEquals(putBody, result);
  }

  @Test
  public void testDeleteRuleNamespace404Tolerance() throws Exception {
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(404).setBody("{\"message\":\"not found\"}"));
    String result = client.deleteRuleNamespace("dq-gone");
    assertTrue(result.contains("success"));
  }

  @Test
  public void testDeleteRuleGroupDelegatesToNamespaceDelete() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(""));
    client.deleteRuleGroup("dq-1", "whatever-group");

    assertEquals(1, mockWebServer.getRequestCount());
    RecordedRequest recorded = mockWebServer.takeRequest();
    assertEquals("DELETE", recorded.getMethod());
    assertTrue(recorded.getPath().startsWith("/workspaces/ws-abc/rulegroupsnamespaces/dq-1"));
    assertFalse(recorded.getPath().contains("whatever-group"));
  }

  @Test
  public void testDeleteRuleGroupIgnoresGroupNameArgument() throws Exception {
    for (String groupName : List.of("alpha", "beta-group", "gamma.dot", "delta_us")) {
      mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(""));
      client.deleteRuleGroup("dq-1", groupName);
      RecordedRequest recorded = mockWebServer.takeRequest();
      assertFalse(recorded.getPath().contains(groupName),
          "path should not contain group name: " + recorded.getPath());
    }
  }

  @Test
  public void testCreateRulesEmptyBodyReturnsSuccess() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(""));
    String yamlBody = "name: g\nrules: []\n";
    String result = client.createOrUpdateRuleGroup("dq-1", yamlBody);
    assertTrue(result.contains("success"));
  }
}
