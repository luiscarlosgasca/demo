package com.cloudframe.app;
import javax.sql.DataSource;
import org.springframework.batch.core.configuration.annotation.BatchConfigurer;
import org.springframework.batch.core.configuration.annotation.DefaultBatchConfigurer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
@Configuration
public class BatchDataSourceConfiguration {
	
	/** 
 	* Spring batch requires a database to store job, flow and step execution
 	* Use this configuration for that datasource instead of default one.
 	* 
 	* @param dataSource
 	* @return
 	*/
	
	@Bean
	BatchConfigurer configurer(@Qualifier("cfBatchDataSource") DataSource cfBatchDataSource){
  		return new DefaultBatchConfigurer(cfBatchDataSource);
	}
	
	/** 
 	* Primary datasource for Spring Batch 
 	* @return 
 	*/ 
	@Bean
	@Primary
	public DataSource cfBatchDataSource() {
		EmbeddedDatabaseBuilder builder = new EmbeddedDatabaseBuilder();
		return builder.addScript("classpath:org/springframework/batch/core/schema-drop-h2.sql")
			.addScript("classpath:org/springframework/batch/core/schema-h2.sql").setType(EmbeddedDatabaseType.H2) // .H2 or any other DB
			.build();
	}
    
}   
