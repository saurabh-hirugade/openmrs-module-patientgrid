package org.openmrs.module.patientgrid.definition;

import org.openmrs.EncounterType;
import org.openmrs.module.patientgrid.period.DateRange;
import org.openmrs.module.reporting.data.BaseDataDefinition;
import org.openmrs.module.reporting.data.patient.definition.PatientDataDefinition;
import org.openmrs.module.reporting.definition.configuration.ConfigurationProperty;

import java.util.Date;

public class DateForLatestEncounterPatientDataDefinition extends BaseDataDefinition implements PatientDataDefinition {
	
	@ConfigurationProperty
	private EncounterType encounterType;
	
	public DateRange getPeriodRange() {
		return periodRange;
	}
	
	public void setPeriodRange(DateRange periodRange) {
		this.periodRange = periodRange;
	}
	
	@ConfigurationProperty
	private DateRange periodRange;
	
	@Override
	public Class<?> getDataType() {
		return Date.class;
	}
	
	/**
	 * Gets the encounterType
	 *
	 * @return the encounterType
	 */
	public EncounterType getEncounterType() {
		return encounterType;
	}
	
	/**
	 * Sets the encounterType
	 *
	 * @param encounterType the encounterType to set
	 */
	public void setEncounterType(EncounterType encounterType) {
		this.encounterType = encounterType;
	}
	
}
