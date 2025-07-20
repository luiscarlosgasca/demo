package com.cloudframe.app;
 
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.metrics.BatchMetrics;
import java.time.Duration;
import org.springframework.batch.core.scope.context.ChunkContext;
import com.cloudframe.app.utility.SpringContextHandler;
import org.springframework.beans.factory.InitializingBean;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import com.cloudframe.app.process.BaseProcess;
import com.cloudframe.app.step.StepInfo;
import com.cloudframe.app.utility.CFUtil;
import com.cloudframe.app.utility.Cond;
import com.cloudframe.app.exception.CFException;
    
@Component
public class CFStepHandler implements InitializingBean {
    
    private Logger logger = LoggerFactory.getLogger(CFStepHandler.class);
    private Map<String, List<String>> sharedVarMap ;
    private Map<String, List<String>> programMap ;
    private List<String> refreshList = new ArrayList<>();
    private Map<String, Integer> stepMap = new HashMap<>();
    private List<String> skipList = new ArrayList<>();
    private List<String> abendList = new ArrayList<>();
    private Map<String, StepExecution> stepInfo = new HashMap<>();
    private Map<String, Set<String>> dynamicBeanMap = new HashMap<>();
    private List<String> ignorePrograms = Arrays.asList("IDCAMS","SORT");
    private boolean skipFirst = true;
    private List<String> stepSummary ;
    private List<String> stepProgm ;
    private int jobRc;
    private boolean isNormalExit = true;
    private String abendCode = "";
    private List<StepInfo> stepsInfo;
    private static final String DELETE = "delete";
    private static final String STEP01 = "step01";
    
    @Override
    public void afterPropertiesSet() throws Exception {
   sharedVarMap = CFUtil.getBeanInfoFromJson("sharedVarMap.json");
   programMap = CFUtil.getBeanInfoFromJson("programMap.json");
  try {
   	stepsInfo = CFUtil.getJSONAsObject("stepInfo.json", new TypeReference<List<StepInfo>>() {});
   	stepSummary = new ArrayList<>();
   	stepProgm = new ArrayList<>();
   	if (!stepsInfo.isEmpty()) {
   		stepsInfo.forEach(si -> {
   			stepSummary.add(si.getStepName().toLowerCase());
   			stepProgm.add(si.getProgName());
   			SpringContextHandler.updateDDInfo(si.getDataSetInfos());
   		});
   	}
} catch (IOException e) {
   logger.error("Unable to load stepInfo from json. err msg :  {}" , e.getMessage());
}
}
public void setAbendCode(CFException cfe) {
	//sets abend code if only it is not setted by any of the previous steps
	if(CFUtil.isEmpty(abendCode) && !CFUtil.isEmpty(cfe.getAbendCode()))
		abendCode = cfe.getAbendCode();
	this.isNormalExit = false;
}
    
/**   
 * Decides whether the step can be executed
* 
* @param stepName
* @return
*/
    public boolean stepDecider(String stepName) {
	      boolean canExecute = false;
	      switch (stepName) {
	          case DELETE:
	              canExecute = (true);
	          break;
	          case STEP01:
	              canExecute = abendList.isEmpty();
	          break;
	          default:
	              canExecute = false;
	          break;
        }
if (!canExecute) skipList.add(stepName);
return canExecute;
    }
    
/**
* Initialize's this step for execution and return the process bean
* 
* 1. closes previously opened qsam and vsam files
* 2. refreshes the driverProgram and its child's for execution
* 3. init qsam, vsam and dynamic beans list for the current run
* 
* 
* @param stepName
* @param driverProgName
* 
* @return the process bean to be executed
*  
*/
    public BaseProcess initAndGetProcessBean(String stepName , String driverProgName) throws CFException {
      if (!skipFirst) {
        SpringContextHandler.close();
        refreshBeans(driverProgName);
        SpringContextHandler.initStep();
      } else skipFirst = false;
      handleStepOverrides(stepName);
      return SpringContextHandler.getProcess(driverProgName);
    }
    
  public void handleStepOverrides(String stepName) throws CFException {
        switch(stepName) {
            case "delete":  /* setting the disp position for file(s) info for this step(DELETE). */
 		    	   		SpringContextHandler.handleDispPostion("DELETE.IEFBR14.Reporder","DELETE.IEFBR14.Reporder.DISP");
            break;
            case "step01":  /* setting the appropriate file(s) info for this step(STEP01). */
 		    	   		SpringContextHandler.setFileDispPosition("trdpb006_reporder", "TRDPB006.Reporder.DISP");
            break;
                default: break;
        }
}
    
/**
* Updates the rc for this step
 * 
 * 
 * @param stepName
 * @param rc
 */
    public void postStepExecution(String stepName, int rc) throws CFException {
      if(isNormalExit) {
          stepMap.put(stepName, rc);
          updateJobRc(rc);
      } else {
          abendList.add(stepName);
      }
      SpringContextHandler.handleDispPostionAtStepEnd(isNormalExit);
    }
    
/**
* Updates the rc for this step and keeps track of dynamic beans for this program
 * 
 * 
 * @param stepName
 * @param rc
 */
   public void postStepExecution(String stepName, int rc , String driveProgramName) throws CFException {
     if(isNormalExit) {
          stepMap.put(stepName, rc);
          updateJobRc(rc);
      } else {
          abendList.add(stepName);
      } 
      if(!ignorePrograms.contains(driveProgramName)) {
          SpringContextHandler.close();
          updateDynamicBeanMap(driveProgramName);
          SpringContextHandler.handleDispPostionAtStepEnd(isNormalExit);
      }
      SpringContextHandler.clearCache();
  }
    
