package net.floodlightcontroller.sdiot;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import net.floodlightcontroller.sdiot.ConnectDatabase;

public class ResourceOrchestrator {
	
	public String getStateASdiotControllerFrDB(String controllerName){
        String state="";
//        String strSQL = "SELECT state from tab_iot_cluster WHERE controller_name = '" 
//                + controllerName + "' AND IP_Address = '" + ipAddr + "';";
        
        String strSQL = "SELECT state from tab_iot_cluster WHERE controller_name = '" 
                + controllerName + "';";
        try {
        	ConnectDatabase c = new ConnectDatabase();
            Statement stmt = (Statement) c.getConnection().createStatement();
            ResultSet rs = stmt.executeQuery(strSQL);
            
            while (rs.next()) {
                if (rs.getString("state").length() > 0) {
                    state = rs.getString("state");
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return state;
    }
}
