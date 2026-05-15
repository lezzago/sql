/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.commons.transport.ppl;

import java.io.IOException;
import lombok.Getter;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

/**
 * Response payload for {@link PPLQueryAction}. Wire format: {@code writeString(result);
 * writeString(contentType);}.
 */
public class PPLQueryResponse extends ActionResponse {
  public static final String DEFAULT_CONTENT_TYPE = "application/json; charset=UTF-8";

  @Getter private final String result;
  @Getter private final String contentType;

  public PPLQueryResponse(String result) {
    this(result, DEFAULT_CONTENT_TYPE);
  }

  public PPLQueryResponse(String result, String contentType) {
    this.result = result;
    this.contentType = contentType;
  }

  public PPLQueryResponse(StreamInput in) throws IOException {
    super(in);
    this.result = in.readString();
    this.contentType = in.readString();
  }

  @Override
  public void writeTo(StreamOutput out) throws IOException {
    out.writeString(result);
    out.writeString(contentType);
  }
}
