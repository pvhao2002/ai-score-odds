package com.app.kira.config.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration("DBConfiguration")
@EnableTransactionManagement
public class DBConfiguration {

    @Bean
    @ReadDB
    @ConfigurationProperties(prefix = "application.datasource.read")
    public HikariConfig readHikariConfig() {
        return new HikariConfig();
    }

    @Bean
    @WriteDB
    @ConfigurationProperties(prefix = "application.datasource.write")
    public HikariConfig writeHikariConfig() {
        return new HikariConfig();
    }

    @Bean
    @ReadDB
    public DataSource readDataSource(@ReadDB HikariConfig config) {
        return getDataSource(config);
    }

    @Bean
    @WriteDB
    public DataSource writeDataSource(@WriteDB HikariConfig config) {
        return getDataSource(config);
    }

    private DataSource getDataSource(HikariConfig configuration) {
        return new HikariDataSource(configuration);
    }

    @Bean
    @ReadDB
    public JdbcTemplate readJdbcTemplate(@ReadDB DataSource ds) {
        return new JdbcTemplate(ds);
    }

    @Bean
    @WriteDB
    @Primary
    public JdbcTemplate writeJdbcTemplate(@WriteDB DataSource ds) {
        return new JdbcTemplate(ds);
    }

    @Bean
    @ReadDB
    public NamedParameterJdbcTemplate readNamedParameterJdbcTemplate(@ReadDB DataSource ds) {
        return new NamedParameterJdbcTemplate(this.readJdbcTemplate(ds));
    }

    @Bean
    @WriteDB
    @Primary
    public NamedParameterJdbcTemplate writeNamedParameterJdbcTemplate(@WriteDB DataSource ds) {
        return new NamedParameterJdbcTemplate(this.writeJdbcTemplate(ds));
    }

    @Bean
    @ReadDB
    public JdbcClient readJdbcClient(@ReadDB DataSource ds) {
        return JdbcClient.create(ds);
    }

    @Bean
    @WriteDB
    @Primary
    public JdbcClient writeJdbcClient(@WriteDB DataSource ds) {
        return JdbcClient.create(ds);
    }

    @Bean
    public DataSourceTransactionManager transactionManager(@WriteDB DataSource ds) {
        var txManager = new DataSourceTransactionManager();
        txManager.setDataSource(ds);
        return txManager;
    }
}
