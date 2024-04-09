package mdt.exector.jar.model;

import java.io.File;
import java.time.Duration;

import mdt.model.instance.JarExecutionArguments;
import mdt.model.instance.MDTInstanceStatus;
import mdt.model.instance.StatusResult;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TestJarExecutor {
	public static final void main(String... args) throws Exception {
		JarInstanceExecutor exector = JarInstanceExecutor.builder()
										.workspaceDir(new File("D:\\Dropbox\\Temp\\fa3st-repository"))
										.repositoryEndpointFormat("https://localhost:%d/api/v3.0")
										.sampleInterval(Duration.ofSeconds(1))
										.timeout(Duration.ofSeconds(10))
										.build();
		JarExecutionArguments arguments = JarExecutionArguments.builder()
														.jarFile("fa3st-repository.jar")
														.modelFile("aas_vacuum.json")
														.configFile("conf_vacuum.json")
														.build();
		
		StatusResult result = exector.start("vacuum",
											"http://www.lg.co.kr/refrigerator/Innercase/VacuumFormer",
											arguments);
		do {
			result = exector.getStatus("vacuum");
			System.out.println(result);
			Thread.sleep(1000);
		} while ( result.getStatus() == MDTInstanceStatus.STARTING );
		System.out.println(result);
		
		if ( result.getStatus() == MDTInstanceStatus.RUNNING ) {
			System.out.println("stopping...");
			exector.stop("vacuum");
		}
		
		do {
			result = exector.getStatus("vacuum");
			System.out.println(result);
			Thread.sleep(1000);
		} while ( result.getStatus() == MDTInstanceStatus.STOPPING );
	}
}