package net.floodlightcontroller.sdiot.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using = SdiotClusterSerializer.class)
public class SdiotCluster {
	
	private String clusterName; //clusterName managedArea managedLocation providedServices
	private String managedArea; //the are where the cluster located
	private String managedLocation; //the locations that are managed by the sdiot controller
	private String providedServices; //all services provided by the sdiot cluster
	private String numTask;
	
	public SdiotCluster() {};
	
	/*public SdiotCluster(String name, String area, String locations, String services) {
		this.clusterName = name;
		this.managedArea = area;
		this.managedLocation = locations;
		this.providedServices = services;
	}
	*/
	public SdiotCluster(String name, String area, String locations, String services, String number) {
		this.clusterName = name;
		this.managedArea = area;
		this.managedLocation = locations;
		this.providedServices = services;
		this.numTask = number;
	}
	
	
	public String getNumTask() {
		return numTask;
	}

	public void setNumTask(String numTask) {
		this.numTask = numTask;
	}

	public String getClusterName() {
		return clusterName;
	}
	public void setClusterName(String clusterName) {
		this.clusterName = clusterName;
	}
	public String getManagedArea() {
		return managedArea;
	}
	public void setManagedArea(String managedArea) {
		this.managedArea = managedArea;
	}
	public String getManagedLocation() {
		return managedLocation;
	}
	public void setManagedLocation(String managedLocation) {
		this.managedLocation = managedLocation;
	}
	public String getProvidedServices() {
		return providedServices;
	}
	public void setProvidedServices(String providedServices) {
		this.providedServices = providedServices;
	}
	@Override
	public String toString() {
		return "SdiotCluster [clusterName=" + clusterName + ", managedArea=" + managedArea + ", managedLocation="
				+ managedLocation + ", providedServices=" + providedServices + ", numTask=" + numTask + "]";
	}
	
	
}
