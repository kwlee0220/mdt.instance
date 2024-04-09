package mdt.instance.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import utils.jdbc.JdbcProcessor;
import utils.stream.FStream;
import utils.stream.Generator;

import mdt.model.instance.MDTInstanceManagerException;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class MDTInstanceStore  {
	private final JdbcProcessor m_jdbc;

	public MDTInstanceStore(JdbcProcessor jdbc) {
		m_jdbc = jdbc;

		try ( Connection conn = m_jdbc.connect() ) {
			if ( !m_jdbc.existsTable(TABLE_MDT_INSTANCES) ) {
				createTable(conn);
			}
		}
		catch ( SQLException e ) {
			throw new MDTInstanceManagerException(e);
		}
	}
	
	public JdbcProcessor getJdbcProcessor() {
		return m_jdbc;
	}
	
	public List<MDTInstanceRecord> getRecordAll() throws SQLException {
		try ( Connection conn = m_jdbc.connect();
				Statement stmt = conn.createStatement(); ) {
			ResultSet rs =  stmt.executeQuery(SQL_SELECT_ALL);
			return streamResultSet(rs).toList();
		}
	}

	public MDTInstanceRecord getRecordByInstanceId(String instId) throws SQLException {
		try ( Connection conn = m_jdbc.connect();
				PreparedStatement pstmt = conn.prepareStatement(SQL_SELECT_BY_INST_ID); ) {
			pstmt.setString(1, instId);
			ResultSet rs =  pstmt.executeQuery();
			return streamResultSet(rs).findFirst().getOrNull();
		}
	}

	public MDTInstanceRecord getRecordByAASId(String aasId) throws SQLException {
		try ( Connection conn = m_jdbc.connect();
				PreparedStatement pstmt = conn.prepareStatement(SQL_SELECT_BY_AAS_ID); ) {
			pstmt.setString(1, aasId);
			ResultSet rs =  pstmt.executeQuery();
			return streamResultSet(rs).findFirst().getOrNull();
		}
	}

	public List<MDTInstanceRecord> getRecordByAASIdShort(String aasIdShort) throws SQLException {
		try ( Connection conn = m_jdbc.connect();
				PreparedStatement pstmt = conn.prepareStatement(SQL_SELECT_BY_AAS_ID_SHORT); ) {
			pstmt.setString(1, aasIdShort);
			
			ResultSet rs =  pstmt.executeQuery();
			return streamResultSet(rs).toList();
		}
	}
	
	public void addRecord(MDTInstanceRecord record) throws SQLException {
		String insertSql = "insert into " + TABLE_MDT_INSTANCES
							+ "(instance_id, aas_id, aas_id_short, command) "
							+ "values (?, ?, ?, ?)";
		
		try ( Connection conn = m_jdbc.connect() ) {
			try ( PreparedStatement pstmt = conn.prepareStatement(insertSql); ) {
				pstmt.setString(1, record.getInstanceId());
				pstmt.setString(2, record.getAasId());
				pstmt.setString(3, record.getAasIdShort());
				pstmt.setString(4, record.getArguments());
				pstmt.execute();
			}
		}
	}
	
	public void updateRecord(MDTInstanceRecord record) throws SQLException {
		String insertInstance
			= "update " + TABLE_MDT_INSTANCES + " set "
					+ "aas_id_short = ?, "
					+ "command = ?, "
				+ "where instance_id = ?";
		
		try ( Connection conn = m_jdbc.connect() ) {
			try ( PreparedStatement pstmt = conn.prepareStatement(insertInstance); ) {
				pstmt.setString(1, record.getAasIdShort());
				pstmt.setString(2, record.getArguments());
				pstmt.setString(3, record.getInstanceId());
				pstmt.execute();
			}
		}
	}
	
	public void deleteRecord(String instanceId) throws SQLException {
		String deleteSql = String.format("delete from %s where instance_id='%s'",
										TABLE_MDT_INSTANCES, instanceId);
		m_jdbc.executeUpdate(deleteSql);
	}
	
	public void deleteRecordAll() throws SQLException {
		String deleteSql = "delete from " + TABLE_MDT_INSTANCES;
		m_jdbc.executeUpdate(deleteSql);
	}

	public static void createTable(Connection conn) throws SQLException {
		try (Statement stmt = conn.createStatement()) {
			stmt.executeUpdate(SQL_CREATE_TABLE_MDT_INSTANCES);
		}
	}

	public static void dropTable(Connection conn) throws SQLException {
		try (Statement stmt = conn.createStatement()) {
			stmt.executeUpdate("drop table if exists " + TABLE_MDT_INSTANCES);
		}
	}

	private static final String TABLE_MDT_INSTANCES = "mdt_instances";
	private static final String SQL_CREATE_TABLE_MDT_INSTANCES
		= "create table " + TABLE_MDT_INSTANCES + " ("
			+ "instance_id varchar not null, " 	// 1
			+ "aas_id varchar not null, " 		// 2
			+ "aas_id_short varchar, " 			// 3
			+ "command varchar not null, "		// 4
			+ "primary key (instance_id)"
			+ ")";
	private static final String SQL_SELECT_ALL
			= "select instance_id, aas_id, aas_id_short, command "
			+ "from " + TABLE_MDT_INSTANCES;
	private static final String SQL_SELECT_BY_INST_ID
			= "select instance_id, aas_id, aas_id_short, command "
			+ "from " + TABLE_MDT_INSTANCES
			+ " where instance_id = ?";
	private static final String SQL_SELECT_BY_AAS_ID
			= "select instance_id, aas_id, aas_id_short, command "
			+ "from " + TABLE_MDT_INSTANCES
			+ " where aas_id = ?";
	private static final String SQL_SELECT_BY_AAS_ID_SHORT
			= "select instance_id, aas_id, aas_id_short, command "
			+ "from " + TABLE_MDT_INSTANCES
			+ " where aas_id_short = ?";
	
	private FStream<MDTInstanceRecord> streamResultSet(ResultSet rs) throws SQLException {
		return new Generator<MDTInstanceRecord>(5) {
			@Override
			public void run() throws Throwable {
				while ( rs.next() ) {
					MDTInstanceRecord instRecord = MDTInstanceRecord.builder()
															.instanceId(rs.getString(1))
															.aasId(rs.getString(2))
															.aasIdShort(rs.getString(3))
															.arguments(rs.getString(4))
															.build();
					this.yield(instRecord);
				}
			}
		};
	}	
}
