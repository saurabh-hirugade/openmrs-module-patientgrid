package org.openmrs.module.patientgrid;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.openmrs.*;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.patientgrid.PatientGridColumn.ColumnDatatype;
import org.openmrs.module.patientgrid.converter.PatientGridAgeConverter;
import org.openmrs.module.patientgrid.converter.PatientGridAgeRangeConverter;
import org.openmrs.module.patientgrid.definition.*;
import org.openmrs.module.patientgrid.filter.PatientGridFilterUtils;
import org.openmrs.module.patientgrid.filter.definition.LocationCohortDefinition;
import org.openmrs.module.patientgrid.period.DateRange;
import org.openmrs.module.reporting.common.AgeRange;
import org.openmrs.module.reporting.common.SortCriteria;
import org.openmrs.module.reporting.common.TimeQualifier;
import org.openmrs.module.reporting.data.converter.DataConverter;
import org.openmrs.module.reporting.data.converter.ObjectFormatter;
import org.openmrs.module.reporting.data.converter.PropertyConverter;
import org.openmrs.module.reporting.data.patient.definition.EncountersForPatientDataDefinition;
import org.openmrs.module.reporting.data.patient.service.PatientDataService;
import org.openmrs.module.reporting.data.person.definition.GenderDataDefinition;
import org.openmrs.module.reporting.data.person.definition.PreferredNameDataDefinition;
import org.openmrs.module.reporting.dataset.definition.PatientDataSetDefinition;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.evaluation.EvaluationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

import static org.openmrs.module.patientgrid.PatientGridConstants.*;
import static org.openmrs.module.reporting.common.Age.Unit.YEARS;

public class PatientGridUtils {
	
	public static final ObjectMapper MAPPER = new ObjectMapper();
	
	private static final Logger LOG = LoggerFactory.getLogger(PatientGridUtils.class);
	
	private static final DataConverter COUNTRY_CONVERTER = new PropertyConverter(String.class, "country");
	
	private static final LocationEncounterDataDefinition LOCATION_DATA_DEF = new LocationEncounterDataDefinition();
	
	private static final PreferredNameDataDefinition NAME_DATA_DEF = new PreferredNameDataDefinition();
	
	private static final GenderDataDefinition GENDER_DATA_DEF = new GenderDataDefinition();
	
	private static final PersonUuidDataDefinition UUID_DATA_DEF = new PersonUuidDataDefinition();
	
	private static final DataConverter OBJECT_CONVERTER = new ObjectFormatter();
	
	private static final DataConverter AGE_CONVERTER = new PatientGridAgeConverter();
	
	private static PatientGridAgeRangeConverter ageRangeConverter;
	
