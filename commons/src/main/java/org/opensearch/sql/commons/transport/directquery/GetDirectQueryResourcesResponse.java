/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.commons.transport.directquery;

import java.io.IOException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

/**
 * Response payload for {@link GetDirectQueryResourcesAction}. Wire format: {@code
 * writeString(result);}.
 */
@Getter
@RequiredArgsConstructor
public class GetDirectQueryResourcesResponse extends ActionResponse {
  private final String result;

  public GetDirectQueryResourcesResponse(StreamInput in) throws IOException {
    super(in);
    this.result = in.readString();
  }

  @Override
  public void writeTo(StreamOutput out) throws IOException {
    out.writeString(result);
  }
}
