/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.prometheus.client.ruler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.opensearch.secure_sm.AccessController;

/**
 * Ruler client targeting AWS Managed Prometheus's control plane
 * ({@code aps.<region>.amazonaws.com}). AMP's rule admin API lives on a different host than its
 * Prometheus-compatible data plane, uses a JSON-wrapped base64-encoded YAML body, and has no
 * per-group delete endpoint. This client normalizes those differences while preserving the
 * {@link RulerClient} contract.
 *
 * <p><b>1:1 invariant.</b> AMP replaces a namespace's contents atomically via PUT. To keep the
 * Cortex-shaped "add or replace a single group" contract safe, this client requires that the
 * incoming YAML contain exactly one rule group — multi-group payloads are rejected
 * synchronously before any HTTP call. Under this invariant, PUT is safe because a namespace can
 * only ever contain the one group, so replacing its contents never clobbers an unrelated group.
 *
 * <p><b>Reads delegate to the data plane.</b> AMP serves the Prometheus-compatible
 * {@code /api/v1/rules} endpoints from the workspace data-plane host; this client proxies the
 * two GET methods to a {@link CortexRulerClient} configured against that host.
 */
public class AmpRulerClient extends AbstractRulerClient {

  private static final Logger logger = LogManager.getLogger(AmpRulerClient.class);
  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

  /** AMP RuleGroupsNamespace names: first char alnum, then [-._0-9A-Za-z], length ≤ 64. */
  private static final Pattern NAMESPACE_PATTERN =
      Pattern.compile("^[0-9A-Za-z][-._0-9A-Za-z]*$");
  private static final int NAMESPACE_MAX_LENGTH = 64;

  private final OkHttpClient controlPlaneHttpClient;
  private final URI controlPlaneUri;
  private final String workspaceId;
  private final RulerClient readDelegate;

  public AmpRulerClient(
      OkHttpClient controlPlaneHttpClient,
      URI controlPlaneUri,
      String workspaceId,
      RulerClient readDelegate) {
    if (workspaceId == null || workspaceId.isBlank()) {
      throw new IllegalArgumentException("AMP workspaceId must be non-null and non-blank");
    }
    this.controlPlaneHttpClient = controlPlaneHttpClient;
    this.controlPlaneUri = controlPlaneUri;
    this.workspaceId = workspaceId;
    this.readDelegate = readDelegate;
  }

  @Override
  public JSONObject getRules(Map<String, String> queryParams) throws IOException {
    return readDelegate.getRules(queryParams);
  }

  @Override
  public JSONObject getRulesByNamespace(String namespace, Map<String, String> queryParams)
      throws IOException {
    return readDelegate.getRulesByNamespace(namespace, queryParams);
  }

  @Override
  public String createOrUpdateRuleGroup(String namespace, String yamlBody) throws IOException {
    validateNamespaceName(namespace);
    String normalizedYaml = validateAndNormalizeYaml(namespace, yamlBody);
    String base64Data =
        Base64.getEncoder().encodeToString(normalizedYaml.getBytes(StandardCharsets.UTF_8));

    String createResult = tryCreate(namespace, base64Data);
    if (createResult != null) {
      return createResult;
    }
    return doPut(namespace, base64Data);
  }

  @Override
  public String deleteRuleNamespace(String namespace) throws IOException {
    String clientToken = UUID.randomUUID().toString();
    String queryUrl =
        String.format(
            "%s/workspaces/%s/rulegroupsnamespaces/%s?clientToken=%s",
            controlPlaneUri.toString().replaceAll("/$", ""),
            URLEncoder.encode(workspaceId, StandardCharsets.UTF_8),
            URLEncoder.encode(namespace, StandardCharsets.UTF_8),
            URLEncoder.encode(clientToken, StandardCharsets.UTF_8));
    logger.debug("AMP DELETE rulegroupsnamespaces for namespace {}", namespace);
    Request request = new Request.Builder().url(queryUrl).delete().build();
    String body;
    try (Response response =
        AccessController.doPrivilegedChecked(
            () -> controlPlaneHttpClient.newCall(request).execute())) {
      // Mirror Cortex path: a missing namespace is treated as success.
      body =
          response.code() == 404
              ? ""
              : readRulerResponse(response, "DELETE namespace " + namespace);
    }
    return body.isEmpty() ? "{\"status\":\"success\"}" : body;
  }

  @Override
  public String deleteRuleGroup(String namespace, String groupName) throws IOException {
    logger.debug(
        "AMP deleteRuleGroup({}, {}) delegating to deleteRuleNamespace under 1:1 invariant",
        namespace,
        groupName);
    return deleteRuleNamespace(namespace);
  }