	/**
	 * Create a {@link PatientDataSetDefinition} instance from the specified {@link PatientGrid} object
	 *
	 * @param patientGrid {@link PatientGrid} object
	 * @param includeObs specifies if obs data should include or not
	 * @return PatientDataSetDefinition
	 */
	public static PatientDataSetDefinition createPatientDataSetDefinition(PatientGrid patientGrid, boolean includeObs,
	        String currentUserTimeZone) {
		PatientDataSetDefinition dataSetDef = new PatientDataSetDefinition();
		dataSetDef.addColumn(COLUMN_UUID, UUID_DATA_DEF, (String) null);
		LocationCohortDefinition locationCohortDefinition = PatientGridFilterUtils.extractLocations(patientGrid);
		DateRange dateRange = PatientGridFilterUtils.extractPeriodRange(patientGrid, currentUserTimeZone);
		
		for (PatientGridColumn columnDef : patientGrid.getColumns()) {
			if (!includeObs && columnDef.getDatatype() == ColumnDatatype.OBS) {
				continue;
			}
			
			switch (columnDef.getDatatype()) {
				case NAME:
					dataSetDef.addColumn(columnDef.getName(), NAME_DATA_DEF, (String) null, OBJECT_CONVERTER);
					break;
				case GENDER:
					dataSetDef.addColumn(columnDef.getName(), GENDER_DATA_DEF, (String) null);
					break;
				case ENC_DATE:
					EncounterDatePatientGridColumn dateColumn = (EncounterDatePatientGridColumn) columnDef;
					DateForLatestEncounterPatientDataDefinition dateDef = new DateForLatestEncounterPatientDataDefinition();
					dateDef.setEncounterType(dateColumn.getEncounterType());
					dateDef.setPeriodRange(dateRange);
					dateDef.setLocationCohortDefinition(locationCohortDefinition);
					SortCriteria sortCriteria = new SortCriteria();
					sortCriteria.addSortElement(columnDef.getName(), SortCriteria.SortDirection.DESC);
					dataSetDef.setSortCriteria(sortCriteria);
					dataSetDef.addColumn(columnDef.getName(), dateDef, (String) null);
					break;
				case ENC_AGE:
					AgeAtEncounterPatientGridColumn ageColumn = (AgeAtEncounterPatientGridColumn) columnDef;
					AgeAtLatestEncounterPatientDataDefinition ageDef = new AgeAtLatestEncounterPatientDataDefinition();
					ageDef.setEncounterType(ageColumn.getEncounterType());
					ageDef.setPeriodRange(dateRange);
					ageDef.setLocationCohortDefinition(locationCohortDefinition);
					if (ageColumn.getConvertToAgeRange()) {
						if (ageRangeConverter == null) {
							ageRangeConverter = new PatientGridAgeRangeConverter();
							getAgeRanges().forEach(r -> ageRangeConverter.addAgeRange(r));
						}
						
						dataSetDef.addColumn(columnDef.getName(), ageDef, (String) null, ageRangeConverter);
					} else {
						dataSetDef.addColumn(columnDef.getName(), ageDef, (String) null, AGE_CONVERTER);
					}
					
					break;
				case OBS:
					ObsPatientGridColumn obsColumn = (ObsPatientGridColumn) columnDef;
					ObsForLatestEncounterPatientDataDefinition obsDataDef = new ObsForLatestEncounterPatientDataDefinition();
					obsDataDef.setConcept(obsColumn.getConcept());
					obsDataDef.setEncounterType(obsColumn.getEncounterType());
					obsDataDef.setLocationCohortDefinition(locationCohortDefinition);
					obsDataDef.setPeriodRange(dateRange);
					dataSetDef.addColumn(columnDef.getName(), obsDataDef, (String) null, OBS_CONVERTER);
					break;
				case ENC_LOCATION:
					dataSetDef.addColumn(columnDef.getName(), LOCATION_DATA_DEF, (String) null, OBJECT_CONVERTER);
					break;
				case ENC_COUNTRY:
					dataSetDef.addColumn(columnDef.getName(), LOCATION_DATA_DEF, (String) null, COUNTRY_CONVERTER);
					break;
				default:
					throw new APIException("Don't know how to handle column type: " + columnDef.getDatatype());
			}
		}
		
		return dataSetDef;
	}
	
