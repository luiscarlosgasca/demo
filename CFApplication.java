package com.cloudframe.app;
 
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
  
/**
 * The application can be started in three different ways 1. Via mainframe JCL 2. Via rest
 * invocation 3. Via console command , inputValue(if any) to be filled in application.properties
 *
 * @author CloudFrame Inc. Code Generator
 */
@SpringBootApplication
public class CFApplication {
  
  public static void main(String[] args) {
			 ConfigurableApplicationContext context = SpringApplication.run(CFApplication.class);
			 System.exit(SpringApplication.exit(context));
			 context.close();
    }
}
