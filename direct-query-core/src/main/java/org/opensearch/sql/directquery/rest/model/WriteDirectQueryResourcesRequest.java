/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.directquery.rest.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
//TODO: @ashisagr add a WriteDQRRequest
public class WriteDirectQueryResourcesRequest {
  private String dataSource;
  private DirectQueryResourceType resourceType;
  private String resourceName;
  private String request;

  // Optional fields
  private Map<String, String> requestOptions;

  /**
   * Sets the resource type from a string value.
   *
   * @param resourceTypeStr The resource type as a string
   */
  public void setResourceTypeFromString(String resourceTypeStr) {
    if (resourceTypeStr != null) {
      this.resourceType = DirectQueryResourceType.fromString(resourceTypeStr);
    }
  }
}
