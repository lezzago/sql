/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.directquery.transport.format;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.opensearch.sql.commons.transport.directquery.DirectQueryResultEntry;
import org.opensearch.sql.commons.transport.directquery.ExecuteDirectQueryRequest;
import org.opensearch.sql.commons.transport.directquery.ExecuteDirectQueryResponse;
import org.opensearch.sql.commons.transport.directquery.GetDirectQueryResourcesRequest;
import org.opensearch.sql.commons.transport.directquery.WriteDirectQueryResourcesRequest;
import org.opensearch.sql.directquery.rest.model.DirectQueryResourceType;
import org.opensearch.sql.prometheus.model.PrometheusOptions;
import org.opensearch.sql.spark.rest.model.LangType;

/** Bridge between commons wire DTOs and the rich {@code :direct-query-core} domain POJOs. */
@UtilityClass
public class DirectQueryCommonsConverter {

  private static final String OPT_QUERY_TYPE = "queryType";
  private static final String OPT_STEP = "step";
  private static final String OPT_TIME = "time";
  private static final String OPT_START = "start";
  private static final String OPT_END = "end";

  /** commons {@code ExecuteDirectQueryRequest} → rich legacy {@code ExecuteDirectQueryRequest}. */
  public static org.opensearch.sql.directquery.rest.model.ExecuteDirectQueryRequest toLegacy(
      ExecuteDirectQueryRequest commons) {
    org.opensearch.sql.directquery.rest.model.ExecuteDirectQueryRequest legacy =
        new org.opensearch.sql.directquery.rest.model.ExecuteDirectQueryRequest();
    legacy.setDataSources(commons.getDataSources());
    legacy.setQuery(commons.getQuery());
    if (commons.getLanguage() != null) {
      legacy.setLanguage(LangType.fromString(commons.getLanguage()));
    }
    legacy.setSourceVersion(commons.getSourceVersion());
    legacy.setMaxResults(commons.getMaxResults());
    legacy.setTimeout(commons.getTimeout());
    legacy.setSessionId(commons.getSessionId());
    if (commons.getOptions() != null && legacy.getLanguage() == LangType.PROMQL) {
      legacy.setPrometheusOptions(toPrometheusOptions(commons.getOptions()));
    }
    return legacy;
  }

  /** Rich legacy {@code ExecuteDirectQueryRequest} → commons {@code ExecuteDirectQueryRequest}. */
  public static ExecuteDirectQueryRequest fromLegacy(
      org.opensearch.sql.directquery.rest.model.ExecuteDirectQueryRequest legacy) {
    ExecuteDirectQueryRequest commons = new ExecuteDirectQueryRequest();
    commons.setDataSources(legacy.getDataSources());
    commons.setQuery(legacy.getQuery());
    if (legacy.getLanguage() != null) {
      commons.setLanguage(legacy.getLanguage().getText());
    }
    commons.setSourceVersion(legacy.getSourceVersion());
    commons.setMaxResults(legacy.getMaxResults());
    commons.setTimeout(legacy.getTimeout());
    commons.setSessionId(legacy.getSessionId());
    if (legacy.getLanguage() == LangType.PROMQL) {
      commons.setOptions(fromPrometheusOptions(legacy.getPrometheusOptions()));
    }
    return commons;
  }

  /** commons {@code GetDirectQueryResourcesRequest} → legacy. */
  public static org.opensearch.sql.directquery.rest.model.GetDirectQueryResourcesRequest toLegacy(
      GetDirectQueryResourcesRequest commons) {
    org.opensearch.sql.directquery.rest.model.GetDirectQueryResourcesRequest legacy =
        new org.opensearch.sql.directquery.rest.model.GetDirectQueryResourcesRequest();
    legacy.setDataSource(commons.getDataSource());
    if (commons.getResourceType() != null) {
      legacy.setResourceType(DirectQueryResourceType.valueOf(commons.getResourceType().name()));
    }
    legacy.setResourceName(commons.getResourceName());
    legacy.setQueryParams(commons.getQueryParams());
    return legacy;
  }

  /** commons {@code WriteDirectQueryResourcesRequest} → legacy. */
  public static org.opensearch.sql.directquery.rest.model.WriteDirectQueryResourcesRequest toLegacy(
      WriteDirectQueryResourcesRequest commons) {
    org.opensearch.sql.directquery.rest.model.WriteDirectQueryResourcesRequest legacy =
        new org.opensearch.sql.directquery.rest.model.WriteDirectQueryResourcesRequest();
    legacy.setDataSource(commons.getDataSource());
    if (commons.getResourceType() != null) {
      legacy.setResourceType(DirectQueryResourceType.valueOf(commons.getResourceType().name()));
    }
    legacy.setResourceName(commons.getResourceName());
    legacy.setRequest(commons.getRequest());
    legacy.setRequestOptions(commons.getRequestOptions());
    legacy.setGroupName(commons.getGroupName());
    legacy.setDelete(commons.isDelete());
    return legacy;
  }

  /** Build a commons {@link ExecuteDirectQueryResponse} from a single executor result. */
  public static ExecuteDirectQueryResponse toCommonsResponse(
      String queryId, String result, String sessionId, String dataSourceName, String dataSourceType) {
    Map<String, DirectQueryResultEntry> entries = new HashMap<>();
    if (dataSourceName != null && result != null) {
      entries.put(dataSourceName, new DirectQueryResultEntry(dataSourceType, result));
    }
    return new ExecuteDirectQueryResponse(queryId, entries, sessionId);
  }

  private static PrometheusOptions toPrometheusOptions(Map<String, String> opts) {
    PrometheusOptions p = new PrometheusOptions();
    String queryType = opts.get(OPT_QUERY_TYPE);
    if (queryType != null) {
      p.setQueryType(
          org.opensearch.sql.prometheus.model.PrometheusQueryType.fromString(queryType));
    }
    p.setStep(opts.get(OPT_STEP));
    p.setTime(opts.get(OPT_TIME));
    p.setStart(opts.get(OPT_START));
    p.setEnd(opts.get(OPT_END));
    return p;
  }

  private static Map<String, String> fromPrometheusOptions(PrometheusOptions p) {
    Map<String, String> opts = new HashMap<>();
    if (p == null) {
      return opts;
    }
    if (p.getQueryType() != null) {
      opts.put(OPT_QUERY_TYPE, p.getQueryType().getValue());
    }
    if (p.getStep() != null) {
      opts.put(OPT_STEP, p.getStep());
    }
    if (p.getTime() != null) {
      opts.put(OPT_TIME, p.getTime());
    }
    if (p.getStart() != null) {
      opts.put(OPT_START, p.getStart());
    }
    if (p.getEnd() != null) {
      opts.put(OPT_END, p.getEnd());
    }
    return opts;
  }
}
