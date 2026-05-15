/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.commons.transport.ppl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;

public class PPLQueryRequestTest {

  @Test
  public void roundTripAllFields() throws IOException {
    PPLQueryRequest original =
        new PPLQueryRequest("source=t | head 5", "{\"query\":\"x\"}", "/_plugins/_ppl", "jdbc");
    original.sanitize(false);
    original.style(Style.PRETTY);
    original.profile(true);
    original.queryId("abc-123");

    PPLQueryRequest restored = serializeAndRead(original);

    assertEquals("source=t | head 5", restored.getRequest());
    assertEquals("{\"query\":\"x\"}", restored.getJsonContentRaw());
    assertEquals("/_plugins/_ppl", restored.getPath());
    assertEquals("jdbc", restored.getFormat());
    assertFalse(restored.sanitize());
    assertEquals(Style.PRETTY, restored.style());
    assertTrue(restored.profile());
    assertEquals("abc-123", restored.queryId());
  }

  @Test
  public void roundTripWithNullsUsesDefaults() throws IOException {
    PPLQueryRequest original = new PPLQueryRequest(null, null, null);
    PPLQueryRequest restored = serializeAndRead(original);

    assertNull(restored.getRequest());
    assertNull(restored.getJsonContentRaw());
    assertNull(restored.getPath());
    assertEquals("", restored.getFormat());
    assertTrue(restored.sanitize());
    assertEquals(Style.COMPACT, restored.style());
    assertFalse(restored.profile());
    assertNull(restored.queryId());
  }

  @Test
  public void styleEnumOrdinalsMatchProtocolStyle() {
    // Wire compatibility: must match
    // org.opensearch.sql.protocol.response.format.JsonResponseFormatter.Style
    assertEquals(0, Style.PRETTY.ordinal());
    assertEquals(1, Style.COMPACT.ordinal());
  }

  @Test
  public void isExplainRequestDetectsPath() {
    assertTrue(new PPLQueryRequest("q", null, "/_plugins/_ppl/_explain").isExplainRequest());
    assertFalse(new PPLQueryRequest("q", null, "/_plugins/_ppl").isExplainRequest());
  }

  @Test
  public void isGrammarRequestDetectsPath() {
    assertTrue(new PPLQueryRequest("q", null, "/_plugins/_ppl/_grammar").isGrammarRequest());
    assertFalse(new PPLQueryRequest("q", null, "/_plugins/_ppl").isGrammarRequest());
  }

  @Test
  public void fromActionRequestReturnsSameInstanceWhenAlreadyTyped() {
    PPLQueryRequest req = new PPLQueryRequest("q", null, "/p");
    assertSame(req, PPLQueryRequest.fromActionRequest(req));
  }

  @Test
  public void getDescriptionIncludesQueryId() {
    PPLQueryRequest req = new PPLQueryRequest("source=t", null, "/p");
    req.queryId("Q1");
    assertEquals("PPL [queryId=Q1]: source=t", req.getDescription());
  }

  @Test
  public void getDescriptionWithoutQueryIdHasPlainPrefix() {
    PPLQueryRequest req = new PPLQueryRequest("source=t", null, "/p");
    assertEquals("PPL: source=t", req.getDescription());
  }

  private static PPLQueryRequest serializeAndRead(PPLQueryRequest req) throws IOException {
    try (BytesStreamOutput out = new BytesStreamOutput()) {
      req.writeTo(out);
      try (StreamInput in = out.bytes().streamInput()) {
        return new PPLQueryRequest(in);
      }
    }
  }
}