	/**
	 * Fetches the encounters for the specified cohort of patients matching the given encounter type.
	 *
	 * @param type the encounter type to match
	 * @param context {@link EvaluationContext} object
	 * @param mostRecentOnly specifies whether to return only the most recent encounter for each patient
	 *            or their encounter history
	 * @return a map of patient ids to encounters
	 */
	public static Map<Integer, Object> getEncounters(EncounterType type, EvaluationContextPersistantCache context,
	        LocationCohortDefinition locationCohortDefinition, boolean mostRecentOnly, DateRange periodRange)
	        throws EvaluationException {
		Cohort cohort = null;
		if (context != null) {
			cohort = context.getBaseCohort();
		}
		
		if (cohort == null || cohort.size() > 1) {
			LOG.info("Fetching encounters of type:{}, most recently: {}", type, mostRecentOnly);
		}
		
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		
		EncountersForPatientDataDefinition encDef = new EncountersForPatientDataDefinition();
		
		if (periodRange != null) {
			encDef.setOnOrAfter(periodRange.getFromInServerTz());
			encDef.setOnOrBefore(periodRange.getToInServerTz());
		}
		if (locationCohortDefinition != null) {
			if (locationCohortDefinition.getCountry()) {
				Set<Location> allLocations = new HashSet<>();
				locationCohortDefinition.getLocations().forEach(l -> allLocations.addAll(l.getDescendantLocations(false)));
				encDef.setLocationList(new ArrayList<>(allLocations));
			} else {
				encDef.setLocationList(locationCohortDefinition.getLocations());
			}
		}
		
		encDef.setTypes(Collections.singletonList(type));
		if (mostRecentOnly) {
			encDef.setWhich(TimeQualifier.LAST);
		}
		
		Map<Integer, Object> results = Context.getService(PatientDataService.class).evaluate(encDef, context).getData();
		results.entrySet().forEach(entry -> {
			if (entry.getValue() instanceof List) {
				((List) entry.getValue()).forEach(encounter -> {
					context.saveLatestEncDate(entry.getKey(), (Encounter) encounter);
				});
			} else {
				context.saveLatestEncDate(entry.getKey(), (Encounter) entry.getValue());
			}
		});
		//sort from newer to older. Warn LocationEncounterDataEvaluator is expecting this order to get the newer.
		EncounterComparator.sortListOfEncounters(results);
		
		stopWatch.stop();
		
		if (cohort == null || cohort.size() > 1) {
			LOG.info("Fetching encounters of type: {}, most recently: {} completed in {}", type, mostRecentOnly,
			    stopWatch.toString());
		}
		
		return results;
	}
	
	/**
	 * Gets the observation from the specified encounter with a question concept that matches the
	 * specified concept ignoring obs groupings and voided obs.
	 *
	 * @param encounter the encounter containing the obs to search
	 * @param concept the question concept to match
	 * @return Observation if match is found otherwise null
	 */
	public static Obs getObsByConcept(Encounter encounter, Concept concept) {
		Set<Obs> obs = encounter.getObs();
		if (obs != null && concept != null) {
			int conceptHashcode = concept.hashCode();
			List<Obs> matches = obs.stream()
			        .filter(o -> !o.getVoided() && o.getObsGroup() == null && o.getConcept().hashCode() == conceptHashcode
			                && o.getConcept().equals(concept) && !o.hasGroupMembers(true))
			        .collect(Collectors.toList());
			//		List<Obs> matches = encounter.getObsAtTopLevel(false).stream()
			//		        .filter(o -> o.getConcept().equals(concept) && !o.hasGroupMembers(true)).collect(Collectors.toList());
			
			if (matches.size() > 1) {
				LOG.debug("Multi obs answer not yet supported. No data will be returned for " + encounter);
			}
			
			if (matches.size() == 1) {
				return matches.get(0);
			}
		}
		return null;
	}
	
	/**
	 * Parses the specified age range string to generate a list of {@link AgeRange} objects
	 *
	 * @param ageRangesAsString the age range string to parse
	 * @return a list of {@link AgeRange} objects
	 */
	protected static List<AgeRange> parseAgeRangeString(String ageRangesAsString) {
		String[] ranges = ageRangesAsString.split(",");
		List<AgeRange> ageRanges = new ArrayList(ranges.length);
		for (int i = 0; i < ranges.length; i++) {
			if (i < ranges.length - 1) {
				String[] rangeAndLabel = ranges[i].trim().split(":");
				String range;
				String label;
				if (rangeAndLabel.length == 1) {
					range = rangeAndLabel[0].trim();
					label = range;
				} else {
					range = rangeAndLabel[0].trim();
					label = rangeAndLabel[1].trim();
				}
				
				String[] minAndMax = range.split("-");
				Integer min = Integer.parseInt(minAndMax[0]);
				Integer max = Integer.parseInt(minAndMax[1]);
				ageRanges.add(new AgeRange(min, YEARS, max, YEARS, label));
			} else {
				ageRanges.add(new AgeRange(ageRanges.get(i - 1).getMaxAge() + 1, YEARS, null, null, ranges[i].trim()));
			}
		}
		
		return ageRanges;
	}
	
