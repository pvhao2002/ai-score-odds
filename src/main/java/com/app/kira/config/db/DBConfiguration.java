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
    @WriteDB
    @ConfigurationProperties(prefix = "application.datasource.write")
    public HikariConfig writeHikariConfig() {
        return new HikariConfig();
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
    @WriteDB
    @Primary
    public JdbcTemplate writeJdbcTemplate(@WriteDB DataSource ds) {
        return new JdbcTemplate(ds);
    }

    @Bean
    @WriteDB
    @Primary
    public NamedParameterJdbcTemplate writeNamedParameterJdbcTemplate(@WriteDB DataSource ds) {
        return new NamedParameterJdbcTemplate(this.writeJdbcTemplate(ds));
    }

    @Bean
    @WriteDB
    @Primary
    public JdbcClient writeJdbcClient(@WriteDB DataSource ds) {
        return JdbcClient.create(ds);
    }

    @Bean
    @Primary
    public DataSourceTransactionManager transactionManager(@WriteDB DataSource ds) {
        var txManager = new DataSourceTransactionManager();
        txManager.setDataSource(ds);
        return txManager;
    }


}