   private void updateDynamicBeanMap(String driveProgramName) {     
      dynamicBeanMap.computeIfAbsent(driveProgramName,  key -> new HashSet<>());
      SpringContextHandler.removeFromDynamicBeans(driveProgramName);
      dynamicBeanMap.get(driveProgramName).addAll(SpringContextHandler.dynamicBeans());
   }
    
    private void refreshBeans(String driverProgName) {
        refreshList.clear();
        SpringContextHandler.removeFromDynamicBeans(driverProgName);
        SpringContextHandler.dynamicBeans().forEach(this::refreshProcessBean);
        if(dynamicBeanMap.containsKey(driverProgName))
                dynamicBeanMap.get(driverProgName).forEach(this::refreshProcessBean);
        refreshProcessBean(driverProgName);
    }
    
    private void refreshProcessBean(String processBean) {
        if (sharedVarMap.containsKey(processBean))
            sharedVarMap.get(processBean).forEach(globalBean -> {
                if (!refreshList.contains(globalBean)) {
                    SpringContextHandler.refreshBean(globalBean);
                    refreshList.add(globalBean);
                }
            });
        if (programMap.containsKey(processBean))
            programMap.get(processBean).forEach(childProcessBean -> {
                if (!refreshList.contains(childProcessBean)) {
                    refreshList.add(childProcessBean);
                    refreshProcessBean(childProcessBean);
                }
            });
        SpringContextHandler.refreshBean(processBean);
    }
    
/**
 * Gets the formatted parm by removing the preceding and succeeding quotes
* 
* @param parm 
* @return
*/ 
    public String getParm(String parm) {
        if (parm.startsWith("'") && parm.endsWith("'"))
            return parm.substring(1, parm.length() - 1);
        return parm;
    }
    
	/**
	  * Logs the step-wise execution summary
	  * 
	 */
    public void printExecutionSummary() {
       int stepNameMaxLength = findMaxLength(stepSummary);
       int progNameMaxLength = findMaxLength(stepProgm);
       int statusMaxLength = Math.max("Status".length(), Math.max("FLUSH".length(), "ABEND".length()));
       int elapsedTimeMaxLength = "Elapsed Time".length();
    
       StringJoiner report = new StringJoiner("\n");
       report.add("\n");
       report.add("Stepwise Execution Summary :");
       String border = generateBorder(stepSummary, stepProgm);
       report.add(border);
       report.add(formatRow("STEPNAME", "PROGRAM", "RC", "ELAPSED",stepNameMaxLength, progNameMaxLength, statusMaxLength, elapsedTimeMaxLength));
       report.add(formatRow("", "", "", "HH:MM:SS:ss",stepNameMaxLength, progNameMaxLength, statusMaxLength, elapsedTimeMaxLength));
       report.add(border);
       for (int i = 0; i < stepSummary.size(); i++) {
       	String stepName = stepSummary.get(i);
       	String retCode = String.format("%02d", (stepMap.containsKey(stepName)) ? stepMap.get(stepName) : 0);
       	String formattedStepName = stepName.toUpperCase();
       	String progName = stepProgm.get(i).toUpperCase();
       	String status;
       	if (skipList.contains(stepName)) {
       		status = "FLUSH";
       	} else if (abendList.contains(stepName)) {
       		status = "ABEND";
       	} else {
       		status = retCode;
      	}
       	String elapsedTime = getElapsed(stepName);
       	String formattedElapsedTime = formatElapsedTime(elapsedTime);
       	report.add(formatRow(formattedStepName, progName, status, formattedElapsedTime, stepNameMaxLength, progNameMaxLength, statusMaxLength, elapsedTimeMaxLength));
       	report.add(border);
      }
      report.add("  ");
      if(abendList.isEmpty()) {
          String formattedJobRc = String.format("%04d", jobRc);
          logger.info("JOB : TRADERPT ENDED -  {}", formattedJobRc);
      } else { 
          logger.info("JOB : TRADERPT ENDED -  ABEND={}", abendCode);
      } 
      logger.info(report.toString());
      SpringContextHandler.deleteTmpFiles();
    }
    
