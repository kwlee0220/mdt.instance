package mdt;

import java.io.File;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import utils.jdbc.JdbcProcessor;

import mdt.client.HttpServiceFactory;
import mdt.exector.jar.model.JarInstanceExecutor;
import mdt.instance.model.MDTInstanceStore;
import mdt.registry.model.CachingFileMDTAASRegistry;
import mdt.registry.model.CachingFileMDTSubmodelRegistry;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Configuration
public class MDTConfiguration {
	@Value("file:${registry.workspace_dir}") private File m_workspaceDir;
	@Value("${registry.cache_size.aas}") private int m_aasCacheSize;
	@Value("${registry.cache_size.submodel}") private int m_submodelCacheSize;
	
	@Bean
	HttpServiceFactory getServiceFactory() throws KeyManagementException, NoSuchAlgorithmException {
		return new HttpServiceFactory();
	}
	
	@Bean
	CachingFileMDTAASRegistry getAASRegistryFileStore() {
		File workspaceDir = new File(m_workspaceDir, "shells");
		return new CachingFileMDTAASRegistry(workspaceDir, m_aasCacheSize);
	}
	
	@Bean
	CachingFileMDTSubmodelRegistry getSubmodelRegistryFileStore() {
		File workspaceDir = new File(m_workspaceDir, "submodels");
		return new CachingFileMDTSubmodelRegistry(workspaceDir, m_submodelCacheSize);
	}
	
	@Bean
	@ConfigurationProperties(prefix = "executor")
	JarInstanceExecutor getJarInstanceExecutor() {
		return new JarInstanceExecutor();
	}
	
	@Bean
	MDTInstanceStore getMDTInstanceStore() throws Exception {
		JdbcProcessor jdbc = getJdbcProcessor();
		return new MDTInstanceStore(jdbc);
	}
	
	@Bean
	JdbcProcessor getJdbcProcessor() {
		JdbcProcessor.Configuration jconf = getJdbcConfiguration();
		return JdbcProcessor.create(jconf);
	}
	
	@Bean
	@ConfigurationProperties(prefix = "jdbc")
	JdbcProcessor.Configuration getJdbcConfiguration() {
		return new JdbcProcessor.Configuration();
	}
}
