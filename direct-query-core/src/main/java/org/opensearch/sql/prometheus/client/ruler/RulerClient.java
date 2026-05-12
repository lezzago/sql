/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.prometheus.client.ruler;

import java.io.IOException;
import java.util.Map;
import org.json.JSONObject;

/**
 * Abstraction over the ruler API surface that {@link
 * org.opensearch.sql.prometheus.client.PrometheusClient} exposes. The Cortex/Thanos-compatible
 * implementation issues native Prometheus ruler calls; the AMP implementation routes writes to
 * AWS Managed Prometheus's control plane.
 *
 * <p>Implementations own the full wire contract (reads + writes) for the ruler endpoint so the
 * read / write split never leaks across backends.
 */
public interface RulerClient {

  /**
   * Get all recording and alerting rules, normalized to {"groups":[...]}.
   */
  JSONObject getRules(Map<String, String> queryParams) throws IOException;

  /**
   * Get rules for a specific namespace, normalized to {"groups":[...]}.
   */
  JSONObject getRulesByNamespace(String namespace, Map<String, String> queryParams)
      throws IOException;

  /**
   * Create or update the rule group in the given namespace. For AMP, the body must contain exactly
   * one rule group (the 1:1 invariant).
   */
  String createOrUpdateRuleGroup(String namespace, String yamlBody) throws IOException;

  /**
   * Delete all rules in a namespace.
   */
  String deleteRuleNamespace(String namespace) throws IOException;

  /**
   * Delete a specific rule group in a namespace. On AMP this delegates to namespace delete because
   * AMP has no per-group API.
   */
  String deleteRuleGroup(String namespace, String groupName) throws IOException;
}
