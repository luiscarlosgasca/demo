package com.cloudframe.app;
 
import java.util.Map;
import org.springframework.batch.core.configuration.annotation.JobScope;
import com.cloudframe.app.process.Trdpb006;
import java.util.HashMap;
import java.util.Collection;
import java.util.Iterator;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.job.flow.support.SimpleFlow;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.job.flow.support.SimpleFlow;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.metrics.BatchMetrics;
import com.cloudframe.app.utility.SpringContextHandler;
import com.cloudframe.app.exception.CFException;
    
@Configuration
@EnableBatchProcessing
    
public class BatchConfiguration {
    
    @Autowired
    StepBuilderFactory stepBuilderFactory;
    
    @Autowired
    CFStepHandler cfStepHandler;
    
    private static final String JOBNAME = "TRADERPT";
    
    /* Driver Programs used in steps */  
    private static final String TRDPB006_BEAN = "trdpb006";
    
    private static final String COMPLETED_STATUS = "COMPLETED";
    
    /*
     * DELETE IEFBR14 starts here
     */
    
    private static final String DELETE_STEP = "delete";
    
    @Bean
    public Step delete() {
    
      return stepBuilderFactory.get(DELETE_STEP).tasklet(
    		new Tasklet() {
    
 		       @Override
 		       public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
                      String rcStatus = "";
                      if (cfStepHandler.stepDecider(DELETE_STEP)) {
 	 					int rc = 0; // IEFBR14 PROGRAM 
                      try {
 		    	   		cfStepHandler.handleStepOverrides(DELETE_STEP);
                          } catch (CFException e) {
                             rc = 12;
                             cfStepHandler.setAbendCode(e);
                          }
 		    	  		rcStatus+=rc;
 		    	   		cfStepHandler.postStepExecution(DELETE_STEP, rc );
                      } 
    			   cfStepHandler.updateStepExecution(DELETE_STEP, chunkContext , rcStatus , COMPLETED_STATUS);
 		          return RepeatStatus.FINISHED;
 		     } 
    
 	     }
 	    ).build();
    
    }
    /*
     * DELETE IEFBR14 Ends here 
     */
    
    /*
     * STEP01 TRDPB006 starts here
     * Member Info:
     *   DSN SYSTEM (DBCG)                                                             
     *   RUN PROGRAM(TRDPB006) -                                                       
     *   PLAN   (CLFRPLAN)                                                             
     *   END                                                                           
     *   DSN SYSTEM (DBCG)                                                             
     *   RUN PROGRAM(TRDPB006) -                                                       
     *   PLAN   (CLFRPLAN)                                                             
     *   END                                                                           
     */
    
    private static final String STEP01_STEP = "step01";
    
    @Bean
    public Step step01() {
    
      return stepBuilderFactory.get(STEP01_STEP).tasklet(
    		new Tasklet() {
    
 		       @Override
 		       public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
                      String rcStatus = "";
                      if (cfStepHandler.stepDecider(STEP01_STEP)) {
     int rc = 0;
 	   try { 
 		    	  		rc = ((Trdpb006)cfStepHandler.initAndGetProcessBean(STEP01_STEP,TRDPB006_BEAN)).process();
 } catch (CFException cfe) { 
 if (cfe.isTerminated()) {
 rc = cfe.getCode();
  } else {
rc = Integer.MAX_VALUE;
cfStepHandler.setAbendCode(cfe);
}
}
 		    	  		rcStatus+=rc;
 		    	   		cfStepHandler.postStepExecution(STEP01_STEP, rc ,TRDPB006_BEAN);
                      } 
    			   cfStepHandler.updateStepExecution(STEP01_STEP, chunkContext , rcStatus , COMPLETED_STATUS);
 		          return RepeatStatus.FINISHED;
 		     } 
    
 	     }
 	    ).build();
    
    }
    /*
     * STEP01 TRDPB006 Ends here 
     */
    
	
	@Bean
	public Step printSummary() {
    return stepBuilderFactory.get("printSummary").tasklet(new Tasklet() {
	    @Override
	    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
          cfStepHandler.printExecutionSummary();
          return RepeatStatus.FINISHED;
      }
	  }).build();
	}
	    
/** 
 * Execute the Job that consists of 
 * DELETE - IEFBR14
 * STEP01 - TRDPB006
 * 
 * @param jobBuilderFactory 
 * @return 
 */ 
@Bean 
public Job job(JobBuilderFactory jobBuilderFactory) { 
	return jobBuilderFactory.get(JOBNAME)
			.start(delete())
			.next(step01()) /*  */
		.next(printSummary()) 

			.build();
} 
}
