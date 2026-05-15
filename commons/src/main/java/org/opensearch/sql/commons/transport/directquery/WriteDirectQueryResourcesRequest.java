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
 * Wire DTO for invoking {@link WriteDirectQueryResourcesAction}.
 *
 * <p>Wire format: {@code writeOptionalString(dataSource); writeOptionalString(resourceType.name());
 * writeOptionalString(resourceName); writeOptionalString(request); writeMap(requestOptions, String,
 * String); writeOptionalString(groupName); writeOptionalBoolean(delete);}
 *
 * <p>Note: parent {@code super(in)} / {@code super.writeTo} are intentionally skipped on both
 * sides to preserve byte-compatibility with the prior {@code WriteDirectQueryResourcesActionRequest}
 * which also skipped both. {@link GetDirectQueryResourcesRequest} calls them on both sides.
 */
@Getter
@Setter
public class WriteDirectQueryResourcesRequest extends ActionRequest {
  private String dataSource;
  private DirectQueryResourceType resourceType;
  private String resourceName;
  private String request;
  private Map<String, String> requestOptions;
  private String groupName;
  private boolean delete;

  public WriteDirectQueryResourcesRequest() {
    super();
  }

  public WriteDirectQueryResourcesRequest(StreamInput in) throws IOException {
    // Intentionally not calling super(in) — see class Javadoc.
    this.dataSource = in.readOptionalString();
    String resourceTypeStr = in.readOptionalString();
    this.resourceType =
        resourceTypeStr == null ? null : DirectQueryResourceType.fromString(resourceTypeStr);
    this.resourceName = in.readOptionalString();
    this.request = in.readOptionalString();
    this.requestOptions = in.readMap(StreamInput::readString, StreamInput::readString);
    this.groupName = in.readOptionalString();
    Boolean deleteValue = in.readOptionalBoolean();
    this.delete = deleteValue != null && deleteValue;
  }

  public void setResourceTypeFromString(String resourceTypeStr) {
    if (resourceTypeStr != null) {
      this.resourceType = DirectQueryResourceType.fromString(resourceTypeStr);
    }
  }

  @Override
  public void writeTo(StreamOutput out) throws IOException {
    out.writeOptionalString(dataSource);
    out.writeOptionalString(resourceType == null ? null : resourceType.name());
    out.writeOptionalString(resourceName);
    out.writeOptionalString(request);
    out.writeMap(
        requestOptions == null ? Map.of() : requestOptions,
        StreamOutput::writeString,
        StreamOutput::writeString);
    out.writeOptionalString(groupName);
    out.writeOptionalBoolean(delete);
  }

  @Override
  public ActionRequestValidationException validate() {
    return null;
  }
}
