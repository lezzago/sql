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
 * Wire DTO for invoking {@link GetDirectQueryResourcesAction}.
 *
 * <p>Wire format: {@code super.writeTo; writeOptionalString(dataSource);
 * writeOptionalString(resourceType.name()); writeOptionalString(resourceName);
 * writeMap(queryParams, String, String);}
 */
@Getter
@Setter
public class GetDirectQueryResourcesRequest extends ActionRequest {
  private String dataSource;
  private DirectQueryResourceType resourceType;
  private String resourceName;
  private Map<String, String> queryParams;

  public GetDirectQueryResourcesRequest() {
    super();
  }

  public GetDirectQueryResourcesRequest(StreamInput in) throws IOException {
    super(in);
    this.dataSource = in.readOptionalString();
    String resourceTypeStr = in.readOptionalString();
    this.resourceType =
        resourceTypeStr == null ? null : DirectQueryResourceType.fromString(resourceTypeStr);
    this.resourceName = in.readOptionalString();
    this.queryParams = in.readMap(StreamInput::readString, StreamInput::readString);
  }

  public void setResourceTypeFromString(String resourceTypeStr) {
    if (resourceTypeStr != null) {
      this.resourceType = DirectQueryResourceType.fromString(resourceTypeStr);
    }
  }

  @Override
  public void writeTo(StreamOutput out) throws IOException {
    super.writeTo(out);
    out.writeOptionalString(dataSource);
    out.writeOptionalString(resourceType == null ? null : resourceType.name());
    out.writeOptionalString(resourceName);
    out.writeMap(
        queryParams == null ? Map.of() : queryParams,
        StreamOutput::writeString,
        StreamOutput::writeString);
  }

  @Override
  public ActionRequestValidationException validate() {
    return null;
  }
}
