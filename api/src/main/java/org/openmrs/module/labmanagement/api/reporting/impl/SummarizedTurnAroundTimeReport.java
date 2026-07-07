package org.openmrs.module.labmanagement.api.reporting.impl;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVParserBuilder;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.labmanagement.api.LabManagementService;
import org.openmrs.module.labmanagement.api.dto.*;
import org.openmrs.module.labmanagement.api.model.BatchJob;
import org.openmrs.module.labmanagement.api.model.ReferralLocation;
import org.openmrs.module.labmanagement.api.reporting.GenericObject;
import org.openmrs.module.labmanagement.api.reporting.ReportGenerator;
import org.openmrs.module.labmanagement.api.utils.DateUtil;
import org.openmrs.module.labmanagement.api.utils.FileUtil;
import org.openmrs.module.labmanagement.api.utils.GlobalProperties;
import org.openmrs.module.labmanagement.api.utils.TurnAroundTimeCalculator;
import org.openmrs.module.labmanagement.api.utils.csv.CSVWriter;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SummarizedTurnAroundTimeReport extends ReportGenerator {

	protected final Log log = LogFactory.getLog(this.getClass());

	protected LabManagementService labManagementService = null;

	protected BatchJob batchJob = null;
	protected Concept testConcept = null;

	private File step1File = null;

	private int abortReadingTat = 0;

	private Integer tatReadCount = 0;

	Integer pageSize = null;

	CSVWriter csvWriter = null;

	Writer writer = null;

	public int getAbortReadingTat() {
		return abortReadingTat;
	}

	public void setAbortReadingTat(int abortReadingTat) {
		this.abortReadingTat = abortReadingTat;
	}

	@Override
	public void execute(BatchJob batchJob, Function<BatchJob, Boolean> shouldStopExecution) {
		this.batchJob = batchJob;
		labManagementService = Context.getService(LabManagementService.class);
		pageSize = GlobalProperties.GetReportingRecordsPageSize();

		if (!restoreExecutionState(batchJob, labManagementService, log)) {
			return;
		}

		if (shouldStopExecution.apply(batchJob)) {
			return;
		}

		try {
			parameters =  parameters == null ? GenericObject.parseJson(batchJob.getParameters()) : parameters;
		}
		catch (Exception e) {
			labManagementService.failBatchJob(batchJob.getUuid(), "Failed to read parameters");
			log.error(e.getMessage(), e);
			return;
		}

		Date startDate = getStartDate(parameters);
		Date endDate = getEndDate(parameters);
		String diagnosticLocationUuid = getDiagnosticLocation(parameters);
		String testTypeUuid = getTestType(parameters);
		String patientUuid = getPatient(parameters);
		String referralLocationUuid = getReferralLocation(parameters);
		String testerUuid = getTester(parameters);

		CSVWriter csvWriter = null;
		Writer writer = null;

		try {
			TestRequestReportItemFilter filter = new TestRequestReportItemFilter();
			filter.setStartDate(startDate);
			if (endDate != null) {
				filter.setEndDate(DateUtil.endOfDay(endDate));
			}

			if (!StringUtils.isBlank(diagnosticLocationUuid)) {
				Location location = Context.getLocationService().getLocationByUuid(diagnosticLocationUuid);
				if (location == null) {
					labManagementService.failBatchJob(batchJob.getUuid(), "Report diagnostic location parameter not found");
					return;
				}
				filter.setDiagonisticLocationId(location.getId());
			}

			if (!StringUtils.isBlank(patientUuid)) {
				Patient patient = Context.getPatientService().getPatientByUuid(patientUuid);
				if (patient == null) {
					labManagementService.failBatchJob(batchJob.getUuid(), "Report patient parameter not found");
					return;
				}
				filter.setPatientId(patient.getId());
			}

			if (!StringUtils.isBlank(referralLocationUuid)) {
				ReferralLocation referralLocation = labManagementService.getReferralLocationByUuid(referralLocationUuid);
				if (referralLocation == null) {
					labManagementService.failBatchJob(batchJob.getUuid(), "Report reference location parameter not found");
					return;
				}
				filter.setReferralLocationId(referralLocation.getId());
			}


			if (!StringUtils.isBlank(testTypeUuid)) {
				testConcept = Context.getConceptService().getConceptByUuid(testTypeUuid);
				if (testConcept == null) {
					labManagementService.failBatchJob(batchJob.getUuid(), "Report test type parameter not found");
					return;
				}
				filter.setTestConceptId(testConcept.getId());
			}


			if (!StringUtils.isBlank(testerUuid)) {
				User user = Context.getUserService().getUserByUuid(testerUuid);
				if (user == null) {
					labManagementService.failBatchJob(batchJob.getUuid(), "Report tester parameter not found");
					return;
				}
				filter.setTesterUserId(user.getId());
			}

			filter.setLimit(pageSize);

			boolean step1Complete = requireTurnAroundTime(filter,shouldStopExecution, parameters);
			if(abortReadingTat > 0) {
				if(abortReadingTat == 1){
					return;
				}
				else if (!step1Complete) {
					labManagementService.failBatchJob(batchJob.getUuid(), "Failed to fetch the turn around time in step 1");
					return;
				}
			}

			updateExecutionState(batchJob, executionState, pageIndex, recordsProcessed, null, null, labManagementService, 1);

			Map<Integer, Object[]> list = new HashMap<>();
			try (Reader reader = Files.newBufferedReader(step1File.toPath())) {
				int testConceptId = 0;
				int testName=1;
				int tat = 2;
				CSVReader csvReader = new CSVReaderBuilder(reader).withCSVParser(new CSVParserBuilder().withSeparator(',').withQuoteChar('\"').build()).build();
				String[] csvLine = null;
				while ((csvLine = csvReader.readNext()) != null) {
					Integer conceptId = Integer.parseInt(csvLine[testConceptId]);
					if(list.containsKey(conceptId)){
						Object[] row = list.get(conceptId);
						row[1] = ((BigDecimal)row[1]).add(new BigDecimal(csvLine[tat]));
						row[2] = ((Integer)row[2]) + 1;
					}else{
						list.put(conceptId, new Object[]{csvLine[testName], new BigDecimal(csvLine[tat]), Integer.valueOf(1)});
					}
				}
			}

			if(!list.isEmpty()) {
				TestConfigSearchFilter testConfigFilter = new TestConfigSearchFilter();
				Map<Integer, List<TestConfigDTO>> testConfigResult = labManagementService.findTestConfigurations(testConfigFilter)
						.getData().stream().collect(Collectors.groupingBy(TestConfigDTO::getTestId));

				writer = Files.newBufferedWriter(resultsFile.toPath(), StandardCharsets.UTF_8,
						StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
				csvWriter = createCsvWriter(writer);
				writeHeaders(csvWriter);

				for (Map.Entry<Integer, Object[]> row : list.entrySet()) {
					String group = null;
					List<TestConfigDTO> testConfigs =  testConfigResult.getOrDefault(row.getKey(), null);
					if(testConfigs != null && !testConfigs.isEmpty()) {
						group = testConfigs.get(0).getTestGroupName();
					}
					writeRow(csvWriter, row.getValue(), group);
				}

				csvWriter.flush();


			}
			long fileSizeInBytes = 0;
			if(csvWriter != null) {
				csvWriter.close();
				fileSizeInBytes = Files.size(resultsFile.toPath());
			}
			completeBatchJob(batchJob, fileSizeInBytes, "csv", fileSizeInBytes <= (1024 * 1024), labManagementService);
		}
		catch (IOException e) {
			labManagementService.failBatchJob(batchJob.getUuid(), "Input/Output error: " + e.getMessage());
			log.error(e.getMessage(), e);
		}
		finally {
			if (csvWriter != null) {
				try {
					try {
						csvWriter.flush();
					}
					catch (Exception e) {}
					csvWriter.close();
				}
				catch (Exception csvWriterException) {}
			}
			if (writer != null) {
				try {
					try {
						writer.flush();
					}
					catch (Exception e) {}
					writer.close();
				}
				catch (Exception we) {}
			}
		}

	}

	protected void writeRow(CSVWriter csvWriter, Object[] row, String group) {

		BigDecimal turnAroundTime = ((BigDecimal) row[1]).divide(BigDecimal.valueOf((Integer)row[2]), BigDecimal.ROUND_FLOOR).setScale(0, BigDecimal.ROUND_FLOOR);
		writeLineToCsv(csvWriter, (String)row[0], group,
				TurnAroundTimeCalculator.formatTurnAroundTime(turnAroundTime),
				turnAroundTime.divide(BigDecimal.valueOf(60000), BigDecimal.ROUND_FLOOR).setScale(0, BigDecimal.ROUND_FLOOR).toBigInteger().toString()
				);
	}


	protected void writeHeaders(CSVWriter csvWriter) {
		writeLineToCsv(csvWriter,
				"Test",
				"Group",
				"Average Turn Around Time",
				"Average Turn Around Time Minutes");
	}

	protected boolean requireTurnAroundTime(TestRequestReportItemFilter filter, Function<BatchJob, Boolean> shouldStopExecution, GenericObject parameters) throws IOException {
		step1File =  new File(FileUtil.getBatchJobFolder(), batchJob.getUuid()+".step1");
		if(hasRestoredExecutionState && executionStep > 0 && step1File.exists()){
			return true;
		}
		BufferedWriter bufferedWriter = null;
		try{
			bufferedWriter = Files.newBufferedWriter(step1File.toPath(), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			CSVWriter step1Writer = createCsvWriter(bufferedWriter);
			boolean hasMoreRecords = true;
			while (hasMoreRecords) {
				if (shouldStopExecution.apply(batchJob)) {
					setAbortReadingTat(1);
					return false;
				}
				filter.setStartIndex(pageIndex);
				filter.setTestRequestItemIdMin(lastRecordProcessed);
				filter.setTestRequestIdMin(lastTestRequestProcessed);

				Result<TestRequestReportItem> data = labManagementService.findTestRequestReportItems(filter);
				if (shouldStopExecution.apply(batchJob)) {
					return false;
				}

				if (!data.getData().isEmpty()) {
					int count = 100;
					for (TestRequestReportItem row : data.getData()) {
						writeLineToCsv(step1Writer, Integer.toString(row.getOrderConceptId()),
								formatTestName(row.getTestName(), row.getTestShortName()),
							Long.toString(TurnAroundTimeCalculator.getTurnAroundTime(getTurnAroundStartDate(row.getCollectionDate(),row.getRequestApprovalDate() ,row.getDateCreated()),
										row.getResultDate() != null ? row.getResultDate() : row.getCompletedDate())));
						count--;
						if(count == 0){
							step1Writer.flush();
							count = 100;
						}
					}
					step1Writer.flush();
					recordsProcessed += data.getData().size();
					pageIndex++;
					TestRequestReportItem lastRecord = data.getData().get(data.getData().size() - 1);
					updateExecutionState(batchJob, executionState, pageIndex, recordsProcessed, lastRecord.getTestRequestItemId(), lastRecord.getTestRequestId(), labManagementService, 0);
				} else if (pageIndex == 0) {
					updateExecutionState(batchJob, executionState, pageIndex, recordsProcessed, null, null,
							labManagementService, 0);
				}

				hasMoreRecords = data.getData().size() >= pageSize;
			}

			if(abortReadingTat > 0){
				return false;
			}
		} catch (IOException e) {
			setAbortReadingTat(2);
			log.error(e.getMessage(), e);
			return false;
		}
		finally {
			if(bufferedWriter != null){
				try{
					try {
						bufferedWriter.flush();
					}catch (Exception e){}
					bufferedWriter.close();
				}catch (Exception exception){
				}
			}
		}
		return true;
	}
}