	/**
	 * Gets the list of age ranges
	 *
	 * @return list of AgeRange objects
	 */
	public static List<AgeRange> getAgeRanges() {
		//TODO cache the age ranges and update with a GlobalPropertyListener
		String ageRange = Context.getAdministrationService().getGlobalProperty(GP_AGE_RANGES);
		if (StringUtils.isBlank(ageRange)) {
			throw new APIException(
			        "No age ranges defined, please set the value for the global property named: " + GP_AGE_RANGES);
		}
		
		return parseAgeRangeString(ageRange);
	}
	
	private static AgeRange extractFromAgeRangeName(String value) {
		List<AgeRange> ageRanges = getAgeRanges();
		Optional<AgeRange> first = ageRanges.stream().filter(ageRange -> ageRange.getLabel().equals(value)).findFirst();
		return first.isPresent() ? first.get() : null;
	}
	
	/**
	 * Gets all encounter types defined on all ObsPatientGridColumns in the specified
	 * {@link PatientGrid}
	 *
	 * @param patientGrid {@link PatientGrid} object
	 * @return set of {@link EncounterType} objects
	 */
	public static Set<EncounterType> getEncounterTypes(PatientGrid patientGrid) {
		return patientGrid.getObsColumns().stream().map(c -> c.getEncounterType()).collect(Collectors.toSet());
	}
	
	/**
	 * Converts the specified string to the specified type
	 *
	 * @param value the value to convert
	 * @param clazz the type to convert to
	 * @return the converted value
	 */
	public static <T> T convert(String value, Class<T> clazz) throws APIException {
		Object ret;
		if (Double.class.isAssignableFrom(clazz)) {
			ret = Double.valueOf(value);
		} else if (Integer.class.isAssignableFrom(clazz)) {
			ret = Integer.valueOf(value);
		} else if (Boolean.class.isAssignableFrom(clazz)) {
			ret = Boolean.valueOf(value);
		} else if (Date.class.isAssignableFrom(clazz)) {
			try {
				ret = DATETIME_FORMAT.parse(value);
			}
			catch (ParseException e) {
				try {
					ret = DATE_FORMAT.parse(value);
				}
				catch (ParseException pe) {
					throw new APIException("Failed to convert " + value + " to a date", pe);
				}
			}
		} else if (Concept.class.isAssignableFrom(clazz)) {
			ret = Context.getConceptService().getConceptByUuid(value);
		} else if (Location.class.isAssignableFrom(clazz)) {
			ret = Context.getLocationService().getLocationByUuid(value);
		} else if (AgeRange.class.isAssignableFrom(clazz)) {
			try {
				Map map = MAPPER.readValue(value, Map.class);
				ret = new AgeRange((Integer) map.get("minAge"), (Integer) map.get("maxAge"));
			}
			catch (IOException e) {
				ret = extractFromAgeRangeName(value);
			}
		} else {
			throw new APIException("Don't know how to convert operand value to type: " + clazz.getName());
		}
		
		return (T) ret;
	}
	
	public static String getCurrentUserTimeZone() {
		String userTimeZone = Context.getAuthenticatedUser().getUserProperty("clientTimezone");
		if (userTimeZone == null) {
			userTimeZone = TimeZone.getDefault().getID();
			LOG.warn("use server timezone {} instead of User Timezone", userTimeZone);
		}
		return userTimeZone;
	}
	
}
