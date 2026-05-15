/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.commons.transport.ppl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.tasks.TaskId;

/**
 * Wire DTO for invoking {@link PPLQueryAction}. Holds primitive fields only — JSON request bodies
 * travel as raw strings via {@link #getJsonContentRaw()} so consumers do not need to depend on a
 * particular JSON parser.
 *
 * <p>Wire format (must remain byte-compatible across versions): {@code super.writeTo;
 * writeOptionalString(pplQuery); writeOptionalString(format); writeOptionalString(explainMode);
 * writeOptionalString(jsonContentRaw); writeOptionalString(path); writeBoolean(sanitize);
 * writeEnum(style); writeBoolean(profile); writeOptionalString(queryId);}
 */
public class PPLQueryRequest extends ActionRequest {
  public static final PPLQueryRequest NULL = new PPLQueryRequest("", null, "");

  @Getter private final String pplQuery;
  @Getter private final String jsonContentRaw;
  @Getter private final String path;

  @Getter private String format = "";
  @Getter private String explainMode;

  @Setter
  @Getter
  @Accessors(fluent = true)
  private boolean sanitize = true;

  @Setter
  @Getter
  @Accessors(fluent = true)
  private Style style = Style.COMPACT;

  @Setter
  @Getter
  @Accessors(fluent = true)
  private boolean profile = false;

  @Setter
  @Getter
  @Accessors(fluent = true)
  private String queryId = null;

  public PPLQueryRequest(String pplQuery, String jsonContentRaw, String path) {
    this.pplQuery = pplQuery;
    this.jsonContentRaw = jsonContentRaw;
    this.path = path;
  }

  public PPLQueryRequest(String pplQuery, String jsonContentRaw, String path, String format) {
    this(pplQuery, jsonContentRaw, path);
    this.format = format;
  }

  public PPLQueryRequest(
      String pplQuery, String jsonContentRaw, String path, String format, String explainMode) {
    this(pplQuery, jsonContentRaw, path, format);
    this.explainMode = explainMode;
  }

  public PPLQueryRequest(
      String pplQuery,
      String jsonContentRaw,
      String path,
      String format,
      String explainMode,
      boolean profile) {
    this(pplQuery, jsonContentRaw, path, format, explainMode);
    this.profile = profile;
  }

  public PPLQueryRequest(StreamInput in) throws IOException {
    super(in);
    this.pplQuery = in.readOptionalString();
    this.format = in.readOptionalString();
    this.explainMode = in.readOptionalString();
    this.jsonContentRaw = in.readOptionalString();
    this.path = in.readOptionalString();
    this.sanitize = in.readBoolean();
    this.style = in.readEnum(Style.class);
    this.profile = in.readBoolean();
    this.queryId = in.readOptionalString();
  }

  /** Re-create the object from the actionRequest. */
  public static PPLQueryRequest fromActionRequest(final ActionRequest actionRequest) {
    if (actionRequest instanceof PPLQueryRequest) {
      return (PPLQueryRequest) actionRequest;
    }

    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
      actionRequest.writeTo(osso);
      try (InputStreamStreamInput input =
          new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
        return new PPLQueryRequest(input);
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("failed to parse ActionRequest into PPLQueryRequest", e);
    }
  }

  @Override
  public void writeTo(StreamOutput out) throws IOException {
    super.writeTo(out);
    out.writeOptionalString(pplQuery);
    out.writeOptionalString(format);
    out.writeOptionalString(explainMode);
    out.writeOptionalString(jsonContentRaw);
    out.writeOptionalString(path);
    out.writeBoolean(sanitize);
    out.writeEnum(style);
    out.writeBoolean(profile);
    out.writeOptionalString(queryId);
  }

  /** The raw PPL query string. */
  public String getRequest() {
    return pplQuery;
  }

  /** Whether the request targets the {@code /_explain} endpoint. */
  public boolean isExplainRequest() {
    return path != null && path.endsWith("/_explain");
  }

  /** Whether the request targets the {@code /_grammar} metadata endpoint. */
  public boolean isGrammarRequest() {
    return path != null && path.endsWith("/_grammar");
  }

  @Override
  public ActionRequestValidationException validate() {
    return null;
  }

  @Override
  public PPLQueryTask createTask(
      long id, String type, String action, TaskId parentTaskId, Map<String, String> headers) {
    return new PPLQueryTask(id, type, action, getDescription(), parentTaskId, headers);
  }

  @Override
  public String getDescription() {
    String prefix = (queryId != null) ? "PPL [queryId=" + queryId + "]: " : "PPL: ";
    return prefix + pplQuery;
  }
}
