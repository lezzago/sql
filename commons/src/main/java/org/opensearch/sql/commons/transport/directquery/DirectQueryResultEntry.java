/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.commons.transport.directquery;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Opaque per-data-source entry inside an {@link ExecuteDirectQueryResponse}. Holds the data source
 * type identifier (e.g. {@code "prometheus"}) and the raw JSON-encoded result string. Consumers
 * decide how to parse the JSON; commons does not pull a JSON library at the wire boundary.
 *
 * @opensearch.experimental
 */
@Getter
@AllArgsConstructor
public class DirectQueryResultEntry {
  private final String type;
  private final String json;
}
