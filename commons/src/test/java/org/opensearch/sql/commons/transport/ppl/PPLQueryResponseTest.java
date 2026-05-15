/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.commons.transport.ppl;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;

public class PPLQueryResponseTest {

  @Test
  public void roundTripPreservesResultAndContentType() throws IOException {
    PPLQueryResponse original = new PPLQueryResponse("{\"a\":1}", "application/json");
    try (BytesStreamOutput out = new BytesStreamOutput()) {
      original.writeTo(out);
      try (StreamInput in = out.bytes().streamInput()) {
        PPLQueryResponse restored = new PPLQueryResponse(in);
        assertEquals("{\"a\":1}", restored.getResult());
        assertEquals("application/json", restored.getContentType());
      }
    }
  }

  @Test
  public void singleArgConstructorUsesDefaultContentType() {
    PPLQueryResponse response = new PPLQueryResponse("{}");
    assertEquals(PPLQueryResponse.DEFAULT_CONTENT_TYPE, response.getContentType());
  }
}
