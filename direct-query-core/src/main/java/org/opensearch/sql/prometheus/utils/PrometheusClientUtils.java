/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.prometheus.utils;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.opensearch.sql.common.interceptors.AwsSigningInterceptor;
import org.opensearch.sql.common.interceptors.BasicAuthenticationInterceptor;
import org.opensearch.sql.common.interceptors.URIValidatorInterceptor;
import org.opensearch.sql.common.setting.Settings;
import org.opensearch.sql.datasource.client.exceptions.DataSourceClientException;
import org.opensearch.sql.datasource.model.DataSourceMetadata;
import org.opensearch.sql.datasources.auth.AuthenticationType;
import org.opensearch.sql.prometheus.client.PrometheusClient;
import org.opensearch.sql.prometheus.client.PrometheusClientImpl;
import org.opensearch.sql.prometheus.client.ruler.AmpRulerClient;
import org.opensearch.sql.prometheus.client.ruler.CortexRulerClient;
import org.opensearch.sql.prometheus.client.ruler.RulerClient;

/*
 * @opensearch.experimental
 */
public class PrometheusClientUtils {
  private PrometheusClientUtils() {}

  // Prometheus auth constants
  public static final String AUTH_TYPE = "prometheus.auth.type";
  public static final String USERNAME = "prometheus.auth.username";
  public static final String PASSWORD = "prometheus.auth.password";
  public static final String REGION = "prometheus.auth.region";
  public static final String ACCESS_KEY = "prometheus.auth.access_key";
  public static final String SECRET_KEY = "prometheus.auth.secret_key";

  // Prometheus URI constants
  public static final String PROMETHEUS_URI = "prometheus.uri";
  public static final String RULER_URI = "prometheus.ruler.uri";

  // Ruler selection constants
  public static final String RULER_TYPE = "prometheus.ruler.type";
  public static final String RULER_WORKSPACE_ID = "prometheus.ruler.workspace_id";
  public static final String RULER_ENDPOINT = "prometheus.ruler.endpoint";
  public static final String RULER_TYPE_CORTEX = "cortex";
  public static final String RULER_TYPE_AMP = "amp";

  // AlertManager constants
  public static final String ALERTMANAGER_URI = "alertmanager.uri";
  public static final String ALERTMANAGER_AUTH_TYPE = "alertmanager.auth.type";
  public static final String ALERTMANAGER_USERNAME = "alertmanager.auth.username";
  public static final String ALERTMANAGER_PASSWORD = "alertmanager.auth.password";
  public static final String ALERTMANAGER_REGION = "alertmanager.auth.region";
  public static final String ALERTMANAGER_ACCESS_KEY = "alertmanager.auth.access_key";
  public static final String ALERTMANAGER_SECRET_KEY = "alertmanager.auth.secret_key";

  public static OkHttpClient getHttpClient(Map<String, String> config, Settings settings) {
    OkHttpClient.Builder okHttpClient = new OkHttpClient.Builder();
    okHttpClient.callTimeout(1, TimeUnit.MINUTES);
    okHttpClient.connectTimeout(30, TimeUnit.SECONDS);
    okHttpClient.followRedirects(false);
    okHttpClient.addInterceptor(
        new URIValidatorInterceptor(
            settings.getSettingValue(Settings.Key.DATASOURCES_URI_HOSTS_DENY_LIST)));
    if (config.get(AUTH_TYPE) != null) {
      AuthenticationType authenticationType =
          AuthenticationType.get(config.get(AUTH_TYPE));
      if (AuthenticationType.BASICAUTH.equals(authenticationType)) {
        okHttpClient.addInterceptor(
            new BasicAuthenticationInterceptor(
                config.get(USERNAME), config.get(PASSWORD)));
      } else if (AuthenticationType.AWSSIGV4AUTH.equals(authenticationType)) {
        okHttpClient.addInterceptor(
            new AwsSigningInterceptor(
                new AWSStaticCredentialsProvider(
                    new BasicAWSCredentials(
                        config.get(ACCESS_KEY), config.get(SECRET_KEY))),
                config.get(REGION),
                "aps"));
      } else {
        throw new IllegalArgumentException(
            String.format(
                "AUTH Type : %s is not supported with Prometheus Connector",
                config.get(AUTH_TYPE)));
      }
    }
    return okHttpClient.build();
  }

