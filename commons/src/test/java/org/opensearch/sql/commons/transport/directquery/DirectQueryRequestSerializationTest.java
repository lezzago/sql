/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.commons.transport.directquery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;

public class DirectQueryRequestSerializationTest {

  @Test
  public void executeRequestRoundTripAllFields() throws IOException {
    ExecuteDirectQueryRequest req = new ExecuteDirectQueryRequest();
    req.setDataSources("ds1");
    req.setQuery("up");
    req.setLanguage("PROMQL");
    req.setSourceVersion("v1");
    req.setMaxResults(100);
    req.setTimeout(30);
    req.setSessionId("sess-1");
    Map<String, String> opts = new HashMap<>();
    opts.put("step", "60s");
    req.setOptions(opts);

    ExecuteDirectQueryRequest restored = roundTrip(req);

    assertEquals("ds1", restored.getDataSources());
    assertEquals("up", restored.getQuery());
    assertEquals("PROMQL", restored.getLanguage());
    assertEquals("v1", restored.getSourceVersion());
    assertEquals(Integer.valueOf(100), restored.getMaxResults());
    assertEquals(Integer.valueOf(30), restored.getTimeout());
    assertEquals("sess-1", restored.getSessionId());
    assertEquals(opts, restored.getOptions());
  }

  @Test
  public void executeRequestRoundTripWithNulls() throws IOException {
    ExecuteDirectQueryRequest req = new ExecuteDirectQueryRequest();
    ExecuteDirectQueryRequest restored = roundTrip(req);

    assertNull(restored.getDataSources());
    assertNull(restored.getQuery());
    assertNull(restored.getLanguage());
    assertNull(restored.getSourceVersion());
    assertNull(restored.getMaxResults());
    assertNull(restored.getTimeout());
    assertNull(restored.getSessionId());
    assertTrue(restored.getOptions().isEmpty());
  }

  @Test
  public void getResourcesRequestRoundTrip() throws IOException {
    GetDirectQueryResourcesRequest req = new GetDirectQueryResourcesRequest();
    req.setDataSource("prom");
    req.setResourceType(DirectQueryResourceType.LABELS);
    req.setResourceName("__name__");
    Map<String, String> params = new HashMap<>();
    params.put("match[]", "up");
    req.setQueryParams(params);

    GetDirectQueryResourcesRequest restored = roundTripGet(req);

    assertEquals("prom", restored.getDataSource());
    assertEquals(DirectQueryResourceType.LABELS, restored.getResourceType());
    assertEquals("__name__", restored.getResourceName());
    assertEquals(params, restored.getQueryParams());
  }

  @Test
  public void writeResourcesRequestRoundTrip() throws IOException {
    WriteDirectQueryResourcesRequest req = new WriteDirectQueryResourcesRequest();
    req.setDataSource("prom");
    req.setResourceType(DirectQueryResourceType.RULES);
    req.setResourceName("alert.yml");
    req.setRequest("{}");
    req.setRequestOptions(Map.of("a", "b"));
    req.setGroupName("g1");
    req.setDelete(true);

    WriteDirectQueryResourcesRequest restored = roundTripWrite(req);

    assertEquals("prom", restored.getDataSource());
    assertEquals(DirectQueryResourceType.RULES, restored.getResourceType());
    assertEquals("alert.yml", restored.getResourceName());
    assertEquals("{}", restored.getRequest());
    assertEquals(Map.of("a", "b"), restored.getRequestOptions());
    assertEquals("g1", restored.getGroupName());
    assertTrue(restored.isDelete());
  }

  @Test
  public void writeResourcesRequestNullDeleteDefaultsFalse() throws IOException {
    WriteDirectQueryResourcesRequest req = new WriteDirectQueryResourcesRequest();
    WriteDirectQueryResourcesRequest restored = roundTripWrite(req);
    assertFalse(restored.isDelete());
  }

  @Test
  public void executeResponseRoundTrip() throws IOException {
    Map<String, DirectQueryResultEntry> entries = new HashMap<>();
    entries.put("ds1", new DirectQueryResultEntry("prometheus", "{\"status\":\"success\"}"));
    ExecuteDirectQueryResponse resp = new ExecuteDirectQueryResponse("q1", entries, "sess");

    try (BytesStreamOutput out = new BytesStreamOutput()) {
      resp.writeTo(out);
      try (StreamInput in = out.bytes().streamInput()) {
        ExecuteDirectQueryResponse restored = new ExecuteDirectQueryResponse(in);
        assertEquals("q1", restored.getQueryId());
        assertEquals("sess", restored.getSessionId());
        assertEquals(1, restored.getResults().size());
        DirectQueryResultEntry entry = restored.getResults().get("ds1");
        assertEquals("prometheus", entry.getType());
        assertEquals("{\"status\":\"success\"}", entry.getJson());
      }
    }
  }

  @Test
  public void getResourcesResponseRoundTrip() throws IOException {
    GetDirectQueryResourcesResponse resp = new GetDirectQueryResourcesResponse("[\"a\",\"b\"]");
    try (BytesStreamOutput out = new BytesStreamOutput()) {
      resp.writeTo(out);
      try (StreamInput in = out.bytes().streamInput()) {
        assertEquals("[\"a\",\"b\"]", new GetDirectQueryResourcesResponse(in).getResult());
      }
    }
  }

  @Test
  public void writeResourcesResponseRoundTrip() throws IOException {
    WriteDirectQueryResourcesResponse resp = new WriteDirectQueryResourcesResponse("ok");
    try (BytesStreamOutput out = new BytesStreamOutput()) {
      resp.writeTo(out);
      try (StreamInput in = out.bytes().streamInput()) {
        assertEquals("ok", new WriteDirectQueryResourcesResponse(in).getResult());
      }
    }
  }

  @Test
  public void resourceTypeFromStringIsCaseInsensitive() {
    assertEquals(DirectQueryResourceType.LABELS, DirectQueryResourceType.fromString("labels"));
    assertEquals(DirectQueryResourceType.LABELS, DirectQueryResourceType.fromString("LABELS"));
    assertEquals(DirectQueryResourceType.LABELS, DirectQueryResourceType.fromString("Labels"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void resourceTypeFromStringRejectsNull() {
    DirectQueryResourceType.fromString(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void resourceTypeFromStringRejectsUnknown() {
    DirectQueryResourceType.fromString("not-a-real-type");
  }

  private static ExecuteDirectQueryRequest roundTrip(ExecuteDirectQueryRequest req)
      throws IOException {
    try (BytesStreamOutput out = new BytesStreamOutput()) {
      req.writeTo(out);
      try (StreamInput in = out.bytes().streamInput()) {
        return new ExecuteDirectQueryRequest(in);
      }
    }
  }

  private static GetDirectQueryResourcesRequest roundTripGet(GetDirectQueryResourcesRequest req)
      throws IOException {
    try (BytesStreamOutput out = new BytesStreamOutput()) {
      req.writeTo(out);
      try (StreamInput in = out.bytes().streamInput()) {
        return new GetDirectQueryResourcesRequest(in);
      }
    }
  }

  private static WriteDirectQueryResourcesRequest roundTripWrite(
      WriteDirectQueryResourcesRequest req) throws IOException {
    try (BytesStreamOutput out = new BytesStreamOutput()) {
      req.writeTo(out);
      try (StreamInput in = out.bytes().streamInput()) {
        return new WriteDirectQueryResourcesRequest(in);
      }
    }
  }
}
