/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.commons.transport.directquery;

/**
 * Resource type for direct query resource read/write operations.
 *
 * @opensearch.experimental
 */
public enum DirectQueryResourceType {
  UNKNOWN,
  LABELS,
  LABEL,
  METADATA,
  SERIES,
  ALERTS,
  RULES,
  ALERTMANAGER_ALERTS,
  ALERTMANAGER_ALERT_GROUPS,
  ALERTMANAGER_RECEIVERS,
  ALERTMANAGER_SILENCES,
  ALERTMANAGER_STATUS;

  /** Convert a string to the corresponding enum value, case-insensitive. */
  public static DirectQueryResourceType fromString(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Resource type cannot be null");
    }
    try {
      return valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid resource type: " + value);
    }
  }
}
