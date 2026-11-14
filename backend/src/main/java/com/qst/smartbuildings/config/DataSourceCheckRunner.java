package com.qst.smartbuildings.config;

import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;

@Component
@Slf4j
public class DataSourceCheckRunner implements ApplicationListener<ApplicationReadyEvent> {

    @Autowired
    private DataSource dataSource;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try (Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL();
            String userName = conn.getMetaData().getUserName();
            log.info("数据库连接正常 | URL: {} | User: {}", url, userName);
        } catch (SQLException e) {
            log.error("数据库连接失败", e);
        }
    }
}
