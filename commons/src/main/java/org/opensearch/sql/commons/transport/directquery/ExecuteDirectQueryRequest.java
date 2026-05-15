/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.commons.transport.directquery;

import java.io.IOException;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

/**
 * Wire DTO for invoking {@link ExecuteDirectQueryAction}. Holds primitive fields plus a
 * source-specific {@code options} map so commons can carry direct-query payloads without depending
 * on Prometheus or async-query domain types.
 *
 * <p>Wire format: {@code super.writeTo; writeOptionalString(dataSources);
 * writeOptionalString(query); writeOptionalString(language); writeOptionalString(sourceVersion);
 * writeOptionalInt(maxResults); writeOptionalInt(timeout); writeOptionalString(sessionId);
 * writeMap(options, String, String);}
 */
@Getter
@Setter
public class ExecuteDirectQueryRequest extends ActionRequest {

  private String dataSources;
  private String query;
  private String language;
  private String sourceVersion;

  private Integer maxResults;
  private Integer timeout;
  private String sessionId;

  private Map<String, String> options;

  public ExecuteDirectQueryRequest() {
    super();
  }

  public ExecuteDirectQueryRequest(StreamInput in) throws IOException {
    super(in);
    this.dataSources = in.readOptionalString();
    this.query = in.readOptionalString();
    this.language = in.readOptionalString();
    this.sourceVersion = in.readOptionalString();
    this.maxResults = in.readOptionalInt();
    this.timeout = in.readOptionalInt();
    this.sessionId = in.readOptionalString();
    this.options = in.readMap(StreamInput::readString, StreamInput::readString);
  }

  @Override
  public void writeTo(StreamOutput out) throws IOException {
    super.writeTo(out);
    out.writeOptionalString(dataSources);
    out.writeOptionalString(query);
    out.writeOptionalString(language);
    out.writeOptionalString(sourceVersion);
    out.writeOptionalInt(maxResults);
    out.writeOptionalInt(timeout);
    out.writeOptionalString(sessionId);
    out.writeMap(
        options == null ? Map.of() : options, StreamOutput::writeString, StreamOutput::writeString);
  }

  @Override
  public ActionRequestValidationException validate() {
    return null;
  }
}