  /** Returns the success body on 200/202 create, or null when the caller should fall through to PUT. */
  private String tryCreate(String namespace, String base64Data) throws IOException {
    String clientToken = UUID.randomUUID().toString();
    String payload = buildJsonPayload(namespace, base64Data, clientToken);
    String queryUrl =
        String.format(
            "%s/workspaces/%s/rulegroupsnamespaces",
            controlPlaneUri.toString().replaceAll("/$", ""),
            URLEncoder.encode(workspaceId, StandardCharsets.UTF_8));
    logger.debug("AMP POST rulegroupsnamespaces (create) for namespace {}", namespace);
    Request request =
        new Request.Builder()
            .url(queryUrl)
            .header("Content-Type", "application/json")
            .post(RequestBody.create(payload.getBytes(StandardCharsets.UTF_8)))
            .build();
    boolean conflict;
    String body;
    try (Response response =
        AccessController.doPrivilegedChecked(
            () -> controlPlaneHttpClient.newCall(request).execute())) {
      conflict = response.code() == 409;
      body =
          conflict
              ? ""
              : readRulerResponse(response, "POST create rulegroupsnamespace " + namespace);
    }
    if (conflict) {
      logger.debug("AMP namespace {} already exists (409); retrying as PUT", namespace);
      return null;
    }
    return body.isEmpty() ? "{\"status\":\"success\"}" : body;
  }

  private String doPut(String namespace, String base64Data) throws IOException {
    String clientToken = UUID.randomUUID().toString();
    String payload = buildJsonPayload(null, base64Data, clientToken);
    String queryUrl =
        String.format(
            "%s/workspaces/%s/rulegroupsnamespaces/%s",
            controlPlaneUri.toString().replaceAll("/$", ""),
            URLEncoder.encode(workspaceId, StandardCharsets.UTF_8),
            URLEncoder.encode(namespace, StandardCharsets.UTF_8));
    logger.debug("AMP PUT rulegroupsnamespaces (update) for namespace {}", namespace);
    Request request =
        new Request.Builder()
            .url(queryUrl)
            .header("Content-Type", "application/json")
            .put(RequestBody.create(payload.getBytes(StandardCharsets.UTF_8)))
            .build();
    try (Response response =
        AccessController.doPrivilegedChecked(
            () -> controlPlaneHttpClient.newCall(request).execute())) {
      String body = readRulerResponse(response, "PUT update rulegroupsnamespace " + namespace);
      return body.isEmpty() ? "{\"status\":\"success\"}" : body;
    }
  }

  private static String buildJsonPayload(String nameOrNull, String base64Data, String clientToken) {
    JSONObject payload = new JSONObject();
    if (nameOrNull != null) {
      payload.put("name", nameOrNull);
    }
    payload.put("data", base64Data);
    payload.put("clientToken", clientToken);
    return payload.toString();
  }

  private static void validateNamespaceName(String namespace) {
    if (namespace.length() > NAMESPACE_MAX_LENGTH
        || !NAMESPACE_PATTERN.matcher(namespace).matches()) {
      throw new IllegalArgumentException(
          String.format(
              "AMP namespace '%s' is invalid. Allowed: alphanumeric, '-', '.', '_'; max length"
                  + " %d; must begin with an alphanumeric character.",
              namespace, NAMESPACE_MAX_LENGTH));
    }
  }

  /**
   * Parses and validates {@code yamlBody} under the 1:1 invariant, returning the AMP-compatible
   * wrapped form ({@code groups:\n- ...}) ready for base64 encoding.
   */
  @SuppressWarnings("unchecked")
  private static String validateAndNormalizeYaml(String namespace, String yamlBody)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    Object parsed;
    try {
      parsed = YAML_MAPPER.readValue(yamlBody, new TypeReference<Object>() {});
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Invalid YAML for rule group in namespace '" + namespace + "': " + e.getMessage(), e);
    }
    if (!(parsed instanceof Map)) {
      throw new IllegalArgumentException(
          "Rule group YAML for namespace '"
              + namespace
              + "' must be a mapping (either a bare rule group or a 'groups:' wrapper).");
    }
    Map<String, Object> root = (Map<String, Object>) parsed;

    Map<String, Object> singleGroup;
    if (root.containsKey("groups")) {
      List<Object> groups = (List<Object>) root.get("groups");
      if (groups.size() != 1) {
        throw new IllegalArgumentException(
            String.format(
                "AMP datasources require exactly one rule group per namespace (got %d groups"
                    + " in namespace '%s'). Split into separate namespaces if you need multiple"
                    + " groups.",
                groups.size(), namespace));
      }
      singleGroup = (Map<String, Object>) groups.get(0);
    } else {
      singleGroup = root;
    }

    if (!singleGroup.containsKey("name")) {
      throw new IllegalArgumentException(
          "Rule group in namespace '" + namespace + "' is missing required field 'name'.");
    }

    return YAML_MAPPER.writeValueAsString(Map.of("groups", List.of(singleGroup)));
  }

}
