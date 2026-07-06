package org.linlinjava.litemall.db.config;



import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;
import java.io.IOException;

@Configuration
@MapperScan("org.linlinjava.litemall.db.dao")
public class MyBatisConfig{


  @Bean
    public SqlSessionFactoryBean sqlSessionFactoryBean(DataSource dataSource) throws IOException {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        // Add location for your XML mapper files if needed
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath:org/linlinjava/litemall/db/dao/*.xml"));
        // This custom factory bean makes mybatis-spring-boot-autoconfigure back off, so the
        // `mybatis.configuration.map-underscore-to-camel-case: true` declared in application-db.yml
        // is NEVER applied. Set it on the Configuration explicitly so hand-written resultType
        // mappers (e.g. LitemallCjProductMapper) bind snake_case columns like image_url -> imageUrl
        // and discount_price -> discountPrice. Generated mappers use explicit <resultMap>s and are
        // unaffected either way.
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        factoryBean.setConfiguration(configuration);
        return factoryBean;
    }

    //@Bean
   /* public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);
        sessionFactory.setMapperLocations(
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath:org/linlinjava/litemall/db/dao/*.xml"));
        return sessionFactory.getObject();
    }*/
    @Bean
    public SqlSession sqlSession(SqlSessionFactory sqlSessionFactory) {
        return sqlSessionFactory.openSession();
    }
}
