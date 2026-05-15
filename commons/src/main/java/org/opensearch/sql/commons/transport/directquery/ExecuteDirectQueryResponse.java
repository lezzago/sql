/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.commons.transport.directquery;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

/**
 * Response payload for {@link ExecuteDirectQueryAction}. Carries opaque per-data-source result
 * entries — Jackson-typed decoding is left to consumers.
 *
 * <p>Wire format: {@code writeString(queryId); writeInt(size); for each entry:
 * writeString(dataSourceId), writeString(type), writeString(json); writeOptionalString(sessionId);}
 */
@Getter
public class ExecuteDirectQueryResponse extends ActionResponse {
  private final String queryId;
  private final Map<String, DirectQueryResultEntry> results;
  private final String sessionId;

  public ExecuteDirectQueryResponse(
      String queryId, Map<String, DirectQueryResultEntry> results, String sessionId) {
    this.queryId = queryId;
    this.results = results == null ? new HashMap<>() : results;
    this.sessionId = sessionId;
  }

  public ExecuteDirectQueryResponse(StreamInput in) throws IOException {
    super(in);
    this.queryId = in.readString();
    int resultCount = in.readInt();
    Map<String, DirectQueryResultEntry> entries = new HashMap<>(resultCount);
    for (int i = 0; i < resultCount; i++) {
      String dataSourceId = in.readString();
      String type = in.readString();
      String json = in.readString();
      entries.put(dataSourceId, new DirectQueryResultEntry(type, json));
    }
    this.results = entries;
    this.sessionId = in.readOptionalString();
  }

  @Override
  public void writeTo(StreamOutput out) throws IOException {
    out.writeString(queryId);
    out.writeInt(results.size());
    for (Map.Entry<String, DirectQueryResultEntry> e : results.entrySet()) {
      out.writeString(e.getKey());
      out.writeString(e.getValue().getType());
      out.writeString(e.getValue().getJson());
    }
    out.writeOptionalString(sessionId);
  }
}
