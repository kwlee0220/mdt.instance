package mdt.exector.jar.model;

import java.io.File;
import java.time.Duration;

import mdt.exector.jar.controller.JarExecutorController;
import mdt.model.instance.JarExecutionArguments;
import mdt.model.instance.MDTInstanceStatus;
import mdt.model.instance.StatusResult;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TestJarExecutorController {
	public static final void main(String... args) throws Exception {
		JarInstanceExecutor exector = JarInstanceExecutor.builder()
										.workspaceDir(new File("D:\\Dropbox\\Temp\\fa3st-repository"))
										.repositoryEndpointFormat("https://localhost:%d/api/v3.0")
										.sampleInterval(Duration.ofSeconds(1))
										.timeout(Duration.ofMinutes(1))
										.build();
		
		JarExecutorController ctrl = new JarExecutorController();
		ctrl.setJarExecutor(exector);
		ctrl.afterPropertiesSet();
		
		JarExecutionArguments arguments = JarExecutionArguments.builder()
														.jarFile("fa3st-repository.jar")
														.modelFile("aas_vacuum.json")
														.configFile("conf_vacuum.json")
														.build();
		
		JarExecutionCommand cmd = new JarExecutionCommand("vacuum",
														"http://www.lg.co.kr/refrigerator/Innercase/VacuumFormer",
														arguments);
		StatusResult result = ctrl.start(cmd);
		do {
			result = exector.getStatus(cmd.getInstanceId());
			System.out.println(result);
			Thread.sleep(1000);
		} while ( result.getStatus() == MDTInstanceStatus.STARTING );
		System.out.println(result);
		
		Thread.sleep(3000);
		
		if ( result.getStatus() == MDTInstanceStatus.RUNNING ) {
			System.out.println("stopping...");
			ctrl.stop("vacuum");
		}
		
		do {
			result = exector.getStatus(cmd.getInstanceId());
			System.out.println(result);
			Thread.sleep(1000);
		} while ( result.getStatus() == MDTInstanceStatus.STOPPING );
		System.out.println(result);
		
		Thread.sleep(3000);
	}
}