  /**
   * Creates a properties map for Alertmanager authentication based on the data source properties.
   *
   * @param properties The data source properties
   * @return A map containing Alertmanager authentication properties
   */
  public static Map<String, String> createAlertmanagerProperties(Map<String, String> properties) {
    Map<String, String> alertmanagerProperties = new HashMap<>();

    if (properties.containsKey(ALERTMANAGER_AUTH_TYPE)) {
      alertmanagerProperties.put(AUTH_TYPE, properties.get(ALERTMANAGER_AUTH_TYPE));

      String authType = properties.get(ALERTMANAGER_AUTH_TYPE);
      if (Objects.nonNull(authType)) {
        if (authType.equalsIgnoreCase("basicauth")) {
          alertmanagerProperties.put(USERNAME, properties.get(ALERTMANAGER_USERNAME));
          alertmanagerProperties.put(PASSWORD, properties.get(ALERTMANAGER_PASSWORD));
        } else if (authType.equalsIgnoreCase("awssigv4auth")) {
          alertmanagerProperties.put(ACCESS_KEY, properties.get(ALERTMANAGER_ACCESS_KEY));
          alertmanagerProperties.put(SECRET_KEY, properties.get(ALERTMANAGER_SECRET_KEY));
          alertmanagerProperties.put(REGION, properties.get(ALERTMANAGER_REGION));
        }
      }
    }

    return alertmanagerProperties;
  }

  /**
   * Checks if AlertManager configuration is present in the properties.
   *
   * @param properties The data source properties
   * @return true if Alertmanager URI is present, false otherwise
   */
  public static boolean hasAlertmanagerConfig(Map<String, String> properties) {
    return Objects.nonNull(properties.get(ALERTMANAGER_URI));
  }

  /**
   * Creates a PrometheusClient instance based on the provided data source metadata and settings. If
   * alertmanager settings are present, it creates an alertmanager client as well. Otherwise, it
   * reuses the prometheus http client for alertmanager calls with URI to be
   * {prometheus.url}/alertmanager.
   *
   * @param metadata The data source metadata
   * @param settings The application settings
   * @return A PrometheusClient instance
   * @throws DataSourceClientException if the host is not provided
   */
  public static PrometheusClient createPrometheusClient(
      DataSourceMetadata metadata, Settings settings) {
    String host = metadata.getProperties().get(PrometheusClientUtils.PROMETHEUS_URI);
    if (Objects.isNull(host)) {
      throw new DataSourceClientException("Host is required for Prometheus data source");
    }
    URI uri = URI.create(host);

    Map<String, String> properties = metadata.getProperties();
    OkHttpClient prometheusHttpClient = PrometheusClientUtils.getHttpClient(properties, settings);

    URI alertmanagerUri;
    OkHttpClient alertmanagerHttpClient = prometheusHttpClient;

    if (PrometheusClientUtils.hasAlertmanagerConfig(properties)) {
      String alertmanagerHost = properties.get(PrometheusClientUtils.ALERTMANAGER_URI);
      alertmanagerUri = URI.create(alertmanagerHost);

      Map<String, String> alertmanagerProperties =
          PrometheusClientUtils.createAlertmanagerProperties(properties);

      if (!alertmanagerProperties.isEmpty()) {
        alertmanagerHttpClient =
            PrometheusClientUtils.getHttpClient(alertmanagerProperties, settings);
      }
    } else {
      alertmanagerUri = URI.create(host.replaceAll("/$", "") + "/alertmanager");
    }

    // Ruler URI defaults to prometheus.uri when not explicitly configured
    String rulerHost = properties.get(PrometheusClientUtils.RULER_URI);
    URI rulerUri = rulerHost != null ? URI.create(rulerHost) : uri;

    RulerClient rulerClient =
        buildRulerClient(properties, settings, prometheusHttpClient, rulerUri);

    return new PrometheusClientImpl(
        prometheusHttpClient, uri, alertmanagerHttpClient, alertmanagerUri, rulerClient);
  }

  private static RulerClient buildRulerClient(
      Map<String, String> properties,
      Settings settings,
      OkHttpClient prometheusHttpClient,
      URI rulerUri) {
    String rulerType = properties.get(RULER_TYPE);
    if (rulerType == null || RULER_TYPE_CORTEX.equalsIgnoreCase(rulerType)) {
      return new CortexRulerClient(prometheusHttpClient, rulerUri);
    }
    if (!RULER_TYPE_AMP.equalsIgnoreCase(rulerType)) {
      throw new IllegalArgumentException(
          String.format(
              "Unsupported %s value '%s'. Supported values: %s, %s.",
              RULER_TYPE, rulerType, RULER_TYPE_CORTEX, RULER_TYPE_AMP));
    }
    String workspaceId = properties.get(RULER_WORKSPACE_ID);
    String controlPlaneEndpoint = properties.get(RULER_ENDPOINT);
    if (workspaceId == null || controlPlaneEndpoint == null) {
      throw new IllegalArgumentException(
          String.format(
              "%s=amp requires both %s and %s to be set.",
              RULER_TYPE, RULER_WORKSPACE_ID, RULER_ENDPOINT));
    }
    URI controlPlaneUri = URI.create(controlPlaneEndpoint);
    OkHttpClient controlPlaneHttpClient = getHttpClient(properties, settings);
    RulerClient readDelegate = new CortexRulerClient(prometheusHttpClient, rulerUri);
    return new AmpRulerClient(controlPlaneHttpClient, controlPlaneUri, workspaceId, readDelegate);
  }
}
