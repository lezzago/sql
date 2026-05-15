/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.commons.transport.ppl;

/**
 * JSON response format style. Wire-compatible with {@code
 * org.opensearch.sql.protocol.response.format.JsonResponseFormatter.Style} — ordinal layout {@code
 * PRETTY=0, COMPACT=1} must be preserved for cross-version StreamInput/StreamOutput compatibility.
 */
public enum Style {
  PRETTY,
  COMPACT
}
