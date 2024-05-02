package mdt.instance;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.springframework.util.FileSystemUtils;

import com.fasterxml.jackson.databind.json.JsonMapper;

import utils.func.Try;
import utils.stream.FStream;

import mdt.model.instance.MDTInstanceManagerException;
import mdt.model.registry.ResourceAlreadyExistsException;
import mdt.model.registry.ResourceNotFoundException;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class FileBasedInstanceDescriptorManager implements InstanceDescriptorManager {
	private static final String DESCRIPTOR_FILE_NAME = "descriptor.json";
	
	private final ReadWriteLock m_rwLock = new ReentrantReadWriteLock();
	private final File m_workspaceDir;
	private final JsonMapper m_mapper = JsonMapper.builder().build();
	
	public FileBasedInstanceDescriptorManager(File workspaceDir) {
		m_workspaceDir = workspaceDir;
	}

	@Override
	public InstanceDescriptor getInstanceDescriptor(String id) throws MDTInstanceManagerException,
																		ResourceNotFoundException {
		return readDescriptor(id);
	}

	@Override
	public InstanceDescriptor getInstanceDescriptorByAasId(String aasId) throws MDTInstanceManagerException,
																					ResourceNotFoundException {
		m_rwLock.readLock().lock();
		try {
			InstanceDescriptor desc = FStream.of(m_workspaceDir.listFiles(File::isDirectory))
												.map(File::getName)
												.mapOrIgnore(this::readDescriptor)
												.filter(d -> d.getAasId().equals(aasId))
												.findFirst()
												.getOrNull();
			if ( desc != null ) {
				return desc;
			}
			else {
				throw new ResourceNotFoundException("MDTInstance", "aas-id=" + aasId);
			}
		}
		finally {
			m_rwLock.readLock().unlock();
		}
	}

	@Override
	public List<InstanceDescriptor> getInstanceDescriptorAllByAasIdShort(String aasIdShort)
		throws MDTInstanceManagerException {
		m_rwLock.readLock().lock();
		try {
			return FStream.of(m_workspaceDir.listFiles(File::isDirectory))
							.map(File::getName)
							.mapOrIgnore(this::readDescriptor)
							.filter(d -> aasIdShort.equals(d.getAasIdShort()))
							.toList();
		}
		finally {
			m_rwLock.readLock().unlock();
		}
	}

	@Override
	public InstanceDescriptor getInstanceDescriptorBySubmodelId(String submodelId)
		throws MDTInstanceManagerException {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<InstanceDescriptor> getInstanceDescriptorAll() throws MDTInstanceManagerException {
		m_rwLock.readLock().lock();
		try {
			return FStream.of(m_workspaceDir.listFiles(File::isDirectory))
							.map(File::getName)
							.mapOrIgnore(this::readDescriptor)
							.toList();
		}
		finally {
			m_rwLock.readLock().unlock();
		}
	}

	@Override
	public void addInstanceDescriptor(InstanceDescriptor desc)
			throws MDTInstanceManagerException, ResourceAlreadyExistsException {
		File instanceDir = new File(m_workspaceDir, desc.getId());
		try {
			Files.createDirectories(instanceDir.toPath());
			
			File descFile = new File(instanceDir, "descriptor.json");
			m_mapper.writeValue(descFile, desc);
		}
		catch ( Exception e ) {
	    	Try.run(() -> FileSystemUtils.deleteRecursively(instanceDir));
			
			throw new MDTInstanceManagerException("Failed to create MDTInstance: desc=" + desc
												+ ", cause=" + e);
		}
	}

	@Override
	public void removeInstanceDescriptor(String id) throws MDTInstanceManagerException {
		m_rwLock.writeLock().lock();
		try {
	    	File instanceDir = new File(m_workspaceDir, id);
			File descFile = new File(instanceDir, "descriptor.json");
			descFile.delete();
		}
//		catch ( IOException e ) { }
		finally {
			m_rwLock.writeLock().unlock();
		}
	}
	
	private InstanceDescriptor readDescriptor(String id) throws ResourceNotFoundException,
																	MDTInstanceManagerException {
		m_rwLock.readLock().lock();
		try {
			File instanceDir = new File(m_workspaceDir, id);
			if ( !instanceDir.isDirectory() ) {
				throw new ResourceNotFoundException("MDTInstance", id);
			}
	
			File descFile = new File(instanceDir, DESCRIPTOR_FILE_NAME);
			try {
				return m_mapper.readValue(descFile, InstanceDescriptor.class);
			}
			catch ( Exception e ) {
				throw new MDTInstanceManagerException("Failed to read InstanceDescriptor file: " + descFile
														+ ", cause=" + e);
			}
		}
		finally {
			m_rwLock.readLock().unlock();
		}
	}
}
