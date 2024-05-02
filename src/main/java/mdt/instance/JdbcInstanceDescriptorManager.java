package mdt.instance;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import com.google.common.collect.Lists;

import utils.func.CheckedFunctionX;
import utils.jdbc.JdbcProcessor;
import utils.jdbc.JdbcRowSource;

import mdt.model.instance.MDTInstanceManagerException;
import mdt.model.registry.ResourceAlreadyExistsException;
import mdt.model.registry.ResourceNotFoundException;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcInstanceDescriptorManager implements InstanceDescriptorManager {
	public static final String TABLE = "instance_descriptors";
	public static final String TABLE_SUBMODEL = "submodels";
	
	private final JdbcProcessor m_jdbc;
	
	public JdbcInstanceDescriptorManager(JdbcProcessor jdbc) {
		m_jdbc = jdbc;
	}

	@Override
	public InstanceDescriptor getInstanceDescriptor(String id) throws MDTInstanceManagerException,
																		ResourceNotFoundException {
		String sqlStrFormat = "select id, aas_id, aas_id_short, arguments from %s where id = '%s'";
		String sqlStr = String.format(sqlStrFormat, TABLE, id);
		
		try ( Connection conn = m_jdbc.connect() ) {
			conn.setAutoCommit(false);
			
			InstanceDescriptor desc = getInstanceDescriptor(conn, sqlStr, id);
			desc.setSubmodels(getSubmodelDescriptorAllById(conn, id));
			conn.commit();
			
			return desc;
		}
		catch ( SQLException e ) {
			throw new MDTInstanceManagerException("Failed to get InstanceDescriptor: id=" + id + ", cause=" + e);
		}
	}

	@Override
	public InstanceDescriptor getInstanceDescriptorByAasId(String aasId) throws MDTInstanceManagerException,
																				ResourceNotFoundException {
		String sqlStrFormat = "select id, aas_id, aas_id_short, arguments from %s where aas_id = '%s'";
		String sqlStr = String.format(sqlStrFormat, TABLE, aasId);
		
		try ( Connection conn = m_jdbc.connect() ) {
			conn.setAutoCommit(false);
			
			InstanceDescriptor desc = getInstanceDescriptor(conn, sqlStr, aasId);
			desc.setSubmodels(getSubmodelDescriptorAllById(conn, desc.getId()));
			conn.commit();
			
			return desc;
		}
		catch ( SQLException e ) {
			throw new MDTInstanceManagerException("Failed to get InstanceDescriptor: aas_id=" + aasId
													+ ", cause=" + e);
		}
	}

	@Override
	public List<InstanceDescriptor> getInstanceDescriptorAllByAasIdShort(String aasIdShort)
			throws MDTInstanceManagerException {
		String sqlStrFormat = "select id, aas_id, aas_id_short, arguments from %s where aas_id_short = '%s'";
		String sqlStr = String.format(sqlStrFormat, TABLE, aasIdShort);
		
		try ( Connection conn = m_jdbc.connect() ) {
			conn.setAutoCommit(false);
			
			List<InstanceDescriptor> descList = Lists.newArrayList();
			try ( Statement stmt = conn.createStatement() ) {
				ResultSet rs = stmt.executeQuery(sqlStr);
				while ( rs.next() ) {
					InstanceDescriptor desc = DESER.apply(rs);
					desc.setSubmodels(getSubmodelDescriptorAllById(conn, desc.getId()));
					descList.add(desc);
				}
			}
			conn.commit();
			
			return descList;
		}
		catch ( SQLException e ) {
			throw new MDTInstanceManagerException("Failed to get InstanceDescriptorList: aas_id_short=" + aasIdShort
													+ ", cause=" + e);
		}
	}

	@Override
	public List<InstanceDescriptor> getInstanceDescriptorAll() throws MDTInstanceManagerException {
		String sqlStrFormat = "select id, aas_id, aas_id_short, arguments from %s";
		String sqlStr = String.format(sqlStrFormat, TABLE);
		
		try ( Connection conn = m_jdbc.connect() ) {
			conn.setAutoCommit(false);
			
			List<InstanceDescriptor> descList = Lists.newArrayList();
			try ( Statement stmt = conn.createStatement() ) {
				ResultSet rs = stmt.executeQuery(sqlStr);
				while ( rs.next() ) {
					InstanceDescriptor desc = DESER.apply(rs);
					desc.setSubmodels(getSubmodelDescriptorAllById(conn, desc.getId()));
					descList.add(desc);
				}
			}
			conn.commit();
			
			return descList;
		}
		catch ( SQLException e ) {
			throw new MDTInstanceManagerException("Failed to get all InstanceDescriptorList, cause=" + e);
		}
	}

	@Override
	public InstanceDescriptor getInstanceDescriptorBySubmodelId(String submodelId)
		throws MDTInstanceManagerException {
		String sqlStrFormat = "select id, aas_id, aas_id_short, arguments "
							+ "from %s as d, %s as s "
							+ "where d.id = s.instance_id "
							+ "and s.submodel_id = %s ";
		String sqlStr = String.format(sqlStrFormat, TABLE, TABLE_SUBMODEL, submodelId);
		
		try ( Connection conn = m_jdbc.connect() ) {
			conn.setAutoCommit(false);
			
			InstanceDescriptor desc
					= JdbcRowSource.select(DESER)
									.from(conn)
									.executeQuery(sqlStr)
									.first()
									.getOrThrow(() -> new ResourceNotFoundException("MDTInstance", "submodel_id=" + submodelId));
			desc.setSubmodels(getSubmodelDescriptorAllById(conn, desc.getId()));
			conn.commit();
			
			return desc;
		}
		catch ( SQLException e ) {
			throw new MDTInstanceManagerException("Failed to get InstanceDescriptor: submodel_id=" + submodelId
													+ ", cause=" + e);
		}
	}
	
	@Override
	public void addInstanceDescriptor(InstanceDescriptor desc)
			throws MDTInstanceManagerException, ResourceAlreadyExistsException {
		try ( Connection conn = m_jdbc.connect() ) {
			conn.setAutoCommit(false);
			
			String insertDescSql = "insert into " + TABLE + "(id, aas_id, aas_id_short, arguments) "
									+ "values (?, ?, ?, ?)";
			try ( PreparedStatement pstmt = conn.prepareStatement(insertDescSql) ) {
				pstmt.setString(1, desc.getId());
				pstmt.setString(2, desc.getAasId());
				pstmt.setString(3, desc.getAasIdShort());
				pstmt.setString(4, desc.getArguments());
				pstmt.execute();
			}
			String insertSubmodelSql = "insert into " + TABLE_SUBMODEL
									+ "(submodel_id, instance_id, submodel_id_short) "
									+ "values (?, ?, ?)";
			try ( PreparedStatement pstmt = conn.prepareStatement(insertSubmodelSql) ) {
				for ( InstanceSubmodelDescriptor smDesc: desc.getSubmodels() ) {
					pstmt.setString(1, smDesc.getSubmodelId());
					pstmt.setString(2, smDesc.getInstanceId());
					pstmt.setString(3, smDesc.getSubmodelIdShort());
					pstmt.execute();
				}
			}
			conn.commit();
		}
		catch ( SQLException e ) {
			throw new MDTInstanceManagerException("Failed to insert InstanceDescriptor: id=" + desc.getId()
													+ ", cause=" + e);
		}
	}

	@Override
	public void removeInstanceDescriptor(String id) throws MDTInstanceManagerException {
		try ( Connection conn = m_jdbc.connect() ) {
			String deleteDescSql = String.format("delete from %s where id='%s'", TABLE, id);
			try ( Statement stmt = conn.createStatement() ) {
				stmt.execute(deleteDescSql);
			}
		}
		catch ( SQLException e ) {
			throw new MDTInstanceManagerException("Failed to remove InstanceDescriptor: id=" + id + ", cause=" + e);
		}
	}

	public static void createTable(Connection conn) throws SQLException {
		try ( Statement stmt = conn.createStatement() ) {
			stmt.executeUpdate(SQL_CREATE_TABLE_DESCRIPTORS);
			stmt.executeUpdate(SQL_CREATE_INDEX_DESCRIPTORS);
			stmt.executeUpdate(SQL_CREATE_TABLE_SUMODELS);
		}
	}

	public static void dropTable(Connection conn) throws SQLException {
		try ( Statement stmt = conn.createStatement() ) {
			stmt.executeUpdate("drop table if exists " + SQL_CREATE_TABLE_SUMODELS);
			stmt.executeUpdate("drop table if exists " + SQL_CREATE_TABLE_DESCRIPTORS);
		}
	}
	
	private InstanceDescriptor getInstanceDescriptor(Connection conn, String sqlStr, String id) throws SQLException {
		try ( Statement stmt = conn.createStatement() ) {
			ResultSet rs = stmt.executeQuery(sqlStr);
			if ( rs.next() ) {
				return DESER.apply(rs);
			}
			else {
				throw new ResourceNotFoundException("MDTInstance", id);
			}
		}
	}
	
	private static final String SQL_GET_SUBMODELS = String.format(
			"select instance_id, submodel_id, submodel_id_short from %s where instance_id = ?",
			TABLE_SUBMODEL);
	private List<InstanceSubmodelDescriptor> getSubmodelDescriptorAllById(Connection conn, String id)
		throws SQLException {
		try ( PreparedStatement pstmt = conn.prepareStatement(SQL_GET_SUBMODELS) ) {
			pstmt.setString(1, id);
			
			List<InstanceSubmodelDescriptor> submodelDescList = Lists.newArrayList();
			ResultSet rs = pstmt.executeQuery();
			while ( rs.next() ) {
				submodelDescList.add(new InstanceSubmodelDescriptor(rs.getString(1), rs.getString(2),
																	rs.getString(3)));
			}
			return submodelDescList;
		}
	}
	
	private static final CheckedFunctionX<ResultSet, InstanceDescriptor, SQLException> DESER = new CheckedFunctionX<>() {
		@Override
		public InstanceDescriptor apply(ResultSet rs) throws SQLException {
			return new InstanceDescriptor(rs.getString(1), rs.getString(2), rs.getString(3), null,
											rs.getString(4));
		}
	};
	
	private static final CheckedFunctionX<ResultSet, InstanceSubmodelDescriptor, SQLException> SUBMODEL_DESER
	= new CheckedFunctionX<>() {
		@Override
		public InstanceSubmodelDescriptor apply(ResultSet rs) throws SQLException {
			return new InstanceSubmodelDescriptor(rs.getString(1), rs.getString(2), rs.getString(3));
		}
	};

	private static final String SQL_CREATE_TABLE_DESCRIPTORS = "create table " + TABLE + " ("
			+ "id varchar not null, " // 1
			+ "aas_id varchar not null, " // 2
			+ "aas_id_short varchar, " // 3
			+ "arguments varchar, " // 4
			+ "primary key (id)" + ")";
	private static final String SQL_CREATE_INDEX_DESCRIPTORS
		= "create unique index " + TABLE + "_idx on " + TABLE + "(aas_id)";
	
	private static final String SQL_CREATE_TABLE_SUMODELS = "create table " + TABLE_SUBMODEL + " ("
			+ "submodel_id varchar not null, " // 1
			+ "instance_id varchar not null, " // 2
			+ "submodel_id_short varchar, " // 3
			+ "primary key (submodel_id),"
			+ "foreign key (instance_id) references " + TABLE + "(id) on delete cascade"
			+ ")";
}
