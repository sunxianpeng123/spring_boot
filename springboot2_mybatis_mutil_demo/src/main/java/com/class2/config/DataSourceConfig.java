package com.class2.config;

import com.alibaba.druid.pool.DruidDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * \* Created with IntelliJ IDEA.
 * \* User: sunxianpeng
 * \* Date: 2018/12/20
 * \* Time: 20:46
 * \* To change this template use File | Settings | File Templates.
 * \* Description:
 * \
 */
@Configuration
@MapperScan("com.class2.dao")
public class DataSourceConfig {
    /**
     * 创建数据源配置类DataSourceConfig
     * 以上的代码中：
     * (1) 注入类 Environment 可以很方便的获取配置文件中的参数
     * (2) DataSourceProperties和（4）中的 @ConfigurationProperties(prefix = "spring.datasource.master")配合使用，将配置文件中的配置数据自动封装到实体类DataSourceProperties中
     * (3) @Value注解同样是指定获取配置文件中的配置
     */
    //4
    private  static Logger logger = LoggerFactory.getLogger(DataSourceConfig.class);
    @Autowired
    private Environment env; // (1)
    @Autowired
    private DataSourceProperties properties; // (2)
    @Value("${spring.datasource.druid.filters}") // (3)
    private String filters;
    @Value("${spring.datasource.druid.initial-size}")
    private Integer initialSize;
    @Value("${spring.datasource.druid.min-idle}")
    private Integer minIdle;
    @Value("${spring.datasource.druid.max-active}")
    private Integer maxActive;
    @Value("${spring.datasource.druid.max-wait}")
    private Integer maxWait;
    @Value("${spring.datasource.druid.time-between-eviction-runs-millis}")
    private Long timeBetweenEvictionRunsMillis;
    @Value("${spring.datasource.druid.min-evictable-idle-time-millis}")
    private Long minEvictableIdleTimeMillis;
    @Value("${spring.datasource.druid.validation-query}")
    private String validationQuery;
    @Value("${spring.datasource.druid.test-while-idle}")
    private Boolean testWhileIdle;
    @Value("${spring.datasource.druid.test-on-borrow}")
    private boolean testOnBorrow;
    @Value("${spring.datasource.druid.test-on-return}")
    private boolean testOnReturn;
    @Value("${spring.datasource.druid.pool-prepared-statements}")
    private boolean poolPreparedStatements;
    @Value("${spring.datasource.druid.max-pool-prepared-statement-per-connection-size}")
    private Integer maxPoolPreparedStatementPerConnectionSize; /**
     * 通过Spring JDBC 快速创建 DataSource
     * @return
     * */
    @Bean(name = "masterDataSource")
    @Qualifier("masterDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.master") // (4)
    public DataSource masterDataSource() {
        return DataSourceBuilder.create().build();
    }
    /**
     * 手动创建DruidDataSource,通过DataSourceProperties 读取配置
     * @return
     * @throws SQLException
     */
    @Bean(name = "slaveDataSource")
    @Qualifier("slaveDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.slave")
    public DataSource slaveDataSource()
            throws SQLException {
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setFilters(filters);
        dataSource.setUrl(properties.getUrl());
        dataSource.setDriverClassName(properties.getDriverClassName());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        dataSource.setInitialSize(initialSize);
        dataSource.setMinIdle(minIdle);
        dataSource.setMaxActive(maxActive);
        dataSource.setMaxWait(maxWait);
        dataSource.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
        dataSource.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
        dataSource.setValidationQuery(validationQuery);
        dataSource.setTestWhileIdle(testWhileIdle);
        dataSource.setTestOnBorrow(testOnBorrow);
        dataSource.setTestOnReturn(testOnReturn);
        dataSource.setPoolPreparedStatements(poolPreparedStatements);
        dataSource.setMaxPoolPreparedStatementPerConnectionSize(maxPoolPreparedStatementPerConnectionSize);
        return dataSource;
    }
    /**
     *  构造多数据源连接池
     *  Master 数据源连接池采用 HikariDataSource
     *  Slave  数据源连接池采用 DruidDataSource
     * @param master
     * @param slave
     * @return
     */
    @Bean
    @Primary
    public DynamicDataSource dataSource(@Qualifier("masterDataSource") DataSource master,
                                        @Qualifier("slaveDataSource") DataSource slave) {
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DatabaseType.master, master);
        targetDataSources.put(DatabaseType.slave, slave);
        DynamicDataSource dataSource = new DynamicDataSource();
        dataSource.setTargetDataSources(targetDataSources);// 该方法是AbstractRoutingDataSource的方法
        dataSource.setDefaultTargetDataSource(slave);// 默认的datasource设置为myTestDbDataSource
        String read = env.getProperty("spring.datasource.read");
        dataSource.setMethodType(DatabaseType.slave, read);
        String write = env.getProperty("spring.datasource.write");
        dataSource.setMethodType(DatabaseType.master, write);
        return dataSource;
    }
    @Bean
    public SqlSessionFactory sqlSessionFactory(
            @Qualifier("masterDataSource") DataSource myTestDbDataSource,
            @Qualifier("slaveDataSource") DataSource myTestDb2DataSource)
            throws Exception {
        SqlSessionFactoryBean fb = new SqlSessionFactoryBean();
        fb.setDataSource(this.dataSource(myTestDbDataSource, myTestDb2DataSource));
        fb.setTypeAliasesPackage(env.getProperty("mybatis.type-aliases-package"));
        fb.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(env.getProperty("mybatis.mapper-locations")));
        return fb.getObject();
    }
    @Bean
    public DataSourceTransactionManager transactionManager(DynamicDataSource dataSource) throws Exception {
        return new DataSourceTransactionManager(dataSource); }


}