	 /**
	  * Generates a border string to be used for formatting in a report, based on the maximum lengths 
	  * of step names, program names, status, and elapsed time. 
	  * 
	  * @param list of step summary
    	  * @param list of step programs
	  * @return string representing the generated border.
	 */
    private String generateBorder(List<String> stepSummary, List<String> stepProgm) {
       int stepNameMaxLength = findMaxLength(stepSummary);
       int progNameMaxLength = findMaxLength(stepProgm);
       int statusMaxLength = Math.max("Status".length(), Math.max("FLUSH".length(), "ABEND".length()));
       int elapsedTimeMaxLength = "Elapsed Time".length();
       int totalWidth = stepNameMaxLength + progNameMaxLength + statusMaxLength + elapsedTimeMaxLength+31;
       StringBuilder borderBuilder = new StringBuilder("+");
       for (int i = 0; i < totalWidth; i++) {
       	borderBuilder.append("-");
       }
       borderBuilder.append("+");
       return borderBuilder.toString();
    }
    
	/**
	* Finds the maximum length among the strings in the provided list.
	* 
	*/
    private int findMaxLength(List<String> strings) {
       int maxLength = 0;
       for (String s : strings) {
       	maxLength = Math.max(maxLength, s.length());
       }
       return maxLength;
    }
    
	/**
	* Formats the elapsed time represented by the given string into a standardized format of hours, 
	* minutes, seconds, and milliseconds. 
	* 
	*/
    public static String formatElapsedTime(String elapsedTime) {
       int hours = 0, minutes = 0, seconds = 0, milliseconds = 0;
       String[] parts = elapsedTime.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
       for (int i = 0; i < parts.length; i += 2) {
    		int value = Integer.parseInt(parts[i]);
       	if (i + 1 < parts.length) {
       		String unit = parts[i + 1];
       		switch (unit) {
       			case "h":
       				hours = value;
    					break;
       			case "m":
       				minutes = value;
       				break;
    				case "s":
       				seconds = value;
       				break;
       			case "ms":
       				milliseconds = value;
       				break;
       		}
       	}
   	}
   	return String.format("%02d:%02d:%02d:%03d", hours, minutes, seconds, milliseconds);
   }
    
	/**
	* Formats a row of data with specified step name, program name, status, and elapsed time, 
	* adjusting the padding to fit the maximum lengths provided for each field. 
	* 
	*/
	private String formatRow(String stepName, String progName, String status, String elapsedTime, int stepNameMaxLength, int progNameMaxLength, int statusMaxLength, int elapsedTimeMaxLength) {
		String format = "| %-"+(stepNameMaxLength+5)+"s | %-"+(progNameMaxLength+5)+"s | %-"+(statusMaxLength+5)+"s | %-"+(elapsedTimeMaxLength+5)+"s |";
		return String.format(format, stepName, progName, status, elapsedTime);
	}
    
	private String getElapsed(String stepName) {
	    StepExecution stepExecution = stepInfo.get(stepName);
		if(stepExecution != null) { 
		    Duration duration = BatchMetrics.calculateDuration(stepExecution.getStartTime(), stepExecution.getEndTime());
		    return (duration != null && duration.isZero()) ? "1ms" /*Less than a millisecond */ : BatchMetrics.formatDuration(duration);
		}
	    return "";
	}
    
/**
 * Updates the step execution info
* 
* @param stepName
* @param chunkContext
* @param rcStatus
* @param status
*/
    public void updateStepExecution(String stepName, ChunkContext chunkContext , String rcStatus , String status) {
    	StepExecution stepExecution = chunkContext.getStepContext().getStepExecution();
      ExitStatus exitStatus = new ExitStatus(status, rcStatus);
      stepExecution.setExitStatus(exitStatus);
      stepInfo.put(stepName, stepExecution);
      isNormalExit = true; //resetting normalExit at end
    }
    
    public Integer getRcForStep(String stepName) {
   	return stepMap.get(stepName);
    }
    
    private void updateJobRc(int rc) {
    	if (jobRc < rc)
    		jobRc = rc;
    }
    
    public int getJobRc() {
    	return jobRc;
    }
    public void intializeStepDetails() {
        init();
        stepsInfo.forEach(this::updateDDInfo);
    }
    
    private void updateDDInfo(StepInfo si) {
        SpringContextHandler.updateDDInfo(si.getDataSetInfos());
    }
    
    private void init() {
        skipFirst = true;
        jobRc = 0;
        isNormalExit = true;
        abendCode = "";
        refreshList.clear();
        stepMap.clear();
        skipList.clear();
        abendList.clear();
        stepInfo.clear();
        dynamicBeanMap.clear();
        stepSummary.clear();
        stepProgm.clear();
        SpringContextHandler.getCFContext().getFileMap().clear();
    }
}
