package mdt;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

import org.mandas.docker.client.exceptions.DockerException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import utils.jdbc.JdbcProcessor;

import lombok.Data;
import lombok.Setter;
import mdt.client.HttpServiceFactory;
import mdt.controller.MDTInstanceManagerConfiguration;
import mdt.exector.jar.JarInstanceExecutor;
import mdt.instance.InstanceDescriptorManager;
import mdt.instance.JdbcInstanceDescriptorManager;
import mdt.instance.docker.DockerConfiguration;
import mdt.instance.docker.DockerInstanceManager;
import mdt.instance.jar.JarInstanceManager;
import mdt.instance.k8s.KubernetesInstanceManager;
import mdt.model.ServiceFactory;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.instance.MDTInstanceManagerException;
import mdt.registry.CachingFileMDTAASRegistry;
import mdt.registry.CachingFileMDTSubmodelRegistry;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Configuration
public class MDTConfiguration {
	@Value("${instance-manager.type}") private String m_instanceManagerType;
	@Value("file:${instance-manager.workspaceDir}") private File m_workspaceDir;
	
	@Bean
	HttpServiceFactory getServiceFactory() throws MDTInstanceManagerException {
		try {
			return new HttpServiceFactory();
		}
		catch ( Exception e ) {
			throw new MDTInstanceManagerException("" + e);
		}
	}

	@Bean
	@ConfigurationProperties(prefix = "instance-manager")
	MDTInstanceManagerConfiguration getMDTInstanceManagerConfiguration() {
		return new MDTInstanceManagerConfiguration();
	}

	@Bean
	MDTInstanceManager getMDTInstanceManager() throws DockerException, InterruptedException {
		ServiceFactory svcFact = getServiceFactory();
		CachingFileMDTAASRegistry aasRegistry = getAssetAdministrationShellRegistry();
		CachingFileMDTSubmodelRegistry submodelRegistry = getSubmodelRegistry();
		
		JdbcProcessor jdbc = getJdbcProcessor();
		if ( !jdbc.existsTable(JdbcInstanceDescriptorManager.TABLE) ) {
			try ( Connection conn = jdbc.connect() ) {
				JdbcInstanceDescriptorManager.createTable(conn);
			}
			catch ( SQLException e ) {
				throw new MDTInstanceManagerException("Failed to format MDTInstanceManager, cause=" + e);
			}
		}
		
		InstanceDescriptorManager descMgr = new JdbcInstanceDescriptorManager(jdbc);
		
		MDTInstanceManagerConfiguration conf = getMDTInstanceManagerConfiguration();
		String format = conf.getRepositoryEndpointFormat();
		switch ( conf.getType() ) {
			case "jar":
				return JarInstanceManager.builder()
										.serviceFactory(svcFact)
										.aasRegistry(aasRegistry)
										.submodeRegistry(submodelRegistry)
										.repositoryEndpointFormat(format)
										.workspaceDir(m_workspaceDir)
										.executor(getJarInstanceExecutor())
										.instanceDescriptorManager(descMgr)
										.build();
			case "docker":
				DockerConfiguration dockerConf = getDockerConfiguration();
				return DockerInstanceManager.builder()
											.serviceFactory(svcFact)
											.aasRegistry(aasRegistry)
											.submodeRegistry(submodelRegistry)
											.repositoryEndpointFormat(format)
											.workspaceDir(m_workspaceDir)
											.instanceDescriptorManager(descMgr)
											.dockerHost(dockerConf.getDockerHost())
											.mountPrefix(dockerConf.getMountPrefix())
											.build();
			case "kubernetes":
				return KubernetesInstanceManager.builder()
												.serviceFactory(svcFact)
												.aasRegistry(aasRegistry)
												.submodeRegistry(submodelRegistry)
												.repositoryEndpointFormat(format)
												.workspaceDir(m_workspaceDir)
												.instanceDescriptorManager(descMgr)
												.build();
			default:
				throw new MDTInstanceManagerException("Unknown MDTInstanceManager type: "
														+ m_instanceManagerType);
		}
	}
	
	@Bean
	CachingFileMDTAASRegistry getAssetAdministrationShellRegistry() {
		CachingFileBasedRegistryConfiguration aasConf = getAASRegistryConfiguration();
		return new CachingFileMDTAASRegistry(aasConf.workspaceDir, aasConf.cacheSize);
	}

	@Bean
	@ConfigurationProperties(prefix = "registry.aas")
	CachingFileBasedRegistryConfiguration getAASRegistryConfiguration() {
		return new CachingFileBasedRegistryConfiguration();
	}
	
	@Bean
	CachingFileMDTSubmodelRegistry getSubmodelRegistry() {
		CachingFileBasedRegistryConfiguration smConf = getSubmodelRegistryConfiguration();
		return new CachingFileMDTSubmodelRegistry(smConf.workspaceDir, smConf.cacheSize);
	}
	
	@Bean
	@ConfigurationProperties(prefix = "registry.submodel")
	CachingFileBasedRegistryConfiguration getSubmodelRegistryConfiguration() {
		return new CachingFileBasedRegistryConfiguration();
	}
	
	@Setter
	public static class CachingFileBasedRegistryConfiguration {
		private File workspaceDir;
		private int cacheSize;
	}
	
	@Bean
	JdbcProcessor getJdbcProcessor() {
		JdbcConfiguration conf = getJdbcConfiguration();
		return JdbcProcessor.create(conf.getUrl(), conf.getUser(), conf.getPassword());
	}

	@Data
	public static class JdbcConfiguration {
		private String url;
		private String user;
		private String password;
	}
	@Bean
	@ConfigurationProperties(prefix = "instance-manager.jdbc")
	JdbcConfiguration getJdbcConfiguration() {
		return new JdbcConfiguration();
	}
	
//	@Bean
//	KubernetesRemote getKubernetesRemote() {
//		return KubernetesRemote.connect();
//	}
	
	@Bean
	JarInstanceExecutor getJarInstanceExecutor() {
		JarInstanceExecutor.Builder builder = getJarInstanceExecutorBuilder();
		return builder.build();
	}
	
	@Bean
	@ConfigurationProperties(prefix = "instance-manager.executor")
	JarInstanceExecutor.Builder getJarInstanceExecutorBuilder() {
		return JarInstanceExecutor.builder();
	}
	
	@Bean
	@ConfigurationProperties(prefix = "instance-manager.docker")
	DockerConfiguration getDockerConfiguration() {
		return new DockerConfiguration();
	}
}
