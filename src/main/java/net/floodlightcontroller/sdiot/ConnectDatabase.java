package net.floodlightcontroller.sdiot;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import net.floodlightcontroller.core.internal.Controller;

/**
 * This class enable a connection to the database
 * @author chau
 */
public class ConnectDatabase {
    String USERNAME = "root";
    String PASSWORD = "11111111";
    String CONN_STRING = "jdbc:mysql://localhost:3306/db_floodlight";
    BufferedReader is;
    PrintStream os;
    int id;
    private Controller server;
    private Connection connMySql = null;     //MySQL Connection

    public Connection getConnection() {
        return connMySql;
    }

    public ConnectDatabase() {
        int port = 9998;
        startServer();
    }

    /**
     * Try to open a server socket on the given port
     * Note that we can't choose a port less than 1024 if we are not
     * privileged users (root)
     */
    public void startServer() {
        try {
        	Class.forName("com.mysql.jdbc.Driver");
            connMySql = initMySQLConnection();
            String sql = "select * from tab_iot_cluster;";
            try {
                Statement stmt = (Statement) connMySql.createStatement();
                ResultSet rs = stmt.executeQuery(sql);
                while (rs.next()) {
                    if (rs.getString("cluster_name").length() > 0) {
                        String controller_Name = rs.getString("cluster_name");
//                        String ip_Address = rs.getString("IP_Address");
//                        System.out.println("ConnectDatabase: Sdiot controller name is " + controller_Name + ", IP_Addr =" + ip_Address);

                    }
                }
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public Connection initMySQLConnection() {
        try {
            return DriverManager.getConnection(CONN_STRING, USERNAME, PASSWORD);

        } catch (SQLException e) {
            System.out.println("Can not initialise MySQL Connection. Error: " + e.getMessage());
            return null;
        }
    }
}