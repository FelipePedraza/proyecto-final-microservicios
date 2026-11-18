require "date"
require "minitest/autorun"
require "yaml"
require "json"

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

  def test_prometheus_continuously_scrapes_every_postgresql_database
    config = yaml("observability/prometheus/prometheus.yml")
    job = config.fetch("scrape_configs")
      .find { |scrape| scrape["job_name"] == "postgresql-databases" }

    refute_nil job
    assert_equal "10s", job["scrape_interval"]
    assert_equal "8s", job["scrape_timeout"]

    targets = job.fetch("static_configs")
    assert_equal 7, targets.length
    assert_equal %w[auth departamento empleado notificaciones perfiles sonar vacaciones],
      targets.map { |target| target.dig("labels", "database") }.sort
    targets.each do |target|
      assert_match(/-db-exporter:9187\z/, target.fetch("targets").first)
      refute_empty target.dig("labels", "db_service")
      refute_empty target.dig("labels", "owner_service")
    end
  end

  def test_postgres_exporters_use_supported_environment_configuration
    services = yaml("docker-compose.yml").fetch("services")
    exporters = {
      "empleado-db-exporter" => "db_empleado_password",
      "departamento-db-exporter" => "db_departamento_password",
      "notificaciones-db-exporter" => "db_notificaciones_password",
      "perfiles-db-exporter" => "db_perfiles_password",
      "vacaciones-db-exporter" => "db_vacaciones_password",
      "auth-db-exporter" => "db_auth_password",
      "sonar-db-exporter" => "sonar_db_password"
    }

    exporters.each do |name, secret|
      exporter = services.fetch(name)
      environment = exporter.fetch("environment")

      assert_equal "quay.io/prometheuscommunity/postgres-exporter:v0.20.1",
        exporter.fetch("image")
      assert environment.key?("DATA_SOURCE_URI")
      assert environment.key?("DATA_SOURCE_USER")
      assert_equal "/run/secrets/#{secret}", environment["DATA_SOURCE_PASS_FILE"]
      assert_includes exporter.fetch("secrets"), secret
      refute exporter.key?("command")
    end
  end

  def test_database_alerts_cover_database_and_exporter_failures
    config = yaml("observability/prometheus/rules/database-alerts.yml")
    rules = config.fetch("groups").first.fetch("rules")

    assert_includes config.fetch("groups").first["name"], "postgresql"
    assert rules.any? { |rule| rule["record"] == "database:postgresql_available" }
    assert rules.any? { |rule| rule["alert"] == "PostgreSQLDatabaseDown" && rule["expr"].include?("pg_up") }
    assert rules.any? { |rule| rule["alert"] == "PostgreSQLExporterDown" && rule["expr"].include?("up{") }
  end

  def test_postgresql_dashboard_displays_live_availability
    dashboard = JSON.parse(File.read(File.join(ROOT,
      "observability/grafana/provisioning/dashboards/postgresql.json")))
    expressions = dashboard.fetch("panels").flat_map do |panel|
      panel.fetch("targets", []).map { |target| target["expr"] }
    end

    assert_equal "10s", dashboard["refresh"]
    assert_includes expressions, "database:postgresql_available"
    assert expressions.any? { |expression| expression.include?("pg_stat_database_numbackends") }
  end
end
