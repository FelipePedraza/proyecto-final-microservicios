require "date"
require "minitest/autorun"
require "yaml"

class ObservabilityConfigTest < Minitest::Test
  ROOT = File.expand_path("../..", __dir__)

  def yaml(path)
    YAML.safe_load(
      File.read(File.join(ROOT, path)),
      permitted_classes: [Date],
      aliases: true
    )
  end

  def test_loki_datasource_is_provisioned_with_zipkin_link
    config = yaml("observability/grafana/provisioning/datasources/datasources.yml")
    loki = config.fetch("datasources").find { |source| source["uid"] == "loki" }

    refute_nil loki
    assert_equal "${LOKI_DATASOURCE_URL}", loki["url"]
    assert_equal false, loki["editable"]
    assert_equal true, config["prune"]

    field = loki.dig("jsonData", "derivedFields").first
    assert_equal "TraceID", field["name"]
    assert_includes field["matcherRegex"], "\\s*"
    assert_includes field["url"], "${ZIPKIN_EXTERNAL_URL}"
    assert_includes field["url"], "$${__value.raw}"
  end

  def test_promtail_filters_services_and_keeps_trace_as_metadata
    config = yaml("observability/promtail/promtail-config.yml")
    scrape = config.fetch("scrape_configs").first
    stages = scrape.fetch("pipeline_stages")
    relabel = scrape.fetch("relabel_configs")

    keep = relabel.find { |rule| rule["action"] == "keep" }
    %w[
      gateway-service empleado-service departamento-service
      notificaciones-service perfiles-service vacaciones-service auth-service
    ].each { |service| assert_includes keep["regex"], service }

    labels = stages.find { |stage| stage.key?("labels") }.fetch("labels")
    metadata = stages.find { |stage| stage.key?("structured_metadata") }
      .fetch("structured_metadata")

    assert_equal ["level"], labels.keys
    assert_equal %w[traceId spanId logger], metadata.keys
  end

  def test_loki_supports_structured_metadata_and_bounded_retention
    config = yaml("observability/loki/loki-config.yml")
    schema = config.dig("schema_config", "configs").first

    assert_equal "tsdb", schema["store"]
    assert_equal "v13", schema["schema"]
    assert_equal true, config.dig("limits_config", "allow_structured_metadata")
    assert_equal "168h", config.dig("limits_config", "retention_period")
    assert_equal true, config.dig("compactor", "retention_enabled")
    assert_equal "filesystem", config.dig("compactor", "delete_request_store")
  end
end
